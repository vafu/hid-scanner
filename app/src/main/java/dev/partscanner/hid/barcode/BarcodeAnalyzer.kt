package dev.partscanner.hid.barcode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode
import com.google.mlkit.vision.common.InputImage
import dev.partscanner.hid.domain.DetectedBarcode
import dev.partscanner.hid.domain.ScannedBarcode
import dev.partscanner.hid.domain.ScannerBarcodeFormat
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeAnalyzer(
    enabledFormats: Set<ScannerBarcodeFormat>,
    private val previewCropProvider: () -> PreviewCrop?,
    private val previewTransformProvider: () -> OutputTransform?,
    private val onDetections: (List<DetectedBarcode>) -> Unit,
    private val onError: (Throwable) -> Unit,
) : ImageAnalysis.Analyzer {
    private var frameIndex = 0L
    private val inFlight = AtomicBoolean(false)
    private val imageTransformFactory = ImageProxyTransformFactory().apply {
        setUsingCropRect(true)
        setUsingRotationDegrees(true)
    }
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(enabledFormats)
            .build(),
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val frame = ++frameIndex
        val startedAtMillis = System.currentTimeMillis()
        if (!inFlight.compareAndSet(false, true)) {
            Log.d(Tag, "frame=$frame dropped analyzer_busy")
            imageProxy.close()
            return
        }

        val imageTransform = imageTransformFactory.getOutputTransform(imageProxy)
        val previewTransform = previewTransformProvider()
        val coordinateTransform = if (previewTransform != null) {
            CoordinateTransform(imageTransform, previewTransform)
        } else {
            null
        }
        val croppedInput = imageProxy.toCroppedInput(previewCropProvider())
        val mediaImage = imageProxy.image
        val inputImage = when {
            croppedInput != null -> InputImage.fromBitmap(croppedInput.bitmap, 0)
            mediaImage != null -> InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            else -> {
                Log.d(Tag, "frame=$frame empty image_proxy")
                inFlight.set(false)
                imageProxy.close()
                return
            }
        }

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val detections = barcodes.mapNotNull { barcode ->
                    barcode.toDetectedBarcode { rect ->
                        if (croppedInput != null) {
                            croppedInput.mapToPreview(rect)
                        } else {
                            coordinateTransform?.mapRect(rect)
                        }
                    }
                }
                val elapsedMillis = System.currentTimeMillis() - startedAtMillis
                Log.d(
                    Tag,
                    "frame=$frame success raw=${barcodes.size} detections=${detections.size} " +
                        "cropped=${croppedInput != null} transform=${coordinateTransform != null} " +
                        "elapsed=${elapsedMillis}ms " +
                        detections.joinToString(prefix = "[", postfix = "]") { it.logSummary() },
                )
                onDetections(detections)
            }
            .addOnFailureListener { error ->
                Log.w(Tag, "frame=$frame failure ${error.message}", error)
                onError(error)
            }
            .addOnCompleteListener {
                croppedInput?.recycle()
                inFlight.set(false)
                imageProxy.close()
            }
    }

    private fun MlKitBarcode.toDetectedBarcode(mapRect: (RectF) -> Unit): DetectedBarcode? {
        val scanned = ScannedBarcode(rawValue, rawBytes)
        if (scanned.displayValue().isBlank() && rawBytes == null) return null

        val rect = boundingBox?.let { RectF(it) } ?: RectF()
        mapRect(rect)
        return DetectedBarcode(scanned, rect)
    }

    private fun DetectedBarcode.logSummary(): String {
        return "${barcode.displayValue().take(32)} " +
            "box=${boundingBox.left.toInt()},${boundingBox.top.toInt()}," +
            "${boundingBox.right.toInt()},${boundingBox.bottom.toInt()}"
    }

    private companion object {
        const val Tag = "SmartPartsScanner"
    }

    private data class CroppedInput(
        val sourceBitmap: Bitmap,
        val bitmap: Bitmap,
        val previewCrop: PreviewCrop,
    ) {
        fun mapToPreview(rect: RectF) {
            val previewRect = previewCrop.rect
            val scaleX = previewRect.width() / bitmap.width.toFloat()
            val scaleY = previewRect.height() / bitmap.height.toFloat()
            rect.set(
                previewRect.left + rect.left * scaleX,
                previewRect.top + rect.top * scaleY,
                previewRect.left + rect.right * scaleX,
                previewRect.top + rect.bottom * scaleY,
            )
        }

        fun recycle() {
            if (bitmap !== sourceBitmap) bitmap.recycle()
            sourceBitmap.recycle()
        }
    }

    private fun ImageProxy.toCroppedInput(previewCrop: PreviewCrop?): CroppedInput? {
        if (previewCrop == null || previewCrop.rect.width() <= 0f || previewCrop.rect.height() <= 0f) return null
        return try {
            val source = toBitmapViaNv21().rotate(imageInfo.rotationDegrees)
            val cropRect = source.cropRectFor(previewCrop)
            val crop = Bitmap.createBitmap(
                source,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height(),
            )
            CroppedInput(source, crop, previewCrop)
        } catch (error: Throwable) {
            Log.w(Tag, "could not crop analyzer input; falling back to full frame", error)
            null
        }
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        recycle()
        return rotated
    }

    private fun Bitmap.cropRectFor(previewCrop: PreviewCrop): Rect {
        val scale = maxOf(
            previewCrop.viewWidth / width.toFloat(),
            previewCrop.viewHeight / height.toFloat(),
        )
        val displayedWidth = width * scale
        val displayedHeight = height * scale
        val offsetX = (previewCrop.viewWidth - displayedWidth) / 2f
        val offsetY = (previewCrop.viewHeight - displayedHeight) / 2f
        val crop = previewCrop.rect

        val left = ((crop.left - offsetX) / scale).toInt().coerceIn(0, width - 1)
        val top = ((crop.top - offsetY) / scale).toInt().coerceIn(0, height - 1)
        val right = ((crop.right - offsetX) / scale).toInt().coerceIn(left + 1, width)
        val bottom = ((crop.bottom - offsetY) / scale).toInt().coerceIn(top + 1, height)
        return Rect(left, top, right, bottom)
    }

    private fun ImageProxy.toBitmapViaNv21(): Bitmap {
        val nv21 = toNv21()
        val jpeg = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), 90, jpeg)
        return BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size())
            ?: error("Could not decode analyzer frame")
    }

    private fun ImageProxy.toNv21(): ByteArray {
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val ySize = width * height
        val output = ByteArray(ySize + ySize / 2)

        copyLumaPlane(
            source = yPlane.buffer,
            width = width,
            height = height,
            rowStride = yPlane.rowStride,
            pixelStride = yPlane.pixelStride,
            output = output,
        )

        var outputOffset = ySize
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        for (row in 0 until chromaHeight) {
            for (column in 0 until chromaWidth) {
                output[outputOffset++] = vPlane.buffer.get(row * vPlane.rowStride + column * vPlane.pixelStride)
                output[outputOffset++] = uPlane.buffer.get(row * uPlane.rowStride + column * uPlane.pixelStride)
            }
        }
        return output
    }

    private fun copyLumaPlane(
        source: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        output: ByteArray,
    ) {
        var outputOffset = 0
        for (row in 0 until height) {
            for (column in 0 until width) {
                output[outputOffset++] = source.get(row * rowStride + column * pixelStride)
            }
        }
    }
}

data class PreviewCrop(
    val rect: RectF,
    val viewWidth: Int,
    val viewHeight: Int,
)

private fun BarcodeScannerOptions.Builder.setBarcodeFormats(
    formats: Set<ScannerBarcodeFormat>,
): BarcodeScannerOptions.Builder {
    val mlKitFormats = formats.ifEmpty { ScannerBarcodeFormat.defaultEnabled }
        .map { it.toMlKitFormat() }
    return setBarcodeFormats(mlKitFormats.first(), *mlKitFormats.drop(1).toIntArray())
}

private fun ScannerBarcodeFormat.toMlKitFormat(): Int {
    return when (this) {
        ScannerBarcodeFormat.AZTEC -> MlKitBarcode.FORMAT_AZTEC
        ScannerBarcodeFormat.CODABAR -> MlKitBarcode.FORMAT_CODABAR
        ScannerBarcodeFormat.CODE_39 -> MlKitBarcode.FORMAT_CODE_39
        ScannerBarcodeFormat.CODE_93 -> MlKitBarcode.FORMAT_CODE_93
        ScannerBarcodeFormat.CODE_128 -> MlKitBarcode.FORMAT_CODE_128
        ScannerBarcodeFormat.DATA_MATRIX -> MlKitBarcode.FORMAT_DATA_MATRIX
        ScannerBarcodeFormat.EAN_8 -> MlKitBarcode.FORMAT_EAN_8
        ScannerBarcodeFormat.EAN_13 -> MlKitBarcode.FORMAT_EAN_13
        ScannerBarcodeFormat.ITF -> MlKitBarcode.FORMAT_ITF
        ScannerBarcodeFormat.PDF_417 -> MlKitBarcode.FORMAT_PDF417
        ScannerBarcodeFormat.QR_CODE -> MlKitBarcode.FORMAT_QR_CODE
        ScannerBarcodeFormat.UPC_A -> MlKitBarcode.FORMAT_UPC_A
        ScannerBarcodeFormat.UPC_E -> MlKitBarcode.FORMAT_UPC_E
    }
}
