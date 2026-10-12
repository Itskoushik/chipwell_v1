package com.example.chipvision.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chipvision.inference.ModelStatusInfo
import com.example.chipvision.ui.ChipVisionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    viewModel: ChipVisionViewModel,
    modifier: Modifier = Modifier
) {
    val diagnostics by viewModel.diagnostics.collectAsState()
    val isReloading by viewModel.isReloadingModels.collectAsState()
    val reloadFeedback by viewModel.lastReloadFeedback.collectAsState()
    val scrollState = rememberScrollState()

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val checkTimeStr = dateFormat.format(Date(diagnostics.lastCheckTimestampMs))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overview Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (diagnostics.allModelsLoaded) {
                    Color(0xFF2E7D32).copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                }
            ),
            modifier = Modifier.fillMaxWidth().testTag("diagnostics_overview_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (diagnostics.allModelsLoaded) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (diagnostics.allModelsLoaded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (diagnostics.allModelsLoaded) "All Models Loaded Successfully" else "Model Compatibility Alert",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (diagnostics.allModelsLoaded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Last verified at $checkTimeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Functional Retry Button with Loading Indicator
                Button(
                    onClick = { viewModel.retryModelLoading() },
                    enabled = !isReloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("retry_model_loading_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isReloading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning Assets & Loading...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Re-scan & Reload Models")
                    }
                }

                // Live Feedback Banner after Retry
                reloadFeedback?.let { feedback ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth().testTag("retry_feedback_banner")
                    ) {
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (diagnostics.allModelsLoaded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Detector Diagnostic Card
        ModelDetailCard(
            title = "1. Chip Detector Model",
            expectedFilename = "chipvision_detector_direct.tflite",
            status = diagnostics.detector,
            classesDescription = "Class 0 = chip, Class 1 = empty impression"
        )

        // Classifier Diagnostic Card
        ModelDetailCard(
            title = "2. Chip Classifier Model",
            expectedFilename = "chipvision_classifier.tflite",
            status = diagnostics.classifier,
            classesDescription = "6 Classes: PCS_06_K (upward, downward, flipped), PCS_26_C (upward, downward, flipped)"
        )

        // Asset Placement Guide Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Asset Placement Instructions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Both TFLite files must be packaged directly in the app module's assets directory:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "app/src/main/assets/chipvision_detector_direct.tflite\napp/src/main/assets/chipvision_classifier.tflite",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = "Note: Creating empty 0-byte placeholder files will fail FlatBuffer validation. Both models must be valid binary files containing the 'TFL3' header.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelDetailCard(
    title: String,
    expectedFilename: String,
    status: ModelStatusInfo,
    classesDescription: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val (badgeBg, badgeText, badgeColor) = when {
                    status.isLoaded -> Triple(Color(0xFF2E7D32).copy(alpha = 0.15f), "LOADED", Color(0xFF2E7D32))
                    status.isMissing -> Triple(Color(0xFFF57F17).copy(alpha = 0.15f), "WAITING FOR UPLOAD", Color(0xFFF57F17))
                    status.isEmptyFile -> Triple(Color(0xFFF57F17).copy(alpha = 0.15f), "EMPTY FILE (0 B)", Color(0xFFF57F17))
                    else -> Triple(Color(0xFFC62828).copy(alpha = 0.15f), "FAILED", Color(0xFFC62828))
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Divider()

            DiagnosticsRow(label = "Filename", value = expectedFilename)
            DiagnosticsRow(label = "Resolved Location", value = status.resolvedLocation)
            DiagnosticsRow(
                label = "File Size",
                value = formatFileSize(status.fileSizeBytes, status.isMissing)
            )
            DiagnosticsRow(
                label = "Status",
                value = when {
                    status.isLoaded -> "Loaded (Verified & Ready for Inference)"
                    status.isMissing -> "Waiting for upload (place file in app/src/main/assets/)"
                    status.isEmptyFile -> "Empty file (0 bytes) - Upload real binary model"
                    else -> "Failed (${status.error})"
                }
            )

            if (status.isLoaded) {
                DiagnosticsRow(
                    label = "FlatBuffer Identifier",
                    value = "TFL3 (Verified)"
                )
                DiagnosticsRow(
                    label = "Input Tensor",
                    value = "${status.inputTensor.shape} (${status.inputTensor.dataType})"
                )
                DiagnosticsRow(
                    label = "Output Tensor",
                    value = "${status.outputTensor.shape} (${status.outputTensor.dataType})"
                )
                DiagnosticsRow(
                    label = "Layout",
                    value = if (status.isNchw) "NCHW (channels first)" else "NHWC (channels last)"
                )
            }

            if (status.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Detail: ${status.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Text(
                text = "Classes: $classesDescription",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatFileSize(bytes: Long, isMissing: Boolean): String {
    if (isMissing || bytes <= 0L) return if (isMissing) "Not Present" else "0 Bytes"
    return when {
        bytes < 1024 -> "$bytes Bytes"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
        else -> "${"%.2f".format(bytes / (1024f * 1024f))} MB"
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
