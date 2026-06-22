package dev.partscanner.hid.ui

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.OutputTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.partscanner.hid.barcode.BarcodeAnalyzer
import dev.partscanner.hid.barcode.PreviewCrop
import dev.partscanner.hid.domain.DetectedBarcode
import dev.partscanner.hid.domain.ScannerBarcodeFormat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@Composable
fun CameraPreview(
    enabledBarcodeFormats: Set<ScannerBarcodeFormat>,
    onDetections: (List<DetectedBarcode>) -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewTransform = remember { AtomicReference<OutputTransform?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                previewTransform.set(outputTransform)
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
        update = { view -> previewTransform.set(view.outputTransform) },
    )

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    DisposableEffect(context, lifecycleOwner, previewView, enabledBarcodeFormats) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            bindCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                previewTransform = previewTransform,
                enabledBarcodeFormats = enabledBarcodeFormats,
                onDetections = onDetections,
                onError = onError,
                cameraExecutor = cameraExecutor,
            )
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }
}

private fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    previewTransform: AtomicReference<OutputTransform?>,
    enabledBarcodeFormats: Set<ScannerBarcodeFormat>,
    onDetections: (List<DetectedBarcode>) -> Unit,
    onError: (Throwable) -> Unit,
    cameraExecutor: java.util.concurrent.Executor,
) {
    val cameraProvider = ProcessCameraProvider.getInstance(context).get()
    val preview = Preview.Builder().build().apply {
        setSurfaceProvider(previewView.surfaceProvider)
    }
    val analyzer = BarcodeAnalyzer(
        enabledFormats = enabledBarcodeFormats,
        previewCropProvider = { previewView.viewfinderCrop() },
        previewTransformProvider = { previewTransform.get() },
        onDetections = onDetections,
        onError = onError,
    )
    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .apply { setAnalyzer(cameraExecutor, analyzer) }

    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview,
        analysis,
    )
    previewTransform.set(previewView.outputTransform)
}

private fun PreviewView.viewfinderCrop(): PreviewCrop? {
    if (width <= 0 || height <= 0) return null
    val side = minOf(width * 0.72f, height * 0.34f)
    val left = (width - side) / 2f
    val top = (height - side) / 2f
    return PreviewCrop(
        rect = RectF(left, top, left + side, top + side),
        viewWidth = width,
        viewHeight = height,
    )
}
