package com.example.chipvision.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.chipvision.data.preferences.InspectionRoi

@Composable
fun InspectionRoiOverlay(
    roi: InspectionRoi,
    frameWidth: Float,
    frameHeight: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (!roi.isValid || frameWidth <= 0f || frameHeight <= 0f) return@Canvas
        val scale = maxOf(size.width / frameWidth, size.height / frameHeight)
        val scaledWidth = frameWidth * scale
        val scaledHeight = frameHeight * scale
        val offsetX = (size.width - scaledWidth) / 2f
        val offsetY = (size.height - scaledHeight) / 2f
        drawRect(
            color = Color.White.copy(alpha = 0.75f),
            topLeft = Offset(
                offsetX + roi.left * scaledWidth,
                offsetY + roi.top * scaledHeight
            ),
            size = Size(
                (roi.right - roi.left) * scaledWidth,
                (roi.bottom - roi.top) * scaledHeight
            ),
            style = Stroke(width = 3f)
        )
    }
}
