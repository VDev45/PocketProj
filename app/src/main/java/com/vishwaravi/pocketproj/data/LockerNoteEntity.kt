package com.vishwaravi.pocketproj.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Only ciphertext and key-envelope bytes are persisted. Plaintext note fields must never
 * be added to this entity.
 */
@Entity(tableName = "locker_notes")
data class LockerNoteEntity(
    @PrimaryKey val id: String,
    val formatVersion: Int,
    val contentIv: ByteArray,
    val ciphertext: ByteArray,
    val wrappedKeyIv: ByteArray,
    val wrappedDataKey: ByteArray,
    val createdAt: Long,
    val updatedAt: Long
)
