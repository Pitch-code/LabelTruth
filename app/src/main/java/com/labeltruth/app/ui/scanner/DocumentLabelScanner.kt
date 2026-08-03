package com.labeltruth.app.ui.scanner

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Reads an ingredient label by flattening it first.
 *
 * A photograph of a curved jar gives the recogniser text that bends away from
 * the lens, and no amount of parsing rescues that: a real groundnut oil bottle
 * produced sentences that were never printed. The document scanner finds the
 * label's edges, corrects the perspective and returns a flat image, which is
 * then read with the same recogniser and the same reading-order logic as a live
 * camera frame.
 *
 * Everything stays on the device. The scanner is delivered through Google Play
 * services rather than bundled, so it costs almost nothing in download size, and
 * no image is uploaded anywhere.
 *
 * Returns a lambda that starts the flow. Null is never returned, but the flow can
 * fail on a device without a current Play services, which is why [onError] is
 * required rather than optional: a capture button that silently does nothing is
 * worse than one that explains itself.
 */
@Composable
internal fun rememberLabelDocumentScanner(
    onText: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    // The analyser outlives recomposition, so the callbacks are read through
    // rememberUpdatedState rather than captured directly.
    val currentOnText by rememberUpdatedState(onText)
    val currentOnError by rememberUpdatedState(onError)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val page = GmsDocumentScanningResult
            .fromActivityResultIntent(activityResult.data)
            ?.pages
            ?.firstOrNull()

        val uri = page?.imageUri
        if (uri == null) {
            currentOnError("Nothing came back from the scanner. Try again.")
            return@rememberLauncherForActivityResult
        }

        val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        runCatching { InputImage.fromFilePath(context, uri) }
            .onFailure {
                recogniser.close()
                currentOnError("Could not open the captured image.")
            }
            .onSuccess { image ->
                recogniser.process(image)
                    .addOnSuccessListener { result ->
                        val text = result.inVisualOrder()
                        if (text.isBlank()) {
                            currentOnError(
                                "No text was found in that capture. Fill the frame " +
                                    "with the ingredient list and try again."
                            )
                        } else {
                            currentOnText(text)
                        }
                    }
                    .addOnFailureListener {
                        currentOnError("Could not read text from that capture.")
                    }
                    .addOnCompleteListener { recogniser.close() }
            }
    }

    return {
        val activity = context as? Activity
        if (activity == null) {
            currentOnError("Could not start the scanner.")
        } else {
            val options = GmsDocumentScannerOptions.Builder()
                // One page: an ingredient list, not a document.
                .setPageLimit(1)
                // A JPEG is what the recogniser wants. A PDF would have to be
                // unpacked again for no gain.
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                // FULL gives edge detection with manual correction, which matters
                // on a curved jar where automatic edges are often slightly wrong.
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                // Importing from the gallery is deliberately off. The promise is
                // that the app reads a label in front of you, and opening the
                // photo library would ask for far more trust than that needs.
                .setGalleryImportAllowed(false)
                .build()

            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener {
                    currentOnError(
                        "The document scanner is not available on this device. " +
                            "The normal capture button still works."
                    )
                }
        }
    }
}
