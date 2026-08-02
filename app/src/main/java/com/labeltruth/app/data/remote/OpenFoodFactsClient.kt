package com.labeltruth.app.data.remote

import android.content.Context
import com.labeltruth.app.BuildConfig
import com.labeltruth.app.domain.model.ProductCategory
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
    val imageUrl: String?,
    /**
     * Which database answered, which tells us the route of exposure. This is
     * how the app knows to read titanium dioxide as a cosmetic UV filter rather
     * than as an additive banned in EU food.
     */
    val category: ProductCategory
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

    /**
     * Tries the food database first, then the beauty one.
     *
     * Open Food Facts and Open Beauty Facts are separate servers with the same
     * API shape, and the food server does not redirect for a cosmetic barcode.
     * Whichever answers also tells us the product category.
     */
    /**
     * Set when the server tells us we are asking too often. Until it passes, no
     * request is made at all: continuing to knock is both rude to a donated
     * public service and pointless, since the answer will be another refusal.
     */
    @Volatile
    private var blockedUntilMs = 0L

    private fun isRateLimited(): Boolean = System.currentTimeMillis() < blockedUntilMs

    suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        if (!barcode.all { it.isDigit() } || barcode.length !in 6..14) {
            return@withContext LookupResult.Error("That does not look like a product barcode.")
        }
        if (isRateLimited()) return@withContext LookupResult.Error(RATE_LIMIT_MESSAGE)

        val food = lookupOn(FOOD_HOST, ProductCategory.FOOD, barcode)
        if (food is LookupResult.Found) return@withContext food

        // If the food server just rate-limited us, asking the beauty server as
        // well would double the load at the exact moment we have been told to
        // back off. Report the refusal instead.
        if (isRateLimited()) return@withContext food

        val beauty = lookupOn(BEAUTY_HOST, ProductCategory.COSMETIC, barcode)
        if (beauty is LookupResult.Found) return@withContext beauty

        // Prefer a "product exists but has no ingredients" answer over a plain
        // not-found, since it is more useful to the user.
        listOf(food, beauty).firstOrNull { it is LookupResult.NoIngredients }
            ?: listOf(food, beauty).firstOrNull { it is LookupResult.Error }
            ?: LookupResult.NotFound
    }

    private suspend fun lookupOn(
        host: String,
        category: ProductCategory,
        barcode: String
    ): LookupResult = withContext(Dispatchers.IO) {
        val url = "$host/api/v2/product/$barcode.json?fields=$FIELDS"
        val request = Request.Builder()
            .url(url)
            // Open Food Facts asks every client to identify itself.
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext LookupResult.NotFound
                if (response.code == 429) {
                    // Honour Retry-After when present, otherwise back off for a
                    // sensible default rather than guessing aggressively.
                    val retryAfter = response.header("Retry-After")
                        ?.trim()
                        ?.toLongOrNull()
                        ?.coerceIn(1, MAX_BACKOFF_SECONDS)
                        ?: DEFAULT_BACKOFF_SECONDS
                    blockedUntilMs = System.currentTimeMillis() + retryAfter * 1000
                    return@withContext LookupResult.Error(RATE_LIMIT_MESSAGE)
                }
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
                        imageUrl = product.imageUrl?.takeIf { it.isNotBlank() },
                        category = category
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
        const val FOOD_HOST = "https://world.openfoodfacts.org"
        const val BEAUTY_HOST = "https://world.openbeautyfacts.org"
        const val CACHE_BYTES = 8L * 1024 * 1024
        const val FIELDS =
            "product_name,product_name_en,brands,quantity,ingredients_text,ingredients_text_en,image_front_url"
        const val DEFAULT_BACKOFF_SECONDS = 60L
        const val MAX_BACKOFF_SECONDS = 600L

        /**
         * Deliberately not phrased as an app fault or a product problem: the
         * lookup was refused, and the product may well be fine.
         */
        const val RATE_LIMIT_MESSAGE =
            "Open Food Facts is asking us to slow down. Wait a minute, or use " +
                "Label mode to read the printed ingredient list instead - that " +
                "works offline."

        // Open Food Facts asks clients to identify themselves, and to include a
        // contact so they can reach us if our traffic causes problems.
        val USER_AGENT =
            "LabelTruth/${BuildConfig.VERSION_NAME} " +
                "(Android; com.labeltruth.app; labeltruth.support@gmail.com)"
    }
}
