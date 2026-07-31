package com.labellens.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [IngredientEntity::class, SynonymEntity::class, ScanEntity::class],
    version = 1,
    exportSchema = true
)
abstract class LabelLensDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao
    abstract fun scanDao(): ScanDao

    companion object {
        private const val NAME = "labellens.db"

        @Volatile
        private var instance: LabelLensDatabase? = null

        fun get(context: Context): LabelLensDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LabelLensDatabase::class.java,
                    NAME
                ).build().also { instance = it }
            }
    }
}
