package com.labeltruth.app.data.seed

import kotlinx.serialization.Serializable

@Serializable
data class SeedFile(
    val version: Int,
    val ingredients: List<SeedIngredient>
)

@Serializable
data class SeedIngredient(
    val id: String,
    val name: String,
    val eNumber: String? = null,
    val synonyms: List<String> = emptyList(),
    val category: String = "food",
    val whatItIs: String,
    val whyUsed: String,
    val riskTier: String,
    val riskReason: String,
    val allergens: List<String> = emptyList(),
    val dietary: List<String> = emptyList(),
    val cautionGroups: List<String> = emptyList(),
    val adi: String? = null,
    val sources: List<SeedSource> = emptyList()
)

@Serializable
data class SeedSource(
    val title: String,
    val url: String? = null
)
