package com.example.chipvision.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chipvision.data.database.InspectionRecord
import com.example.chipvision.ui.ChipVisionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryFilter {
    ALL,
    PASS_ONLY,
    FAIL_ONLY
}

@Composable
fun HistoryScreen(
    viewModel: ChipVisionViewModel,
    modifier: Modifier = Modifier
) {
    val historyRecords by viewModel.historyRecords.collectAsState()
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedRecordForDetail by remember { mutableStateOf<InspectionRecord?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val filteredRecords = remember(historyRecords, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> historyRecords
            HistoryFilter.PASS_ONLY -> historyRecords.filter { it.isPass }
            HistoryFilter.FAIL_ONLY -> historyRecords.filter { !it.isPass }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header with filter chips and Clear action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Inspection Log (${filteredRecords.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (historyRecords.isNotEmpty()) {
                IconButton(
                    onClick = { showClearHistoryDialog = true },
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear History",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedFilter == HistoryFilter.ALL,
                onClick = { selectedFilter = HistoryFilter.ALL },
                label = { Text("All") },
                modifier = Modifier.testTag("filter_all_chip")
            )
            FilterChip(
                selected = selectedFilter == HistoryFilter.PASS_ONLY,
                onClick = { selectedFilter = HistoryFilter.PASS_ONLY },
                label = { Text("Pass Only") },
                modifier = Modifier.testTag("filter_pass_chip")
            )
            FilterChip(
                selected = selectedFilter == HistoryFilter.FAIL_ONLY,
                onClick = { selectedFilter = HistoryFilter.FAIL_ONLY },
                label = { Text("Fail Only") },
                modifier = Modifier.testTag("filter_fail_chip")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No inspection records match filter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("history_list")
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    InspectionRecordCard(
                        record = record,
                        onClick = { selectedRecordForDetail = record }
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear All Inspection History?") },
            text = { Text("This will permanently delete all stored inspection records from the local database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedRecordForDetail?.let { record ->
        InspectionDetailDialog(
            record = record,
            onDismiss = { selectedRecordForDetail = null }
        )
    }
}

@Composable
fun InspectionRecordCard(
    record: InspectionRecord,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss  MMM dd", Locale.getDefault()) }
    val timeStr = remember(record.timestampMs) { dateFormat.format(Date(record.timestampMs)) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("inspection_record_card_${record.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (record.isPass) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (record.isPass) "PASS" else "FAIL",
                tint = if (record.isPass) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (record.isPass) "PASS" else "FAIL",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (record.isPass) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = record.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.reasonCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun InspectionDetailDialog(
    record: InspectionRecord,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(record.timestampMs) { dateFormat.format(Date(record.timestampMs)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (record.isPass) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (record.isPass) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Text(
                    text = if (record.isPass) "Inspection PASS" else "Inspection FAIL",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Timestamp: $formattedDate",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Reason Code: ${record.reasonCode}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Message: ${record.message}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Session ID: ${record.sessionId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.chipDetailsJson.isNotBlank()) {
                    Text(
                        text = "Inspection Details:\n${record.chipDetailsJson}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
