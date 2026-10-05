package com.example.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.data.model.AccountSession
import com.example.data.model.AccountType
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mluona_iptv_sessions", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCOUNTS = "key_saved_accounts"
        private const val KEY_ACTIVE_ACCOUNT_ID = "key_active_account_id"
        private const val KEY_APP_LANGUAGE = "key_app_language"
        private const val KEY_STREAM_FORMAT = "key_stream_format"
        private const val KEY_AUDIO_TYPE = "key_audio_type"
        private const val KEY_USER_AGENT = "key_user_agent"
        private const val KEYSTORE_ALIAS = "MluonaIptvKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENC_PREFIX = "enc:"
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyGenParameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encryptPassword(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            ENC_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            "obf:" + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decryptPassword(encoded: String): String {
        if (encoded.isEmpty()) return ""
        if (encoded.startsWith("obf:")) {
            return try {
                String(Base64.decode(encoded.removePrefix("obf:"), Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Exception) { "" }
        }
        if (!encoded.startsWith(ENC_PREFIX)) {
            return encoded
        }
        return try {
            val raw = Base64.decode(encoded.removePrefix(ENC_PREFIX), Base64.NO_WRAP)
            if (raw.size <= 12) return ""
            val iv = raw.copyOfRange(0, 12)
            val ciphertext = raw.copyOfRange(12, raw.size)
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun getLanguage(): String {
        val saved = prefs.getString(KEY_APP_LANGUAGE, null)
        if (saved != null) return saved
        val deviceLang = java.util.Locale.getDefault().language
        return if (deviceLang.startsWith("ar")) "ar" else if (deviceLang.startsWith("es")) "es" else "en"
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, lang).apply()
    }

    fun getStreamFormat(): String = prefs.getString(KEY_STREAM_FORMAT, "m3u8") ?: "m3u8"

    fun setStreamFormat(format: String) {
        prefs.edit().putString(KEY_STREAM_FORMAT, format).apply()
    }

    fun getAudioType(): String = prefs.getString(KEY_AUDIO_TYPE, "auto") ?: "auto"

    fun setAudioType(type: String) {
        prefs.edit().putString(KEY_AUDIO_TYPE, type).apply()
    }

    fun getUserAgent(): String = prefs.getString(KEY_USER_AGENT, "default") ?: "default"

    fun setUserAgent(ua: String) {
        prefs.edit().putString(KEY_USER_AGENT, ua).apply()
    }

    fun saveAccount(account: AccountSession) {
        val accounts = getAllAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            accounts[index] = account
        } else {
            accounts.add(0, account)
        }
        persistAccounts(accounts)
        setActiveAccountId(account.id)
    }

    fun getActiveAccount(): AccountSession? {
        val activeId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null) ?: return getAllAccounts().firstOrNull()
        return getAllAccounts().find { it.id == activeId } ?: getAllAccounts().firstOrNull()
    }

    fun setActiveAccountId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, id).apply()
    }

    fun getAllAccounts(): List<AccountSession> {
        val jsonString = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<AccountSession>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val accType = try {
                    AccountType.valueOf(obj.optString("type", AccountType.XTREAM.name))
                } catch (_: Exception) {
                    AccountType.XTREAM
                }
                val decryptedPass = decryptPassword(obj.optString("password"))
                val rawStatus = obj.optString("status", "Active")
                val finalStatus = if (accType == AccountType.XTREAM && decryptedPass.isEmpty()) {
                    "يتطلب إعادة تسجيل الدخول"
                } else {
                    rawStatus
                }

                list.add(
                    AccountSession(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        type = accType,
                        serverUrl = obj.optString("serverUrl"),
                        username = obj.optString("username"),
                        password = decryptedPass,
                        m3uUrl = obj.optString("m3uUrl"),
                        status = finalStatus,
                        expDate = obj.optString("expDate", ""),
                        maxConnections = obj.optString("maxConnections", "1"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        lastActiveAt = obj.optLong("lastActiveAt", System.currentTimeMillis()),
                        userAgent = obj.optString("userAgent", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteAccount(id: String) {
        val accounts = getAllAccounts().filter { it.id != id }
        persistAccounts(accounts)
        if (getActiveAccount()?.id == id) {
            setActiveAccountId(accounts.firstOrNull()?.id)
        }
    }

    fun clearActiveSession() {
        setActiveAccountId(null)
    }

    private fun persistAccounts(accounts: List<AccountSession>) {
        val jsonArray = JSONArray()
        for (account in accounts) {
            val obj = JSONObject().apply {
                put("id", account.id)
                put("name", account.name)
                put("type", account.type.name)
                put("serverUrl", account.serverUrl)
                put("username", account.username)
                put("password", encryptPassword(account.password))
                put("m3uUrl", account.m3uUrl)
                put("status", account.status)
                put("expDate", account.expDate)
                put("maxConnections", account.maxConnections)
                put("createdAt", account.createdAt)
                put("lastActiveAt", account.lastActiveAt)
                put("userAgent", account.userAgent)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_ACCOUNTS, jsonArray.toString()).apply()
    }
}
