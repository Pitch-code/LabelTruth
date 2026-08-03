package com.labeltruth.app.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
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
            // Restricted to the formats that actually appear on retail packaging.
            // Narrowing the set makes detection faster and reduces false reads,
            // so QR and the industrial 2D formats are deliberately excluded.
            //
            // ITF is included for ITF-14, which is common on multipacks and
            // outer cartons. The lookup validates digit count afterwards, which
            // guards against ITF's tendency to misread striped artwork.
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODE_39
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

    /**
     * ImageProxy.getImage is an opt-in CameraX API, and this must be androidx's
     * OptIn rather than Kotlin's. ExperimentalGetImage is a Java annotation
     * enforced by lint, so kotlin.OptIn compiles but does not satisfy it - the
     * compiler even warns that it "has no effect", which is easy to act on
     * incorrectly by deleting the annotation and turning a warning into a lint
     * error.
     */
    @OptIn(markerClass = [ExperimentalGetImage::class])
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
                        result.inVisualOrder().takeIf { it.isNotBlank() }?.let(onLabelText)
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
