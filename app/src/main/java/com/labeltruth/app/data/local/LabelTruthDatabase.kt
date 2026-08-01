package com.labeltruth.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        IngredientEntity::class,
        SynonymEntity::class,
        ScanEntity::class,
        BookmarkEntity::class
    ],
    // v2 added a category column to synonyms and made (name, category) the
    // uniqueness rule, so the same substance can exist for food and cosmetics.
    // v3 added a category column to scan_history, needed to reopen a saved scan
    // against the right route of exposure.
    // v4 added the bookmarks table.
    version = 4,
    exportSchema = true
)
abstract class LabelTruthDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao
    abstract fun scanDao(): ScanDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        private const val NAME = "labeltruth.db"

        /**
         * Adds the bookmarks table without touching anything else.
         *
         * The SQL must match what Room generates for [BookmarkEntity] exactly,
         * or Room rejects the database at open time. It is verified against the
         * exported schema in app/schemas.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` " +
                        "(`ingredientId` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ingredientId`))"
                )
            }
        }

        @Volatile
        private var instance: LabelTruthDatabase? = null

        fun get(context: Context): LabelTruthDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LabelTruthDatabase::class.java,
                    NAME
                )
                    // Bookmarks and scan history are things the user made, so
                    // v3 -> v4 is a real migration rather than a wipe. The
                    // destructive fallback stays only as a backstop for the
                    // dictionary, which is a rebuildable cache of a bundled
                    // asset. Every future version that touches user data needs
                    // a migration like this one.
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
