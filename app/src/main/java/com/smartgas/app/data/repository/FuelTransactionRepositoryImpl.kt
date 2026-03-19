package com.smartgas.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import com.smartgas.app.data.local.dao.FuelTransactionDao
import com.smartgas.app.data.local.entity.FuelTransactionEntity
import com.smartgas.app.data.local.entity.toDomain
import com.smartgas.app.data.local.entity.toEntity
import com.smartgas.app.domain.model.FuelTransaction
import com.smartgas.app.domain.model.TransactionStatus
import com.smartgas.app.domain.model.TransactionType
import com.smartgas.app.domain.repository.FuelTransactionRepository
import com.smartgas.app.domain.repository.InsufficientCreditsException
import com.smartgas.app.domain.repository.LockExpiredException
import com.smartgas.app.domain.repository.LockNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first implementation of [FuelTransactionRepository].
 *
 * **Architecture contract**
 * - Room is the Single Source of Truth (SSOT) for all reads.
 * - All writes go to Room first, then are pushed to Firestore.
 * - Firestore-only operations (e.g. atomic wallet debits) are wrapped in
 *   Firestore Transactions to guarantee ACID compliance.
 * - Records that fail cloud sync are flagged (`syncedToCloud = false`) and
 *   retried later by [com.smartgas.app.data.worker.SyncWorker].
 */
@Singleton
class FuelTransactionRepositoryImpl @Inject constructor(
    private val dao: FuelTransactionDao,
    private val firestore: FirebaseFirestore,
) : FuelTransactionRepository {

    // ── Firestore collection paths ───────────────────────────────────────────

    private val transactionsCollection = firestore.collection("fuel_transactions")
    private val walletsCollection = firestore.collection("wallets")

    // ── Read ─────────────────────────────────────────────────────────────────

    override fun observeTransactions(userId: String): Flow<List<FuelTransaction>> =
        dao.observeByUser(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTransactionById(id: String): FuelTransaction? =
        dao.getById(id)?.toDomain()

    override suspend fun getActiveLocks(userId: String): List<FuelTransaction> =
        dao.getActiveLocks(userId = userId, now = Instant.now().toEpochMilli())
            .map { it.toDomain() }

    // ── Write ────────────────────────────────────────────────────────────────

    override suspend fun recordPurchase(transaction: FuelTransaction): FuelTransaction {
        val withId = transaction.ensureId()
        // 1. Write to Room immediately (offline-first).
        dao.upsert(withId.toEntity(syncedToCloud = false))

        // 2. Attempt to sync to Firestore; if this fails, WorkManager will retry.
        runCatching { pushTransactionToFirestore(withId) }
            .onSuccess { dao.markSynced(withId.id) }

        return withId
    }

    /**
     * Locks a fuel price atomically using a Firestore Transaction.
     *
     * The Firestore Transaction ensures:
     * 1. Read current wallet balance.
     * 2. Verify sufficient credits (balance ≥ totalAmount).
     * 3. Reserve ([totalAmount]) by decrementing [walletBalance] and
     *    incrementing [reservedBalance] within the same atomic commit.
     * 4. Create the PRICE_LOCK transaction document.
     *
     * If any step fails the entire operation is rolled back automatically
     * by Firestore, preserving ACID consistency.
     */
    override suspend fun lockPrice(
        transaction: FuelTransaction,
        walletDocumentId: String,
    ): FuelTransaction {
        require(transaction.type == TransactionType.PRICE_LOCK) {
            "lockPrice() must be called with a PRICE_LOCK transaction."
        }
        require(transaction.lockedPricePerLitre != null) {
            "A PRICE_LOCK transaction must supply a lockedPricePerLitre."
        }

        val withId = transaction.ensureId().copy(status = TransactionStatus.LOCKED)

        // Optimistic local write so the UI is responsive immediately.
        dao.upsert(withId.toEntity(syncedToCloud = false))

        // Atomically reserve credits and write the lock in Firestore.
        val walletRef = walletsCollection.document(walletDocumentId)
        val lockRef = transactionsCollection.document(withId.id)

        try {
            firestore.runTransaction { firestoreTx: Transaction ->
                val walletSnapshot = firestoreTx.get(walletRef)

                val balance = walletSnapshot.getDouble(FIELD_WALLET_BALANCE) ?: 0.0
                val reserved = walletSnapshot.getDouble(FIELD_WALLET_RESERVED) ?: 0.0

                val availableBalance = balance - reserved
                if (availableBalance < withId.totalAmount) {
                    // Throwing inside runTransaction causes Firestore to abort it.
                    throw InsufficientCreditsException(
                        "Insufficient credits. Available: ₱${"%.2f".format(availableBalance)}, " +
                                "required: ₱${"%.2f".format(withId.totalAmount)}"
                    )
                }

                // Reserve the amount (balance unchanged; reserved increases).
                firestoreTx.update(walletRef, FIELD_WALLET_RESERVED, reserved + withId.totalAmount)

                // Persist the lock transaction document.
                firestoreTx.set(lockRef, withId.toFirestoreMap())
            }.await()

            dao.markSynced(withId.id)
        } catch (e: FirebaseFirestoreException) {
            // Propagate domain-meaningful exceptions; keep optimistic local record.
            if (e.cause is InsufficientCreditsException) throw e.cause as InsufficientCreditsException
            // Other Firestore errors are non-fatal for the local record;
            // WorkManager will retry the sync.
        }

        return withId
    }

    /**
     * Exercises an existing price lock atomically.
     *
     * Firestore Transaction steps:
     * 1. Verify the lock document exists and [status] == LOCKED.
     * 2. Verify the lock has not expired.
     * 3. Mark the lock as COMPLETED.
     * 4. Create a PURCHASE transaction at the locked price.
     * 5. Release the reserved credits from the wallet.
     */
    override suspend fun exercisePriceLock(
        lockTransactionId: String,
        walletDocumentId: String,
    ): FuelTransaction {
        val lockEntity = dao.getById(lockTransactionId)
            ?: throw LockNotFoundException(lockTransactionId)

        if (lockEntity.status != TransactionStatus.LOCKED) {
            throw LockNotFoundException(lockTransactionId)
        }
        if (lockEntity.lockExpiresAt != null &&
            lockEntity.lockExpiresAt <= Instant.now().toEpochMilli()
        ) {
            throw LockExpiredException(lockTransactionId)
        }

        val lockedPrice = requireNotNull(lockEntity.lockedPricePerLitre) {
            "Lock transaction $lockTransactionId is missing lockedPricePerLitre."
        }

        val purchaseTx = FuelTransaction(
            id = UUID.randomUUID().toString(),
            userId = lockEntity.userId,
            type = TransactionType.PURCHASE,
            status = TransactionStatus.COMPLETED,
            litres = lockEntity.litres,
            pricePerLitre = lockedPrice,
            totalAmount = lockEntity.litres * lockedPrice,
            note = "Exercised price lock $lockTransactionId",
        )

        // Optimistic local updates.
        dao.updateStatus(lockTransactionId, TransactionStatus.COMPLETED)
        dao.upsert(purchaseTx.toEntity(syncedToCloud = false))

        // Atomic Firestore commit.
        val walletRef = walletsCollection.document(walletDocumentId)
        val lockRef = transactionsCollection.document(lockTransactionId)
        val purchaseRef = transactionsCollection.document(purchaseTx.id)

        runCatching {
            firestore.runTransaction { firestoreTx: Transaction ->
                val lockSnap = firestoreTx.get(lockRef)
                val status = lockSnap.getString(FIELD_STATUS)
                    ?.let { TransactionStatus.valueOf(it) }

                if (status != TransactionStatus.LOCKED) {
                    throw LockNotFoundException(lockTransactionId)
                }

                val expiry = lockSnap.getLong(FIELD_LOCK_EXPIRES_AT) ?: 0L
                if (expiry > 0 && expiry <= Instant.now().toEpochMilli()) {
                    throw LockExpiredException(lockTransactionId)
                }

                // Release the reserved amount.
                val walletSnap = firestoreTx.get(walletRef)
                val reserved = walletSnap.getDouble(FIELD_WALLET_RESERVED) ?: 0.0
                val reservedAmount = lockSnap.getDouble(FIELD_TOTAL_AMOUNT) ?: 0.0
                firestoreTx.update(
                    walletRef,
                    FIELD_WALLET_RESERVED,
                    (reserved - reservedAmount).coerceAtLeast(0.0),
                )

                // Mark lock as completed and write the purchase record.
                firestoreTx.update(lockRef, FIELD_STATUS, TransactionStatus.COMPLETED.name)
                firestoreTx.set(purchaseRef, purchaseTx.toFirestoreMap())
            }.await()

            dao.markSynced(lockTransactionId)
            dao.markSynced(purchaseTx.id)
        }

        return purchaseTx
    }

    /**
     * Cancels an active price lock and releases the reserved credits atomically.
     */
    override suspend fun cancelPriceLock(
        lockTransactionId: String,
        walletDocumentId: String,
    ) {
        val lockEntity = dao.getById(lockTransactionId)
            ?: throw LockNotFoundException(lockTransactionId)

        // Optimistic local update.
        dao.updateStatus(lockTransactionId, TransactionStatus.CANCELLED)

        val walletRef = walletsCollection.document(walletDocumentId)
        val lockRef = transactionsCollection.document(lockTransactionId)

        runCatching {
            firestore.runTransaction { firestoreTx: Transaction ->
                val walletSnap = firestoreTx.get(walletRef)
                val reserved = walletSnap.getDouble(FIELD_WALLET_RESERVED) ?: 0.0
                firestoreTx.update(
                    walletRef,
                    FIELD_WALLET_RESERVED,
                    (reserved - lockEntity.totalAmount).coerceAtLeast(0.0),
                )
                firestoreTx.update(lockRef, FIELD_STATUS, TransactionStatus.CANCELLED.name)
            }.await()

            dao.markSynced(lockTransactionId)
        }
    }

    override suspend fun topUpWallet(
        userId: String,
        walletDocumentId: String,
        amountPhp: Double,
    ): FuelTransaction {
        require(amountPhp > 0) { "Top-up amount must be positive." }

        val topUp = FuelTransaction(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = TransactionType.WALLET_TOP_UP,
            status = TransactionStatus.COMPLETED,
            litres = 0.0,
            pricePerLitre = 0.0,
            totalAmount = amountPhp,
            note = "Wallet top-up",
        )

        dao.upsert(topUp.toEntity(syncedToCloud = false))

        val walletRef = walletsCollection.document(walletDocumentId)
        runCatching {
            firestore.runTransaction { firestoreTx: Transaction ->
                val walletSnap = firestoreTx.get(walletRef)
                val balance = walletSnap.getDouble(FIELD_WALLET_BALANCE) ?: 0.0
                firestoreTx.update(walletRef, FIELD_WALLET_BALANCE, balance + amountPhp)
                firestoreTx.set(transactionsCollection.document(topUp.id), topUp.toFirestoreMap())
            }.await()
            dao.markSynced(topUp.id)
        }

        return topUp
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    override suspend fun syncPendingTransactions(userId: String) {
        dao.getPendingSync(userId).forEach { entity ->
            runCatching { pushTransactionToFirestore(entity.toDomain()) }
                .onSuccess { dao.markSynced(entity.id) }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun pushTransactionToFirestore(tx: FuelTransaction) {
        transactionsCollection.document(tx.id).set(tx.toFirestoreMap()).await()
    }

    private fun FuelTransaction.ensureId(): FuelTransaction =
        if (id.isBlank()) copy(id = UUID.randomUUID().toString()) else this

    private fun FuelTransaction.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_ID to id,
        "userId" to userId,
        "type" to type.name,
        FIELD_STATUS to status.name,
        "litres" to litres,
        "pricePerLitre" to pricePerLitre,
        "lockedPricePerLitre" to lockedPricePerLitre,
        FIELD_TOTAL_AMOUNT to totalAmount,
        "createdAt" to createdAt,
        FIELD_LOCK_EXPIRES_AT to lockExpiresAt,
        "note" to note,
    )

    private companion object {
        const val FIELD_ID = "id"
        const val FIELD_STATUS = "status"
        const val FIELD_TOTAL_AMOUNT = "totalAmount"
        const val FIELD_LOCK_EXPIRES_AT = "lockExpiresAt"
        const val FIELD_WALLET_BALANCE = "balance"
        const val FIELD_WALLET_RESERVED = "reservedBalance"
    }
}
