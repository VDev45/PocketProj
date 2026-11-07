package com.vishwaravi.pocketproj.data

import com.vishwaravi.pocketproj.security.EncryptedNote
import com.vishwaravi.pocketproj.security.LockerNoteCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

data class LockerNote(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Persistence boundary for Hidden Locker notes.
 *
 * The caller must enforce a valid Hidden Locker session before collecting [observeAll]
 * or calling any read/write method. This class intentionally has no access to PIN or
 * session preferences; authorization belongs to the UI/session layer.
 */
class LockerNoteRepository(
    private val dao: LockerNoteDao,
    private val crypto: LockerNoteCrypto,
    private val now: () -> Long = System::currentTimeMillis
) {
    fun observeAll(): Flow<List<LockerNote>> =
        dao.observeAll().map { rows -> rows.map(::decrypt) }

    suspend fun get(id: String): LockerNote? =
        dao.findById(id)?.let(::decrypt)

    suspend fun create(title: String, body: String): LockerNote {
        val timestamp = now()
        val note = LockerNote(
            id = UUID.randomUUID().toString(),
            title = title,
            body = body,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        persist(note)
        return note
    }

    suspend fun update(id: String, title: String, body: String): LockerNote {
        val existing = dao.findById(id)
            ?: throw NoSuchElementException("Locker note not found")
        val note = LockerNote(
            id = id,
            title = title,
            body = body,
            createdAt = existing.createdAt,
            updatedAt = now()
        )
        persist(note)
        return note
    }

    suspend fun delete(id: String): Boolean = dao.deleteById(id) > 0

    private suspend fun persist(note: LockerNote) {
        val payload = JSONObject()
            .put("title", note.title)
            .put("body", note.body)
            .toString()
        val encrypted = crypto.encrypt(note.id, payload)
        dao.upsert(
            LockerNoteEntity(
                id = note.id,
                formatVersion = encrypted.formatVersion,
                contentIv = encrypted.contentIv,
                ciphertext = encrypted.ciphertext,
                wrappedKeyIv = encrypted.wrappedKeyIv,
                wrappedDataKey = encrypted.wrappedDataKey,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt
            )
        )
    }

    private fun decrypt(entity: LockerNoteEntity): LockerNote {
        val payload = crypto.decrypt(
            entity.id,
            EncryptedNote(
                formatVersion = entity.formatVersion,
                contentIv = entity.contentIv,
                ciphertext = entity.ciphertext,
                wrappedKeyIv = entity.wrappedKeyIv,
                wrappedDataKey = entity.wrappedDataKey
            )
        )
        val json = JSONObject(payload)
        return LockerNote(
            id = entity.id,
            title = json.getString("title"),
            body = json.getString("body"),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
