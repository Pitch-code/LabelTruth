package com.labeltruth.app.data.remote

import android.content.Context
import com.labeltruth.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
private data class OffResponse(
    val status: Int = 0,
    val product: OffProduct? = null
)

@Serializable
private data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_en") val productNameEn: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null,
    @SerialName("ingredients_text_en") val ingredientsTextEn: String? = null,
    @SerialName("image_front_url") val imageUrl: String? = null
)

/** What the app actually needs out of a barcode lookup. */
data class RemoteProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val quantity: String?,
    val ingredientsText: String,
    val imageUrl: String?
)

sealed interface LookupResult {
    data class Found(val product: RemoteProduct) : LookupResult
    /** Barcode is valid but the product is not in the database yet. */
    data object NotFound : LookupResult
    /** Product exists but has no ingredient list, so there is nothing to analyse. */
    data class NoIngredients(val name: String) : LookupResult
    data class Error(val message: String) : LookupResult
}

/**
 * Barcode -> product lookup against Open Food Facts.
 *
 * Data is licensed under the Open Database License; attribution is shown in the
 * app's About screen and is a licence condition, not a courtesy.
 *
 * Note for production: this currently calls Open Food Facts directly. Before
 * launch this should move behind our own caching proxy, which lets us add
 * certificate pinning against a certificate we control, cache popular barcodes,
 * and swap providers without shipping an app update.
 */
class OpenFoodFactsClient(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "off-http"), CACHE_BYTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        if (!barcode.all { it.isDigit() } || barcode.length !in 6..14) {
            return@withContext LookupResult.Error("That does not look like a product barcode.")
        }

        val url = "$BASE_URL/api/v2/product/$barcode.json?fields=$FIELDS"
        val request = Request.Builder()
            .url(url)
            // Open Food Facts asks every client to identify itself.
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext LookupResult.NotFound
                if (!response.isSuccessful) {
                    return@withContext LookupResult.Error("Lookup failed (${response.code}).")
                }
                val body = response.body?.string()
                    ?: return@withContext LookupResult.Error("Empty response from server.")

                val parsed = json.decodeFromString<OffResponse>(body)
                val product = parsed.product
                if (parsed.status != 1 || product == null) return@withContext LookupResult.NotFound

                val name = product.productNameEn?.takeIf { it.isNotBlank() }
                    ?: product.productName?.takeIf { it.isNotBlank() }
                    ?: "Unnamed product"

                val ingredients = product.ingredientsTextEn?.takeIf { it.isNotBlank() }
                    ?: product.ingredientsText?.takeIf { it.isNotBlank() }
                    ?: return@withContext LookupResult.NoIngredients(name)

                LookupResult.Found(
                    RemoteProduct(
                        barcode = barcode,
                        name = name,
                        brand = product.brands?.split(",")?.firstOrNull()?.trim()
                            ?.takeIf { it.isNotEmpty() },
                        quantity = product.quantity?.takeIf { it.isNotBlank() },
                        ingredientsText = ingredients,
                        imageUrl = product.imageUrl?.takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: java.io.IOException) {
            LookupResult.Error("No connection. Barcode lookup needs internet.")
        } catch (e: Exception) {
            LookupResult.Error("Could not read the product data.")
        }
    }

    private companion object {
        const val BASE_URL = "https://world.openfoodfacts.org"
        const val CACHE_BYTES = 8L * 1024 * 1024
        const val FIELDS =
            "product_name,product_name_en,brands,quantity,ingredients_text,ingredients_text_en,image_front_url"
        // Open Food Facts asks clients to identify themselves, and to include a
        // contact so they can reach us if our traffic causes problems. Add a real
        // contact URL or email here before going to production.
        val USER_AGENT =
            "LabelTruth/${BuildConfig.VERSION_NAME} (Android; com.labeltruth.app)"
    }
}
