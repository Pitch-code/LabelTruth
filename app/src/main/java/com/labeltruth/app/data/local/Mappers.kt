package com.labeltruth.app.data.local

import com.labeltruth.app.domain.model.Ingredient
import com.labeltruth.app.domain.model.RiskTier
import com.labeltruth.app.domain.model.SourceRef

private fun String.splitList(): List<String> =
    if (isBlank()) emptyList() else split(LIST_DELIMITER).filter { it.isNotBlank() }

fun IngredientEntity.toDomain(): Ingredient = Ingredient(
    id = id,
    name = canonicalName,
    eNumber = eNumber,
    category = category,
    whatItIs = whatItIs,
    whyUsed = whyUsed,
    riskTier = RiskTier.from(riskTier),
    riskReason = riskReason,
    allergens = allergens.splitList(),
    dietaryFlags = dietaryFlags.splitList(),
    cautionGroups = cautionGroups.splitList(),
    adi = adi,
    sources = if (sources.isBlank()) emptyList() else sources.split(SOURCE_DELIMITER)
        .mapNotNull { entry ->
            val parts = entry.split(SOURCE_FIELD_DELIMITER)
            val title = parts.getOrNull(0)?.trim().orEmpty()
            if (title.isEmpty()) null
            else SourceRef(title, parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() })
        }
)
