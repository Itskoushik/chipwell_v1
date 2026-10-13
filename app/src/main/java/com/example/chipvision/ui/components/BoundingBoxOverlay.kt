package com.example.chipvision.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.chipvision.data.model.ChipOrientation
import com.example.chipvision.data.model.TrackedCandidate

@Composable
fun BoundingBoxOverlay(
    candidates: List<TrackedCandidate>,
    frameWidth: Float,
    frameHeight: Float,
    showConfidenceLabels: Boolean = true,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("bounding_box_overlay")
    ) {
        if (candidates.isEmpty()) return@Canvas
        if (frameWidth <= 0f || frameHeight <= 0f) return@Canvas

        val canvasW = size.width
        val canvasH = size.height

        // Calculate aspect fill scaling to match CameraX PreviewView
        val scaleX = canvasW / frameWidth
        val scaleY = canvasH / frameHeight
        val scale = maxOf(scaleX, scaleY)

        val scaledW = frameWidth * scale
        val scaledH = frameHeight * scale
        val offsetX = (canvasW - scaledW) / 2f
        val offsetY = (canvasH - scaledH) / 2f

        val strokeWidth = 3.5.dp.toPx()

        for (candidate in candidates) {
            val box = candidate.boundingBox
            val left = (box.left * scale) + offsetX
            val top = (box.top * scale) + offsetY
            val right = (box.right * scale) + offsetX
            val bottom = (box.bottom * scale) + offsetY
            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) continue

            val cls = candidate.classification

            // Color specification:
            // Green: correctly upward chip
            // Yellow: validated empty impression; red: failed chip classification
            // Neutral: live chip awaiting classification
            val (boxColor, labelText) = when {
                candidate.isEmptyImpression -> {
                    val confText = if (showConfidenceLabels) {
                        " (${(candidate.confidence * 100).toInt()}%)"
                    } else {
                        ""
                    }
                    Pair(Color(0xFFFFC107), "EMPTY$confText")
                }
                cls == null -> {
                    Pair(Color(0xFFFFB300), "Awaiting Classification")
                }
                cls.orientation == ChipOrientation.CORRECT_UPWARD -> {
                    val confText = if (showConfidenceLabels) " (${(cls.confidence * 100).toInt()}%)" else ""
                    Pair(Color(0xFF43A047), "${cls.chipType.displayName} - Upward$confText")
                }
                cls.orientation == ChipOrientation.DOWNWARD -> {
                    val confText = if (showConfidenceLabels) " (${(cls.confidence * 100).toInt()}%)" else ""
                    Pair(Color(0xFFE53935), "${cls.chipType.displayName} - Downward$confText")
                }
                cls.orientation == ChipOrientation.FLIPPED -> {
                    val confText = if (showConfidenceLabels) " (${(cls.confidence * 100).toInt()}%)" else ""
                    Pair(Color(0xFFE53935), "${cls.chipType.displayName} - Flipped$confText")
                }
                else -> {
                    Pair(Color(0xFFE53935), "Uncertain Classification")
                }
            }

            // Draw bounding box
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )

            // Draw label banner on top of bounding box
            drawTextLabel(
                text = labelText,
                x = left,
                y = top,
                boxColor = boxColor
            )
        }
    }
}

private fun DrawScope.drawTextLabel(
    text: String,
    x: Float,
    y: Float,
    boxColor: Color
) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 32f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    val textBounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, textBounds)

    val paddingX = 14f
    val paddingY = 8f
    val labelW = textBounds.width() + paddingX * 2
    val labelH = textBounds.height() + paddingY * 2

    val bannerTop = if (y - labelH > 0) y - labelH else y
    val bannerLeft = x.coerceAtLeast(0f)

    // Background pill
    drawRoundRect(
        color = boxColor,
        topLeft = Offset(bannerLeft, bannerTop),
        size = Size(labelW, labelH),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    )

    // Native text
    drawContext.canvas.nativeCanvas.drawText(
        text,
        bannerLeft + paddingX,
        bannerTop + labelH - paddingY,
        paint
    )
}
