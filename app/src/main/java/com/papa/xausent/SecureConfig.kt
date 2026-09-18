package com.papa.xausent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureConfig {
    private const val PREF = "oanda_secure_config"
    private const val KEY_ALIAS = "xau_sent_oanda"
    private const val TOKEN = "token"
    private const val ACCOUNT = "account"

    fun save(context: Context, token: String, account: String) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(TOKEN, encrypt(token.trim()))
            .putString(ACCOUNT, encrypt(account.trim()))
            .apply()
    }

    fun read(context: Context): Credentials? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val token = prefs.getString(TOKEN, null)?.let(::decrypt).orEmpty()
        val account = prefs.getString(ACCOUNT, null)?.let(::decrypt).orEmpty()
        return if (token.isBlank() || account.isBlank()) null else Credentials(token, account)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        return try {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, 12))
            String(cipher.doFinal(payload, 12, payload.size - 12), StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    data class Credentials(val token: String, val account: String)
}