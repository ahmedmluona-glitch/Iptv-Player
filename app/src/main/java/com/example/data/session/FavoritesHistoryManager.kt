package com.example.data.session

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.LiveChannel
import com.example.data.model.SeriesItem
import com.example.data.model.VodMovie
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class UserProfile(
    val id: String,
    val name: String,
    val isKids: Boolean = false,
    val pinProtected: Boolean = false
)

class FavoritesHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mluona_iptv_favorites_history", Context.MODE_PRIVATE)

    private var activeAccountId: String = "default"
    private var activeProfileId: String = "default"

    // Session unlock state for adult categories in memory
    var isAdultSessionUnlocked: Boolean = false

    companion object {
        private const val MAX_RECENT_ITEMS = 40
        private const val KEY_PIN_HASH = "key_parental_pin_hash"
        private const val KEY_PARENTAL_ENABLED = "key_parental_enabled"
        private const val KEY_ADULT_KEYWORDS = "key_adult_keywords"
        private const val KEY_PROFILES = "key_user_profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "key_active_profile_id"

        val DEFAULT_ADULT_KEYWORDS = listOf(
            "adult", "xxx", "18+", "+18", "للكبار", "porn", "erotic", "sex", "mature", "for adult", "adults"
        )
    }

    private fun storageKeySuffix(): String {
        return if (activeProfileId == "default") activeAccountId else "${activeAccountId}_$activeProfileId"
    }

    private fun favChannelsKey() = "key_fav_channels_${storageKeySuffix()}"
    private fun recentChannelsKey() = "key_recent_channels_${storageKeySuffix()}"
    private fun favMoviesKey() = "key_fav_movies_${storageKeySuffix()}"
    private fun recentMoviesKey() = "key_recent_movies_${storageKeySuffix()}"
    private fun favSeriesKey() = "key_fav_series_${storageKeySuffix()}"
    private fun recentSeriesKey() = "key_recent_series_${storageKeySuffix()}"
    private fun customChannelNameKey(streamId: Int) = "custom_ch_name_${storageKeySuffix()}_$streamId"

    // In-memory high-speed cache for 0ms latency during fast TV remote navigation
    private val favChannelsCache = mutableListOf<LiveChannel>()
    private val favChannelIds = hashSetOf<Int>()
    private val recentChannelsCache = mutableListOf<LiveChannel>()

    private val favMoviesCache = mutableListOf<VodMovie>()
    private val favMovieIds = hashSetOf<Int>()
    private val recentMoviesCache = mutableListOf<VodMovie>()

    private val favSeriesCache = mutableListOf<SeriesItem>()
    private val favSeriesIds = hashSetOf<Int>()
    private val recentSeriesCache = mutableListOf<SeriesItem>()

    init {
        migrateLegacyKeysIfNeeded()
        activeProfileId = prefs.getString(KEY_ACTIVE_PROFILE_ID, "default") ?: "default"
        reloadCaches()
    }

    private fun migrateLegacyKeysIfNeeded() {
        val legacyKeys = listOf(
            "key_fav_channels" to "key_fav_channels_default",
            "key_recent_channels" to "key_recent_channels_default",
            "key_fav_movies" to "key_fav_movies_default",
            "key_recent_movies" to "key_recent_movies_default",
            "key_fav_series" to "key_fav_series_default",
            "key_recent_series" to "key_recent_series_default"
        )
        val editor = prefs.edit()
        var hasChanges = false
        for ((oldKey, targetKey) in legacyKeys) {
            if (prefs.contains(oldKey)) {
                if (!prefs.contains(targetKey)) {
                    val data = prefs.getString(oldKey, null)
                    if (data != null) {
                        editor.putString(targetKey, data)
                    }
                }
                editor.remove(oldKey)
                hasChanges = true
            }
        }
        if (hasChanges) {
            editor.apply()
        }
    }

    @Synchronized
    fun clearAccountData(accountId: String) {
        val editor = prefs.edit()
        editor.remove("key_fav_channels_$accountId")
        editor.remove("key_recent_channels_$accountId")
        editor.remove("key_fav_movies_$accountId")
        editor.remove("key_recent_movies_$accountId")
        editor.remove("key_fav_series_$accountId")
        editor.remove("key_recent_series_$accountId")
        val prefix = "custom_ch_name_${accountId}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
        if (activeAccountId == accountId) {
            reloadCaches()
        }
    }

    @Synchronized
    fun setCurrentAccount(accountId: String?, profileId: String? = null) {
        val newId = if (accountId.isNullOrBlank()) "default" else accountId
        val newProf = if (profileId.isNullOrBlank()) activeProfileId else profileId
        if (activeAccountId != newId || activeProfileId != newProf) {
            activeAccountId = newId
            activeProfileId = newProf
            prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, newProf).apply()
            reloadCaches()
        }
    }

    // ==========================================
    // 25. MULTI-PROFILE SUPPORT
    // ==========================================
    @Synchronized
    fun getProfiles(): List<UserProfile> {
        val json = prefs.getString(KEY_PROFILES, null)
        if (json.isNullOrBlank()) {
            val defaults = listOf(
                UserProfile("default", "الرئيسي (Default)", isKids = false, pinProtected = false),
                UserProfile("kids", "الأطفال (Kids)", isKids = true, pinProtected = false),
                UserProfile("adult", "خاص للكبار (Private)", isKids = false, pinProtected = true)
            )
            saveProfiles(defaults)
            return defaults
        }
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<UserProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    UserProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        isKids = obj.optBoolean("isKids", false),
                        pinProtected = obj.optBoolean("pinProtected", false)
                    )
                )
            }
            if (list.isEmpty()) getProfiles() else list
        } catch (_: Exception) {
            listOf(UserProfile("default", "الرئيسي", false, false))
        }
    }

    @Synchronized
    fun getActiveProfile(): UserProfile {
        return getProfiles().find { it.id == activeProfileId } ?: getProfiles().first()
    }

    @Synchronized
    fun setActiveProfile(profileId: String) {
        setCurrentAccount(activeAccountId, profileId)
    }

    @Synchronized
    fun createProfile(name: String, isKids: Boolean, pinProtected: Boolean): UserProfile {
        val list = getProfiles().toMutableList()
        val newProfile = UserProfile(
            id = "prof_${System.currentTimeMillis() % 10000}",
            name = name,
            isKids = isKids,
            pinProtected = pinProtected
        )
        list.add(newProfile)
        saveProfiles(list)
        return newProfile
    }

    private fun saveProfiles(list: List<UserProfile>) {
        val array = JSONArray()
        for (p in list) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("isKids", p.isKids)
                put("pinProtected", p.pinProtected)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    // ==========================================
    // 22. PARENTAL CONTROL & HASHED PIN
    // ==========================================
    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("mluona_parental_salt_$pin".toByteArray(Charsets.UTF_8))
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun hasParentalPin(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    fun setParentalPin(pin: String) {
        val hashed = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hashed).putBoolean(KEY_PARENTAL_ENABLED, true).apply()
    }

    fun verifyParentalPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: hashPin("0000") // default 0000 if not set
        return hashPin(pin) == savedHash
    }

    fun isParentalControlEnabled(): Boolean {
        return prefs.getBoolean(KEY_PARENTAL_ENABLED, true)
    }

    fun setParentalControlEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PARENTAL_ENABLED, enabled).apply()
    }

    fun getAdultKeywords(): List<String> {
        val raw = prefs.getString(KEY_ADULT_KEYWORDS, null)
        if (raw.isNullOrBlank()) return DEFAULT_ADULT_KEYWORDS
        return raw.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }

    fun setAdultKeywords(keywords: List<String>) {
        prefs.edit().putString(KEY_ADULT_KEYWORDS, keywords.joinToString(",")).apply()
    }

    fun isAdultCategory(categoryName: String?): Boolean {
        if (!isParentalControlEnabled()) return false
        val cat = (categoryName ?: "").lowercase()
        return getAdultKeywords().any { keyword -> cat.contains(keyword) }
    }

    // ==========================================
    // 24. CLOUD SYNC EXPORT & IMPORT
    // ==========================================
    @Synchronized
    fun exportFavoritesJson(): JSONObject {
        val obj = JSONObject()
        val channelsArray = JSONArray()
        for (c in favChannelsCache) {
            channelsArray.put(JSONObject().apply {
                put("streamId", c.streamId)
                put("name", c.name)
                put("streamType", "live")
                put("streamIcon", c.streamIcon ?: "")
                put("epgChannelId", c.epgChannelId ?: "")
                put("categoryId", c.categoryId ?: "")
            })
        }
        val moviesArray = JSONArray()
        for (m in favMoviesCache) {
            moviesArray.put(JSONObject().apply {
                put("streamId", m.streamId)
                put("name", m.name)
                put("streamIcon", m.streamIcon ?: "")
                put("rating", m.rating ?: "")
                put("categoryId", m.categoryId ?: "")
                put("containerExtension", m.containerExtension)
            })
        }
        val seriesArray = JSONArray()
        for (s in favSeriesCache) {
            seriesArray.put(JSONObject().apply {
                put("seriesId", s.seriesId)
                put("name", s.name)
                put("cover", s.cover ?: "")
                put("rating", s.rating ?: "")
                put("categoryId", s.categoryId ?: "")
            })
        }
        obj.put("favChannels", channelsArray)
        obj.put("favMovies", moviesArray)
        obj.put("favSeries", seriesArray)
        return obj
    }

    @Synchronized
    fun importFavoritesJson(json: JSONObject) {
        val channels = json.optJSONArray("favChannels")
        if (channels != null) {
            for (i in 0 until channels.length()) {
                val item = channels.getJSONObject(i)
                val ch = LiveChannel(
                    num = 0,
                    name = item.optString("name"),
                    streamId = item.optInt("streamId"),
                    streamIcon = item.optString("streamIcon", null as String?),
                    epgChannelId = item.optString("epgChannelId", null as String?),
                    categoryId = item.optString("categoryId", null as String?)
                )
                if (!favChannelIds.contains(ch.streamId)) {
                    favChannelsCache.add(ch)
                    favChannelIds.add(ch.streamId)
                }
            }
            saveChannels(favChannelsKey(), favChannelsCache)
        }
    }

    @Synchronized
    private fun reloadCaches() {
        favChannelsCache.clear()
        favChannelIds.clear()
        recentChannelsCache.clear()
        favMoviesCache.clear()
        favMovieIds.clear()
        recentMoviesCache.clear()
        favSeriesCache.clear()
        favSeriesIds.clear()
        recentSeriesCache.clear()

        favChannelsCache.addAll(loadChannels(favChannelsKey()))
        favChannelsCache.forEach { favChannelIds.add(it.streamId) }
        recentChannelsCache.addAll(loadChannels(recentChannelsKey()))

        favMoviesCache.addAll(loadMovies(favMoviesKey()))
        favMoviesCache.forEach { favMovieIds.add(it.streamId) }
        recentMoviesCache.addAll(loadMovies(recentMoviesKey()))

        favSeriesCache.addAll(loadSeries(favSeriesKey()))
        favSeriesCache.forEach { favSeriesIds.add(it.seriesId) }
        recentSeriesCache.addAll(loadSeries(recentSeriesKey()))
    }

    // ==========================================
    // LIVE CHANNELS
    // ==========================================
    @Synchronized
    fun getFavoriteChannels(): List<LiveChannel> {
        return favChannelsCache.toList()
    }

    fun isChannelFavorite(streamId: Int): Boolean {
        return favChannelIds.contains(streamId)
    }

    @Synchronized
    fun toggleChannelFavorite(channel: LiveChannel): Boolean {
        val index = favChannelsCache.indexOfFirst { it.streamId == channel.streamId }
        val isNowFav: Boolean
        if (index >= 0) {
            favChannelsCache.removeAt(index)
            favChannelIds.remove(channel.streamId)
            isNowFav = false
        } else {
            favChannelsCache.add(0, channel)
            favChannelIds.add(channel.streamId)
            isNowFav = true
        }
        saveChannels(favChannelsKey(), favChannelsCache)
        return isNowFav
    }

    @Synchronized
    fun getRecentChannels(): List<LiveChannel> {
        return recentChannelsCache.toList()
    }

    fun getCustomChannelName(streamId: Int): String? {
        return prefs.getString(customChannelNameKey(streamId), null)
    }

    fun setCustomChannelName(streamId: Int, newName: String) {
        prefs.edit().putString(customChannelNameKey(streamId), newName).apply()
    }

    @Synchronized
    fun addChannelToRecent(channel: LiveChannel) {
        recentChannelsCache.removeAll { it.streamId == channel.streamId }
        recentChannelsCache.add(0, channel)
        if (recentChannelsCache.size > MAX_RECENT_ITEMS) {
            val trimmed = recentChannelsCache.take(MAX_RECENT_ITEMS).toMutableList()
            recentChannelsCache.clear()
            recentChannelsCache.addAll(trimmed)
        }
        saveChannels(recentChannelsKey(), recentChannelsCache)
    }

    private fun loadChannels(key: String): List<LiveChannel> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<LiveChannel>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    LiveChannel(
                        streamId = obj.getInt("streamId"),
                        num = if (obj.has("num")) obj.getInt("num") else null,
                        name = obj.getString("name"),
                        streamIcon = obj.optString("streamIcon", null as String?),
                        categoryId = obj.optString("categoryId", null as String?),
                        directSourceUrl = obj.optString("directSourceUrl", null as String?),
                        epgChannelId = obj.optString("epgChannelId", null as String?)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveChannels(key: String, list: List<LiveChannel>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("streamId", item.streamId)
                if (item.num != null) put("num", item.num)
                put("name", item.name)
                put("streamIcon", item.streamIcon ?: "")
                put("categoryId", item.categoryId ?: "")
                put("directSourceUrl", item.directSourceUrl ?: "")
                put("epgChannelId", item.epgChannelId ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    // ==========================================
    // MOVIES (VOD)
    // ==========================================
    @Synchronized
    fun getFavoriteMovies(): List<VodMovie> {
        return favMoviesCache.toList()
    }

    fun isMovieFavorite(streamId: Int): Boolean {
        return favMovieIds.contains(streamId)
    }

    @Synchronized
    fun toggleMovieFavorite(movie: VodMovie): Boolean {
        val index = favMoviesCache.indexOfFirst { it.streamId == movie.streamId }
        val isNowFav: Boolean
        if (index >= 0) {
            favMoviesCache.removeAt(index)
            favMovieIds.remove(movie.streamId)
            isNowFav = false
        } else {
            favMoviesCache.add(0, movie)
            favMovieIds.add(movie.streamId)
            isNowFav = true
        }
        saveMovies(favMoviesKey(), favMoviesCache)
        return isNowFav
    }

    @Synchronized
    fun getRecentMovies(): List<VodMovie> {
        return recentMoviesCache.toList()
    }

    @Synchronized
    fun addMovieToRecent(movie: VodMovie) {
        recentMoviesCache.removeAll { it.streamId == movie.streamId }
        recentMoviesCache.add(0, movie)
        if (recentMoviesCache.size > MAX_RECENT_ITEMS) {
            val trimmed = recentMoviesCache.take(MAX_RECENT_ITEMS).toMutableList()
            recentMoviesCache.clear()
            recentMoviesCache.addAll(trimmed)
        }
        saveMovies(recentMoviesKey(), recentMoviesCache)
    }

    private fun loadMovies(key: String): List<VodMovie> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<VodMovie>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    VodMovie(
                        streamId = obj.getInt("streamId"),
                        name = obj.getString("name"),
                        streamIcon = obj.optString("streamIcon", null as String?),
                        rating = obj.optString("rating", null as String?),
                        categoryId = obj.optString("categoryId", null as String?),
                        containerExtension = obj.optString("containerExtension", "mp4"),
                        directSourceUrl = obj.optString("directSourceUrl", null as String?)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveMovies(key: String, list: List<VodMovie>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("streamId", item.streamId)
                put("name", item.name)
                put("streamIcon", item.streamIcon ?: "")
                put("rating", item.rating ?: "")
                put("categoryId", item.categoryId ?: "")
                put("containerExtension", item.containerExtension ?: "mp4")
                put("directSourceUrl", item.directSourceUrl ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    // ==========================================
    // SERIES
    // ==========================================
    @Synchronized
    fun getFavoriteSeries(): List<SeriesItem> {
        return favSeriesCache.toList()
    }

    fun isSeriesFavorite(seriesId: Int): Boolean {
        return favSeriesIds.contains(seriesId)
    }

    @Synchronized
    fun toggleSeriesFavorite(series: SeriesItem): Boolean {
        val index = favSeriesCache.indexOfFirst { it.seriesId == series.seriesId }
        val isNowFav: Boolean
        if (index >= 0) {
            favSeriesCache.removeAt(index)
            favSeriesIds.remove(series.seriesId)
            isNowFav = false
        } else {
            favSeriesCache.add(0, series)
            favSeriesIds.add(series.seriesId)
            isNowFav = true
        }
        saveSeries(favSeriesKey(), favSeriesCache)
        return isNowFav
    }

    @Synchronized
    fun getRecentSeries(): List<SeriesItem> {
        return recentSeriesCache.toList()
    }

    @Synchronized
    fun addSeriesToRecent(series: SeriesItem) {
        recentSeriesCache.removeAll { it.seriesId == series.seriesId }
        recentSeriesCache.add(0, series)
        if (recentSeriesCache.size > MAX_RECENT_ITEMS) {
            val trimmed = recentSeriesCache.take(MAX_RECENT_ITEMS).toMutableList()
            recentSeriesCache.clear()
            recentSeriesCache.addAll(trimmed)
        }
        saveSeries(recentSeriesKey(), recentSeriesCache)
    }

    private fun loadSeries(key: String): List<SeriesItem> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<SeriesItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SeriesItem(
                        seriesId = obj.getInt("seriesId"),
                        name = obj.getString("name"),
                        cover = obj.optString("cover", null as String?),
                        rating = obj.optString("rating", null as String?),
                        categoryId = obj.optString("categoryId", null as String?)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSeries(key: String, list: List<SeriesItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("seriesId", item.seriesId)
                put("name", item.name)
                put("cover", item.cover ?: "")
                put("rating", item.rating ?: "")
                put("categoryId", item.categoryId ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
