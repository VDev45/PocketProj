package com.vishwaravi.pocketproj.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromMetadataState(state: MetadataState): String = state.name

    @TypeConverter
    fun toMetadataState(value: String): MetadataState =
        MetadataState.entries.firstOrNull { it.name == value } ?: MetadataState.PENDING
}

@Database(
    entities = [LinkEntity::class, CollectionEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PocketDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE links ADD COLUMN collection TEXT NOT NULL DEFAULT 'Inbox'")
                database.execSQL("ALTER TABLE links ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE links ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE links ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_collections_name ON collections(name)"
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO collections(name, createdAt) VALUES('Inbox', ${System.currentTimeMillis()})"
                )
            }
        }

        /**
         * Links switch from a free-text `collection` column to a `collectionId` foreign key.
         * Uses a table rebuild (not DROP COLUMN, which needs SQLite 3.35+ but minSdk is 26):
         * 1. Ensure a collection row exists for every distinct collection name used by links.
         * 2. Ensure Inbox exists.
         * 3. Rebuild `links` with a resolved collectionId (case-insensitive name match,
         *    orphaned/blank names fall back to Inbox).
         * 4. Drop the legacy table and add the FK index.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO collections(name, createdAt)
                    SELECT DISTINCT TRIM(l.collection), ${System.currentTimeMillis()}
                    FROM links l
                    WHERE TRIM(l.collection) != ''
                    """.trimIndent()
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO collections(name, createdAt) VALUES('Inbox', ${System.currentTimeMillis()})"
                )

                database.execSQL("ALTER TABLE links RENAME TO links_old")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        url TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        imageUrl TEXT NOT NULL,
                        domain TEXT NOT NULL,
                        collectionId INTEGER NOT NULL,
                        tags TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isFavorite INTEGER NOT NULL,
                        FOREIGN KEY(collectionId) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO links (id, url, title, description, imageUrl, domain, collectionId, tags, createdAt, isFavorite)
                    SELECT l.id, l.url, l.title, l.description, l.imageUrl, l.domain,
                           COALESCE(
                             (SELECT c.id FROM collections c WHERE c.name = TRIM(l.collection) COLLATE NOCASE ORDER BY c.id LIMIT 1),
                             (SELECT c.id FROM collections c WHERE c.name = 'Inbox' COLLATE NOCASE ORDER BY c.id LIMIT 1)
                           ),
                           l.tags, l.createdAt, l.isFavorite
                    FROM links_old l
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE links_old")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_links_collectionId ON links(collectionId)")
            }
        }

        /**
         * v6 adds the explicit hidden flag and the background-metadata lifecycle columns,
         * aligns the collection-name uniqueness index with the app's case-insensitive lookup
         * (reassigning then dropping historical case-variant duplicates first), and indexes
         * `url` for duplicate detection.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE links ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE links ADD COLUMN metadataState TEXT NOT NULL DEFAULT 'PENDING'")
                database.execSQL("ALTER TABLE links ADD COLUMN metadataAttempts INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_links_url ON links(url)")

                // Reassign links that point at a case-variant duplicate to its lowest id…
                database.execSQL(
                    """
                    UPDATE links SET collectionId = (
                        SELECT MIN(c2.id) FROM collections c2
                        WHERE c2.name = (SELECT c1.name FROM collections c1 WHERE c1.id = links.collectionId) COLLATE NOCASE
                    ) WHERE EXISTS (
                        SELECT 1 FROM collections c2
                        WHERE c2.name = (SELECT c1.name FROM collections c1 WHERE c1.id = links.collectionId) COLLATE NOCASE
                          AND c2.id != links.collectionId
                    )
                    """.trimIndent()
                )
                // …then drop the duplicates before installing the case-insensitive unique index.
                database.execSQL(
                    "DELETE FROM collections WHERE id NOT IN (SELECT MIN(id) FROM collections GROUP BY name COLLATE NOCASE)"
                )
                database.execSQL("DROP INDEX IF EXISTS index_collections_name")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_collections_name ON collections(name COLLATE NOCASE)"
                )
            }
        }

        @Volatile private var INSTANCE: PocketDatabase? = null

        fun get(context: Context): PocketDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PocketDatabase::class.java,
                    "pocket_proj.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}