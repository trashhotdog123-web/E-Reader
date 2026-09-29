package com.trashhotdog123.ereader

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

object VaultCrypto {
    private const val ALIAS = "EReaderVaultKey"

    private fun key() = run {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(ALIAS, null)
        if (existing != null) return@run existing
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    fun encrypt(input: File, output: File) {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        output.parentFile?.mkdirs()
        FileInputStream(input).use { source ->
            FileOutputStream(output).use { target ->
                target.write(iv)
                CipherOutputStream(target, cipher).use { crypto -> source.copyTo(crypto, 64 * 1024) }
            }
        }
    }

    fun decrypt(input: File, output: File) {
        FileInputStream(input).use { source ->
            val iv = ByteArray(12)
            val read = source.read(iv)
            require(read == 12) { "Invalid vault file" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            }
            FileOutputStream(output).use { target ->
                CipherInputStream(source, cipher).use { crypto -> crypto.copyTo(target, 64 * 1024) }
            }
        }
    }
}
