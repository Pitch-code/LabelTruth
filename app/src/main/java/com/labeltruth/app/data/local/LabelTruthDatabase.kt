package com.labeltruth.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [IngredientEntity::class, SynonymEntity::class, ScanEntity::class],
    version = 1,
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
                ).build().also { instance = it }
            }
    }
}
