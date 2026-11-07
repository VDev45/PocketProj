package com.vishwaravi.pocketproj.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Guards the hand-written `MIGRATION_6_7`.
 *
 * The app sets `exportSchema = false`, so there is no schema artifact to migrate from. The v6
 * database is therefore built by Room itself from a v6 copy of the entity list — the `links` and
 * `collections` entities have not changed since v6, so that is exactly the schema a v6 install has
 * on disk. Opening the same file as v7 then runs the real migration and, crucially, makes Room
 * validate the resulting schema against [LockerNoteEntity]: a `CREATE TABLE` that disagrees with the
 * entity throws instead of shipping, which is the failure mode this test exists to prevent.
 */
@RunWith(AndroidJUnit4::class)
class PocketDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "migration-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun v6DatabaseUpgradesToV7KeepingLinksAndAddingNotes() = runBlocking {
        val legacy = Room.databaseBuilder(context, LegacyV6Database::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        val inboxId = legacy.collectionDao().insert(CollectionEntity(name = "Inbox"))
        legacy.linkDao().insert(
            LinkEntity(url = "https://example.com/saved", collectionId = inboxId, isHidden = true)
        )
        legacy.close()

        val upgraded = openV7()

        // Existing rows survive the upgrade untouched.
        val links = upgraded.linkDao().getAll()
        assertEquals(listOf("https://example.com/saved"), links.map { it.url })
        assertTrue(links.single().isHidden)
        assertEquals("Inbox", upgraded.collectionDao().findById(inboxId)?.name)

        // The new table exists with the column set the entity declares.
        val notes = upgraded.lockerNoteDao()
        assertNull(notes.findById("missing"))
        val row = LockerNoteEntity(
            id = "note-1",
            formatVersion = 1,
            contentIv = ByteArray(12) { it.toByte() },
            ciphertext = byteArrayOf(1, 2, 3),
            wrappedKeyIv = ByteArray(12) { (it + 1).toByte() },
            wrappedDataKey = byteArrayOf(4, 5, 6),
            createdAt = 1L,
            updatedAt = 2L
        )
        notes.insert(row)
        val stored = notes.findById("note-1")!!
        assertEquals(1, stored.formatVersion)
        assertEquals(3, stored.ciphertext.size)
        assertEquals(2L, stored.updatedAt)

        upgraded.close()
    }

    private fun openV7(): PocketDatabase =
        Room.databaseBuilder(context, PocketDatabase::class.java, databaseName)
            .addMigrations(*PocketDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
}

/**
 * The pre-v7 schema, declared with the v6 entity set. Kept separate from [PocketDatabase] so adding
 * an entity there cannot silently change what this test considers "the old version".
 */
@Database(
    entities = [LinkEntity::class, CollectionEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LegacyV6Database : RoomDatabase() {
    abstract fun linkDao(): LinkDao
    abstract fun collectionDao(): CollectionDao
}
