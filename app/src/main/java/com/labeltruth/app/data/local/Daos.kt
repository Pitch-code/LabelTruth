package com.labeltruth.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    @Query("SELECT COUNT(*) FROM ingredients")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSynonyms(items: List<SynonymEntity>)

    /**
     * Seeds both tables in a single transaction. With thousands of rows this
     * matters: two separate transactions would roughly double the write cost,
     * and a crash between them would leave a half-populated dictionary.
     */
    @Transaction
    suspend fun seed(ingredients: List<IngredientEntity>, synonyms: List<SynonymEntity>) {
        insertIngredients(ingredients)
        insertSynonyms(synonyms)
    }

    /**
     * Lookups prefer the category of the product being scanned.
     *
     * Scanning sunscreen must not surface the food verdict for titanium
     * dioxide, which is banned in EU food but permitted as a cosmetic UV
     * filter. The ORDER BY puts the matching category first and falls back to
     * the other, so we still answer rather than returning nothing.
     */
    @Query(
        """
        SELECT * FROM ingredients WHERE normalizedName = :normalized
        ORDER BY CASE WHEN category = :preferred THEN 0 ELSE 1 END
        LIMIT 1
        """
    )
    suspend fun byNormalizedName(normalized: String, preferred: String): IngredientEntity?

    @Query(
        """
        SELECT i.* FROM ingredients i
        INNER JOIN synonyms s ON s.ingredientId = i.id
        WHERE s.normalized = :normalized
        ORDER BY CASE WHEN i.category = :preferred THEN 0 ELSE 1 END
        LIMIT 1
        """
    )
    suspend fun bySynonym(normalized: String, preferred: String): IngredientEntity?

    @Query(
        """
        SELECT * FROM ingredients WHERE eNumber = :eNumber
        ORDER BY CASE WHEN category = :preferred THEN 0 ELSE 1 END
        LIMIT 1
        """
    )
    suspend fun byENumber(eNumber: String, preferred: String): IngredientEntity?

    @Query("SELECT * FROM ingredients WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): IngredientEntity?

    @Query("SELECT normalizedName AS name, id, category FROM ingredients")
    suspend fun allNormalizedNames(): List<NameRow>

    @Query("SELECT normalized AS name, ingredientId AS id, category FROM synonyms")
    suspend fun allSynonymNames(): List<NameRow>

    @Query(
        """
        SELECT * FROM ingredients
        WHERE normalizedName LIKE '%' || :query || '%'
           OR eNumber LIKE '%' || :query || '%'
        ORDER BY LENGTH(canonicalName) ASC
        LIMIT 40
        """
    )
    suspend fun search(query: String): List<IngredientEntity>
}

@Dao
interface ScanDao {

    @Insert
    suspend fun insert(scan: ScanEntity): Long

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<ScanEntity>>

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}
