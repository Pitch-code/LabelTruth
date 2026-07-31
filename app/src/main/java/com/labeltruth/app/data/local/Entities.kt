package com.labeltruth.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * List-valued columns are stored as a single delimited string. This keeps the
 * schema flat and the bundled dictionary small, which matters when this grows
 * from a few hundred rows to tens of thousands.
 */
const val LIST_DELIMITER = "|"
const val SOURCE_DELIMITER = ";;"
const val SOURCE_FIELD_DELIMITER = "@@"

@Entity(
    tableName = "ingredients",
    indices = [
        // Not unique any more: the same name can exist once per category.
        Index(value = ["normalizedName"]),
        Index(value = ["normalizedName", "category"], unique = true),
        Index(value = ["eNumber"])
    ]
)
data class IngredientEntity(
    @PrimaryKey val id: String,
    val canonicalName: String,
    val normalizedName: String,
    val eNumber: String?,
    val category: String,
    val whatItIs: String,
    val whyUsed: String,
    val riskTier: String,
    val riskReason: String,
    val allergens: String,
    val dietaryFlags: String,
    val cautionGroups: String,
    val adi: String?,
    val sources: String
)

/**
 * Keyed on (normalized, category), not on normalized alone.
 *
 * The same word can mean different things by route of exposure. "Titanium
 * dioxide" is banned in EU food but permitted as a cosmetic UV filter, so both
 * entries must be able to own that synonym within their own category.
 */
@Entity(
    tableName = "synonyms",
    primaryKeys = ["normalized", "category"],
    indices = [Index(value = ["ingredientId"]), Index(value = ["normalized"])]
)
data class SynonymEntity(
    val normalized: String,
    val category: String,
    val ingredientId: String
)

@Entity(tableName = "scan_history")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val brand: String?,
    val barcode: String?,
    val score: Int,
    val timestamp: Long,
    val ingredientsRaw: String
)

/** Lightweight projection used to build the in-memory fuzzy-match index. */
data class NameRow(val name: String, val id: String, val category: String)
