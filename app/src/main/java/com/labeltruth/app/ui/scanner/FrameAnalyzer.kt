package com.labeltruth.app.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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
                        readInVisualOrder(result).takeIf { it.isNotBlank() }?.let(onLabelText)
                    }
                    .addOnCompleteListener {
                        busy.set(false)
                        imageProxy.close()
                    }
            }
        }
    }

    /**
     * Rebuilds the recognised text in the order a person would read it.
     *
     * [Text.getText] concatenates blocks in the order the recogniser happened to
     * emit them, which is not reading order. On a groundnut oil bottle that
     * fused two unrelated lines into "FEATURES: nutrients intact without
     * Suitable for daily cooking", so the parser saw a sentence that was never
     * printed and produced ingredients that do not exist.
     *
     * Lines rather than blocks are the unit here, because a block can span
     * columns. Lines are grouped into rows using a fraction of the median line
     * height as the tolerance, so gently tilted labels still group correctly,
     * then ordered left to right within each row.
     */
    private fun readInVisualOrder(result: Text): String {
        val lines = result.textBlocks.flatMap { it.lines }
        // Without geometry there is nothing to sort by, so keep the original.
        if (lines.isEmpty() || lines.any { it.boundingBox == null }) return result.text

        val boxes = lines.mapNotNull { line -> line.boundingBox?.let { line to it } }
        val heights = boxes.map { it.second.height() }.sorted()
        val medianHeight = heights[heights.size / 2].coerceAtLeast(1)
        val rowTolerance = (medianHeight * ROW_TOLERANCE_FRACTION).toInt().coerceAtLeast(1)

        val byVertical = boxes.sortedBy { it.second.centerY() }
        val rows = mutableListOf<MutableList<Pair<Text.Line, android.graphics.Rect>>>()
        for (entry in byVertical) {
            val lastRow = rows.lastOrNull()
            val sameRow = lastRow != null &&
                entry.second.centerY() - lastRow.last().second.centerY() <= rowTolerance
            if (sameRow) lastRow.add(entry) else rows.add(mutableListOf(entry))
        }

        return rows.joinToString("\n") { row ->
            row.sortedBy { it.second.left }.joinToString(" ") { it.first.text.trim() }
        }
    }

    fun close() {
        runCatching { barcodeScanner.close() }
        runCatching { textRecognizer.close() }
    }

    private companion object {
        /**
         * Fraction of the median line height within which two lines count as the
         * same row. Generous enough for a hand-held, slightly rotated photo,
         * tight enough not to merge adjacent lines of a dense ingredient list.
         */
        const val ROW_TOLERANCE_FRACTION = 0.6
    }
}
