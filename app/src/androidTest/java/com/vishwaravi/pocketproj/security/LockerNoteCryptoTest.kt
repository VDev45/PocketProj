package com.vishwaravi.pocketproj.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID
import javax.crypto.AEADBadTagException

@RunWith(AndroidJUnit4::class)
class LockerNoteCryptoTest {
    private lateinit var alias: String
    private lateinit var crypto: LockerNoteCrypto

    @Before
    fun setUp() {
        alias = "pocket.test.notes.${UUID.randomUUID()}"
        crypto = LockerNoteCrypto(keyAlias = alias)
    }

    @After
    fun tearDown() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry(alias)
    }

    @Test
    fun roundTripsPlaintextIncludingUnicode() {
        val plaintext = "Private note 🔐\nTamil: வணக்கம்"
        val encrypted = crypto.encrypt("note-1", plaintext)

        assertEquals(plaintext, crypto.decrypt("note-1", encrypted))
        assertNotEquals(plaintext, encrypted.ciphertext.decodeToString())
    }

    @Test
    fun wrongNoteIdFailsAuthentication() {
        val encrypted = crypto.encrypt("note-1", "secret")

        assertThrows(AEADBadTagException::class.java) {
            crypto.decrypt("note-2", encrypted)
        }
    }

    @Test
    fun modifiedCiphertextFailsAuthentication() {
        val encrypted = crypto.encrypt("note-1", "secret")
        val tampered = encrypted.copy(ciphertext = encrypted.ciphertext.copyOf().also {
            it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte()
        })

        assertThrows(AEADBadTagException::class.java) {
            crypto.decrypt("note-1", tampered)
        }
    }

    @Test
    fun modifiedWrappedKeyFailsAuthentication() {
        val encrypted = crypto.encrypt("note-1", "secret")
        val tampered = encrypted.copy(wrappedDataKey = encrypted.wrappedDataKey.copyOf().also {
            it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte()
        })

        assertThrows(AEADBadTagException::class.java) {
            crypto.decrypt("note-1", tampered)
        }
    }

    @Test
    fun eachEncryptionUsesFreshIvAndDataKey() {
        val first = crypto.encrypt("note-1", "same plaintext")
        val second = crypto.encrypt("note-1", "same plaintext")

        assertNotEquals(first.contentIv.toList(), second.contentIv.toList())
        assertNotEquals(first.wrappedKeyIv.toList(), second.wrappedKeyIv.toList())
        assertNotEquals(first.ciphertext.toList(), second.ciphertext.toList())
        assertNotEquals(first.wrappedDataKey.toList(), second.wrappedDataKey.toList())
    }

    @Test
    fun wrappedKeyCanBeUsedByANewCryptoInstanceForSameKeystoreAlias() {
        val encrypted = crypto.encrypt("note-1", "survives process recreation")
        val recreated = LockerNoteCrypto(keyAlias = alias)

        assertEquals("survives process recreation", recreated.decrypt("note-1", encrypted))
    }

    @Test
    fun decryptWithMissingKeyFailsInsteadOfGeneratingOne() {
        // A fresh alias has no Keystore entry. Decrypting must report that plainly and must not mint
        // a replacement key: doing so would make every note wrapped by the lost key undecryptable
        // while looking like ordinary corruption.
        val envelope = EncryptedNote(
            contentIv = ByteArray(12),
            ciphertext = byteArrayOf(1, 2, 3),
            wrappedKeyIv = ByteArray(12),
            wrappedDataKey = byteArrayOf(4, 5, 6)
        )

        assertThrows(IllegalStateException::class.java) {
            crypto.decrypt("note-1", envelope)
        }
        assertFalse(
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias)
        )
    }

    @Test
    fun unsupportedEnvelopeVersionIsRejected() {
        val encrypted = crypto.encrypt("note-1", "secret").copy(formatVersion = 999)

        assertThrows(IllegalArgumentException::class.java) {
            crypto.decrypt("note-1", encrypted)
        }
    }
}
