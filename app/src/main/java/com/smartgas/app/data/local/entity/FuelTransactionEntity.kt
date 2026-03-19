package com.smartgas.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartgas.app.domain.model.FuelTransaction
import com.smartgas.app.domain.model.TransactionStatus
import com.smartgas.app.domain.model.TransactionType

/**
 * Room database entity that mirrors [FuelTransaction] for local persistence.
 *
 * An index on [userId] speeds up per-user queries (dashboard, history).
 * The [syncedToCloud] flag is used by WorkManager to identify records
 * that still need to be pushed to Firestore.
 */
@Entity(
    tableName = "fuel_transactions",
    indices = [Index(value = ["userId"]), Index(value = ["status"])],
)
data class FuelTransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val litres: Double,
    val pricePerLitre: Double,
    val lockedPricePerLitre: Double?,
    val totalAmount: Double,
    val createdAt: Long,
    val lockExpiresAt: Long?,
    val note: String,
    /** True once this record has been successfully written to Firestore. */
    val syncedToCloud: Boolean = false,
)

// ── Mapping extensions ───────────────────────────────────────────────────────

fun FuelTransactionEntity.toDomain(): FuelTransaction = FuelTransaction(
    id = id,
    userId = userId,
    type = type,
    status = status,
    litres = litres,
    pricePerLitre = pricePerLitre,
    lockedPricePerLitre = lockedPricePerLitre,
    totalAmount = totalAmount,
    createdAt = createdAt,
    lockExpiresAt = lockExpiresAt,
    note = note,
)

fun FuelTransaction.toEntity(syncedToCloud: Boolean = false): FuelTransactionEntity =
    FuelTransactionEntity(
        id = id,
        userId = userId,
        type = type,
        status = status,
        litres = litres,
        pricePerLitre = pricePerLitre,
        lockedPricePerLitre = lockedPricePerLitre,
        totalAmount = totalAmount,
        createdAt = createdAt,
        lockExpiresAt = lockExpiresAt,
        note = note,
        syncedToCloud = syncedToCloud,
    )
