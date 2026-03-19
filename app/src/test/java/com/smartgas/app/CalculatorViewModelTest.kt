package com.smartgas.app.presentation.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Unit tests for the traffic-aware cost calculation logic in [CalculatorViewModel].
 *
 * Pure Kotlin tests — no Android framework required.
 */
class CalculatorViewModelTest {

    // ── Manila rush-hour detection ────────────────────────────────────────────

    @Test
    fun `isManilaRushHour - morning rush start is detected`() {
        assertTrue(isManilaRushHour(LocalTime.of(7, 0)))
    }

    @Test
    fun `isManilaRushHour - morning rush mid-point is detected`() {
        assertTrue(isManilaRushHour(LocalTime.of(8, 30)))
    }

    @Test
    fun `isManilaRushHour - morning rush end boundary is NOT rush hour`() {
        // End is exclusive: 10:00 is NOT rush hour
        assertFalse(isManilaRushHour(LocalTime.of(10, 0)))
    }

    @Test
    fun `isManilaRushHour - midday is NOT rush hour`() {
        assertFalse(isManilaRushHour(LocalTime.of(12, 0)))
    }

    @Test
    fun `isManilaRushHour - evening rush start is detected`() {
        assertTrue(isManilaRushHour(LocalTime.of(17, 0)))
    }

    @Test
    fun `isManilaRushHour - evening rush mid-point is detected`() {
        assertTrue(isManilaRushHour(LocalTime.of(19, 15)))
    }

    @Test
    fun `isManilaRushHour - evening rush end boundary is NOT rush hour`() {
        // End is exclusive: 21:00 is NOT rush hour
        assertFalse(isManilaRushHour(LocalTime.of(21, 0)))
    }

    @Test
    fun `isManilaRushHour - late night is NOT rush hour`() {
        assertFalse(isManilaRushHour(LocalTime.of(23, 0)))
    }

    // ── Traffic multiplier ────────────────────────────────────────────────────

    @Test
    fun `computeTrafficMultiplier - clear road off-peak gives 1_0`() {
        val multiplier = computeTrafficMultiplier(intensity = 0f, isRushHour = false)
        assertEquals(1.0, multiplier, 0.001)
    }

    @Test
    fun `computeTrafficMultiplier - full congestion off-peak gives 2_0`() {
        val multiplier = computeTrafficMultiplier(intensity = 1f, isRushHour = false)
        assertEquals(2.0, multiplier, 0.001)
    }

    @Test
    fun `computeTrafficMultiplier - clear road rush hour gives 1_5 base`() {
        val multiplier = computeTrafficMultiplier(intensity = 0f, isRushHour = true)
        assertEquals(RUSH_HOUR_BASE_MULTIPLIER, multiplier, 0.001)
    }

    @Test
    fun `computeTrafficMultiplier - full congestion rush hour gives 2_0 max`() {
        val multiplier = computeTrafficMultiplier(intensity = 1f, isRushHour = true)
        assertEquals(MAX_MULTIPLIER, multiplier, 0.001)
    }

    @Test
    fun `computeTrafficMultiplier - mid slider off-peak gives 1_5`() {
        val multiplier = computeTrafficMultiplier(intensity = 0.5f, isRushHour = false)
        assertEquals(1.5, multiplier, 0.001)
    }

    // ── CalculatorUiState.recalculate() ─────────────────────────────────────

    @Test
    fun `recalculate - valid inputs produce correct total cost`() {
        val state = CalculatorUiState(
            distanceKm = "25",
            fuelEfficiencyKmPerLitre = "12.5",
            pricePerLitrePhp = "62",
            trafficIntensity = 0f,
            useCurrentTime = false,
            selectedTime = LocalTime.of(12, 0), // off-peak
        ).recalculate()

        // fuel = 25 / 12.5 = 2.0 L
        // cost = 2.0 × 62 × 1.0 = 124.0
        assertEquals(2.0, state.fuelRequiredLitres!!, 0.001)
        assertEquals(124.0, state.totalCostPhp!!, 0.001)
        assertNull(state.inputError)
        assertFalse(state.isManilaRushHour)
    }

    @Test
    fun `recalculate - rush hour applies 1_5 base multiplier`() {
        val state = CalculatorUiState(
            distanceKm = "20",
            fuelEfficiencyKmPerLitre = "10",
            pricePerLitrePhp = "60",
            trafficIntensity = 0f,  // slider at 0 → floor = 1.5 during rush
            useCurrentTime = false,
            selectedTime = LocalTime.of(8, 0), // morning rush
        ).recalculate()

        // fuel = 20 / 10 = 2.0 L
        // cost = 2.0 × 60 × 1.5 = 180.0
        assertTrue(state.isManilaRushHour)
        assertEquals(1.5, state.trafficMultiplier, 0.001)
        assertEquals(180.0, state.totalCostPhp!!, 0.001)
    }

    @Test
    fun `recalculate - zero distance yields validation error`() {
        val state = CalculatorUiState(
            distanceKm = "0",
            fuelEfficiencyKmPerLitre = "10",
            pricePerLitrePhp = "60",
        ).recalculate()

        assertEquals("Distance must be greater than 0", state.inputError)
        assertNull(state.totalCostPhp)
    }

    @Test
    fun `recalculate - non-numeric input yields validation error`() {
        val state = CalculatorUiState(
            distanceKm = "abc",
            fuelEfficiencyKmPerLitre = "10",
            pricePerLitrePhp = "60",
        ).recalculate()

        assertEquals("Distance must be a number", state.inputError)
        assertNull(state.totalCostPhp)
    }

    @Test
    fun `recalculate - empty inputs yield null cost without error`() {
        val state = CalculatorUiState().recalculate()
        assertNull(state.inputError)
        assertNull(state.totalCostPhp)
    }

    @Test
    fun `recalculate - max traffic intensity doubles cost off-peak`() {
        val baseState = CalculatorUiState(
            distanceKm = "10",
            fuelEfficiencyKmPerLitre = "10",
            pricePerLitrePhp = "50",
            trafficIntensity = 0f,
            useCurrentTime = false,
            selectedTime = LocalTime.of(12, 0),
        ).recalculate()

        val heavyState = baseState.copy(trafficIntensity = 1f).recalculate()

        // base cost = 1L × ₱50 × 1.0 = ₱50
        // heavy cost = 1L × ₱50 × 2.0 = ₱100
        assertEquals(50.0, baseState.totalCostPhp!!, 0.001)
        assertEquals(100.0, heavyState.totalCostPhp!!, 0.001)
    }
}
