package dev.partscanner.hid.barcode

import android.graphics.RectF
import dev.partscanner.hid.domain.DetectedBarcode
import dev.partscanner.hid.domain.ScannedBarcode

class StableBarcodeTracker(
    private val maxMissedFrames: Int = 5,
) {
    private val tracked = linkedMapOf<String, TrackedBarcode>()

    fun update(detections: List<DetectedBarcode>, nowMillis: Long): List<DetectedBarcode> {
        val visibleIdentities = linkedSetOf<String>()

        for (detection in detections) {
            val identity = BarcodeIdentity.identityOf(detection.barcode)
            if (identity.isBlank()) continue
            visibleIdentities += identity
            tracked.getOrPut(identity) { TrackedBarcode(detection.barcode, detection.boundingBox) }
                .update(detection.barcode, detection.boundingBox, nowMillis)
        }

        val iterator = tracked.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in visibleIdentities && !entry.value.markMissed(maxMissedFrames)) {
                iterator.remove()
            }
        }

        return tracked.values.map { it.toDetection() }
    }

    fun clear() {
        tracked.clear()
    }

    private class TrackedBarcode(
        private var barcode: ScannedBarcode,
        initialBox: RectF,
    ) {
        private val left = OneEuroFilter()
        private val top = OneEuroFilter()
        private val right = OneEuroFilter()
        private val bottom = OneEuroFilter()
        private var box = RectF(initialBox)

        fun update(newBarcode: ScannedBarcode, newBox: RectF, nowMillis: Long) {
            barcode = newBarcode
            missedFrames = 0
            if (newBox.width() > 0f && newBox.height() > 0f) {
                box = RectF(
                    left.filter(newBox.left, nowMillis),
                    top.filter(newBox.top, nowMillis),
                    right.filter(newBox.right, nowMillis),
                    bottom.filter(newBox.bottom, nowMillis),
                )
            }
        }

        fun markMissed(maxMissedFrames: Int): Boolean {
            missedFrames += 1
            return missedFrames <= maxMissedFrames
        }

        fun toDetection(): DetectedBarcode = DetectedBarcode(barcode, RectF(box))

        private var missedFrames = 0
    }
}
