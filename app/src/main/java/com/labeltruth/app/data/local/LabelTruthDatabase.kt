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
    // v5 made scan_history.score nullable, so a label that was read but could
    // not be scored honestly is still kept rather than silently discarded.
    // v6 added scan_history.saved, so a scanned product can be pinned to the
    // Saved tab and not just an individual ingredient.
    version = 6,
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

        /**
         * Makes scan_history.score nullable, preserving every stored scan.
         *
         * SQLite cannot relax a NOT NULL constraint in place, so this is the
         * standard recreate-copy-swap. The CREATE statement is copied verbatim
         * from Room's own exported schema at app/schemas/.../5.json, because
         * Room compares the resulting schema against what it expects and
         * refuses to open the database on any mismatch. scan_history has no
         * indices or foreign keys, so there is nothing else to rebuild.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scan_history_new` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`productName` TEXT NOT NULL, `brand` TEXT, `barcode` TEXT, " +
                        "`score` INTEGER, `timestamp` INTEGER NOT NULL, " +
                        "`ingredientsRaw` TEXT NOT NULL, `category` TEXT NOT NULL)"
                )
                connection.execSQL(
                    "INSERT INTO `scan_history_new` " +
                        "(`id`, `productName`, `brand`, `barcode`, `score`, " +
                        "`timestamp`, `ingredientsRaw`, `category`) " +
                        "SELECT `id`, `productName`, `brand`, `barcode`, `score`, " +
                        "`timestamp`, `ingredientsRaw`, `category` FROM `scan_history`"
                )
                connection.execSQL("DROP TABLE `scan_history`")
                connection.execSQL("ALTER TABLE `scan_history_new` RENAME TO `scan_history`")
            }
        }

        /**
         * Adds scan_history.saved.
         *
         * Adding a column with a default is a single statement in SQLite, unlike
         * the recreate-copy-swap that MIGRATION_4_5 needed to relax a NOT NULL
         * constraint. Existing scans default to not saved, which is correct:
         * nobody has pinned them.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE `scan_history` ADD COLUMN `saved` " +
                        "INTEGER NOT NULL DEFAULT 0"
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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
