package com.labeltruth.app.ui.scanner

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX preview bound to the Compose lifecycle.
 *
 * ImageAnalysis uses KEEP_ONLY_LATEST so that if recognition falls behind, we
 * drop stale frames instead of building a queue and making the UI feel laggy.
 */
@Composable
fun CameraPreview(
    analyzer: FrameAnalyzer,
    torchOn: Boolean,
    /**
     * When false the camera is released entirely.
     *
     * The result sheet is a separate window layered over this screen, so the
     * preview kept streaming behind it: the torch stayed lit, frames kept being
     * analysed and the battery kept draining while the user read a result. A
     * scanner app holding the camera open when it has nothing to scan is also
     * hard to defend on privacy grounds.
     *
     * Deliberately has no default: forgetting to pass it would silently
     * reintroduce the always-on camera.
     */
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Keyed on the analyzer as well, so a replaced analyzer is actually rebound
    // rather than silently ignored, and on `active` so releasing and reacquiring
    // the camera is just a state change.
    LaunchedEffect(lifecycleOwner, analyzer, active) {
        val cameraProvider = awaitCameraProvider(context)
        provider = cameraProvider

        if (!active) {
            // Unbind rather than merely stopping the analyzer, so the camera
            // indicator goes out and the hardware is genuinely released.
            runCatching { cameraProvider.unbindAll() }
            camera = null
            return@LaunchedEffect
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        runCatching {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        }
    }

    LaunchedEffect(torchOn, camera) {
        val info = camera?.cameraInfo
        if (info?.hasFlashUnit() == true) {
            runCatching { camera?.cameraControl?.enableTorch(torchOn) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Release the camera and our analysis thread, but deliberately do
            // NOT close the analyzer: this composable is handed one and does not
            // own it. Closing it here was a real bug, because the caller keeps
            // the same instance across recomposition. Revoking camera permission
            // and re-granting it would then rebind a closed ML Kit detector,
            // which throws on the analysis thread.
            runCatching { provider?.unbindAll() }
            camera = null
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * ProcessCameraProvider hands back a ListenableFuture. Bridge it into a
 * coroutine so the caller can just suspend.
 */
private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (throwable: Throwable) {
                    continuation.resumeWithException(throwable)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }
