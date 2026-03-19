package com.smartgas.app.domain.model

import java.time.Instant

/**
 * Domain entity representing a fuel transaction in SmartGas.
 *
 * A [FuelTransaction] captures every financial event related to fuel — including
 * direct purchases and the "Price Lock" hedge mechanism, where a user locks a
 * price-per-litre for a future fill-up against potential price increases.
 */
data class FuelTransaction(
    /** Unique identifier (UUID string). Empty until persisted. */
    val id: String = "",

    /** Firebase UID of the transaction owner. */
    val userId: String,

    /** Classification of this transaction. */
    val type: TransactionType,

    /** Current status of the transaction. */
    val status: TransactionStatus,

    /** Volume of fuel in litres covered by this transaction. */
    val litres: Double,

    /** Price per litre at the time of creation (in PHP). */
    val pricePerLitre: Double,

    /**
     * Locked price per litre for [TransactionType.PRICE_LOCK] transactions.
     * Null for regular purchases.
     */
    val lockedPricePerLitre: Double? = null,

    /**
     * Wallet credit balance deducted for this transaction (in PHP).
     * Equals [litres] × [pricePerLitre] for purchases;
     * equals [litres] × [lockedPricePerLitre] when a lock is exercised.
     */
    val totalAmount: Double,

    /** Epoch-millis timestamp when the transaction was created. */
    val createdAt: Long = Instant.now().toEpochMilli(),

    /**
     * Epoch-millis timestamp at which a [TransactionType.PRICE_LOCK] expires.
     * Null for regular purchases.
     */
    val lockExpiresAt: Long? = null,

    /** Human-readable note or reference (e.g. station name). */
    val note: String = "",
) {
    /**
     * Returns true if this is a price-lock transaction that has not yet expired
     * and is in a [TransactionStatus.LOCKED] state.
     */
    val isActiveLock: Boolean
        get() = type == TransactionType.PRICE_LOCK &&
                status == TransactionStatus.LOCKED &&
                lockExpiresAt != null &&
                lockExpiresAt > Instant.now().toEpochMilli()

    /**
     * Calculates the savings (in PHP) achieved by exercising a price lock,
     * i.e., market price minus locked price multiplied by litres.
     * Returns null when no locked price is available.
     */
    fun lockSavings(currentMarketPrice: Double): Double? =
        lockedPricePerLitre?.let { locked ->
            (currentMarketPrice - locked) * litres
        }
}

/** Broad categories of fuel-related financial events. */
enum class TransactionType {
    /** Standard fuel purchase deducted from the wallet. */
    PURCHASE,

    /**
     * User has hedged against price increases by locking a price per litre
     * for a specified volume and time window.
     */
    PRICE_LOCK,

    /** Top-up of fuel credits into the virtual wallet. */
    WALLET_TOP_UP,

    /** Refund of credits back to the wallet (e.g. unused lock). */
    REFUND,
}

/** Lifecycle states a [FuelTransaction] can be in. */
enum class TransactionStatus {
    /** Transaction has been submitted but not yet processed. */
    PENDING,

    /** Transaction has been successfully completed. */
    COMPLETED,

    /**
     * A [TransactionType.PRICE_LOCK] that is currently active.
     * Transitions to [COMPLETED] when exercised or [CANCELLED] when expired.
     */
    LOCKED,

    /** Transaction was revoked or timed out before completion. */
    CANCELLED,
}
