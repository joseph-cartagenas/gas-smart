package com.smartgas.app.presentation.calculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

/**
 * Traffic-Aware Advanced Calculator screen.
 *
 * Applies the formula:
 * ```
 * Total Cost = (Distance / Fuel Efficiency) × Price × Traffic Multiplier
 * ```
 *
 * The Traffic Multiplier is derived from two sources:
 * 1. A manual slider set by the user (0 = clear road, max = standstill).
 * 2. Automatic Manila rush-hour detection (floor raised to 1.5× during peak hours).
 *
 * All state is hoisted to [CalculatorViewModel] and collected via [StateFlow],
 * ensuring instant, reactive recalculation on every input change.
 */
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CalculatorContent(
        uiState = uiState,
        onDistanceChanged = viewModel::onDistanceChanged,
        onFuelEfficiencyChanged = viewModel::onFuelEfficiencyChanged,
        onPricePerLitreChanged = viewModel::onPricePerLitreChanged,
        onTrafficIntensityChanged = viewModel::onTrafficIntensityChanged,
        onUseCurrentTimeChanged = viewModel::onUseCurrentTimeChanged,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalculatorContent(
    uiState: CalculatorUiState,
    onDistanceChanged: (String) -> Unit,
    onFuelEfficiencyChanged: (String) -> Unit,
    onPricePerLitreChanged: (String) -> Unit,
    onTrafficIntensityChanged: (Float) -> Unit,
    onUseCurrentTimeChanged: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Advanced Calculator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Inputs card ─────────────────────────────────────────────────
            SectionCard(title = "Trip Details") {
                NumberInputField(
                    value = uiState.distanceKm,
                    onValueChange = onDistanceChanged,
                    label = "Distance (km)",
                    icon = Icons.Default.DirectionsCar,
                    placeholder = "e.g. 25",
                )
                NumberInputField(
                    value = uiState.fuelEfficiencyKmPerLitre,
                    onValueChange = onFuelEfficiencyChanged,
                    label = "Fuel Efficiency (km/L)",
                    icon = Icons.Default.Speed,
                    placeholder = "e.g. 12",
                )
                NumberInputField(
                    value = uiState.pricePerLitrePhp,
                    onValueChange = onPricePerLitreChanged,
                    label = "Fuel Price (₱/L)",
                    icon = Icons.Default.LocalGasStation,
                    placeholder = "e.g. 62.50",
                )
            }

            // ── Traffic card ────────────────────────────────────────────────
            SectionCard(title = "Traffic Conditions") {
                RushHourBanner(isRushHour = uiState.isManilaRushHour)

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Use current time",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = uiState.useCurrentTime,
                        onCheckedChange = onUseCurrentTimeChanged,
                    )
                }

                Text(
                    text = buildString {
                        append("Time: ")
                        append(uiState.selectedTime.format(TIME_FORMATTER))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Traffic,
                        contentDescription = null,
                        tint = trafficColor(uiState.trafficIntensity),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Traffic Intensity",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        trafficLabel(uiState.trafficIntensity),
                        style = MaterialTheme.typography.bodySmall,
                        color = trafficColor(uiState.trafficIntensity),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Slider(
                    value = uiState.trafficIntensity,
                    onValueChange = onTrafficIntensityChanged,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = trafficColor(uiState.trafficIntensity),
                        activeTrackColor = trafficColor(uiState.trafficIntensity),
                    ),
                )

                Text(
                    text = "Multiplier: ${"%.2f".format(uiState.trafficMultiplier)}×",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Validation error ────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.inputError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                uiState.inputError?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // ── Results card ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.totalCostPhp != null && uiState.inputError == null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ResultsCard(uiState = uiState)
            }
        }
    }
}

// ── Supporting composables ────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@Composable
private fun NumberInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.outline) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

@Composable
private fun RushHourBanner(isRushHour: Boolean) {
    val backgroundColor = if (isRushHour)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    val textColor = if (isRushHour)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    val message = if (isRushHour)
        "⚠ Manila Rush Hour — 1.5× base multiplier applied"
    else
        "✓ Off-peak hours — standard multiplier"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ResultsCard(uiState: CalculatorUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Trip Cost Estimate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            ResultRow(
                label = "Fuel Required",
                value = "${"%.2f".format(uiState.fuelRequiredLitres ?: 0.0)} L",
            )
            ResultRow(
                label = "Traffic Multiplier",
                value = "${"%.2f".format(uiState.trafficMultiplier)}×" +
                        if (uiState.isManilaRushHour) " (rush hour)" else "",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.shapes.medium,
                    )
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        text = "Total Cost",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "₱${"%.2f".format(uiState.totalCostPhp ?: 0.0)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = "Formula: (${uiState.distanceKm} km ÷ ${uiState.fuelEfficiencyKmPerLitre} km/L)" +
                        " × ₱${uiState.pricePerLitrePhp}/L" +
                        " × ${"%.2f".format(uiState.trafficMultiplier)}×",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun trafficColor(intensity: Float): Color = when {
    intensity < 0.33f -> Color(0xFF4CAF50) // Green — clear
    intensity < 0.66f -> Color(0xFFFFC107) // Amber — moderate
    else -> Color(0xFFF44336)               // Red — heavy
}

private fun trafficLabel(intensity: Float): String = when {
    intensity < 0.33f -> "Clear"
    intensity < 0.66f -> "Moderate"
    else -> "Heavy"
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CalculatorScreenPreview() {
    MaterialTheme {
        CalculatorContent(
            uiState = CalculatorUiState(
                distanceKm = "25",
                fuelEfficiencyKmPerLitre = "12",
                pricePerLitrePhp = "62.50",
                trafficIntensity = 0.6f,
                isManilaRushHour = true,
                trafficMultiplier = 1.7,
                fuelRequiredLitres = 2.083,
                totalCostPhp = 221.35,
                selectedTime = LocalTime.of(8, 30),
            ),
            onDistanceChanged = {},
            onFuelEfficiencyChanged = {},
            onPricePerLitreChanged = {},
            onTrafficIntensityChanged = {},
            onUseCurrentTimeChanged = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty state")
@Composable
private fun CalculatorScreenEmptyPreview() {
    MaterialTheme {
        CalculatorContent(
            uiState = CalculatorUiState(),
            onDistanceChanged = {},
            onFuelEfficiencyChanged = {},
            onPricePerLitreChanged = {},
            onTrafficIntensityChanged = {},
            onUseCurrentTimeChanged = {},
        )
    }
}
