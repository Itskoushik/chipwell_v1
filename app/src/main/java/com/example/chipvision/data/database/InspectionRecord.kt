package com.example.chipvision.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inspection_history")
data class InspectionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMs: Long,
    val sessionId: String,
    val snapshotId: String,
    val isPass: Boolean,
    val reasonCode: String,
    val message: String,
    val chipCount: Int,
    val emptyCount: Int,
    val chipDetailsJson: String
)
