package com.smartgas.app.domain.repository

import com.smartgas.app.domain.model.FuelTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Contract for all fuel-transaction persistence and business operations.
 *
 * Implementations are expected to be **offline-first**: Room acts as the
 * Single Source of Truth (SSOT), and Firestore is used for cloud sync.
 */
interface FuelTransactionRepository {

    // ── Read ────────────────────────────────────────────────────────────────

    /**
     * Observe all transactions for [userId], ordered newest-first.
     * Emits whenever the local Room database is updated.
     */
    fun observeTransactions(userId: String): Flow<List<FuelTransaction>>

    /**
     * Retrieve a single transaction by [id], or null if not found.
     */
    suspend fun getTransactionById(id: String): FuelTransaction?

    /**
     * Returns only the currently active price-lock transactions for [userId].
     */
    suspend fun getActiveLocks(userId: String): List<FuelTransaction>

    // ── Write ───────────────────────────────────────────────────────────────

    /**
     * Record a direct fuel purchase, deducting [transaction.totalAmount] from
     * the user's wallet balance.
     *
     * Persists locally first (Room), then syncs to Firestore.
     *
     * @return The saved [FuelTransaction] with its assigned [FuelTransaction.id].
     */
    suspend fun recordPurchase(transaction: FuelTransaction): FuelTransaction

    /**
     * Atomically lock a fuel price using a Firestore transaction, ensuring that:
     * 1. The wallet has sufficient credit.
     * 2. The locked amount is reserved (held) in the wallet.
     * 3. A PRICE_LOCK [FuelTransaction] record is created.
     *
     * This operation writes optimistically to Room first so the UI is
     * responsive even while the Firestore commit is in flight.
     *
     * @param transaction A [FuelTransaction] with type = PRICE_LOCK.
     * @param walletDocumentId Firestore document ID of the user's wallet.
     * @throws InsufficientCreditsException if the wallet balance is too low.
     * @return The persisted [FuelTransaction] with its assigned id.
     */
    suspend fun lockPrice(
        transaction: FuelTransaction,
        walletDocumentId: String,
    ): FuelTransaction

    /**
     * Exercise an existing price lock identified by [lockTransactionId].
     *
     * Atomically:
     * 1. Marks the lock transaction as COMPLETED.
     * 2. Creates a PURCHASE record at the locked price.
     * 3. Releases any residual reserved credits back to the wallet.
     *
     * @throws LockExpiredException if the lock has already expired.
     * @throws LockNotFoundException if no active lock with [lockTransactionId] exists.
     */
    suspend fun exercisePriceLock(
        lockTransactionId: String,
        walletDocumentId: String,
    ): FuelTransaction

    /**
     * Cancel an active price lock identified by [lockTransactionId],
     * restoring the reserved credits to the wallet.
     */
    suspend fun cancelPriceLock(
        lockTransactionId: String,
        walletDocumentId: String,
    )

    /**
     * Top up the virtual wallet by [amountPhp].
     *
     * @return The wallet top-up [FuelTransaction].
     */
    suspend fun topUpWallet(
        userId: String,
        walletDocumentId: String,
        amountPhp: Double,
    ): FuelTransaction

    // ── Sync ────────────────────────────────────────────────────────────────

    /**
     * Force a one-time sync of pending local transactions to Firestore.
     * Normally handled by WorkManager, but exposed here for on-demand use.
     */
    suspend fun syncPendingTransactions(userId: String)
}

// ── Domain Exceptions ───────────────────────────────────────────────────────

/** Thrown when a price-lock is attempted but the wallet balance is insufficient. */
class InsufficientCreditsException(message: String) : Exception(message)

/** Thrown when trying to exercise a lock that has passed its expiry time. */
class LockExpiredException(lockId: String) :
    Exception("Price lock '$lockId' has expired and can no longer be exercised.")

/** Thrown when a lock transaction cannot be found or is not in LOCKED status. */
class LockNotFoundException(lockId: String) :
    Exception("Active price lock '$lockId' not found.")
