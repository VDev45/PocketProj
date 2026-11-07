package com.vishwaravi.pocketproj.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.vishwaravi.pocketproj.security.LockerNoteCrypto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LockerNoteRepositoryTest {
    private lateinit var database: PocketDatabase
    private lateinit var repository: LockerNoteRepository
    private lateinit var keyAlias: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PocketDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        keyAlias = "pocket.test.repository.${UUID.randomUUID()}"
        repository = LockerNoteRepository(
            dao = database.lockerNoteDao(),
            crypto = LockerNoteCrypto(keyAlias)
        )
    }

    @After
    fun tearDown() {
        database.close()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(keyAlias)
        }
    }

    @Test
    fun createAndReadRoundTripsEncryptedNote() = runBlocking {
        val created = repository.create("Private title", "Sensitive body text")

        val stored = database.lockerNoteDao().findById(created.id)!!
        assertFalse(stored.ciphertext.decodeToString().contains("Private title"))
        assertFalse(stored.ciphertext.decodeToString().contains("Sensitive body text"))
        assertEquals(created, repository.get(created.id))
    }

    @Test
    fun updateKeepsIdentityAndCreationTimeButChangesContent() = runBlocking {
        val created = repository.create("Old title", "Old body")
        val updated = repository.update(created.id, "New title", "New body")

        assertEquals(created.id, updated.id)
        assertEquals(created.createdAt, updated.createdAt)
        assertEquals("New title", repository.get(created.id)?.title)
        assertEquals("New body", repository.get(created.id)?.body)
        assertTrue(updated.updatedAt >= created.updatedAt)
    }

    @Test
    fun deleteRemovesEncryptedRecord() = runBlocking {
        val created = repository.create("Title", "Body")

        assertTrue(repository.delete(created.id))
        assertNull(repository.get(created.id))
        assertFalse(repository.delete(created.id))
    }

    @Test
    fun updateUnknownNoteFailsInsteadOfCreatingIt() = runBlocking {
        val failure = runCatching {
            repository.update("missing-note", "Title", "Body")
        }.exceptionOrNull()

        assertTrue(failure is NoSuchElementException)
        assertNull(database.lockerNoteDao().findById("missing-note"))
    }
}
