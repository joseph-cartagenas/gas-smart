package com.smartgas.app.presentation.calculator

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import javax.inject.Inject

/**
 * ViewModel for the Traffic-Aware Advanced Calculator screen.
 *
 * Exposes a single [StateFlow] of [CalculatorUiState] and a set of
 * state-update functions that are called directly from Compose via
 * state hoisting — no side effects, no coroutines required for pure math.
 *
 * **Formula:**
 * ```
 * Total Cost = (Distance / Fuel Efficiency) × Price × Traffic Multiplier
 * ```
 */
@HiltViewModel
class CalculatorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    // ── User-driven updates ──────────────────────────────────────────────────

    fun onDistanceChanged(km: String) {
        _uiState.update { it.copy(distanceKm = km).recalculate() }
    }

    fun onFuelEfficiencyChanged(kmPerLitre: String) {
        _uiState.update { it.copy(fuelEfficiencyKmPerLitre = kmPerLitre).recalculate() }
    }

    fun onPricePerLitreChanged(php: String) {
        _uiState.update { it.copy(pricePerLitrePhp = php).recalculate() }
    }

    /**
     * Slider value in [0f, 1f] maps to a traffic multiplier of 1.0x–2.0x.
     * At 0 the road is clear; at 1 the road is at maximum congestion.
     */
    fun onTrafficIntensityChanged(intensity: Float) {
        _uiState.update { it.copy(trafficIntensity = intensity).recalculate() }
    }

    /**
     * Override the time used for Manila peak-hour detection.
     * Defaults to the current wall-clock time if not set.
     */
    fun onTimeChanged(time: LocalTime) {
        _uiState.update { it.copy(selectedTime = time).recalculate() }
    }

    fun onUseCurrentTimeChanged(useCurrentTime: Boolean) {
        _uiState.update {
            it.copy(
                useCurrentTime = useCurrentTime,
                selectedTime = if (useCurrentTime) LocalTime.now() else it.selectedTime,
            ).recalculate()
        }
    }
}

// ── UI state ─────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of the calculator screen state.
 *
 * String fields for numeric inputs allow partial / in-progress input
 * without losing the cursor position, which a Double field would cause.
 */
data class CalculatorUiState(
    val distanceKm: String = "",
    val fuelEfficiencyKmPerLitre: String = "",
    val pricePerLitrePhp: String = "",

    /** Normalised traffic intensity slider value: 0f = clear, 1f = standstill. */
    val trafficIntensity: Float = 0f,

    /** Time used to auto-detect Manila peak hours. */
    val selectedTime: LocalTime = LocalTime.now(),

    /** When true [selectedTime] tracks wall-clock time. */
    val useCurrentTime: Boolean = true,

    // ── Derived / output ────────────────────────────────────────────────────

    /** Whether the currently selected time falls within Manila peak hours. */
    val isManilaRushHour: Boolean = false,

    /**
     * Effective traffic multiplier applied to the cost formula.
     * Range: 1.0x (no traffic) → 2.0x (worst congestion).
     */
    val trafficMultiplier: Double = 1.0,

    /** Litres of fuel required for the trip, or null if inputs are invalid. */
    val fuelRequiredLitres: Double? = null,

    /** Total trip cost in PHP, or null if inputs are invalid. */
    val totalCostPhp: Double? = null,

    /** Validation error message, or null when inputs are valid. */
    val inputError: String? = null,
) {
    /**
     * Recalculates all derived fields from the current raw inputs.
     * Called after every user interaction.
     */
    internal fun recalculate(): CalculatorUiState {
        val time = if (useCurrentTime) LocalTime.now() else selectedTime
        val rushHour = isManilaRushHour(time)
        val multiplier = computeTrafficMultiplier(trafficIntensity, rushHour)

        val distance = distanceKm.toDoubleOrNull()
        val efficiency = fuelEfficiencyKmPerLitre.toDoubleOrNull()
        val price = pricePerLitrePhp.toDoubleOrNull()

        val error = when {
            distanceKm.isNotBlank() && distance == null -> "Distance must be a number"
            distance != null && distance <= 0 -> "Distance must be greater than 0"
            fuelEfficiencyKmPerLitre.isNotBlank() && efficiency == null ->
                "Fuel efficiency must be a number"
            efficiency != null && efficiency <= 0 -> "Fuel efficiency must be greater than 0"
            pricePerLitrePhp.isNotBlank() && price == null -> "Price must be a number"
            price != null && price <= 0 -> "Price must be greater than 0"
            else -> null
        }

        val litres = if (distance != null && efficiency != null && efficiency > 0 && error == null)
            distance / efficiency else null

        val totalCost = if (litres != null && price != null && error == null)
            litres * price * multiplier else null

        return copy(
            isManilaRushHour = rushHour,
            trafficMultiplier = multiplier,
            fuelRequiredLitres = litres,
            totalCostPhp = totalCost,
            inputError = error,
        )
    }
}

// ── Manila peak-hour & traffic-multiplier logic ───────────────────────────────

/**
 * Returns true if [time] falls within Metro Manila's defined peak/rush hours:
 * - Morning rush: 07:00 – 10:00
 * - Evening rush: 17:00 – 21:00
 */
internal fun isManilaRushHour(time: LocalTime): Boolean {
    val morningStart = LocalTime.of(7, 0)
    val morningEnd = LocalTime.of(10, 0)
    val eveningStart = LocalTime.of(17, 0)
    val eveningEnd = LocalTime.of(21, 0)

    return time.isBetween(morningStart, morningEnd) ||
            time.isBetween(eveningStart, eveningEnd)
}

/**
 * Maps the user-set [intensity] slider (0f–1f) to a fuel cost multiplier.
 *
 * During Manila rush hour the base multiplier floor is raised to **1.5×**,
 * matching the calibrated peak-hours constant specified in the requirements.
 * The maximum multiplier is capped at **2.0×** to avoid extreme estimates.
 *
 * | Condition       | Slider = 0  | Slider = 1  |
 * |-----------------|-------------|-------------|
 * | Off-peak        | 1.0×        | 2.0×        |
 * | Rush hour       | 1.5×        | 2.0×        |
 */
internal fun computeTrafficMultiplier(intensity: Float, isRushHour: Boolean): Double {
    val floor = if (isRushHour) RUSH_HOUR_BASE_MULTIPLIER else 1.0
    return floor + (MAX_MULTIPLIER - floor) * intensity.toDouble()
}

/** Manila peak-hour baseline multiplier (1.5× as specified in the requirements). */
internal const val RUSH_HOUR_BASE_MULTIPLIER = 1.5

/** Maximum traffic multiplier (full-congestion standstill). */
internal const val MAX_MULTIPLIER = 2.0

// Extension to keep the range check readable.
private fun LocalTime.isBetween(start: LocalTime, end: LocalTime): Boolean =
    this >= start && this < end
