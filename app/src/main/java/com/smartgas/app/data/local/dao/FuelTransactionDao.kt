package com.smartgas.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartgas.app.data.local.entity.FuelTransactionEntity
import com.smartgas.app.domain.model.TransactionStatus
import com.smartgas.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [FuelTransactionEntity].
 *
 * All suspend functions are safe to call from any coroutine dispatcher;
 * Room automatically moves execution off the main thread.
 */
@Dao
interface FuelTransactionDao {

    // ── Inserts ──────────────────────────────────────────────────────────────

    /**
     * Insert or replace a transaction (upsert semantics).
     * Used for both local-first writes and Firestore sync reconciliation.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FuelTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FuelTransactionEntity>)

    // ── Updates ──────────────────────────────────────────────────────────────

    @Update
    suspend fun update(entity: FuelTransactionEntity)

    @Query("UPDATE fuel_transactions SET syncedToCloud = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE fuel_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: TransactionStatus)

    // ── Reads ────────────────────────────────────────────────────────────────

    @Query(
        "SELECT * FROM fuel_transactions WHERE userId = :userId " +
                "ORDER BY createdAt DESC"
    )
    fun observeByUser(userId: String): Flow<List<FuelTransactionEntity>>

    @Query("SELECT * FROM fuel_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FuelTransactionEntity?

    @Query(
        "SELECT * FROM fuel_transactions " +
                "WHERE userId = :userId " +
                "AND type = :type " +
                "AND status = :status " +
                "AND lockExpiresAt > :now"
    )
    suspend fun getActiveLocks(
        userId: String,
        type: TransactionType = TransactionType.PRICE_LOCK,
        status: TransactionStatus = TransactionStatus.LOCKED,
        now: Long,
    ): List<FuelTransactionEntity>

    /** Returns all records that have not yet been pushed to Firestore. */
    @Query("SELECT * FROM fuel_transactions WHERE syncedToCloud = 0 AND userId = :userId")
    suspend fun getPendingSync(userId: String): List<FuelTransactionEntity>

    // ── Deletes ──────────────────────────────────────────────────────────────

    @Query("DELETE FROM fuel_transactions WHERE id = :id")
    suspend fun deleteById(id: String)
}
