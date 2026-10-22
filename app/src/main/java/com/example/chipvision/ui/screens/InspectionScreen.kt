package com.example.chipvision.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chipvision.camera.CameraController
import com.example.chipvision.data.model.InspectionStatus
import com.example.chipvision.engine.InspectionResultDelay
import com.example.chipvision.ui.ChipVisionViewModel
import com.example.chipvision.ui.components.BoundingBoxOverlay
import com.example.chipvision.ui.components.InspectionRoiOverlay
import kotlinx.coroutines.delay

@Composable
fun InspectionScreen(
    viewModel: ChipVisionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val status by viewModel.status.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val visibleCandidates by viewModel.visibleCandidates.collectAsState()
    val currentDecision by viewModel.currentDecision.collectAsState()
    val cumulativeStats by viewModel.cumulativeStats.collectAsState()
    val isStopped by viewModel.isStopped.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val cameraFrameSize by viewModel.cameraFrameSize.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    var showClearStatsDialog by remember { mutableStateOf(false) }
    var shownResultSnapshotId by remember { mutableStateOf<String?>(null) }
    var showInspectionResult by remember { mutableStateOf(false) }

    LaunchedEffect(currentDecision?.snapshotId) {
        val decision = currentDecision
        if (decision != null && decision.snapshotId != shownResultSnapshotId) {
            shownResultSnapshotId = decision.snapshotId
            showInspectionResult = true
            delay(InspectionResultDelay.popupDurationForDecision(decision))
            if (currentDecision?.snapshotId == decision.snapshotId) {
                showInspectionResult = false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraController?.release()
            viewModel.clearCameraFrameSize()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Industrial inspection top bar (clean header, no separate model badges)
        InspectionTopBar(
            simulationMode = settings.simulationMode
        )

        // Main Camera Area with live overlays
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("camera_preview_view"),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        var controller: CameraController? = null
                        controller = CameraController(ctx) { bitmap ->
                            viewModel.onCameraFrame(bitmap) {
                                controller?.notifyFrameProcessingFinished()
                            }
                        }
                        cameraController = controller
                        controller.startCamera(lifecycleOwner, previewView) { err ->
                            // Camera error handled
                        }
                        previewView
                    }
                )

                // Live Bounding Box Overlay for STABLE accepted candidates only
                BoundingBoxOverlay(
                    candidates = visibleCandidates,
                    frameWidth = cameraFrameSize.first.toFloat(),
                    frameHeight = cameraFrameSize.second.toFloat(),
                    modifier = Modifier.fillMaxSize()
                )
                InspectionRoiOverlay(
                    roi = settings.inspectionRoi,
                    frameWidth = cameraFrameSize.first.toFloat(),
                    frameHeight = cameraFrameSize.second.toFloat(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Camera Permission Required",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Grant camera permission to enable conveyor observation and real-time inference.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.testTag("grant_camera_permission_button")
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            // Real-time Status Overlay Banner
            InspectionStateBanner(
                status = status,
                message = statusMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
            )

        }

        // Summary and Controls bottom panel
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Inspection Summary metrics
                CumulativeMetricsRow(
                    stats = cumulativeStats,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Operator Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isStopped) {
                        Button(
                            onClick = { viewModel.resumeInspection() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("resume_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopInspection() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    FilledTonalButton(
                        onClick = { viewModel.startInspection() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("start_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Session", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { showClearStatsDialog = true },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("clear_stats_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Statistics")
                    }
                }
            }
        }
    }

    if (showClearStatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStatsDialog = false },
            title = { Text("Clear Statistics?") },
            text = { Text("This will reset cumulative PASS and FAIL inspection tallies to zero. Inspection history log will be preserved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearStatistics()
                        showClearStatsDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_stats_button")
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStatsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showInspectionResult && currentDecision != null) {
        Dialog(onDismissRequest = { showInspectionResult = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("inspection_result_popup")
            ) {
                Text(
                    text = if (currentDecision!!.isPass) "PASS" else "FAIL",
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 28.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (currentDecision!!.isPass) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
        }
    }
}

@Composable
fun InspectionTopBar(
    simulationMode: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "CONVEYOR INSPECTION",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (simulationMode) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "DEMO SIMULATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InspectionStateBanner(
    status: InspectionStatus,
    message: String,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, icon) = when (status) {
        InspectionStatus.PASS -> Triple(Color(0xFF2E7D32), Color.White, Icons.Default.CheckCircle)
        InspectionStatus.FAIL -> Triple(Color(0xFFC62828), Color.White, Icons.Default.Error)
        InspectionStatus.ERROR -> Triple(Color(0xFFD32F2F), Color.White, Icons.Default.Warning)
        InspectionStatus.WAITING_FOR_STABILITY,
        InspectionStatus.STABLE_SCENE_READY,
        InspectionStatus.EVALUATING,
        InspectionStatus.FINALIZING -> Triple(Color(0xFF0277BD), Color.White, Icons.Default.Info)
        InspectionStatus.STOPPED -> Triple(Color(0xFF616161), Color.White, Icons.Default.Stop)
        else -> Triple(Color(0xFF37474F), Color.White, Icons.Default.Info)
    }

    Surface(
        color = containerColor.copy(alpha = 0.92f),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        modifier = modifier.testTag("inspection_state_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun CumulativeMetricsRow(
    stats: com.example.chipvision.data.model.CumulativeStats,
    modifier: Modifier = Modifier
) {
    val total = stats.totalInspections
    val yieldRate = if (total > 0) (stats.passCount.toFloat() / total * 100f) else 100f

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricTile(
            title = "TOTAL",
            value = total.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            title = "PASS",
            value = stats.passCount.toString(),
            color = Color(0xFF2E7D32),
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            title = "FAIL",
            value = stats.failCount.toString(),
            color = Color(0xFFC62828),
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            title = "YIELD",
            value = "${"%.1f".format(yieldRate)}%",
            color = if (yieldRate >= 95f) Color(0xFF2E7D32) else Color(0xFFF57F17),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
