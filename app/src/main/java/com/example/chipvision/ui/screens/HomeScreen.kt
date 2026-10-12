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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showNotReadyDialog by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            when (val check = viewModel.checkInspectionPrerequisites(hasCameraPermission = true)) {
                is ChipVisionViewModel.InspectionStartResult.Ready -> {
                    viewModel.navigateTo(AppScreen.INSPECTION)
                }
                is ChipVisionViewModel.InspectionStartResult.MissingCameraPermission -> {}
                is ChipVisionViewModel.InspectionStartResult.ModelsNotReady -> {
                    showNotReadyDialog = check.reason
                }
            }
        }
    }

    val onStartInspectionClick = {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            when (val check = viewModel.checkInspectionPrerequisites(hasCameraPermission = true)) {
                is ChipVisionViewModel.InspectionStartResult.Ready -> {
                    viewModel.navigateTo(AppScreen.INSPECTION)
                }
                is ChipVisionViewModel.InspectionStartResult.MissingCameraPermission -> {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
                is ChipVisionViewModel.InspectionStartResult.ModelsNotReady -> {
                    showNotReadyDialog = check.reason
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    val totalSavedInspections = historyRecords.size
    val passSavedCount = historyRecords.count { it.isPass }
    val failSavedCount = historyRecords.count { !it.isPass }

    // Pre-flight validation error dialog
    showNotReadyDialog?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { showNotReadyDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Cannot Start Inspection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotReadyDialog = null
                        viewModel.navigateTo(AppScreen.SETTINGS)
                    }
                ) {
                    Text("Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotReadyDialog = null }
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

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

                // Industrial system readiness banner (no separate detector/classifier badges)
                val isSystemReady = viewModel.isSystemReady()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSystemReady) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().testTag("home_system_status_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isSystemReady) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isSystemReady) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (settings.simulationMode) "System: Demo Simulation Mode" else if (isSystemReady) "System: Ready for Live Inspection" else "System: Standby",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSystemReady) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (hasCameraPermission) "Camera: Ready" else "Camera: Needs Permission",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasCameraPermission) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
                .clickable(onClick = onStartInspectionClick)
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
                    onClick = onStartInspectionClick,
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
            }
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
