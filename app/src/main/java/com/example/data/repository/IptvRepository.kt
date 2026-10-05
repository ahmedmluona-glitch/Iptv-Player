package com.example.data.repository

import com.example.data.model.AccountSession
import com.example.data.model.AccountType
import com.example.data.model.ChannelEpg
import com.example.data.model.EpgProgram
import com.example.data.model.LiveCategory
import com.example.data.model.LiveChannel
import com.example.data.model.SeriesCategory
import com.example.data.model.SeriesDetail
import com.example.data.model.SeriesEpisode
import com.example.data.model.SeriesItem
import com.example.data.model.SeriesSeason
import com.example.data.model.VodCategory
import com.example.data.model.VodDetail
import com.example.data.model.VodMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class IptvResult<out T> {
    data class Success<out T>(val data: T) : IptvResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : IptvResult<Nothing>()
}

class IptvRepository(
    private val cacheDir: java.io.File? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    companion object {
        const val APP_USER_AGENT = "MluonaIPTV/1.0 (Android TV; Mobile)"
        const val VLC_USER_AGENT = "VLC/3.0.18 LibVLC/3.0.18"

        fun stableId(url: String): Int {
            return try {
                val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray(Charsets.UTF_8))
                digest.take(4).fold(0) { a, b -> (a shl 8) or (b.toInt() and 0xFF) } and 0x7FFFFFFF
            } catch (_: Exception) {
                (url.hashCode() and 0x7FFFFFFF)
            }
        }
    }

    fun resolveUserAgent(session: AccountSession? = null, fallbackUa: String? = null): String {
        val ua = session?.userAgent?.takeIf { it.isNotBlank() } ?: fallbackUa?.takeIf { it.isNotBlank() }
        return when (ua?.lowercase(Locale.ROOT)) {
            "vlc" -> VLC_USER_AGENT
            "exoplayer" -> "MluonaIPTV/1.0 (Android TV; ExoPlayer)"
            null, "", "default" -> APP_USER_AGENT
            else -> ua
        }
    }

    private fun getCacheFile(accountId: String, actionKey: String): java.io.File? {
        val dir = cacheDir ?: return null
        val safeKey = actionKey.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return java.io.File(dir, "${accountId}_${safeKey}.json")
    }

    private fun readDiskCache(accountId: String, actionKey: String, maxAgeHours: Long = 6): String? {
        val file = getCacheFile(accountId, actionKey) ?: return null
        if (!file.exists()) return null
        val ageMs = System.currentTimeMillis() - file.lastModified()
        if (ageMs > maxAgeHours * 3600 * 1000L) {
            return null
        }
        return try {
            file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeDiskCache(accountId: String, actionKey: String, content: String) {
        val file = getCacheFile(accountId, actionKey) ?: return
        try {
            file.writeText(content, Charsets.UTF_8)
        } catch (_: Exception) {
            // Ignore cache write error
        }
    }

    private fun JSONObject.optCleanString(name: String, fallback: String? = null): String? {
        if (!has(name) || isNull(name)) return fallback
        val v = optString(name, "")
        return if (v.isBlank() || v.equals("null", ignoreCase = true)) fallback else v
    }

    private fun cleanServerUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    private fun buildXtreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        action: String? = null,
        extraParams: Map<String, String> = emptyMap()
    ): HttpUrl? {
        val base = cleanServerUrl(serverUrl)
        val parsed = base.toHttpUrlOrNull() ?: ("http://" + base.removePrefix("http://").removePrefix("https://")).toHttpUrlOrNull()
            ?: return null

        val builder = parsed.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)

        if (!action.isNullOrBlank()) {
            builder.addQueryParameter("action", action)
        }
        for ((k, v) in extraParams) {
            builder.addQueryParameter(k, v)
        }
        return builder.build()
    }

    private fun formatTimestamp(timestamp: String?): String {
        if (timestamp.isNullOrBlank() || timestamp == "null") return "Unlimited"
        return try {
            val millis = timestamp.toLong() * 1000L
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            sdf.format(Date(millis))
        } catch (_: Exception) {
            timestamp
        }
    }

    /**
     * Authenticate with Xtream Codes API
     */
    suspend fun authenticateXtream(
        rawServerUrl: String,
        username: String,
        password: String,
        accountName: String? = null,
        customUserAgent: String? = null
    ): IptvResult<AccountSession> = withContext(Dispatchers.IO) {
        val serverUrl = cleanServerUrl(rawServerUrl)
        if (username.isBlank() || password.isBlank()) {
            return@withContext IptvResult.Error("يرجى إدخال اسم المستخدم وكلمة المرور")
        }

        val requestUrl = buildXtreamUrl(serverUrl, username, password)
            ?: return@withContext IptvResult.Error("رابط الخادم غير صالح، تحقق من كتابة الرابط بشكل صحيح")
        val request = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", resolveUserAgent(fallbackUa = customUserAgent))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext IptvResult.Error("خطأ في الخادم: رمز الاستجابة ${response.code}")
                }

                val bodyString = response.body?.string() ?: ""
                if (bodyString.isBlank()) {
                    return@withContext IptvResult.Error("رد فارغ من خادم IPTV")
                }

                val rootObj = JSONObject(bodyString)
                val userInfo = rootObj.optJSONObject("user_info")
                if (userInfo == null) {
                    return@withContext IptvResult.Error("بيانات الاعتماد غير صالحة أو تعذر قراءة معلومات المستخدم")
                }

                val authStatus = userInfo.optInt("auth", -1)
                val status = userInfo.optString("status", "Unknown")

                if (authStatus == 0 || status.equals("Disabled", ignoreCase = true) || status.equals("Banned", ignoreCase = true)) {
                    val msg = if (authStatus == 0) "اسم المستخدم أو كلمة المرور غير صحيحة" else "الحساب غير نشط ($status)"
                    return@withContext IptvResult.Error(msg)
                }

                val rawExpDate = userInfo.optString("exp_date", "")
                val expired = status.equals("Expired", true) ||
                    rawExpDate.toLongOrNull()?.let { it * 1000 < System.currentTimeMillis() } == true
                if (expired) return@withContext IptvResult.Error("انتهى اشتراك هذا الحساب، يرجى التجديد")

                val expDate = formatTimestamp(rawExpDate)
                val maxConnections = userInfo.optString("max_connections", "1")

                val name = if (!accountName.isNullOrBlank()) {
                    accountName
                } else {
                    try {
                        java.net.URI(serverUrl).host ?: "Xtream IPTV"
                    } catch (_: Exception) {
                        "Xtream IPTV"
                    }
                }

                val session = AccountSession(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    type = AccountType.XTREAM,
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    status = status,
                    expDate = expDate,
                    maxConnections = maxConnections,
                    lastActiveAt = System.currentTimeMillis(),
                    userAgent = customUserAgent ?: ""
                )

                IptvResult.Success(session)
            }
        } catch (e: UnknownHostException) {
            IptvResult.Error("تعذر العثور على عنوان الخادم، تحقق من الرابط والاتصال بالإنترنت", e)
        } catch (e: SocketTimeoutException) {
            IptvResult.Error("انتهت مهلة الاتصال بالخادم، يرجى المحاولة لاحقاً", e)
        } catch (e: ConnectException) {
            IptvResult.Error("فشل الاتصال بالخادم، تأكد من صحة المنفذ والرابط", e)
        } catch (e: Exception) {
            IptvResult.Error("حدث خطأ أثناء تسجيل الدخول: ${e.localizedMessage ?: e.message}", e)
        }
    }

    /**
     * Fetch Live Categories from Xtream server with disk caching
     */
    suspend fun getLiveCategories(
        session: AccountSession,
        forceRefresh: Boolean = false
    ): IptvResult<List<LiveCategory>> = withContext(Dispatchers.IO) {
        val url = buildXtreamUrl(session.serverUrl, session.username, session.password, "get_live_categories")
            ?: return@withContext IptvResult.Error("رابط الخادم غير صالح")
        fetchCategories(url, session.id, "get_live_categories", forceRefresh, resolveUserAgent(session))
    }

    /**
     * Fetch Live Streams from Xtream server with disk caching
     */
    suspend fun getLiveStreams(
        session: AccountSession,
        categoryId: String? = null,
        forceRefresh: Boolean = false
    ): IptvResult<List<LiveChannel>> = withContext(Dispatchers.IO) {
        val actionKey = if (!categoryId.isNullOrBlank() && categoryId != "all") "get_live_streams_$categoryId" else "get_live_streams"
        if (!forceRefresh) {
            readDiskCache(session.id, actionKey)?.let { cachedJson ->
                try {
                    val channels = parseChannelsJson(cachedJson)
                    if (channels.isNotEmpty()) return@withContext IptvResult.Success(channels)
                } catch (_: Exception) {}
            }
        }

        val params = if (!categoryId.isNullOrBlank() && categoryId != "all") mapOf("category_id" to categoryId) else emptyMap()
        val url = buildXtreamUrl(session.serverUrl, session.username, session.password, "get_live_streams", params)
            ?: return@withContext IptvResult.Error("رابط الخادم غير صالح")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", resolveUserAgent(session))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    readDiskCache(session.id, actionKey, 24 * 7)?.let { cachedJson ->
                        try {
                            val channels = parseChannelsJson(cachedJson)
                            if (channels.isNotEmpty()) return@withContext IptvResult.Success(channels)
                        } catch (_: Exception) {}
                    }
                    return@withContext IptvResult.Error("خطأ في جلب القنوات (${response.code})")
                }
                val body = response.body?.string() ?: "[]"
                if (body.isNotBlank()) {
                    writeDiskCache(session.id, actionKey, body)
                }
                val channels = parseChannelsJson(body)
                IptvResult.Success(channels)
            }
        } catch (e: Exception) {
            readDiskCache(session.id, actionKey, 24 * 7)?.let { cachedJson ->
                try {
                    val channels = parseChannelsJson(cachedJson)
                    if (channels.isNotEmpty()) return@withContext IptvResult.Success(channels)
                } catch (_: Exception) {}
            }
            IptvResult.Error("فشل تحميل قنوات البث المباشر: ${e.localizedMessage}", e)
        }
    }

    /**
     * Fetch VOD (Movies) Categories from Xtream server with disk caching
     */
    suspend fun getVodCategories(
        session: AccountSession,
        forceRefresh: Boolean = false
    ): IptvResult<List<VodCategory>> = withContext(Dispatchers.IO) {
        val url = buildXtreamUrl(session.serverUrl, session.username, session.password, "get_vod_categories")
            ?: return@withContext IptvResult.Error("رابط الخادم غير صالح")
        val res = fetchCategories(url, session.id, "get_vod_categories", forceRefresh, resolveUserAgent(session))
        when (res) {
            is IptvResult.Success -> IptvResult.Success(res.data.map { VodCategory(it.categoryId, it.categoryName) })
            is IptvResult.Error -> IptvResult.Error(res.message, res.cause)
        }
    }

    /**
     * Fetch VOD (Movies) list from Xtream server with disk caching
     */
    suspend fun getVodStreams(
        session: AccountSession,
        categoryId: String? = null,
        forceRefresh: Boolean = false
    ): IptvResult<List<VodMovie>> = withContext(Dispatchers.IO) {
        val actionKey = if (!categoryId.isNullOrBlank() && categoryId != "all") "get_vod_streams_$categoryId" else "get_vod_streams"
        if (!forceRefresh) {
            readDiskCache(session.id, actionKey)?.let { cachedJson ->
                try {
                    val movies = parseMoviesJson(cachedJson)
                    if (movies.isNotEmpty()) return@withContext IptvResult.Success(movies)
                } catch (_: Exception) {}
            }
        }

        val params = if (!categoryId.isNullOrBlank() && categoryId != "all") mapOf("category_id" to categoryId) else emptyMap()
        val url = buildXtreamUrl(session.serverUrl, session.username, session.password, "get_vod_streams", params)
            ?: return@withContext IptvResult.Error("رابط الخادم غير صالح")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", resolveUserAgent(session))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    readDiskCache(session.id, actionKey, 24 * 7)?.let { cachedJson ->
                        try {
                            val movies = parseMoviesJson(cachedJson)
                            if (movies.isNotEmpty()) return@withContext IptvResult.Success(movies)
                        } catch (_: Exception) {}
                    }
                    return@withContext IptvResult.Error("خطأ في جلب الأفلام (${response.code})")
                }
                val body = response.body?.string() ?: "[]"
                if (body.isNotBlank()) {
                    writeDiskCache(session.id, actionKey, body)
                }
                val movies = parseMoviesJson(body)
                IptvResult.Success(movies)
            }
        } catch (e: Exception) {
            readDiskCache(session.id, actionKey, 24 * 7)?.let { cachedJson ->
                try {
                    val movies = parseMoviesJson(cachedJson)
                    if (movies.isNotEmpty()) return@withContext IptvResult.Success(movies)
                } catch (_: Exception) {}
            }
            IptvResult.Error("فشل تحميل الأفلام: ${e.localizedMessage}", e)
        }
    }

    /**
     * Fetch Series Categories from Xtream server with disk caching
     */
    suspend fun getSeriesCategories(
        session: AccountSession,
        forceRefresh: Boolean = false
    ): IptvResult<List<SeriesCategory>> = withContext(Dispatchers.IO) {
        val url = buildXtreamUrl(session.serverUrl, session.username, session.password, "get_series_categories")
            ?: return@withContext IptvResult.Error("رابط الخادم غير صالح")
        val res = fetchCategories(url, session.id, "get_series_categories", forceRefresh, resolveUserAgent(session))
        when (res) {
            is IptvResult.Success -> IptvResult.Success(res.data.map { SeriesCategory(it.categoryId, it.categoryName) })
            is IptvResult.Error -> IptvResult.Error(res.message, res.cause)
        }
    }

    /**
     * Fetch Series list from Xtream server with disk caching
     */
    suspend fun getSeries(
        session: AccountSession,
        categoryId: String? = null,
        forceRefresh: Boolean = false
    ): IptvResult<List<SeriesItem>> = withContext(Dispatchers.IO) {
        val actionKey = if (!categoryId.isNullOrBlank() && categoryId != "all") "get_series_$categoryId" else "get_series"
        if (!forceRefresh) {
            readDiskCache(session.id, actionKey)?.let { cachedJson ->
                try {
                    val seriesList = parseSeriesJson(cachedJson)
                    if (seriesList.isNotEmpty()) return@withContext IptvResult.Success(seriesList)
                } catch (_: Exception) {}
            }
        }

        val params = if (!categoryId.isNullOrBlank() && categoryId != "all") mapOf("category_id" to categoryId) else emptyMap()
        val url = buildXtreamUrl(session.serverUrl, session.username, session.password, "get_series", params)
            ?: return@withContext IptvResult.Error("رابط الخادم غير صالح")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", resolveUserAgent(session))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    readDiskCache(session.id, actionKey, 24 * 7)?.let { cachedJson ->
                        try {
                            val seriesList = parseSeriesJson(cachedJson)
                            if (seriesList.isNotEmpty()) return@withContext IptvResult.Success(seriesList)
                        } catch (_: Exception) {}
                    }
                    return@withContext IptvResult.Error("خطأ في جلب المسلسلات (${response.code})")
                }
                val body = response.body?.string() ?: "[]"
                if (body.isNotBlank()) {
                    writeDiskCache(session.id, actionKey, body)
                }
                val seriesList = parseSeriesJson(body)
                IptvResult.Success(seriesList)
            }
        } catch (e: Exception) {
            readDiskCache(session.id, actionKey, 24 * 7)?.let { cachedJson ->
                try {
                    val seriesList = parseSeriesJson(cachedJson)
                    if (seriesList.isNotEmpty()) return@withContext IptvResult.Success(seriesList)
                } catch (_: Exception) {}
            }
            IptvResult.Error("فشل تحميل المسلسلات: ${e.localizedMessage}", e)
        }
    }

    /**
     * Fetch Series Info (Seasons, Episodes, Plot, Cast) from Xtream server
     */
    suspend fun getSeriesInfo(
        session: AccountSession,
        seriesId: Int
    ): IptvResult<SeriesDetail> = withContext(Dispatchers.IO) {
        val url = buildXtreamUrl(session.serverUrl, session.username, session.password, "get_series_info", mapOf("series_id" to seriesId.toString()))
            ?: return@withContext IptvResult.Error("رابط الخادم غير صالح")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", resolveUserAgent(session))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext IptvResult.Error("خطأ في جلب تفاصيل المسلسل (${response.code})")
                }
                val body = response.body?.string() ?: "{}"
                val root = JSONObject(body)
                val info = root.optJSONObject("info") ?: JSONObject()
                val episodesObj = root.optJSONObject("episodes") ?: JSONObject()

                val seasonsList = mutableListOf<SeriesSeason>()
                val keys = episodesObj.keys()
                while (keys.hasNext()) {
                    val sKey = keys.next()
                    val sNum = sKey.toIntOrNull() ?: 1
                    val epArray = episodesObj.optJSONArray(sKey) ?: JSONArray()
                    val episodes = mutableListOf<SeriesEpisode>()
                    for (j in 0 until epArray.length()) {
                        val epObj = epArray.optJSONObject(j) ?: continue
                        val epId = epObj.optCleanString("id", "${seriesId}_${sNum}_$j") ?: "${seriesId}_${sNum}_$j"
                        val epNum = epObj.optInt("episode_num", j + 1)
                        val title = epObj.optCleanString("title", "Episode $epNum") ?: "Episode $epNum"
                        val ext = epObj.optCleanString("container_extension", "mp4") ?: "mp4"
                        val plot = epObj.optJSONObject("info")?.optCleanString("plot")
                        episodes.add(
                            SeriesEpisode(
                                id = epId,
                                episodeNum = epNum,
                                title = title,
                                containerExtension = ext,
                                info = plot,
                                season = sNum
                            )
                        )
                    }
                    seasonsList.add(
                        SeriesSeason(
                            seasonNumber = sNum,
                            name = "Season $sNum",
                            episodeCount = episodes.size,
                            episodes = episodes.sortedBy { it.episodeNum }
                        )
                    )
                }

                if (seasonsList.isEmpty()) {
                    val seasonsArr = root.optJSONArray("seasons")
                    if (seasonsArr != null) {
                        for (k in 0 until seasonsArr.length()) {
                            val sObj = seasonsArr.optJSONObject(k) ?: continue
                            val sNum = sObj.optInt("season_number", k + 1)
                            val name = sObj.optCleanString("name", "Season $sNum") ?: "Season $sNum"
                            val epCount = sObj.optInt("episode_count", 0)
                            seasonsList.add(
                                SeriesSeason(
                                    seasonNumber = sNum,
                                    name = name,
                                    episodeCount = epCount
                                )
                            )
                        }
                    }
                }

                val detail = SeriesDetail(
                    seriesId = seriesId,
                    name = info.optCleanString("name", "Series $seriesId") ?: "Series $seriesId",
                    cover = info.optCleanString("cover"),
                    plot = info.optCleanString("plot"),
                    genre = info.optCleanString("genre"),
                    releaseDate = info.optCleanString("releaseDate"),
                    rating = info.optCleanString("rating"),
                    cast = info.optCleanString("cast"),
                    director = info.optCleanString("director"),
                    seasons = seasonsList.sortedBy { it.seasonNumber }
                )
                IptvResult.Success(detail)
            }
        } catch (e: Exception) {
            IptvResult.Error("فشل تحميل تفاصيل المسلسل: ${e.localizedMessage}", e)
        }
    }

    /**
     * Fetch VOD Info (plot, cast, duration) from Xtream server
     */
    suspend fun getVodInfo(
        session: AccountSession,
        vodId: Int
    ): IptvResult<VodDetail> = withContext(Dispatchers.IO) {
        val url = buildXtreamUrl(session.serverUrl, session.username, session.password, "get_vod_info", mapOf("vod_id" to vodId.toString()))
            ?: return@withContext IptvResult.Error("رابط الخادم غير صالح")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", resolveUserAgent(session))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext IptvResult.Error("خطأ في جلب تفاصيل الفيلم (${response.code})")
                }
                val body = response.body?.string() ?: "{}"
                val root = JSONObject(body)
                val info = root.optJSONObject("info") ?: JSONObject()
                val movieData = root.optJSONObject("movie_data") ?: JSONObject()

                val detail = VodDetail(
                    streamId = vodId,
                    name = info.optCleanString("name") ?: movieData.optCleanString("name") ?: "Movie $vodId",
                    cover = info.optCleanString("cover_big") ?: info.optCleanString("movie_image"),
                    plot = info.optCleanString("plot") ?: info.optCleanString("description"),
                    genre = info.optCleanString("genre"),
                    releaseDate = info.optCleanString("releasedate") ?: info.optCleanString("release_date"),
                    rating = info.optCleanString("rating"),
                    duration = info.optCleanString("duration") ?: info.optCleanString("duration_secs"),
                    director = info.optCleanString("director"),
                    cast = info.optCleanString("cast") ?: info.optCleanString("actors"),
                    containerExtension = movieData.optCleanString("container_extension", "mp4") ?: "mp4"
                )
                IptvResult.Success(detail)
            }
        } catch (e: Exception) {
            IptvResult.Error("فشل تحميل تفاصيل الفيلم: ${e.localizedMessage}", e)
        }
    }

    /**
     * Parse and fetch real M3U / M3U8 playlist
     */
    suspend fun loadM3uPlaylist(
        rawUrl: String,
        playlistName: String? = null,
        existingId: String? = null,
        customUserAgent: String? = null
    ): IptvResult<Pair<AccountSession, List<LiveChannel>>> = withContext(Dispatchers.IO) {
        val url = cleanServerUrl(rawUrl)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", resolveUserAgent(fallbackUa = customUserAgent))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext IptvResult.Error("تعذر تنزيل قائمة M3U (${response.code})")
                }

                val body = response.body ?: return@withContext IptvResult.Error("الملف المستلم فارغ")
                val reader = BufferedReader(InputStreamReader(body.byteStream()))

                val channels = mutableListOf<LiveChannel>()
                var currentLine: String?
                var currentName = ""
                var currentLogo: String? = null
                var currentGroup: String? = null
                var currentEpgId: String? = null
                var streamCounter = 1

                val tvgIdRegex = Regex("""tvg-id="([^"]*)"""", RegexOption.IGNORE_CASE)
                val logoRegex = Regex("""tvg-logo="([^"]+)"""", RegexOption.IGNORE_CASE)
                val groupRegex = Regex("""group-title="([^"]+)"""", RegexOption.IGNORE_CASE)

                while (reader.readLine().also { currentLine = it } != null) {
                    val line = currentLine?.trim() ?: continue
                    if (line.isEmpty()) continue

                    if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                        val commaIndex = line.lastIndexOf(',')
                        currentName = if (commaIndex != -1 && commaIndex < line.length - 1) {
                            line.substring(commaIndex + 1).trim()
                        } else {
                            "Channel $streamCounter"
                        }

                        currentEpgId = tvgIdRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                        currentLogo = logoRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        currentGroup = groupRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    } else if (!line.startsWith("#")) {
                        if (currentName.isNotEmpty()) {
                            // Compute deterministic stream ID from SHA-1 hash so favorites/history remain stable
                            val streamId = stableId(line).let { if (it <= 0) streamCounter else it }

                            // Classification heuristic for movies / VOD
                            var category = currentGroup
                            val isMovie = line.endsWith(".mp4", ignoreCase = true) ||
                                line.endsWith(".mkv", ignoreCase = true) ||
                                line.endsWith(".avi", ignoreCase = true) ||
                                (currentGroup != null && (
                                    currentGroup.contains("movie", ignoreCase = true) ||
                                    currentGroup.contains("افلام") ||
                                    currentGroup.contains("أفلام") ||
                                    currentGroup.contains("vod", ignoreCase = true) ||
                                    currentGroup.contains("cinema", ignoreCase = true)
                                ))
                            if (isMovie && category == null) {
                                category = "Movies / أفلام"
                            }

                            channels.add(
                                LiveChannel(
                                    streamId = streamId,
                                    num = streamCounter,
                                    name = currentName,
                                    streamIcon = currentLogo,
                                    categoryId = category,
                                    directSourceUrl = line,
                                    epgChannelId = currentEpgId
                                )
                            )
                            streamCounter++
                            currentName = ""
                            currentLogo = null
                            currentGroup = null
                            currentEpgId = null
                        }
                    }
                }

                if (channels.isEmpty()) {
                    return@withContext IptvResult.Error("لم يتم العثور على أي قنوات صالحة في ملف M3U")
                }

                val session = AccountSession(
                    id = existingId ?: java.util.UUID.randomUUID().toString(),
                    name = playlistName?.takeIf { it.isNotBlank() } ?: "M3U Playlist",
                    type = AccountType.M3U,
                    m3uUrl = url,
                    status = "Active",
                    expDate = "Unlimited",
                    lastActiveAt = System.currentTimeMillis(),
                    userAgent = customUserAgent ?: ""
                )

                IptvResult.Success(Pair(session, channels))
            }
        } catch (e: Exception) {
            IptvResult.Error("فشل قراءة رابط M3U: ${e.localizedMessage ?: e.message}", e)
        }
    }

    private fun parseCategoriesJson(body: String): List<LiveCategory> {
        val array = JSONArray(body)
        val categories = mutableListOf<LiveCategory>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val catId = obj.optCleanString("category_id") ?: ""
            val catName = obj.optCleanString("category_name") ?: ""
            if (catId.isNotBlank() && catName.isNotBlank()) {
                categories.add(LiveCategory(catId, catName))
            }
        }
        return categories
    }

    private fun parseChannelsJson(body: String): List<LiveChannel> {
        val array = JSONArray(body)
        val channels = mutableListOf<LiveChannel>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val streamId = obj.optInt("stream_id", -1)
            if (streamId <= 0) continue
            channels.add(
                LiveChannel(
                    streamId = streamId,
                    num = obj.optInt("num", i + 1),
                    name = obj.optCleanString("name", "Channel $streamId") ?: "Channel $streamId",
                    streamIcon = obj.optCleanString("stream_icon"),
                    categoryId = obj.optCleanString("category_id"),
                    epgChannelId = obj.optCleanString("epg_channel_id")
                )
            )
        }
        return channels
    }

    private fun parseMoviesJson(body: String): List<VodMovie> {
        val array = JSONArray(body)
        val movies = mutableListOf<VodMovie>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val streamId = obj.optInt("stream_id", -1)
            if (streamId <= 0) continue
            movies.add(
                VodMovie(
                    streamId = streamId,
                    name = obj.optCleanString("name", "Movie $streamId") ?: "Movie $streamId",
                    streamIcon = obj.optCleanString("stream_icon"),
                    rating = obj.optCleanString("rating"),
                    categoryId = obj.optCleanString("category_id"),
                    containerExtension = obj.optCleanString("container_extension", "mp4") ?: "mp4"
                )
            )
        }
        return movies
    }

    private fun parseSeriesJson(body: String): List<SeriesItem> {
        val array = JSONArray(body)
        val seriesList = mutableListOf<SeriesItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val seriesId = obj.optInt("series_id", -1)
            if (seriesId <= 0) continue
            seriesList.add(
                SeriesItem(
                    seriesId = seriesId,
                    name = obj.optCleanString("name", "Series $seriesId") ?: "Series $seriesId",
                    cover = obj.optCleanString("cover"),
                    rating = obj.optCleanString("rating"),
                    categoryId = obj.optCleanString("category_id"),
                    plot = obj.optCleanString("plot"),
                    genre = obj.optCleanString("genre"),
                    releaseDate = obj.optCleanString("releaseDate")
                )
            )
        }
        return seriesList
    }

    private fun fetchCategories(
        url: HttpUrl,
        accountId: String? = null,
        actionKey: String? = null,
        forceRefresh: Boolean = false,
        userAgent: String = APP_USER_AGENT
    ): IptvResult<List<LiveCategory>> {
        if (!forceRefresh && accountId != null && actionKey != null) {
            readDiskCache(accountId, actionKey)?.let { cachedJson ->
                try {
                    val list = parseCategoriesJson(cachedJson)
                    if (list.isNotEmpty()) return IptvResult.Success(list)
                } catch (_: Exception) {}
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (accountId != null && actionKey != null) {
                        readDiskCache(accountId, actionKey, 24 * 7)?.let { cachedJson ->
                            try {
                                val list = parseCategoriesJson(cachedJson)
                                if (list.isNotEmpty()) return IptvResult.Success(list)
                            } catch (_: Exception) {}
                        }
                    }
                    return IptvResult.Error("خطأ في جلب التصنيفات (${response.code})")
                }
                val body = response.body?.string() ?: "[]"
                if (accountId != null && actionKey != null && body.isNotBlank()) {
                    writeDiskCache(accountId, actionKey, body)
                }
                val categories = parseCategoriesJson(body)
                IptvResult.Success(categories)
            }
        } catch (e: Exception) {
            if (accountId != null && actionKey != null) {
                readDiskCache(accountId, actionKey, 24 * 7)?.let { cachedJson ->
                    try {
                        val list = parseCategoriesJson(cachedJson)
                        if (list.isNotEmpty()) return IptvResult.Success(list)
                    } catch (_: Exception) {}
                }
            }
            IptvResult.Error("فشل تحميل التصنيفات: ${e.localizedMessage}", e)
        }
    }

    private fun decodeEpgText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val base64Regex = Regex("^[A-Za-z0-9+/=]+$")
        if (trimmed.length % 4 == 0 && base64Regex.matches(trimmed) && trimmed.length >= 4) {
            try {
                val decoded = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
                val decodedStr = String(decoded, Charsets.UTF_8).trim()
                if (decodedStr.isNotBlank() && decodedStr.any { it.isLetter() }) {
                    return decodedStr
                }
            } catch (_: Exception) {}
        }
        return trimmed
    }

    private fun parseTimestamp(tsObj: Any?, dateStr: String?): Long {
        if (tsObj is Number) return tsObj.toLong()
        if (tsObj is String) {
            val parsed = tsObj.toLongOrNull()
            if (parsed != null && parsed > 0) return parsed
        }
        if (!dateStr.isNullOrBlank()) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val d = sdf.parse(dateStr)
                if (d != null) return d.time / 1000L
            } catch (_: Exception) {}
        }
        return 0L
    }

    /**
     * Fetch Short EPG for a specific channel from Xtream server
     */
    suspend fun getChannelEpg(
        session: AccountSession,
        streamId: Int,
        limit: Int = 10
    ): IptvResult<ChannelEpg> = withContext(Dispatchers.IO) {
        if (session.type == AccountType.M3U) {
            return@withContext IptvResult.Success(ChannelEpg(streamId = streamId, listings = emptyList()))
        }

        val url = buildXtreamUrl(
            session.serverUrl,
            session.username,
            session.password,
            "get_short_epg",
            mapOf("stream_id" to streamId.toString(), "limit" to limit.toString())
        ) ?: return@withContext IptvResult.Error("رابط الخادم غير صالح")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", resolveUserAgent(session))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext IptvResult.Error("خطأ في جلب EPG (${response.code})")
                }

                val body = response.body?.string() ?: ""
                if (body.isBlank() || body == "[]" || body == "null") {
                    return@withContext IptvResult.Success(ChannelEpg(streamId = streamId, listings = emptyList()))
                }

                val rawArray: JSONArray? = try {
                    if (body.trim().startsWith("{")) {
                        val root = JSONObject(body)
                        root.optJSONArray("epg_listings")
                    } else if (body.trim().startsWith("[")) {
                        JSONArray(body)
                    } else null
                } catch (_: Exception) {
                    null
                }

                if (rawArray == null || rawArray.length() == 0) {
                    return@withContext IptvResult.Success(ChannelEpg(streamId = streamId, listings = emptyList()))
                }

                val nowSec = System.currentTimeMillis() / 1000L
                val programs = mutableListOf<EpgProgram>()

                for (i in 0 until rawArray.length()) {
                    val obj = rawArray.optJSONObject(i) ?: continue
                    val rawTitle = obj.optString("title", "")
                    val decodedTitle = decodeEpgText(rawTitle)
                    if (decodedTitle.isBlank()) continue

                    val rawDesc = obj.optString("description", "")
                    val decodedDesc = decodeEpgText(rawDesc)

                    val startStr = obj.optString("start", "")
                    val endStr = obj.optString("end", "")
                    val startTs = parseTimestamp(obj.opt("start_timestamp"), startStr)
                    val stopTs = parseTimestamp(obj.opt("stop_timestamp"), endStr)
                    val nowPlayingFlag = obj.optInt("now_playing", 0)

                    val isNowPlaying = (nowPlayingFlag == 1) ||
                            (startTs in 1..nowSec && nowSec < stopTs)

                    programs.add(
                        EpgProgram(
                            id = obj.optString("id", null as String?),
                            epgId = obj.optString("epg_id", null as String?),
                            title = decodedTitle,
                            description = decodedDesc.takeIf { it.isNotBlank() },
                            start = startStr.takeIf { it.isNotBlank() },
                            end = endStr.takeIf { it.isNotBlank() },
                            startTimestamp = startTs,
                            stopTimestamp = stopTs,
                            nowPlaying = isNowPlaying
                        )
                    )
                }

                var currentProgram = programs.firstOrNull { it.nowPlaying }
                if (currentProgram == null) {
                    currentProgram = programs.firstOrNull {
                        it.startTimestamp in 1..nowSec && nowSec < it.stopTimestamp
                    }
                }
                if (currentProgram == null && programs.isNotEmpty()) {
                    currentProgram = programs.first()
                }

                val currentIndex = if (currentProgram != null) programs.indexOf(currentProgram) else -1
                val upcomingProgram = if (currentIndex in 0 until programs.size - 1) {
                    programs[currentIndex + 1]
                } else if (programs.size > 1) {
                    programs[1]
                } else null

                IptvResult.Success(
                    ChannelEpg(
                        streamId = streamId,
                        currentProgram = currentProgram,
                        upcomingProgram = upcomingProgram,
                        listings = programs
                    )
                )
            }
        } catch (e: Exception) {
            IptvResult.Error("فشل تحميل دليل البرامج: ${e.localizedMessage}", e)
        }
    }
}
