package com.labellens.app.data.local

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
        Index(value = ["normalizedName"], unique = true),
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

@Entity(
    tableName = "synonyms",
    indices = [Index(value = ["ingredientId"])]
)
data class SynonymEntity(
    @PrimaryKey val normalized: String,
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
data class NameRow(val name: String, val id: String)
