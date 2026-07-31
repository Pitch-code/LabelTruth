package com.labeltruth.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [IngredientEntity::class, SynonymEntity::class, ScanEntity::class],
    // v2 added a category column to synonyms and made (name, category) the
    // uniqueness rule, so the same substance can exist for food and cosmetics.
    version = 2,
    exportSchema = true
)
abstract class LabelTruthDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao
    abstract fun scanDao(): ScanDao

    companion object {
        private const val NAME = "labeltruth.db"

        @Volatile
        private var instance: LabelTruthDatabase? = null

        fun get(context: Context): LabelTruthDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LabelTruthDatabase::class.java,
                    NAME
                )
                    // The dictionary is a rebuildable cache of a bundled asset,
                    // so throwing it away and re-seeding is cheaper and safer
                    // than hand-writing migrations for it. Scan history is the
                    // only real loss, and the app is not published yet.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
