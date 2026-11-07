package com.vishwaravi.pocketproj.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted representation of one locker note.
 *
 * Persist these byte arrays as BLOB columns (or encode them for a transport format).
 * Neither [ciphertext] nor [wrappedDataKey] contains plaintext.
 */
data class EncryptedNote(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val contentIv: ByteArray,
    val ciphertext: ByteArray,
    val wrappedKeyIv: ByteArray,
    val wrappedDataKey: ByteArray
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/**
 * Per-note envelope encryption.
 *
 * A fresh random 256-bit data-encryption key (DEK) encrypts each note with AES-256-GCM.
 * The DEK is then wrapped by an AES-256-GCM key held by Android Keystore. The Keystore
 * key is non-exportable; only the wrapped DEK is persisted.
 *
 * The existing Hidden Locker PIN remains the application access gate. It is deliberately
 * not used as an encryption key: a four-digit PIN has only 10,000 candidates and would
 * permit practical offline guessing if used to wrap note keys. Fingerprint is an
 * authentication gate in the existing UI; binding the wrapping key to BiometricPrompt's
 * CryptoObject is a separate integration step and must not be approximated by a boolean
 * preference check.
 *
 * Call only after the Hidden Locker session has been authenticated. The caller must drop
 * decrypted strings and any references to them when the locker locks.
 */
class LockerNoteCrypto(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun encrypt(noteId: String, plaintext: String): EncryptedNote {
        require(noteId.isNotBlank()) { "noteId must not be blank" }

        val dataKeyBytes = ByteArray(DATA_KEY_SIZE_BYTES).also(secureRandom::nextBytes)
        try {
            val dataKey = SecretKeySpec(dataKeyBytes, AES_ALGORITHM)
            val contentCipher = Cipher.getInstance(TRANSFORMATION)
            contentCipher.init(Cipher.ENCRYPT_MODE, dataKey)
            contentCipher.updateAAD(noteId.toByteArray(StandardCharsets.UTF_8))
            val ciphertext = contentCipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

            val wrappingCipher = Cipher.getInstance(TRANSFORMATION)
            wrappingCipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
            wrappingCipher.updateAAD(noteId.toByteArray(StandardCharsets.UTF_8))
            val wrappedDataKey = wrappingCipher.doFinal(dataKeyBytes)

            return EncryptedNote(
                contentIv = contentCipher.iv,
                ciphertext = ciphertext,
                wrappedKeyIv = wrappingCipher.iv,
                wrappedDataKey = wrappedDataKey
            )
        } finally {
            dataKeyBytes.fill(0)
        }
    }

    fun decrypt(noteId: String, encryptedNote: EncryptedNote): String {
        require(noteId.isNotBlank()) { "noteId must not be blank" }
        require(encryptedNote.formatVersion == EncryptedNote.CURRENT_FORMAT_VERSION) {
            "Unsupported encrypted note format: ${encryptedNote.formatVersion}"
        }
        require(encryptedNote.contentIv.size == GCM_IV_SIZE_BYTES) { "Invalid content IV" }
        require(encryptedNote.wrappedKeyIv.size == GCM_IV_SIZE_BYTES) { "Invalid wrapped-key IV" }

        val wrappingCipher = Cipher.getInstance(TRANSFORMATION)
        wrappingCipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateWrappingKey(),
            GCMParameterSpec(GCM_TAG_SIZE_BITS, encryptedNote.wrappedKeyIv)
        )
        wrappingCipher.updateAAD(noteId.toByteArray(StandardCharsets.UTF_8))
        val dataKeyBytes = wrappingCipher.doFinal(encryptedNote.wrappedDataKey)

        try {
            require(dataKeyBytes.size == DATA_KEY_SIZE_BYTES) { "Invalid note data key" }
            val dataKey = SecretKeySpec(dataKeyBytes, AES_ALGORITHM)
            val contentCipher = Cipher.getInstance(TRANSFORMATION)
            contentCipher.init(
                Cipher.DECRYPT_MODE,
                dataKey,
                GCMParameterSpec(GCM_TAG_SIZE_BITS, encryptedNote.contentIv)
            )
            contentCipher.updateAAD(noteId.toByteArray(StandardCharsets.UTF_8))
            return String(contentCipher.doFinal(encryptedNote.ciphertext), StandardCharsets.UTF_8)
        } finally {
            dataKeyBytes.fill(0)
        }
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val DEFAULT_KEY_ALIAS = "pocket.hidden-locker.notes.wrap.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val DATA_KEY_SIZE_BYTES = KEY_SIZE_BITS / 8
        private const val GCM_TAG_SIZE_BITS = 128
        private const val GCM_IV_SIZE_BYTES = 12
    }
}
