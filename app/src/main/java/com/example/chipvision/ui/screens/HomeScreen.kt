package com.example.chipvision.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.chipvision.ui.AppScreen
import com.example.chipvision.ui.ChipVisionViewModel

@Composable
fun HomeScreen(
    viewModel: ChipVisionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val diagnostics by viewModel.diagnostics.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()
    val cumulativeStats by viewModel.cumulativeStats.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    val scrollState = rememberScrollState()

    val totalSavedInspections = historyRecords.size
    val passSavedCount = historyRecords.count { it.isPass }
    val failSavedCount = historyRecords.count { !it.isPass }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("home_welcome_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Welcome to CHIPVISION",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Inspect chip orientation and detect empty impressions in real time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Readiness indicators row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IndicatorPill(
                        label = "Camera",
                        isOk = hasCameraPermission,
                        okText = "Ready",
                        errText = "Permission Needed"
                    )
                    IndicatorPill(
                        label = "Detector",
                        isOk = diagnostics.detectorLoaded || settings.simulationMode,
                        okText = if (settings.simulationMode) "Demo" else "Loaded",
                        errText = if (diagnostics.detector.isMissing) "Waiting for upload" else "Failed"
                    )
                    IndicatorPill(
                        label = "Classifier",
                        isOk = diagnostics.classifierLoaded || settings.simulationMode,
                        okText = if (settings.simulationMode) "Demo" else "Loaded",
                        errText = if (diagnostics.classifier.isMissing) "Waiting for upload" else "Failed"
                    )
                }
            }
        }

        // Main Navigation Cards
        Text(
            text = "Actions & Workflows",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 1. Live Inspection Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.navigateTo(AppScreen.INSPECTION) }
                .testTag("home_card_inspection")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Camera",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Live Inspection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Inspect chips on conveyor belt in real time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { viewModel.navigateTo(AppScreen.INSPECTION) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("home_start_inspection_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Inspection", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 2. Inspection History Card
        NavActionCard(
            icon = Icons.Default.History,
            iconTint = MaterialTheme.colorScheme.secondary,
            title = "Inspection History",
            description = "Review previous PASS and FAIL results ($totalSavedInspections saved records)",
            tag = "home_card_history",
            onClick = { viewModel.navigateTo(AppScreen.HISTORY) }
        )

        // 3. Settings Card
        NavActionCard(
            icon = Icons.Default.Settings,
            iconTint = MaterialTheme.colorScheme.tertiary,
            title = "Settings",
            description = "Configure detection, stability, audio, and inspection options",
            tag = "home_card_settings",
            onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
        )

        // 4. Model Diagnostics Card
        NavActionCard(
            icon = Icons.Default.Memory,
            iconTint = if (diagnostics.allModelsLoaded) Color(0xFF2E7D32) else Color(0xFFF57F17),
            title = "Model Diagnostics",
            description = if (diagnostics.allModelsLoaded) "Both models verified and ready" else "Check model availability, tensor shapes and errors",
            tag = "home_card_diagnostics",
            onClick = { viewModel.navigateTo(AppScreen.DIAGNOSTICS) }
        )

        // Summary Section
        Text(
            text = "Inspection Summary",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth().testTag("home_summary_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (totalSavedInspections == 0 && cumulativeStats.totalInspections == 0) {
                    Text(
                        text = "No inspections recorded yet. Start live inspection to begin inspecting electronic chips on the conveyor belt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val displayTotal = if (totalSavedInspections > 0) totalSavedInspections else cumulativeStats.totalInspections
                    val displayPass = if (totalSavedInspections > 0) passSavedCount else cumulativeStats.passCount
                    val displayFail = if (totalSavedInspections > 0) failSavedCount else cumulativeStats.failCount
                    val yieldRate = if (displayTotal > 0) (displayPass.toFloat() / displayTotal * 100f) else 100f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStat(label = "Total", value = displayTotal.toString(), color = MaterialTheme.colorScheme.onSurface)
                        SummaryStat(label = "PASS", value = displayPass.toString(), color = Color(0xFF2E7D32))
                        SummaryStat(label = "FAIL", value = displayFail.toString(), color = Color(0xFFC62828))
                        SummaryStat(label = "Yield Rate", value = "${"%.1f".format(yieldRate)}%", color = if (yieldRate >= 95f) Color(0xFF2E7D32) else Color(0xFFF57F17))
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Model Readiness:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (diagnostics.allModelsLoaded) "All Models Loaded" else if (settings.simulationMode) "Simulation Mode" else "Incomplete",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (diagnostics.allModelsLoaded) Color(0xFF2E7D32) else if (settings.simulationMode) MaterialTheme.colorScheme.primary else Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

@Composable
private fun IndicatorPill(
    label: String,
    isOk: Boolean,
    okText: String,
    errText: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOk) Color(0xFF2E7D32) else Color(0xFFC62828))
            )
            Text(
                text = if (isOk) okText else errText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isOk) Color(0xFF2E7D32) else Color(0xFFC62828),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NavActionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}
