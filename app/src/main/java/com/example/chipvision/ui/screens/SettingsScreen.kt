package com.example.chipvision.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chipvision.ui.AppScreen
import com.example.chipvision.ui.ChipVisionViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChipVisionViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section A: Detection
        SettingsSectionCard(
            title = "A. Detection Parameters",
            icon = Icons.Default.Tune
        ) {
            // Detector Threshold
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detector Confidence",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(settings.detectorThreshold * 100).roundToInt()}% (>= threshold)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "A detection is eligible when confidence is at least the configured threshold.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SliderWithStepControls(
                    value = settings.detectorThreshold,
                    onValueChange = { newVal ->
                        val rounded = (newVal * 100).roundToInt() / 100f
                        viewModel.updateSettings { it.copy(detectorThreshold = rounded) }
                    },
                    valueRange = 0.50f..0.98f,
                    step = 0.01f,
                    testTagPrefix = "detector_threshold"
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Classifier Threshold
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Classifier Acceptance",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(settings.classifierThreshold * 100).roundToInt()}% (>= 0.70)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Classification is accepted only when top class probability is at least threshold.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SliderWithStepControls(
                    value = settings.classifierThreshold,
                    onValueChange = { newVal ->
                        val rounded = (newVal * 100).roundToInt() / 100f
                        viewModel.updateSettings { it.copy(classifierThreshold = rounded) }
                    },
                    valueRange = 0.50f..0.95f,
                    step = 0.01f,
                    testTagPrefix = "classifier_threshold"
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Confidence Labels Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Show Confidence Labels",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Display percentage scores on classified bounding boxes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.showConfidenceLabels,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(showConfidenceLabels = checked) }
                    },
                    modifier = Modifier.testTag("confidence_labels_switch")
                )
            }
        }

        // Section B: Stability and Tracking
        SettingsSectionCard(
            title = "B. Stability & Tracking",
            icon = Icons.Default.Schedule
        ) {
            // Stability Window
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stability Verification Window",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${settings.stabilityDurationMs} ms (0.2s)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Camera must remain stable for 0.2 seconds before inspection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Minimum Stable Box IoU
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Minimum Stable Box IoU",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(settings.minStableBoxIou * 100).roundToInt()}%",
                        fontWeight = FontWeight.Bold
                    )
                }
                SliderWithStepControls(
                    value = settings.minStableBoxIou,
                    onValueChange = { newVal ->
                        val rounded = (newVal * 100).roundToInt() / 100f
                        viewModel.updateSettings { it.copy(minStableBoxIou = rounded) }
                    },
                    valueRange = 0.60f..0.95f,
                    step = 0.05f,
                    testTagPrefix = "iou"
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Width & Height change tolerances
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Max Dimension Variation",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${(settings.maxWidthChangeRatio * 100).roundToInt()}%",
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Movement center tolerance",
                style = MaterialTheme.typography.bodyMedium
            )
            SliderWithStepControls(
                value = settings.movementCenterToleranceRatio,
                onValueChange = { newVal ->
                    val rounded = (newVal * 100).roundToInt() / 100f
                    viewModel.updateSettings { it.copy(movementCenterToleranceRatio = rounded) }
                },
                valueRange = 0.02f..0.25f,
                step = 0.01f,
                testTagPrefix = "movement_center_tolerance"
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Movement IoU tolerance",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${(settings.movementIouTolerance * 100).roundToInt()}%",
                    fontWeight = FontWeight.Bold
                )
            }
            SliderWithStepControls(
                value = settings.movementIouTolerance,
                onValueChange = { newVal ->
                    val rounded = (newVal * 100).roundToInt() / 100f
                    viewModel.updateSettings { it.copy(movementIouTolerance = rounded) }
                },
                valueRange = 0.50f..0.95f,
                step = 0.05f,
                testTagPrefix = "movement_iou_tolerance"
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Scene motion sensitivity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scene Motion Sensitivity",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${(settings.globalMotionSensitivity * 100).roundToInt()}%",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Section C: Audio Alerts
        SettingsSectionCard(
            title = "C. Audio Alerts",
            icon = Icons.Default.VolumeUp
        ) {
            // Buzzer Enable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Industrial Failure Buzzer",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Acoustic alarm triggered on confirmed defect or empty impression.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.buzzerEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(buzzerEnabled = checked) }
                    },
                    modifier = Modifier.testTag("buzzer_switch")
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Buzzer Tone Selection
            var toneMenuExpanded by remember { mutableStateOf(false) }
            val toneOptions = listOf(
                950 to "950 Hz (Industrial Beep)",
                1600 to "1600 Hz (High Tone)",
                600 to "600 Hz (Warning Pulse)"
            )
            val currentToneLabel = toneOptions.find { it.first == settings.buzzerFrequencyHz }?.second
                ?: "${settings.buzzerFrequencyHz} Hz"

            ExposedDropdownMenuBox(
                expanded = toneMenuExpanded,
                onExpandedChange = { toneMenuExpanded = !toneMenuExpanded }
            ) {
                OutlinedTextField(
                    value = currentToneLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Buzzer Tone Frequency") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toneMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = toneMenuExpanded,
                    onDismissRequest = { toneMenuExpanded = false }
                ) {
                    toneOptions.forEach { (freq, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateSettings { it.copy(buzzerFrequencyHz = freq) }
                                toneMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Beep Count Slider with Plus/Minus
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Beep Count", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "${settings.beepCount}", fontWeight = FontWeight.Bold)
                }
                SliderWithStepControls(
                    value = settings.beepCount.toFloat(),
                    onValueChange = { newVal ->
                        viewModel.updateSettings { it.copy(beepCount = newVal.roundToInt()) }
                    },
                    valueRange = 1f..5f,
                    step = 1f,
                    testTagPrefix = "beep_count"
                )
            }

            // Volume Slider with Plus/Minus
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Buzzer Volume", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "${(settings.buzzerVolume * 100).roundToInt()}%", fontWeight = FontWeight.Bold)
                }
                SliderWithStepControls(
                    value = settings.buzzerVolume,
                    onValueChange = { newVal ->
                        val rounded = (newVal * 100).roundToInt() / 100f
                        viewModel.updateSettings { it.copy(buzzerVolume = rounded) }
                    },
                    valueRange = 0.10f..1.00f,
                    step = 0.05f,
                    testTagPrefix = "buzzer_volume"
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Voice Announcements
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Voice Announcements (TTS)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Speaks reason code and result verbally to operator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.voiceEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(voiceEnabled = checked) }
                    },
                    modifier = Modifier.testTag("voice_switch")
                )
            }

            // Audio Test Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { viewModel.testBuzzer() },
                    modifier = Modifier.weight(1f).testTag("test_buzzer_button")
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Buzzer")
                }

                FilledTonalButton(
                    onClick = { viewModel.testVoice() },
                    modifier = Modifier.weight(1f).testTag("test_voice_button")
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Voice")
                }
            }
        }

        // Section D: Inspection Behaviour
        SettingsSectionCard(
            title = "D. Inspection Behaviour",
            icon = Icons.Default.GraphicEq
        ) {
            // Simulation / Demo Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Lab / Demo Simulation Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Synthesizes sample detections to test inspection workflow, timing, and audio when conveyor is offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.simulationMode,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(simulationMode = checked) }
                    },
                    modifier = Modifier.testTag("simulation_mode_switch")
                )
            }
        }
    }
}

/**
 * Reusable Slider with accessible Minus and Plus step buttons.
 */
@Composable
fun SliderWithStepControls(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FilledTonalIconButton(
            onClick = {
                val nextVal = (value - step).coerceIn(valueRange.start, valueRange.endInclusive)
                onValueChange(nextVal)
            },
            enabled = value > valueRange.start,
            modifier = Modifier
                .size(38.dp)
                .testTag("${testTagPrefix}_minus_button")
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .weight(1f)
                .testTag("${testTagPrefix}_slider")
        )

        FilledTonalIconButton(
            onClick = {
                val nextVal = (value + step).coerceIn(valueRange.start, valueRange.endInclusive)
                onValueChange(nextVal)
            },
            enabled = value < valueRange.endInclusive,
            modifier = Modifier
                .size(38.dp)
                .testTag("${testTagPrefix}_plus_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            content()
        }
    }
}
