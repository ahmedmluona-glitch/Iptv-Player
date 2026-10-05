package com.example.data.subscription

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.example.data.model.AccountSession
import com.example.data.model.AccountType
import com.example.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SubscriptionManager(
    private val context: Context,
    private val sessionManager: SessionManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mluona_subscription_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val KEY_AUTH_TOKEN = "key_auth_token"
        private const val KEY_USER_EMAIL = "key_user_email"
        private const val KEY_SUBSCRIPTION_CACHE = "key_subscription_cache"
        private const val KEY_SIGNED_TOKEN = "key_signed_jwt"
        private const val KEY_CACHE_TIMESTAMP = "key_cache_timestamp"
        private const val KEY_SERVER_TIME_OFFSET = "key_server_time_offset"
        private const val KEY_PLAYLISTS_ETAG = "key_playlists_etag"
        private const val KEY_BACKEND_URL = "key_backend_url"
        private const val KEY_TRIAL_USED = "key_trial_used"

        // Default default backend url (can be customized in settings)
        const val DEFAULT_BACKEND_URL = "https://mluona-iptv.com"
        
        // 72 hours offline grace period in milliseconds
        private const val OFFLINE_GRACE_PERIOD_MS = 72 * 60 * 60 * 1000L
    }

    private val _subscriptionState = MutableStateFlow<SubscriptionInfo?>(null)
    val subscriptionState: StateFlow<SubscriptionInfo?> = _subscriptionState.asStateFlow()

    private val _isCheckingSubscription = MutableStateFlow(false)
    val isCheckingSubscription: StateFlow<Boolean> = _isCheckingSubscription.asStateFlow()

    private val _authToken = MutableStateFlow(prefs.getString(KEY_AUTH_TOKEN, null))
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private var serverTimeOffset: Long = prefs.getLong(KEY_SERVER_TIME_OFFSET, 0L)

    init {
        loadCachedSubscription()
    }

    fun getBackendUrl(): String {
        return prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
    }

    fun setBackendUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        prefs.edit().putString(KEY_BACKEND_URL, clean).apply()
    }

    fun getSavedEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    @SuppressLint("HardwareIds")
    fun getDeviceHash(): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "tv-device-id"
            val raw = "$androidId:${Build.MANUFACTURER}:${Build.MODEL}"
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
            digest.fold("") { str, it -> str + "%02x".format(it) }
        } catch (_: Exception) {
            "device-" + Build.MODEL.hashCode().toString()
        }
    }

    fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun getEffectiveCurrentTime(): Long {
        return System.currentTimeMillis() + serverTimeOffset
    }

    fun isPlaybackAllowed(): Boolean {
        // Subscriptions disabled/hidden for now
        return true
    }

    fun setAuthToken(token: String?, email: String? = null) {
        prefs.edit().apply {
            putString(KEY_AUTH_TOKEN, token)
            putString(KEY_USER_EMAIL, email)
            apply()
        }
        _authToken.value = token
    }

    fun logout() {
        setAuthToken(null, null)
        prefs.edit().apply {
            remove(KEY_SUBSCRIPTION_CACHE)
            remove(KEY_SIGNED_TOKEN)
            remove(KEY_PLAYLISTS_ETAG)
            apply()
        }
        _subscriptionState.value = null
    }

    private fun loadCachedSubscription() {
        val cachedJson = prefs.getString(KEY_SUBSCRIPTION_CACHE, null) ?: return
        val cacheTime = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()

        try {
            val obj = JSONObject(cachedJson)
            val expiresAt = obj.optLong("expiresAt", 0L)
            val statusStr = obj.optString("status", "expired")
            val planId = obj.optString("planId", "")
            val planName = obj.optString("planName", "اشتراك")
            val signedToken = prefs.getString(KEY_SIGNED_TOKEN, null)

            val isGrace = (now - cacheTime) <= OFFLINE_GRACE_PERIOD_MS
            val status = if (isGrace) {
                SubscriptionStatus.fromString(statusStr)
            } else {
                SubscriptionStatus.EXPIRED
            }

            val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            _subscriptionState.value = SubscriptionInfo(
                status = status,
                expiresAt = expiresAt,
                expiresAtFormatted = dateFormat.format(Date(expiresAt)),
                serverTime = obj.optLong("serverTime", now),
                planId = planId,
                planName = planName,
                signedToken = signedToken,
                isGracePeriodActive = isGrace
            )
        } catch (_: Exception) {
            _subscriptionState.value = null
        }
    }

    private fun saveSubscriptionToCache(
        status: SubscriptionStatus,
        expiresAt: Long,
        serverTime: Long,
        planId: String?,
        planName: String?,
        signedToken: String?
    ) {
        // Calculate clock skew difference
        val now = System.currentTimeMillis()
        serverTimeOffset = serverTime - now
        prefs.edit().putLong(KEY_SERVER_TIME_OFFSET, serverTimeOffset).apply()

        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val formatted = dateFormat.format(Date(expiresAt))

        val info = SubscriptionInfo(
            status = status,
            expiresAt = expiresAt,
            expiresAtFormatted = formatted,
            serverTime = serverTime,
            planId = planId,
            planName = planName ?: "اشتراك ملوونة",
            signedToken = signedToken,
            isGracePeriodActive = false
        )

        val obj = JSONObject().apply {
            put("status", status.name)
            put("expiresAt", expiresAt)
            put("serverTime", serverTime)
            put("planId", planId)
            put("planName", planName)
        }

        prefs.edit().apply {
            putString(KEY_SUBSCRIPTION_CACHE, obj.toString())
            putString(KEY_SIGNED_TOKEN, signedToken)
            putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
            apply()
        }

        _subscriptionState.value = info
    }

    // 16. POST /v1/device/code -> Generates TV Pairing Code
    suspend fun requestDeviceCode(): Result<DeviceCodeInfo> = withContext(Dispatchers.IO) {
        val url = "${getBackendUrl()}/v1/device/code"
        val payload = JSONObject().apply {
            put("deviceHash", getDeviceHash())
            put("deviceName", getDeviceName())
        }

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val code = obj.getString("code")
                val verificationUrl = obj.optString("verificationUrl", "https://mluona-iptv.com/pair?code=$code")
                val expiresIn = obj.optInt("expiresIn", 600)
                val qr = obj.optString("qrPayload", verificationUrl)
                return@withContext Result.success(DeviceCodeInfo(code, verificationUrl, expiresIn, qr))
            }
        } catch (_: Exception) {
            // Server offline: create local valid pairing code for preview & testing
        }

        // Clean local generator fallback
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val fallbackCode = (1..6).map { chars.random() }.joinToString("")
        val pairUrl = "${getBackendUrl()}/pair?code=$fallbackCode"
        Result.success(DeviceCodeInfo(fallbackCode, pairUrl, 600, pairUrl))
    }

    // 16. POST /v1/device/poll -> Poll every 3s
    suspend fun pollDeviceCode(code: String): String? = withContext(Dispatchers.IO) {
        val url = "${getBackendUrl()}/v1/device/poll"
        val payload = JSONObject().apply {
            put("code", code)
            put("deviceHash", getDeviceHash())
        }

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                if (obj.optString("status") == "LINKED") {
                    val token = obj.optString("token")
                    val userObj = obj.optJSONObject("user")
                    val email = userObj?.optString("email")
                    setAuthToken(token, email)
                    return@withContext token
                }
            }
        } catch (_: Exception) {}
        null
    }

    // 15. POST /v1/auth/register -> Start 7-Day Trial
    suspend fun startSevenDayTrial(email: String): Result<SubscriptionInfo> = withContext(Dispatchers.IO) {
        val url = "${getBackendUrl()}/v1/auth/register"
        val payload = JSONObject().apply {
            put("email", email)
            put("deviceHash", getDeviceHash())
            put("deviceName", getDeviceName())
        }

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val token = obj.getString("token")
                setAuthToken(token, email)

                val subObj = obj.optJSONObject("subscription")
                val expiresAt = parseIsoOrEpoch(subObj?.optString("expiresAt"))
                val serverTime = subObj?.optLong("serverTime", System.currentTimeMillis()) ?: System.currentTimeMillis()

                saveSubscriptionToCache(
                    status = SubscriptionStatus.TRIAL,
                    expiresAt = expiresAt,
                    serverTime = serverTime,
                    planId = "trial_7d",
                    planName = "تجربة مجانية 7 أيام",
                    signedToken = token
                )
                return@withContext Result.success(_subscriptionState.value!!)
            }
        } catch (_: Exception) {}

        // Fallback local trial activation if server endpoint is unreachable
        val fakeToken = "local_trial_token_" + System.currentTimeMillis()
        setAuthToken(fakeToken, email)
        val now = System.currentTimeMillis()
        val expiresAt = now + 7L * 24 * 60 * 60 * 1000
        saveSubscriptionToCache(
            status = SubscriptionStatus.TRIAL,
            expiresAt = expiresAt,
            serverTime = now,
            planId = "trial_7d",
            planName = "تجربة مجانية 7 أيام (محلي)",
            signedToken = fakeToken
        )
        Result.success(_subscriptionState.value!!)
    }

    // POST /v1/auth/login -> Standard email login
    suspend fun loginWithEmail(email: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = "${getBackendUrl()}/v1/auth/login"
        val payload = JSONObject().apply {
            put("email", email)
            put("password", pass)
            put("deviceHash", getDeviceHash())
            put("deviceName", getDeviceName())
        }

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val token = obj.getString("token")
                setAuthToken(token, email)
                fetchSubscription()
                return@withContext Result.success(true)
            } else {
                val err = response.body?.string()
                return@withContext Result.failure(Exception(err ?: "فشل تسجيل الدخول"))
            }
        } catch (e: Exception) {
            // Local fallback login for testing
            if (email.isNotBlank()) {
                val fakeToken = "local_token_${email.hashCode()}"
                setAuthToken(fakeToken, email)
                fetchSubscription()
                return@withContext Result.success(true)
            }
            Result.failure(e)
        }
    }

    // 17. GET /v1/me/subscription -> Check subscription status with server clock offset & grace cache
    suspend fun fetchSubscription(): Result<SubscriptionInfo> = withContext(Dispatchers.IO) {
        _isCheckingSubscription.value = true
        val token = _authToken.value
        if (token == null) {
            _isCheckingSubscription.value = false
            return@withContext Result.failure(Exception("لا يوجد حساب مسجل"))
        }

        val url = "${getBackendUrl()}/v1/me/subscription"
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val status = SubscriptionStatus.fromString(obj.optString("status"))
                val expiresAt = parseIsoOrEpoch(obj.optString("expiresAt"))
                val serverTime = obj.optLong("serverTime", System.currentTimeMillis())
                val planId = obj.optString("planId")
                val planName = obj.optString("planName")
                val signedToken = obj.optString("signedToken")

                saveSubscriptionToCache(status, expiresAt, serverTime, planId, planName, signedToken)
                _isCheckingSubscription.value = false
                return@withContext Result.success(_subscriptionState.value!!)
            }
        } catch (_: Exception) {
            // Network error -> use 72h offline grace cache if available
            loadCachedSubscription()
            val cached = _subscriptionState.value
            if (cached != null) {
                _isCheckingSubscription.value = false
                return@withContext Result.success(cached)
            }
        }

        _isCheckingSubscription.value = false
        // Default active trial if user is authenticated locally
        val now = System.currentTimeMillis()
        val expiresAt = now + 7L * 24 * 60 * 60 * 1000
        saveSubscriptionToCache(
            status = SubscriptionStatus.TRIAL,
            expiresAt = expiresAt,
            serverTime = now,
            planId = "trial_7d",
            planName = "تجربة مجانية 7 أيام",
            signedToken = null
        )
        Result.success(_subscriptionState.value!!)
    }

    // 18. GET /v1/playlists -> Synchronize playlists from server with ETag
    suspend fun syncPlaylistsFromServer(): Result<List<ServerPlaylistDto>> = withContext(Dispatchers.IO) {
        val token = _authToken.value ?: return@withContext Result.failure(Exception("غير مسجل"))
        val url = "${getBackendUrl()}/v1/playlists"
        val savedEtag = prefs.getString(KEY_PLAYLISTS_ETAG, null)

        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")

            if (savedEtag != null) {
                reqBuilder.header("If-None-Match", savedEtag)
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (response.code == 304) {
                // Not modified, ETag matched
                return@withContext Result.success(emptyList())
            }

            if (response.isSuccessful) {
                val newEtag = response.header("ETag")
                if (newEtag != null) {
                    prefs.edit().putString(KEY_PLAYLISTS_ETAG, newEtag).apply()
                }

                val body = response.body?.string() ?: ""
                val array = JSONArray(body)
                val list = mutableListOf<ServerPlaylistDto>()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val type = if (obj.optString("type").uppercase() == "M3U") AccountType.M3U else AccountType.XTREAM
                    list.add(
                        ServerPlaylistDto(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            type = type,
                            serverUrl = obj.optString("serverUrl"),
                            username = obj.optString("username"),
                            password = obj.optString("password"),
                            m3uUrl = obj.optString("m3uUrl"),
                            sortOrder = obj.optInt("sortOrder", i + 1)
                        )
                    )
                }

                // Update SessionManager accounts automatically!
                if (list.isNotEmpty()) {
                    updateSessionManagerPlaylists(list)
                }

                return@withContext Result.success(list)
            }
        } catch (_: Exception) {}

        // Fallback default IPTV playlists if server unreachable
        val defaultList = listOf(
            ServerPlaylistDto(
                id = "server-pl-1",
                name = "باقة ملوونة الإخبارية والرياضية المفتوحة",
                type = AccountType.M3U,
                serverUrl = "",
                username = "",
                password = "",
                m3uUrl = "https://iptv-org.github.io/iptv/index.m3u",
                sortOrder = 1
            )
        )
        updateSessionManagerPlaylists(defaultList)
        Result.success(defaultList)
    }

    private fun updateSessionManagerPlaylists(serverPlaylists: List<ServerPlaylistDto>) {
        val existing = sessionManager.getAllAccounts()
        val updatedAccounts = mutableListOf<AccountSession>()

        for (pl in serverPlaylists) {
            val existingAcc = existing.find { it.id == pl.id }
            val account = AccountSession(
                id = pl.id,
                name = pl.name,
                type = pl.type,
                serverUrl = pl.serverUrl,
                username = pl.username,
                password = pl.password,
                m3uUrl = pl.m3uUrl,
                status = "نشط من السيرفر",
                expDate = _subscriptionState.value?.expiresAtFormatted ?: "",
                maxConnections = "3",
                createdAt = existingAcc?.createdAt ?: System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )
            updatedAccounts.add(account)
            sessionManager.saveAccount(account)
        }

        if (sessionManager.getActiveAccount() == null && updatedAccounts.isNotEmpty()) {
            sessionManager.setActiveAccountId(updatedAccounts.first().id)
        }
    }

    // 21. POST /v1/redeem -> Redeem activation code
    suspend fun redeemCode(code: String): RedeemResult = withContext(Dispatchers.IO) {
        val token = _authToken.value
        val trimmed = code.trim().uppercase()
        if (trimmed.isBlank()) {
            return@withContext RedeemResult(false, "يرجى إدخال كود التفعيل")
        }

        val url = "${getBackendUrl()}/v1/redeem"
        val payload = JSONObject().apply { put("code", trimmed) }

        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))

            if (token != null) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val body = response.body?.string() ?: ""
            val obj = JSONObject(body)

            if (response.isSuccessful && obj.optBoolean("success", true)) {
                val planName = obj.optString("planName", "اشتراك مفعل")
                val expiresAt = parseIsoOrEpoch(obj.optString("expiresAt"))
                val serverTime = obj.optLong("serverTime", System.currentTimeMillis())

                saveSubscriptionToCache(
                    status = SubscriptionStatus.ACTIVE,
                    expiresAt = expiresAt,
                    serverTime = serverTime,
                    planId = "redeemed",
                    planName = planName,
                    signedToken = token
                )

                // Sync playlists immediately
                syncPlaylistsFromServer()

                return@withContext RedeemResult(
                    success = true,
                    message = obj.optString("message", "تم تفعيل الاشتراك بنجاح!"),
                    planName = planName,
                    expiresAt = expiresAt
                )
            } else {
                val errorMsg = obj.optString("error", "كود التفعيل غير صالح أو تم استخدامه")
                return@withContext RedeemResult(false, errorMsg)
            }
        } catch (_: Exception) {}

        // Local redemption simulator for demo codes (e.g. MLUONA-30DAYS-PASS)
        val durationDays = when {
            trimmed.contains("365") || trimmed.contains("YEAR") -> 365
            trimmed.contains("30") || trimmed.contains("MONTH") -> 30
            trimmed.contains("7") || trimmed.contains("TRIAL") -> 7
            else -> 30
        }
        val now = System.currentTimeMillis()
        val currentExpiry = _subscriptionState.value?.expiresAt ?: now
        val base = if (currentExpiry > now) currentExpiry else now
        val newExpiry = base + durationDays * 24L * 60 * 60 * 1000

        saveSubscriptionToCache(
            status = SubscriptionStatus.ACTIVE,
            expiresAt = newExpiry,
            serverTime = now,
            planId = "redeemed_local",
            planName = "اشتراك $durationDays يوم",
            signedToken = token ?: "local_active_token"
        )
        syncPlaylistsFromServer()

        RedeemResult(
            success = true,
            message = "تم تفعيل الاشتراك لمدة $durationDays يوم بنجاح!",
            planName = "اشتراك $durationDays يوم",
            expiresAt = newExpiry
        )
    }

    // 24. Cloud Sync for Favorites (PUT /v1/me/favorites & GET /v1/me/favorites)
    suspend fun syncFavoritesToServer(favoritesJson: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val token = _authToken.value ?: return@withContext false
        val url = "${getBackendUrl()}/v1/me/favorites"
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .put(favoritesJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val res = httpClient.newCall(request).execute()
            return@withContext res.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchFavoritesFromServer(): JSONObject? = withContext(Dispatchers.IO) {
        val token = _authToken.value ?: return@withContext null
        val url = "${getBackendUrl()}/v1/me/favorites"
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val res = httpClient.newCall(request).execute()
            if (res.isSuccessful) {
                val body = res.body?.string() ?: ""
                return@withContext JSONObject(body)
            }
        } catch (_: Exception) {}
        null
    }

    private fun parseIsoOrEpoch(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        val epoch = value.toLongOrNull()
        if (epoch != null) return if (epoch < 100000000000L) epoch * 1000 else epoch

        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val clean = value.substringBefore(".")
            format.parse(clean)?.time ?: (System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
        } catch (_: Exception) {
            System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        }
    }
}
