package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AccountSession
import com.example.data.model.AccountType
import com.example.data.model.ChannelEpg
import com.example.data.model.EpgProgram
import com.example.data.model.LiveCategory
import com.example.data.model.LiveChannel
import com.example.data.model.SeriesCategory
import com.example.data.model.SeriesDetail
import com.example.data.model.SeriesItem
import com.example.data.model.VodCategory
import com.example.data.model.VodDetail
import com.example.data.model.VodMovie
import com.example.data.repository.IptvRepository
import com.example.data.repository.IptvResult
import com.example.data.session.SessionManager
import com.example.data.subscription.SubscriptionInfo
import com.example.data.subscription.SubscriptionManager
import com.example.data.subscription.SubscriptionStatus
import com.example.ui.i18n.AppText
import com.example.ui.i18n.LocalizedStrings
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class IptvViewModel(application: Application) : AndroidViewModel(application) {
    val sessionManager = SessionManager(application)
    val favHistoryManager = com.example.data.session.FavoritesHistoryManager(application)
    private val repository = IptvRepository(application.cacheDir)
    val subscriptionManager = SubscriptionManager(application, sessionManager)

    val subscriptionInfo: StateFlow<SubscriptionInfo?> = subscriptionManager.subscriptionState
    val isCheckingSubscription: StateFlow<Boolean> = subscriptionManager.isCheckingSubscription

    companion object {
        const val ID_ALL = "CAT_ALL"
        const val ID_FAVORITES = "CAT_FAVORITES"
        const val ID_RECENTS = "CAT_RECENTS"

        val LIVE_ALL_CATEGORY = LiveCategory(categoryId = ID_ALL, categoryName = "الكل")
        val LIVE_FAV_CATEGORY = LiveCategory(categoryId = ID_FAVORITES, categoryName = "المفضلة")
        val LIVE_RECENT_CATEGORY = LiveCategory(categoryId = ID_RECENTS, categoryName = "آخر المشاهدات")

        val VOD_ALL_CATEGORY = VodCategory(categoryId = ID_ALL, categoryName = "الكل")
        val VOD_FAV_CATEGORY = VodCategory(categoryId = ID_FAVORITES, categoryName = "المفضلة")
        val VOD_RECENT_CATEGORY = VodCategory(categoryId = ID_RECENTS, categoryName = "آخر المشاهدات")

        val SERIES_ALL_CATEGORY = SeriesCategory(categoryId = ID_ALL, categoryName = "الكل")
        val SERIES_FAV_CATEGORY = SeriesCategory(categoryId = ID_FAVORITES, categoryName = "المفضلة")
        val SERIES_RECENT_CATEGORY = SeriesCategory(categoryId = ID_RECENTS, categoryName = "آخر المشاهدات")
    }

    private val _activeAccount = MutableStateFlow<AccountSession?>(null)
    val activeAccount: StateFlow<AccountSession?> = _activeAccount.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<AccountSession>>(emptyList())
    val savedAccounts: StateFlow<List<AccountSession>> = _savedAccounts.asStateFlow()

    // Settings & Localization
    private val _currentLanguage = MutableStateFlow(sessionManager.getLanguage())
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _appText = MutableStateFlow(LocalizedStrings.get(sessionManager.getLanguage()))
    val appText: StateFlow<AppText> = _appText.asStateFlow()

    private val _streamFormat = MutableStateFlow(sessionManager.getStreamFormat())
    val streamFormat: StateFlow<String> = _streamFormat.asStateFlow()

    private val _audioType = MutableStateFlow(sessionManager.getAudioType())
    val audioType: StateFlow<String> = _audioType.asStateFlow()

    private val _currentUserAgent = MutableStateFlow(sessionManager.getUserAgent())
    val currentUserAgent: StateFlow<String> = _currentUserAgent.asStateFlow()

    fun setLanguage(lang: String) {
        sessionManager.setLanguage(lang)
        _currentLanguage.value = lang
        _appText.value = LocalizedStrings.get(lang)
    }

    fun setStreamFormat(format: String) {
        sessionManager.setStreamFormat(format)
        _streamFormat.value = format
    }

    fun setAudioType(type: String) {
        sessionManager.setAudioType(type)
        _audioType.value = type
    }

    fun setUserAgent(ua: String) {
        sessionManager.setUserAgent(ua)
        _currentUserAgent.value = ua
    }

    // Live TV State
    private val _liveCategories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val liveCategories: StateFlow<List<LiveCategory>> = _liveCategories.asStateFlow()

    private val _selectedLiveCategory = MutableStateFlow<LiveCategory?>(null)
    val selectedLiveCategory: StateFlow<LiveCategory?> = _selectedLiveCategory.asStateFlow()

    private val _liveChannels = MutableStateFlow<List<LiveChannel>>(emptyList())
    val liveChannels: StateFlow<List<LiveChannel>> = _liveChannels.asStateFlow()

    private val _allLiveChannels = MutableStateFlow<List<LiveChannel>>(emptyList())
    val allLiveChannels: StateFlow<List<LiveChannel>> = _allLiveChannels.asStateFlow()

    private val _selectedLiveChannel = MutableStateFlow<LiveChannel?>(null)
    val selectedLiveChannel: StateFlow<LiveChannel?> = _selectedLiveChannel.asStateFlow()

    // EPG State
    private val _epgMap = MutableStateFlow<Map<Int, ChannelEpg>>(emptyMap())
    val epgMap: StateFlow<Map<Int, ChannelEpg>> = _epgMap.asStateFlow()

    private val _selectedChannelEpg = MutableStateFlow<ChannelEpg?>(null)
    val selectedChannelEpg: StateFlow<ChannelEpg?> = _selectedChannelEpg.asStateFlow()

    private val _isEpgLoading = MutableStateFlow(false)
    val isEpgLoading: StateFlow<Boolean> = _isEpgLoading.asStateFlow()

    private val inFlightEpgFetches = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    // Persistent scroll & focus state across navigation
    var liveChannelsScrollIndex: Int = 0
    var liveChannelsScrollOffset: Int = 0
    var liveCategoriesScrollIndex: Int = 0
    var liveCategoriesScrollOffset: Int = 0

    var moviesScrollIndex: Int = 0
    var moviesScrollOffset: Int = 0
    var moviesCategoriesScrollIndex: Int = 0

    var seriesScrollIndex: Int = 0
    var seriesScrollOffset: Int = 0
    var seriesCategoriesScrollIndex: Int = 0

    // VOD & Series State
    private val _vodCategories = MutableStateFlow<List<VodCategory>>(emptyList())
    val vodCategories: StateFlow<List<VodCategory>> = _vodCategories.asStateFlow()

    private val _selectedVodCategory = MutableStateFlow<VodCategory?>(null)
    val selectedVodCategory: StateFlow<VodCategory?> = _selectedVodCategory.asStateFlow()

    private val _vodMovies = MutableStateFlow<List<VodMovie>>(emptyList())
    val vodMovies: StateFlow<List<VodMovie>> = _vodMovies.asStateFlow()

    private val _seriesCategories = MutableStateFlow<List<SeriesCategory>>(emptyList())
    val seriesCategories: StateFlow<List<SeriesCategory>> = _seriesCategories.asStateFlow()

    private val _selectedSeriesCategory = MutableStateFlow<SeriesCategory?>(null)
    val selectedSeriesCategory: StateFlow<SeriesCategory?> = _selectedSeriesCategory.asStateFlow()

    private val _seriesList = MutableStateFlow<List<SeriesItem>>(emptyList())
    val seriesList: StateFlow<List<SeriesItem>> = _seriesList.asStateFlow()

    // Per-category in-memory cache to ensure instant switching and prevent re-downloading
    private val liveCategoryCache = mutableMapOf<String, List<LiveChannel>>()
    private val vodCategoryCache = mutableMapOf<String, List<VodMovie>>()
    private val seriesCategoryCache = mutableMapOf<String, List<SeriesItem>>()

    // Counts
    private val _liveCount = MutableStateFlow(0)
    val liveCount: StateFlow<Int> = _liveCount.asStateFlow()

    private val _vodCount = MutableStateFlow(0)
    val vodCount: StateFlow<Int> = _vodCount.asStateFlow()

    private val _seriesCount = MutableStateFlow(0)
    val seriesCount: StateFlow<Int> = _seriesCount.asStateFlow()

    // UI Loading & Status
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLiveChannelsLoading = MutableStateFlow(false)
    val isLiveChannelsLoading: StateFlow<Boolean> = _isLiveChannelsLoading.asStateFlow()

    private val _isVodLoading = MutableStateFlow(false)
    val isVodLoading: StateFlow<Boolean> = _isVodLoading.asStateFlow()

    private val _isSeriesLoading = MutableStateFlow(false)
    val isSeriesLoading: StateFlow<Boolean> = _isSeriesLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _loginInProgress = MutableStateFlow(false)
    val loginInProgress: StateFlow<Boolean> = _loginInProgress.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Cache for M3U channels
    private var cachedM3uChannels: List<LiveChannel> = emptyList()

    init {
        refreshAccounts()
        val currentActive = sessionManager.getActiveAccount()
        _activeAccount.value = currentActive
        if (currentActive != null) {
            loadAccountContent(currentActive)
        }

        // Automatic subscription check and server playlist sync on launch
        viewModelScope.launch {
            if (subscriptionManager.authToken.value != null) {
                subscriptionManager.fetchSubscription()
                if (subscriptionManager.isPlaybackAllowed()) {
                    subscriptionManager.syncPlaylistsFromServer()
                    refreshAccounts()
                    val updatedActive = sessionManager.getActiveAccount()
                    if (updatedActive != null && _activeAccount.value == null) {
                        _activeAccount.value = updatedActive
                        loadAccountContent(updatedActive)
                    }
                }
            }
        }
    }

    fun isPlaybackAllowed(): Boolean = subscriptionManager.isPlaybackAllowed()

    fun onAuthSuccess() {
        viewModelScope.launch {
            subscriptionManager.fetchSubscription()
            subscriptionManager.syncPlaylistsFromServer()
            refreshAccounts()
            val active = sessionManager.getActiveAccount()
            if (active != null) {
                switchAccount(active)
            }
        }
    }

    fun startTrial(email: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = subscriptionManager.startSevenDayTrial(email)
            _isLoading.value = false
            result.onSuccess {
                subscriptionManager.syncPlaylistsFromServer()
                refreshAccounts()
                val active = sessionManager.getActiveAccount()
                if (active != null) {
                    switchAccount(active)
                }
                onSuccess()
            }
        }
    }

    fun loginWithEmail(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = subscriptionManager.loginWithEmail(email, password)
            _isLoading.value = false
            result.onSuccess {
                subscriptionManager.syncPlaylistsFromServer()
                refreshAccounts()
                val active = sessionManager.getActiveAccount()
                if (active != null) {
                    switchAccount(active)
                }
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "فشل تسجيل الدخول")
            }
        }
    }

    fun refreshSubscription(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = subscriptionManager.fetchSubscription()
            val isAllowed = subscriptionManager.isPlaybackAllowed()
            if (isAllowed) {
                subscriptionManager.syncPlaylistsFromServer()
                refreshAccounts()
            }
            onResult(isAllowed)
        }
    }

    fun syncServerPlaylists(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            subscriptionManager.syncPlaylistsFromServer()
            refreshAccounts()
            _isLoading.value = false
            val active = sessionManager.getActiveAccount()
            if (active != null && _allLiveChannels.value.isEmpty()) {
                loadAccountContent(active)
            }
            onComplete()
        }
    }

    fun refreshAccounts() {
        _savedAccounts.value = sessionManager.getAllAccounts()
        _activeAccount.value = sessionManager.getActiveAccount()
    }

    fun loginXtream(
        serverUrl: String,
        username: String,
        password: String,
        accountName: String? = null,
        customUserAgent: String? = null,
        onSuccess: () -> Unit
    ) {
        val effectiveUa = customUserAgent ?: _currentUserAgent.value
        viewModelScope.launch {
            _loginInProgress.value = true
            _loginError.value = null
            _statusMessage.value = "جارٍ الاتصال بالخادم والتحقق من البيانات..."

            when (val result = repository.authenticateXtream(serverUrl, username, password, accountName, customUserAgent = effectiveUa)) {
                is IptvResult.Success -> {
                    val session = result.data
                    sessionManager.saveAccount(session)
                    _activeAccount.value = session
                    refreshAccounts()
                    _loginInProgress.value = false
                    _statusMessage.value = null
                    loadAccountContent(session)
                    onSuccess()
                }
                is IptvResult.Error -> {
                    _loginInProgress.value = false
                    _statusMessage.value = null
                    _loginError.value = result.message
                }
            }
        }
    }

    private var accountLoadJob: kotlinx.coroutines.Job? = null
    private val epgSemaphore = kotlinx.coroutines.sync.Semaphore(3)

    fun loadM3u(
        url: String,
        name: String? = null,
        existingId: String? = null,
        customUserAgent: String? = null,
        onSuccess: () -> Unit
    ) {
        val effectiveUa = customUserAgent ?: _currentUserAgent.value
        accountLoadJob?.cancel()
        accountLoadJob = viewModelScope.launch {
            _loginInProgress.value = true
            _loginError.value = null
            _statusMessage.value = "جارٍ تحميل قائمة القنوات وقراءتها..."

            try {
                when (val result = repository.loadM3uPlaylist(url, name, existingId, customUserAgent = effectiveUa)) {
                    is IptvResult.Success -> {
                        val (session, channels) = result.data
                        cachedM3uChannels = channels
                        favHistoryManager.setCurrentAccount(session.id)
                        sessionManager.saveAccount(session)
                        _activeAccount.value = session
                        refreshAccounts()

                        // Set up categories from M3U
                        val groups = channels.mapNotNull { it.categoryId }.distinct()
                        val catList = groups.map { LiveCategory(categoryId = it, categoryName = it) }
                        val allCats = listOf(LIVE_ALL_CATEGORY, LIVE_FAV_CATEGORY, LIVE_RECENT_CATEGORY) + catList
                        _liveCategories.value = allCats
                        _liveChannels.value = channels
                        _allLiveChannels.value = channels
                        _liveCount.value = channels.size
                        _vodCount.value = 0
                        _seriesCount.value = 0
                        _selectedLiveCategory.value = LIVE_ALL_CATEGORY
                        _selectedLiveChannel.value = channels.firstOrNull()

                        onSuccess()
                    }
                    is IptvResult.Error -> {
                        _loginError.value = result.message
                        _errorMessage.value = result.message
                    }
                }
            } finally {
                if (coroutineContext[kotlinx.coroutines.Job] == accountLoadJob) {
                    _loginInProgress.value = false
                    _isLoading.value = false
                    _statusMessage.value = null
                }
            }
        }
    }

    fun switchAccount(account: AccountSession) {
        accountLoadJob?.cancel()
        clearContent()
        favHistoryManager.setCurrentAccount(account.id)
        sessionManager.setActiveAccountId(account.id)
        _activeAccount.value = account
        refreshAccounts()
        loadAccountContent(account)
    }

    fun deleteAccount(accountId: String) {
        val wasActive = _activeAccount.value?.id == accountId
        sessionManager.deleteAccount(accountId)
        favHistoryManager.clearAccountData(accountId)
        refreshAccounts()
        if (wasActive) {
            clearContent()
            val next = sessionManager.getActiveAccount()
            _activeAccount.value = next
            if (next != null) {
                switchAccount(next)
            }
        }
    }

    fun logout() {
        accountLoadJob?.cancel()
        sessionManager.clearActiveSession()
        _activeAccount.value = null
        clearContent()
    }

    private fun clearContent() {
        accountLoadJob?.cancel()
        _liveCategories.value = emptyList()
        _liveChannels.value = emptyList()
        _allLiveChannels.value = emptyList()
        cachedM3uChannels = emptyList()
        _selectedLiveCategory.value = null
        _selectedLiveChannel.value = null
        _vodCategories.value = emptyList()
        _selectedVodCategory.value = null
        _vodMovies.value = emptyList()
        _seriesCategories.value = emptyList()
        _selectedSeriesCategory.value = null
        _seriesList.value = emptyList()
        liveCategoryCache.clear()
        vodCategoryCache.clear()
        seriesCategoryCache.clear()
        _liveCount.value = 0
        _vodCount.value = 0
        _seriesCount.value = 0
        _epgMap.value = emptyMap()
        _selectedChannelEpg.value = null
        inFlightEpgFetches.clear()
        _isEpgLoading.value = false
        _errorMessage.value = null
        liveChannelsScrollIndex = 0
        liveChannelsScrollOffset = 0
        liveCategoriesScrollIndex = 0
        liveCategoriesScrollOffset = 0
        moviesScrollIndex = 0
        moviesScrollOffset = 0
        moviesCategoriesScrollIndex = 0
        seriesScrollIndex = 0
        seriesScrollOffset = 0
        seriesCategoriesScrollIndex = 0

        _isLoading.value = false
        _isVodLoading.value = false
        _isSeriesLoading.value = false
        _isLiveChannelsLoading.value = false
        _loginInProgress.value = false
    }

    fun loadAccountContent(session: AccountSession) {
        accountLoadJob?.cancel()
        val sessionId = session.id
        favHistoryManager.setCurrentAccount(sessionId)

        if (session.type == AccountType.M3U) {
            if (cachedM3uChannels.isNotEmpty()) {
                val groups = cachedM3uChannels.mapNotNull { it.categoryId }.distinct()
                val catList = groups.map { LiveCategory(categoryId = it, categoryName = it) }
                val allCats = listOf(LIVE_ALL_CATEGORY, LIVE_FAV_CATEGORY, LIVE_RECENT_CATEGORY) + catList
                _liveCategories.value = allCats
                _liveChannels.value = cachedM3uChannels
                _allLiveChannels.value = cachedM3uChannels
                _liveCount.value = cachedM3uChannels.size
                _selectedLiveCategory.value = LIVE_ALL_CATEGORY
                _selectedLiveChannel.value = cachedM3uChannels.firstOrNull()
            } else {
                _isLoading.value = true
                _errorMessage.value = null
                loadM3u(session.m3uUrl, session.name, existingId = session.id) {
                    _isLoading.value = false
                }
            }
            return
        }

        accountLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Fetch live categories
                when (val catRes = repository.getLiveCategories(session)) {
                    is IptvResult.Success -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        val fullList = listOf(LIVE_ALL_CATEGORY, LIVE_FAV_CATEGORY, LIVE_RECENT_CATEGORY) + catRes.data
                        _liveCategories.value = fullList
                        _selectedLiveCategory.value = LIVE_ALL_CATEGORY
                        selectLiveCategory(LIVE_ALL_CATEGORY)
                    }
                    is IptvResult.Error -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        _errorMessage.value = catRes.message
                    }
                }

                // Fetch Live Streams overall to populate total count and pre-cache all categories
                when (val liveRes = repository.getLiveStreams(session)) {
                    is IptvResult.Success -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        _liveCount.value = liveRes.data.size
                        _allLiveChannels.value = liveRes.data
                        // Pre-index all channels by category for 0ms instantaneous switching!
                        val grouped = liveRes.data.groupBy { it.categoryId ?: "" }
                        grouped.forEach { (catId, chList) ->
                            liveCategoryCache[catId] = chList
                        }
                        val activeCat = _selectedLiveCategory.value
                        if (activeCat != null && (_liveChannels.value.isEmpty() || activeCat.categoryId == ID_ALL)) {
                            val channelsForCat = if (activeCat.categoryId == ID_ALL) liveRes.data else (liveCategoryCache[activeCat.categoryId] ?: emptyList())
                            _liveChannels.value = channelsForCat
                            _selectedLiveChannel.value = channelsForCat.firstOrNull()
                            channelsForCat.firstOrNull()?.let { selectLiveChannel(it) }
                            preloadEpgForChannels(channelsForCat)
                        }
                    }
                    is IptvResult.Error -> {
                        if (_activeAccount.value?.id == sessionId) {
                            _errorMessage.value = liveRes.message
                        }
                    }
                }
            } finally {
                if (_activeAccount.value?.id == sessionId) {
                    _isLoading.value = false
                }
            }

            if (_activeAccount.value?.id != sessionId) return@launch

            // Preload VOD and Series in background so remote navigation from Dashboard is instantaneous
            launch {
                loadVodContentIfNeeded(forceRefresh = false)
            }
            launch {
                loadSeriesContentIfNeeded(forceRefresh = false)
            }
        }
    }

    /**
     * Load VOD categories and only the first category's movies initially for maximum speed.
     */
    fun loadVodContentIfNeeded(forceRefresh: Boolean = false) {
        val session = _activeAccount.value ?: return
        val sessionId = session.id
        if (session.type == AccountType.M3U) return
        if (!forceRefresh && _vodCategories.value.isNotEmpty() && _vodMovies.value.isNotEmpty()) return

        viewModelScope.launch {
            _isVodLoading.value = true
            var delegatedToCategory = false
            try {
                when (val vodCatRes = repository.getVodCategories(session)) {
                    is IptvResult.Success -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        val cats = vodCatRes.data
                        val fullList = listOf(VOD_ALL_CATEGORY, VOD_FAV_CATEGORY, VOD_RECENT_CATEGORY) + cats
                        _vodCategories.value = fullList
                        _selectedVodCategory.value = VOD_ALL_CATEGORY
                        delegatedToCategory = true
                        selectVodCategory(VOD_ALL_CATEGORY)
                    }
                    is IptvResult.Error -> {
                        if (_activeAccount.value?.id == sessionId) {
                            _errorMessage.value = vodCatRes.message
                        }
                    }
                }
            } finally {
                if (_activeAccount.value?.id == sessionId && !delegatedToCategory) {
                    _isVodLoading.value = false
                }
            }
        }
    }

    /**
     * Fast category selection for Movies: fetches only this category with caching
     */
    fun selectVodCategory(category: VodCategory) {
        _selectedVodCategory.value = category
        if (category.categoryId == ID_ALL) {
            val cachedAll = vodCategoryCache[ID_ALL]
            if (cachedAll != null) {
                _vodMovies.value = cachedAll
                _isVodLoading.value = false
                return
            }
            val session = _activeAccount.value ?: return
            val sessionId = session.id
            viewModelScope.launch {
                _isVodLoading.value = true
                try {
                    when (val vodRes = repository.getVodStreams(session, null)) {
                        is IptvResult.Success -> {
                            if (_activeAccount.value?.id != sessionId) return@launch
                            vodCategoryCache[ID_ALL] = vodRes.data
                            _vodMovies.value = vodRes.data
                            _vodCount.value = vodRes.data.size
                        }
                        is IptvResult.Error -> {
                            if (_activeAccount.value?.id != sessionId) return@launch
                            val merged = vodCategoryCache.values.flatten().distinctBy { it.streamId }
                            _vodMovies.value = merged
                        }
                    }
                } finally {
                    if (_activeAccount.value?.id == sessionId) {
                        _isVodLoading.value = false
                    }
                }
            }
            return
        }
        if (category.categoryId == ID_FAVORITES) {
            _vodMovies.value = favHistoryManager.getFavoriteMovies()
            _isVodLoading.value = false
            return
        }
        if (category.categoryId == ID_RECENTS) {
            _vodMovies.value = favHistoryManager.getRecentMovies()
            _isVodLoading.value = false
            return
        }

        val cached = vodCategoryCache[category.categoryId]
        if (cached != null) {
            _vodMovies.value = cached
            return
        }

        val session = _activeAccount.value ?: return
        val sessionId = session.id
        viewModelScope.launch {
            _isVodLoading.value = true
            try {
                when (val vodRes = repository.getVodStreams(session, category.categoryId)) {
                    is IptvResult.Success -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        vodCategoryCache[category.categoryId] = vodRes.data
                        _vodMovies.value = vodRes.data
                        if (_vodCount.value == 0) {
                            _vodCount.value = vodRes.data.size
                        }
                    }
                    is IptvResult.Error -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        _vodMovies.value = emptyList()
                    }
                }
            } finally {
                if (_activeAccount.value?.id == sessionId) {
                    _isVodLoading.value = false
                }
            }
        }
    }

    /**
     * Load Series categories and only the first category's series initially for maximum speed.
     */
    fun loadSeriesContentIfNeeded(forceRefresh: Boolean = false) {
        val session = _activeAccount.value ?: return
        val sessionId = session.id
        if (session.type == AccountType.M3U) return
        if (!forceRefresh && _seriesCategories.value.isNotEmpty() && _seriesList.value.isNotEmpty()) return

        viewModelScope.launch {
            _isSeriesLoading.value = true
            var delegatedToCategory = false
            try {
                when (val serCatRes = repository.getSeriesCategories(session)) {
                    is IptvResult.Success -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        val cats = serCatRes.data
                        val fullList = listOf(SERIES_ALL_CATEGORY, SERIES_FAV_CATEGORY, SERIES_RECENT_CATEGORY) + cats
                        _seriesCategories.value = fullList
                        _selectedSeriesCategory.value = SERIES_ALL_CATEGORY
                        delegatedToCategory = true
                        selectSeriesCategory(SERIES_ALL_CATEGORY)
                    }
                    is IptvResult.Error -> {
                        if (_activeAccount.value?.id == sessionId) {
                            _errorMessage.value = serCatRes.message
                        }
                    }
                }
            } finally {
                if (_activeAccount.value?.id == sessionId && !delegatedToCategory) {
                    _isSeriesLoading.value = false
                }
            }
        }
    }

    /**
     * Fast category selection for Series: fetches only this category with caching
     */
    fun selectSeriesCategory(category: SeriesCategory) {
        _selectedSeriesCategory.value = category
        if (category.categoryId == ID_ALL) {
            val cachedAll = seriesCategoryCache[ID_ALL]
            if (cachedAll != null) {
                _seriesList.value = cachedAll
                _isSeriesLoading.value = false
                return
            }
            val session = _activeAccount.value ?: return
            val sessionId = session.id
            viewModelScope.launch {
                _isSeriesLoading.value = true
                try {
                    when (val serRes = repository.getSeries(session, null)) {
                        is IptvResult.Success -> {
                            if (_activeAccount.value?.id != sessionId) return@launch
                            seriesCategoryCache[ID_ALL] = serRes.data
                            _seriesList.value = serRes.data
                            _seriesCount.value = serRes.data.size
                        }
                        is IptvResult.Error -> {
                            if (_activeAccount.value?.id != sessionId) return@launch
                            val merged = seriesCategoryCache.values.flatten().distinctBy { it.seriesId }
                            _seriesList.value = merged
                        }
                    }
                } finally {
                    if (_activeAccount.value?.id == sessionId) {
                        _isSeriesLoading.value = false
                    }
                }
            }
            return
        }
        if (category.categoryId == ID_FAVORITES) {
            _seriesList.value = favHistoryManager.getFavoriteSeries()
            _isSeriesLoading.value = false
            return
        }
        if (category.categoryId == ID_RECENTS) {
            _seriesList.value = favHistoryManager.getRecentSeries()
            _isSeriesLoading.value = false
            return
        }

        val cached = seriesCategoryCache[category.categoryId]
        if (cached != null) {
            _seriesList.value = cached
            return
        }

        val session = _activeAccount.value ?: return
        val sessionId = session.id
        viewModelScope.launch {
            _isSeriesLoading.value = true
            try {
                when (val serRes = repository.getSeries(session, category.categoryId)) {
                    is IptvResult.Success -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        seriesCategoryCache[category.categoryId] = serRes.data
                        _seriesList.value = serRes.data
                        if (_seriesCount.value == 0) {
                            _seriesCount.value = serRes.data.size
                        }
                    }
                    is IptvResult.Error -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        _seriesList.value = emptyList()
                    }
                }
            } finally {
                if (_activeAccount.value?.id == sessionId) {
                    _isSeriesLoading.value = false
                }
            }
        }
    }

    fun selectLiveCategory(category: LiveCategory, resetScroll: Boolean = false) {
        val prevCat = _selectedLiveCategory.value
        _selectedLiveCategory.value = category
        if (resetScroll && prevCat?.categoryId != category.categoryId) {
            liveChannelsScrollIndex = 0
            liveChannelsScrollOffset = 0
        }

        fun applyChannels(list: List<LiveChannel>) {
            _liveChannels.value = list
            val cur = _selectedLiveChannel.value
            if (cur == null || !list.any { it.streamId == cur.streamId }) {
                val first = list.firstOrNull()
                _selectedLiveChannel.value = first
                first?.let { selectLiveChannel(it) }
            } else {
                selectLiveChannel(cur)
            }
            preloadEpgForChannels(list)
        }

        if (category.categoryId == ID_ALL) {
            val all = if (_allLiveChannels.value.isNotEmpty()) {
                _allLiveChannels.value
            } else {
                val sessionType = _activeAccount.value?.type
                if (sessionType == AccountType.M3U) {
                    cachedM3uChannels
                } else {
                    liveCategoryCache.values.flatten().distinctBy { it.streamId }
                }
            }
            if (all.isNotEmpty()) {
                applyChannels(all)
                return
            }
        }

        if (category.categoryId == ID_FAVORITES) {
            applyChannels(favHistoryManager.getFavoriteChannels())
            return
        }
        if (category.categoryId == ID_RECENTS) {
            applyChannels(favHistoryManager.getRecentChannels())
            return
        }

        val session = _activeAccount.value ?: return
        if (session.type == AccountType.M3U) {
            val filtered = cachedM3uChannels.filter { it.categoryId == category.categoryId }
            applyChannels(filtered)
            return
        }

        // Instant in-memory cache lookup: 0ms latency
        val cached = liveCategoryCache[category.categoryId]
        if (cached != null) {
            applyChannels(cached)
            return
        }

        loadLiveChannelsForCategory(session, category.categoryId)
    }

    fun selectLiveChannel(channel: LiveChannel) {
        _selectedLiveChannel.value = channel
        _selectedChannelEpg.value = _epgMap.value[channel.streamId]
        loadEpgForChannel(channel.streamId)
    }

    fun recordChannelPlayed(channel: LiveChannel) {
        favHistoryManager.addChannelToRecent(channel)
    }

    fun getEpgForChannel(streamId: Int): ChannelEpg? {
        return _epgMap.value[streamId]
    }

    fun loadEpgForChannel(streamId: Int, forceRefresh: Boolean = false) {
        val session = _activeAccount.value ?: return
        val sessionId = session.id
        if (session.type == AccountType.M3U) return
        if (!forceRefresh && _epgMap.value.containsKey(streamId)) {
            if (_selectedLiveChannel.value?.streamId == streamId) {
                _selectedChannelEpg.value = _epgMap.value[streamId]
            }
            return
        }
        if (inFlightEpgFetches.contains(streamId)) return
        inFlightEpgFetches.add(streamId)

        viewModelScope.launch {
            if (_selectedLiveChannel.value?.streamId == streamId) {
                _isEpgLoading.value = true
            }
            try {
                when (val res = repository.getChannelEpg(session, streamId)) {
                    is IptvResult.Success -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        _epgMap.update { it + (streamId to res.data) }
                        if (_selectedLiveChannel.value?.streamId == streamId) {
                            _selectedChannelEpg.value = res.data
                        }
                    }
                    is IptvResult.Error -> {}
                }
            } finally {
                inFlightEpgFetches.remove(streamId)
                if (_selectedLiveChannel.value?.streamId == streamId) {
                    _isEpgLoading.value = false
                }
            }
        }
    }

    fun preloadEpgForChannels(channels: List<LiveChannel>, limit: Int = 20) {
        val session = _activeAccount.value ?: return
        val sessionId = session.id
        if (session.type == AccountType.M3U) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val toFetch = channels.take(limit).filter { ch ->
                !_epgMap.value.containsKey(ch.streamId) && !inFlightEpgFetches.contains(ch.streamId)
            }
            toFetch.map { ch ->
                async {
                    if (inFlightEpgFetches.add(ch.streamId)) {
                        try {
                            epgSemaphore.withPermit {
                                if (_activeAccount.value?.id != sessionId) return@withPermit
                                val res = repository.getChannelEpg(session, ch.streamId)
                                if (res is IptvResult.Success) {
                                    if (_activeAccount.value?.id == sessionId) {
                                        _epgMap.update { it + (ch.streamId to res.data) }
                                        if (_selectedLiveChannel.value?.streamId == ch.streamId) {
                                            _selectedChannelEpg.value = res.data
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            inFlightEpgFetches.remove(ch.streamId)
                        }
                    }
                }
            }
        }
    }

    fun markMovieWatched(movie: VodMovie) {
        favHistoryManager.addMovieToRecent(movie)
    }

    fun markSeriesWatched(series: SeriesItem) {
        favHistoryManager.addSeriesToRecent(series)
    }

    fun toggleChannelFavorite(channel: LiveChannel): Boolean {
        val isFav = favHistoryManager.toggleChannelFavorite(channel)
        if (_selectedLiveCategory.value?.categoryId == ID_FAVORITES) {
            _liveChannels.value = favHistoryManager.getFavoriteChannels()
        }
        return isFav
    }

    fun getChannelDisplayName(channel: LiveChannel): String {
        return favHistoryManager.getCustomChannelName(channel.streamId) ?: channel.name
    }

    fun renameLiveChannel(channel: LiveChannel, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        favHistoryManager.setCustomChannelName(channel.streamId, trimmed)
        _liveChannels.value = _liveChannels.value.map {
            if (it.streamId == channel.streamId) it.copy(name = trimmed) else it
        }
        if (_selectedLiveChannel.value?.streamId == channel.streamId) {
            _selectedLiveChannel.value = _selectedLiveChannel.value?.copy(name = trimmed)
        }
    }

    fun isChannelFavorite(channelId: Int): Boolean = favHistoryManager.isChannelFavorite(channelId)

    fun toggleMovieFavorite(movie: VodMovie): Boolean {
        val isFav = favHistoryManager.toggleMovieFavorite(movie)
        if (_selectedVodCategory.value?.categoryId == ID_FAVORITES) {
            _vodMovies.value = favHistoryManager.getFavoriteMovies()
        }
        return isFav
    }

    fun isMovieFavorite(movieId: Int): Boolean = favHistoryManager.isMovieFavorite(movieId)

    fun toggleSeriesFavorite(series: SeriesItem): Boolean {
        val isFav = favHistoryManager.toggleSeriesFavorite(series)
        if (_selectedSeriesCategory.value?.categoryId == ID_FAVORITES) {
            _seriesList.value = favHistoryManager.getFavoriteSeries()
        }
        return isFav
    }

    fun isSeriesFavorite(seriesId: Int): Boolean = favHistoryManager.isSeriesFavorite(seriesId)

    fun playNextLiveChannel(): LiveChannel? {
        val list = _liveChannels.value
        if (list.isEmpty()) return null
        val current = _selectedLiveChannel.value
        val currentIndex = if (current != null) list.indexOfFirst { it.streamId == current.streamId } else -1
        val nextIndex = if (currentIndex in 0 until list.size - 1) currentIndex + 1 else 0
        val nextChannel = list[nextIndex]
        _selectedLiveChannel.value = nextChannel
        return nextChannel
    }

    fun playPreviousLiveChannel(): LiveChannel? {
        val list = _liveChannels.value
        if (list.isEmpty()) return null
        val current = _selectedLiveChannel.value
        val currentIndex = if (current != null) list.indexOfFirst { it.streamId == current.streamId } else -1
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        val prevChannel = list[prevIndex]
        _selectedLiveChannel.value = prevChannel
        return prevChannel
    }

    fun playChannelByNumber(number: Int): LiveChannel? {
        val currentList = _liveChannels.value
        // 1. Check in currently active category channels
        val matchInCurrent = currentList.firstOrNull { it.num == number }
            ?: currentList.getOrNull(number - 1)
            ?: currentList.firstOrNull { it.streamId == number }

        if (matchInCurrent != null) {
            _selectedLiveChannel.value = matchInCurrent
            return matchInCurrent
        }

        // 2. Search across ALL channels in the account
        val all = _allLiveChannels.value.ifEmpty { currentList }
        val matchInAll = all.firstOrNull { it.num == number }
            ?: all.getOrNull(number - 1)
            ?: all.firstOrNull { it.streamId == number }
            ?: all.firstOrNull { ch ->
                val parts = ch.name.split(" ", "-", "_", "|", ":", ".")
                parts.any { it == number.toString() }
            }

        if (matchInAll != null) {
            val catId = matchInAll.categoryId
            if (catId != null) {
                val cat = _liveCategories.value.firstOrNull { it.categoryId == catId }
                if (cat != null) {
                    _selectedLiveCategory.value = cat
                    val catChannels = liveCategoryCache[catId] ?: all.filter { it.categoryId == catId }
                    if (catChannels.isNotEmpty()) {
                        _liveChannels.value = catChannels
                    }
                }
            }
            _selectedLiveChannel.value = matchInAll
            return matchInAll
        }
        return null
    }

    private fun loadLiveChannelsForCategory(session: AccountSession, categoryId: String?) {
        val sessionId = session.id
        viewModelScope.launch {
            _isLiveChannelsLoading.value = true
            try {
                when (val res = repository.getLiveStreams(session, categoryId)) {
                    is IptvResult.Success -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        if (categoryId != null) {
                            liveCategoryCache[categoryId] = res.data
                        }
                        if (_selectedLiveCategory.value?.categoryId == categoryId) {
                            _liveChannels.value = res.data
                            _selectedLiveChannel.value = res.data.firstOrNull()
                            res.data.firstOrNull()?.let { selectLiveChannel(it) }
                            preloadEpgForChannels(res.data)
                        }
                    }
                    is IptvResult.Error -> {
                        if (_activeAccount.value?.id != sessionId) return@launch
                        if (_selectedLiveCategory.value?.categoryId == categoryId) {
                            _errorMessage.value = res.message
                        }
                    }
                }
            } finally {
                if (_activeAccount.value?.id == sessionId) {
                    _isLiveChannelsLoading.value = false
                }
            }
        }
    }

    private fun buildSafeStreamUrl(serverUrl: String, section: String, username: String, password: String, fileSegment: String): String? {
        val parsed = serverUrl.toHttpUrlOrNull()
            ?: ("http://" + serverUrl.removePrefix("http://").removePrefix("https://")).toHttpUrlOrNull()
            ?: return null
        return parsed.newBuilder()
            .addPathSegment(section)
            .addPathSegment(username)
            .addPathSegment(password)
            .addPathSegment(fileSegment)
            .build()
            .toString()
    }

    fun getLiveStreamUrl(channel: LiveChannel): String? {
        if (!channel.directSourceUrl.isNullOrBlank()) {
            return channel.directSourceUrl
        }
        val session = _activeAccount.value ?: return null
        if (session.type == AccountType.XTREAM) {
            val ext = if (_streamFormat.value == "ts") "ts" else "m3u8"
            return buildSafeStreamUrl(session.serverUrl, "live", session.username, session.password, "${channel.streamId}.$ext")
        }
        return null
    }

    fun getVodStreamUrl(movie: VodMovie): String? {
        val session = _activeAccount.value ?: return null
        if (session.type == AccountType.XTREAM) {
            val ext = movie.containerExtension ?: "mp4"
            return buildSafeStreamUrl(session.serverUrl, "movie", session.username, session.password, "${movie.streamId}.$ext")
        }
        return null
    }

    fun getVodStreamUrlFromId(streamId: Int, extension: String = "mp4"): String? {
        val session = _activeAccount.value ?: return null
        if (session.type == AccountType.XTREAM) {
            return buildSafeStreamUrl(session.serverUrl, "movie", session.username, session.password, "$streamId.$extension")
        }
        return null
    }

    fun getEpisodeStreamUrl(episodeId: String, extension: String = "mp4"): String? {
        val session = _activeAccount.value ?: return null
        if (session.type == AccountType.XTREAM) {
            return buildSafeStreamUrl(session.serverUrl, "series", session.username, session.password, "$episodeId.$extension")
        }
        return null
    }

    suspend fun fetchSeriesDetails(seriesId: Int): IptvResult<SeriesDetail> {
        val session = _activeAccount.value ?: return IptvResult.Error("لا يوجد حساب نشط")
        return repository.getSeriesInfo(session, seriesId)
    }

    suspend fun fetchVodDetails(vodId: Int): IptvResult<VodDetail> {
        val session = _activeAccount.value ?: return IptvResult.Error("لا يوجد حساب نشط")
        return repository.getVodInfo(session, vodId)
    }

    fun clearErrors() {
        _errorMessage.value = null
        _loginError.value = null
    }
}
