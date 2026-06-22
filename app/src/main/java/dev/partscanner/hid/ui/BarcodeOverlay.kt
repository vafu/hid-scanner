package dev.partscanner.hid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.partscanner.hid.barcode.BarcodeIdentity
import dev.partscanner.hid.domain.DetectedBarcode
import kotlin.math.hypot

@Composable
fun BarcodeOverlay(
    detections: List<DetectedBarcode>,
    selectedIdentity: String?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestDetections = rememberUpdatedState(detections)
    val latestOnSelect = rememberUpdatedState(onSelect)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val index = latestDetections.value.selectableIndexAt(
                        offset = offset,
                        directPadding = 36.dp.toPx(),
                        nearestDistance = 96.dp.toPx(),
                    )
                    if (index >= 0) latestOnSelect.value(index)
                }
            },
    ) {
        detections.forEachIndexed { index, detection ->
            val rect = detection.boundingBox.toComposeRect()
            if (rect.width <= 0f || rect.height <= 0f) return@forEachIndexed

            val selected = BarcodeIdentity.identityOf(detection.barcode) == selectedIdentity
            val color = if (selected) Color(0xFF4FDC78) else Color(0xFFFFD440)
            drawRoundRect(
                color = color,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = if (selected) 7f else 5f, cap = StrokeCap.Round),
            )

            val label = (index + 1).toString()
            val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                isAntiAlias = true
                textSize = 34f
                setColor(android.graphics.Color.BLACK)
                isFakeBoldText = true
            }
            val padding = 10f
            val textWidth = labelPaint.measureText(label)
            val labelHeight = labelPaint.textSize + padding
            val labelRect = Rect(
                left = rect.left,
                top = (rect.top - labelHeight).coerceAtLeast(0f),
                right = rect.left + textWidth + padding * 2f,
                bottom = (rect.top).coerceAtLeast(labelHeight),
            )
            drawRoundRect(
                color = color,
                topLeft = labelRect.topLeft,
                size = labelRect.size,
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    label,
                    labelRect.left + padding,
                    labelRect.bottom - padding,
                    labelPaint,
                )
            }
        }
    }
}

private fun android.graphics.RectF.toComposeRect(): Rect {
    return Rect(left, top, right, bottom)
}

private fun List<DetectedBarcode>.selectableIndexAt(
    offset: Offset,
    directPadding: Float,
    nearestDistance: Float,
): Int {
    val directHit = indexOfLast { detection ->
        detection.boundingBox.toComposeRect()
            .takeIf { it.width > 0f && it.height > 0f }
            ?.inflate(directPadding)
            ?.contains(offset) == true
    }
    if (directHit >= 0) return directHit

    return mapIndexedNotNull { index, detection ->
        val rect = detection.boundingBox.toComposeRect()
        if (rect.width <= 0f || rect.height <= 0f) return@mapIndexedNotNull null
        index to rect.distanceTo(offset)
    }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= nearestDistance }
        ?.first
        ?: -1
}

private fun Rect.distanceTo(offset: Offset): Float {
    val dx = when {
        offset.x < left -> left - offset.x
        offset.x > right -> offset.x - right
        else -> 0f
    }
    val dy = when {
        offset.y < top -> top - offset.y
        offset.y > bottom -> offset.y - bottom
        else -> 0f
    }
    return hypot(dx, dy)
}
