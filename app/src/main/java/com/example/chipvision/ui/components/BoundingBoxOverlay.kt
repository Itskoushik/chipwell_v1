package com.example.chipvision.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.chipvision.data.model.TrackedCandidate
import java.util.Locale

@Composable
fun BoundingBoxOverlay(
    candidates: List<TrackedCandidate>,
    frameWidth: Float,
    frameHeight: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize().testTag("bounding_box_overlay")) {
        if (candidates.isEmpty() || frameWidth <= 0f || frameHeight <= 0f) return@Canvas
        val scale = maxOf(size.width / frameWidth, size.height / frameHeight)
        val offsetX = (size.width - frameWidth * scale) / 2f
        val offsetY = (size.height - frameHeight * scale) / 2f
        for (candidate in candidates) {
            val box = candidate.boundingBox
            val left = box.left * scale + offsetX
            val top = box.top * scale + offsetY
            val width = box.width() * scale
            val height = box.height() * scale
            if (width <= 0f || height <= 0f) continue
            val chip = candidate.isChip
            val color = if (chip) Color(0xFF64C8FF) else Color(0xFFFFFF00)
            val label = (if (chip) "CHIP" else "MARK") + " ${String.format(Locale.US, "%.2f", candidate.confidence)}"
            drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(8.dp.toPx()), style = Stroke(3.5.dp.toPx()))
            drawTextLabel(label, left, top, color)
        }
    }
}

private fun DrawScope.drawTextLabel(text: String, x: Float, y: Float, color: Color) {
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.BLACK
        textSize = 32f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val pad = 8f
    val width = bounds.width() + pad * 2
    val height = bounds.height() + pad * 2
    val labelY = if (y - height > 0f) y - height else y
    drawRoundRect(color, Offset(x.coerceAtLeast(0f), labelY), Size(width, height), CornerRadius(6.dp.toPx()))
    drawContext.canvas.nativeCanvas.drawText(text, x.coerceAtLeast(0f) + pad, labelY + height - pad, paint)
}
