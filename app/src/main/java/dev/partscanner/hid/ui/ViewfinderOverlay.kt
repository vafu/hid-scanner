package dev.partscanner.hid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val side = minOf(size.width * 0.72f, size.height * 0.34f)
        val window = Rect(
            offset = Offset(
                x = (size.width - side) / 2f,
                y = (size.height - side) / 2f,
            ),
            size = Size(side, side),
        )
        val radius = 18.dp.toPx()

        drawRect(Color.Black.copy(alpha = 0.38f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = window.topLeft,
            size = window.size,
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.78f),
            topLeft = window.topLeft,
            size = window.size,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        val corner = side * 0.16f
        val stroke = 5.dp.toPx()
        val cornerColor = Color(0xFF4FDC78).copy(alpha = 0.92f)
        drawLine(cornerColor, window.topLeft, window.topLeft + Offset(corner, 0f), stroke, StrokeCap.Round)
        drawLine(cornerColor, window.topLeft, window.topLeft + Offset(0f, corner), stroke, StrokeCap.Round)
        drawLine(cornerColor, Offset(window.right, window.top), Offset(window.right - corner, window.top), stroke, StrokeCap.Round)
        drawLine(cornerColor, Offset(window.right, window.top), Offset(window.right, window.top + corner), stroke, StrokeCap.Round)
        drawLine(cornerColor, Offset(window.left, window.bottom), Offset(window.left + corner, window.bottom), stroke, StrokeCap.Round)
        drawLine(cornerColor, Offset(window.left, window.bottom), Offset(window.left, window.bottom - corner), stroke, StrokeCap.Round)
        drawLine(cornerColor, Offset(window.right, window.bottom), Offset(window.right - corner, window.bottom), stroke, StrokeCap.Round)
        drawLine(cornerColor, Offset(window.right, window.bottom), Offset(window.right, window.bottom - corner), stroke, StrokeCap.Round)
    }
}
