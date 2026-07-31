package com.labeltruth.app.ui.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

enum class ScanMode { BARCODE, LABEL }

/**
 * Single CameraX analyser that serves both scanning modes.
 *
 * Barcodes are detected continuously, because that is cheap and users expect a
 * barcode to just "catch". OCR only runs when the user explicitly presses the
 * capture button - running text recognition on every frame would drain the
 * battery and produce a stream of half-read garbage.
 *
 * All recognition happens on-device. No frame ever leaves the phone.
 */
class FrameAnalyzer(
    private val currentMode: () -> ScanMode,
    private val onBarcode: (String) -> Unit,
    private val onLabelText: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128
            )
            .build()
    )

    private val textRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val captureRequested = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)

    /** Called from the UI when the user taps the capture button in LABEL mode. */
    fun requestLabelCapture() {
        captureRequested.set(true)
    }

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        when (currentMode()) {
            ScanMode.BARCODE -> {
                barcodeScanner.process(input)
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstNotNullOfOrNull { it.rawValue }
                            ?.takeIf { it.isNotBlank() }
                            ?.let(onBarcode)
                    }
                    .addOnCompleteListener {
                        busy.set(false)
                        imageProxy.close()
                    }
            }

            ScanMode.LABEL -> {
                if (!captureRequested.compareAndSet(true, false)) {
                    busy.set(false)
                    imageProxy.close()
                    return
                }
                textRecognizer.process(input)
                    .addOnSuccessListener { result ->
                        result.text.takeIf { it.isNotBlank() }?.let(onLabelText)
                    }
                    .addOnCompleteListener {
                        busy.set(false)
                        imageProxy.close()
                    }
            }
        }
    }

    fun close() {
        runCatching { barcodeScanner.close() }
        runCatching { textRecognizer.close() }
    }
}
