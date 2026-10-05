/**
 * Mluona IPTV - LG webOS (4.0+ / Chromium 53+)
 * Fixed build: no async/await, no padStart/Object.entries (Chrome 53 safe),
 * real lazy rendering, engine fallback chain, VOD/Series, search, PIN, EPG.
 */
(function () {
  'use strict';

  var STORAGE_KEYS = {
    ACCOUNTS: 'mluona_accounts_v1',
    ACTIVE_ACCOUNT: 'mluona_active_account_id',
    FAVORITES: 'mluona_favorites_v2',
    HISTORY: 'mluona_history_v2',
    LAST_CHANNEL: 'mluona_last_played_ch',
    LANG: 'mluona_language_code',
    PIN: 'mluona_parental_pin_v1',
    ENGINE: 'mluona_stream_engine_choice'
  };

  var RC_KEYS = {
    ENTER: 13, LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40,
    BACK_WEBOS: 461, BACK_ESC: 27, BACKSPACE: 8,
    RED: 403, GREEN: 404, YELLOW: 405, BLUE: 406,
    CH_UP: 427, CH_DOWN: 428, PAGE_UP: 33, PAGE_DOWN: 34,
    PLAY: 415, PAUSE: 19, STOP: 413, FF: 417, REWIND: 412
  };

  var ADULT_RE = /adult|xxx|porn|\+18|18\+|للكبار|اباحي|إباحي/i;

  var state = {
    currentScreen: 'screen-portal',
    activeNavTarget: 'live',
    accounts: [],
    activeAccount: null,
    connecting: false,
    categories: [],
    channels: [],
    channelMap: {},
    filteredChannels: [],
    selectedCategoryIndex: -1,
    vod: { movies: null, series: null },          // {cats, items, filtered, loaded}
    seriesMode: 'list',
    favorites: new Set(),
    history: [],
    parentalPin: '0000',
    preferredEngine: 'auto',
    language: 'ar',
    unlocked: {},

    // playback
    hlsPlayer: null,
    mpegtsPlayer: null,
    playing: false,
    playToken: 0,
    currentItem: null,
    playList: [],
    playIndex: -1,
    candidates: [],
    candIdx: 0,
    rounds: 0,
    maxRounds: 2,
    hlsNetRetries: 0,
    hlsMediaRetries: 0,
    retryTimer: null,
    watchdog: null,
    osdTimer: null,
    resumeItem: null,

    numBuffer: '',
    numTimer: null,
    pendingPin: null,
    lastFocused: {},
    lastActionAt: 0,
    lastActionEl: null
  };

  var dom = {};
  var lazy = {};

  // ======================================================================
  // Helpers
  // ======================================================================
  function esc(s) {
    return String(s === undefined || s === null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function pad2(n) { n = parseInt(n, 10) || 0; return n < 10 ? '0' + n : String(n); }

  function safeParse(key, fallback) {
    try {
      var raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (_) { return fallback; }
  }

  function safeSet(key, value) {
    try { localStorage.setItem(key, value); } catch (_) {}
  }

  function isTyping() {
    var a = document.activeElement;
    return !!(a && a.tagName === 'INPUT');
  }

  function isVisible(el) {
    return !!(el && el.getClientRects && el.getClientRects().length > 0);
  }

  function accId() { return state.activeAccount ? state.activeAccount.id : ''; }
  function favKey(id) { return accId() + ':' + id; }
  function isFav(id) { return state.favorites.has(favKey(id)); }

  function toast(msg) {
    var t = document.createElement('div');
    t.textContent = msg;
    t.style.cssText = 'position:fixed;bottom:90px;left:50%;transform:translateX(-50%);' +
      'background:rgba(0,0,0,.88);color:#fff;padding:16px 34px;border-radius:14px;' +
      'font-size:24px;z-index:9999;border:2px solid #00E676;';
    document.body.appendChild(t);
    setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 2500);
  }

  // ======================================================================
  // Boot
  // ======================================================================
  window.addEventListener('DOMContentLoaded', function () {
    initDomCache();
    initClock();
    loadStoredData();
    initSettingsUi();
    bindRemoteEvents();
    bindVideoEvents();
    bindNetworkListeners();
    bindLifecycle();

    switchScreen('screen-portal');
    renderSavedAccounts();
    if (state.activeAccount) connectToAccount(state.activeAccount, false);
  });

  function initDomCache() {
    var ids = {
      screenPortal: 'screen-portal', screenDashboard: 'screen-dashboard', screenPlayer: 'screen-player',
      portalStatus: 'portal-status', savedAccountsSection: 'saved-accounts-section',
      savedAccountsList: 'saved-accounts-list', categoriesContainer: 'categories-container',
      channelsGrid: 'channels-grid', channelsViewport: 'channels-viewport',
      activeCategoryTitle: 'active-category-title', channelsCountBadge: 'channels-count-badge',
      streamEngineBadge: 'stream-engine-badge', globalSearchInput: 'global-search-input',
      liveClock: 'live-clock', videoElement: 'video-element', playerSpinner: 'player-spinner',
      spinnerText: 'spinner-text',
      playerErrorBanner: 'player-error-banner', playerErrorMsg: 'player-error-msg',
      retryCounterText: 'retry-counter-text', playerOsd: 'player-osd',
      osdChannelNum: 'osd-channel-num', osdChannelName: 'osd-channel-name',
      osdCategoryName: 'osd-category-name', osdFavIcon: 'osd-fav-icon', osdClock: 'osd-clock',
      osdEngineBadge: 'osd-engine-badge', osdEpgNow: 'osd-epg-now',
      numberJumpOsd: 'number-jump-osd', jumpDigits: 'jump-digits',
      networkOfflineBanner: 'network-offline-banner',
      modalPin: 'modal-pin', modalPinInput: 'modal-pin-input', pinModalError: 'pin-modal-error',
      moviesGrid: 'movies-grid', seriesGrid: 'series-grid',
      vodCats: 'vod-categories-container', seriesCats: 'series-categories-container',
      moviesTitle: 'movies-category-title', seriesTitle: 'series-category-title',
      moviesBadge: 'movies-count-badge', seriesBadge: 'series-count-badge',
      favGrid: 'favorites-grid', histGrid: 'history-grid'
    };
    for (var k in ids) { if (ids.hasOwnProperty(k)) dom[k] = document.getElementById(ids[k]); }
    dom.osdLiveTag = document.querySelector('.osd-status-tags .tag-live');
  }

  function initClock() {
    function update() {
      var d = new Date();
      var s = pad2(d.getHours()) + ':' + pad2(d.getMinutes());
      if (dom.liveClock) dom.liveClock.textContent = s;
      if (dom.osdClock) dom.osdClock.textContent = s;
    }
    update();
    setInterval(update, 15000);
  }

  function loadStoredData() {
    state.accounts = safeParse(STORAGE_KEYS.ACCOUNTS, []);
    if (!Array.isArray(state.accounts)) state.accounts = [];
    var activeId = null;
    try { activeId = localStorage.getItem(STORAGE_KEYS.ACTIVE_ACCOUNT); } catch (_) {}
    state.activeAccount = null;
    for (var i = 0; i < state.accounts.length; i++) {
      if (state.accounts[i].id === activeId) state.activeAccount = state.accounts[i];
    }
    var favs = safeParse(STORAGE_KEYS.FAVORITES, []);
    state.favorites = new Set(Array.isArray(favs) ? favs : []);
    var hist = safeParse(STORAGE_KEYS.HISTORY, []);
    state.history = (Array.isArray(hist) ? hist : []).filter(function (h) { return h && h.accId && h.id; });
    var pin = null, eng = null, lang = null;
    try {
      pin = localStorage.getItem(STORAGE_KEYS.PIN);
      eng = localStorage.getItem(STORAGE_KEYS.ENGINE);
      lang = localStorage.getItem(STORAGE_KEYS.LANG);
    } catch (_) {}
    state.parentalPin = pin || '0000';
    state.preferredEngine = eng || 'auto';
    state.language = lang || 'ar';
  }

  function initSettingsUi() {
    var i, nodes = document.querySelectorAll('[data-action="set-engine"]');
    for (i = 0; i < nodes.length; i++) nodes[i].classList.toggle('active', nodes[i].dataset.engine === state.preferredEngine);
    nodes = document.querySelectorAll('[data-action="set-lang"]');
    for (i = 0; i < nodes.length; i++) nodes[i].classList.toggle('active', nodes[i].dataset.lang === state.language);
    dom.streamEngineBadge.textContent = engineLabel(state.preferredEngine);
    document.documentElement.setAttribute('lang', state.language);
    dom.globalSearchInput.addEventListener('input', debounce(runSearch, 350));
  }

  function engineLabel(e) {
    return { auto: 'Auto Engine', hls: 'Hls.js', native: 'webOS Native', mpegts: 'MpegTS.js' }[e] || 'Auto Engine';
  }

  function debounce(fn, ms) {
    var t = null;
    return function () { clearTimeout(t); t = setTimeout(fn, ms); };
  }

  function saveAccounts() { safeSet(STORAGE_KEYS.ACCOUNTS, JSON.stringify(state.accounts)); }
  function saveFavorites() { safeSet(STORAGE_KEYS.FAVORITES, JSON.stringify(Array.from(state.favorites))); }
  function saveHistory() { safeSet(STORAGE_KEYS.HISTORY, JSON.stringify(state.history.slice(0, 30))); }

  // ======================================================================
  // Screens
  // ======================================================================
  function switchScreen(screenId) {
    var all = document.querySelectorAll('.screen');
    for (var i = 0; i < all.length; i++) all[i].classList.remove('active');
    var target = document.getElementById(screenId);
    if (!target) return;
    target.classList.add('active');
    state.currentScreen = screenId;

    setTimeout(function () {
      if (state.currentScreen !== screenId) return;
      var last = state.lastFocused[screenId];
      if (last && document.body.contains(last) && isVisible(last)) {
        last.focus();
      } else {
        var first = target.querySelector('.focusable:not([disabled])');
        if (first && isVisible(first)) first.focus();
      }
    }, 60);
  }

  function switchNavTarget(targetId) {
    state.activeNavTarget = targetId;
    var tabs = document.querySelectorAll('.nav-tab');
    for (var i = 0; i < tabs.length; i++) tabs[i].classList.toggle('active', tabs[i].dataset.target === targetId);
    var views = document.querySelectorAll('.dash-view');
    for (var j = 0; j < views.length; j++) views[j].classList.toggle('active', views[j].id === 'section-' + targetId);
    dom.globalSearchInput.value = '';

    if (targetId === 'favorites') renderFavoritesView();
    else if (targetId === 'history') renderHistoryView();
    else if (targetId === 'accounts') renderAccountsManagement();
    else if (targetId === 'movies') ensureVod('movies');
    else if (targetId === 'series') ensureVod('series');

    setTimeout(function () {
      var view = document.getElementById('section-' + targetId);
      if (view) {
        var first = view.querySelector('.focusable');
        if (first && isVisible(first)) first.focus();
      }
    }, 80);
  }

  // ======================================================================
  // Network
  // ======================================================================
  function makeNetworkRequest(url, options) {
    options = options || {};
    var timeoutMs = options.timeout || 20000;
    var maxRetries = options.retries !== undefined ? options.retries : 2;

    return new Promise(function (resolve, reject) {
      var attempts = 0;
      function execute() {
        attempts++;
        var xhr = new XMLHttpRequest();
        xhr.open(options.method || 'GET', url, true);
        xhr.timeout = timeoutMs;
        xhr.onload = function () {
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(xhr.responseText);
          } else if (xhr.status === 401 || xhr.status === 403) {
            reject(new Error('اسم المستخدم أو كلمة المرور غير صحيحة، أو انتهت صلاحية الاشتراك'));
          } else if (xhr.status === 404) {
            reject(new Error('الرابط غير موجود على السيرفر (404)'));
          } else if (attempts <= maxRetries) {
            setTimeout(execute, 1000 * attempts);
          } else {
            reject(new Error('استجاب السيرفر برمز خطأ (' + xhr.status + ')'));
          }
        };
        xhr.ontimeout = function () {
          if (attempts <= maxRetries) setTimeout(execute, 1500 * attempts);
          else reject(new Error('انتهت مهلة الاتصال بالسيرفر. تحقق من الرابط وسرعة الإنترنت'));
        };
        xhr.onerror = function () {
          if (attempts <= maxRetries) setTimeout(execute, 1500 * attempts);
          else reject(new Error('تعذر الاتصال بالسيرفر. تحقق من الرابط/المنفذ واتصال الإنترنت'));
        };
        xhr.send(options.body || null);
      }
      execute();
    });
  }

  function parseJson(text) {
    try { return JSON.parse(text); }
    catch (_) { throw new Error('رد السيرفر ليس JSON صالحاً. تحقق من رابط السيرفر والمنفذ'); }
  }

  function xtreamApi(acc, action, extra) {
    var url = acc.host + '/player_api.php?username=' + encodeURIComponent(acc.user) +
      '&password=' + encodeURIComponent(acc.pass) + (action ? '&action=' + action : '') + (extra || '');
    return makeNetworkRequest(url, { timeout: 60000 }).then(parseJson);
  }

  // ======================================================================
  // Account connection
  // ======================================================================
  function resetData() {
    state.categories = [];
    state.channels = [];
    state.channelMap = {};
    state.filteredChannels = [];
    state.vod = { movies: null, series: null };
    state.seriesMode = 'list';
    state.unlocked = {};
  }

  function connectToAccount(account, isNew) {
    if (state.connecting) return;
    state.connecting = true;
    showPortalStatus('جارٍ الاتصال بالسيرفر وتحميل البيانات...');
    var previous = state.activeAccount;

    var work = account.type === 'xtream' ? loadXtreamAccount(account) : loadM3uAccount(account);
    work.then(function () {
      state.activeAccount = account;
      safeSet(STORAGE_KEYS.ACTIVE_ACCOUNT, account.id);
      if (isNew) saveNewAccount(account);
      showPortalStatus('');
      switchScreen('screen-dashboard');
      switchNavTarget('live');
    }).catch(function (err) {
      state.activeAccount = previous && previous.id !== account.id ? previous : null;
      showPortalStatus((err && err.message) || 'فشل الاتصال', true);
      switchScreen('screen-portal');
      renderSavedAccounts();
    }).then(function () {
      state.connecting = false;
    });
  }

  function loadXtreamAccount(account) {
    return xtreamApi(account, '').then(function (authData) {
      var ui = authData && authData.user_info;
      if (!ui || Number(ui.auth) === 0) throw new Error('فشل تسجيل الدخول: اسم المستخدم أو كلمة المرور غير صحيحة');
      var st = String(ui.status || '').toLowerCase();
      if (st === 'expired' || st === 'banned' || st === 'disabled') throw new Error('الاشتراك غير فعّال (' + ui.status + ')');
      return Promise.all([xtreamApi(account, 'get_live_categories'), xtreamApi(account, 'get_live_streams')]);
    }).then(function (res) {
      var cats = res[0], streams = res[1];
      resetData();
      state.categories = Array.isArray(cats) ? cats.map(function (c) {
        return { category_id: String(c.category_id), category_name: c.category_name || 'بدون اسم' };
      }) : [];
      var host = account.host, u = encodeURIComponent(account.user), p = encodeURIComponent(account.pass);
      var list = Array.isArray(streams) ? streams : [];
      state.channels = list.map(function (ch, idx) {
        return {
          live: true,
          id: String(ch.stream_id),
          num: ch.num || (idx + 1),
          name: ch.name || ('قناة ' + (idx + 1)),
          categoryId: ch.category_id === null || ch.category_id === undefined ? '' : String(ch.category_id),
          epgId: ch.epg_channel_id || '',
          logo: ch.stream_icon || '',
          url: host + '/live/' + u + '/' + p + '/' + ch.stream_id + '.m3u8',
          altUrl: host + '/live/' + u + '/' + p + '/' + ch.stream_id + '.ts'
        };
      });
      finishChannelsLoad();
    });
  }

  function loadM3uAccount(account) {
    return makeNetworkRequest(account.url, { timeout: 90000 }).then(function (content) {
      resetData();
      parseM3u(content);
      if (!state.channels.length) throw new Error('ملف M3U لا يحتوي على قنوات صالحة');
      finishChannelsLoad();
    });
  }

  function finishChannelsLoad() {
    state.channelMap = {};
    for (var i = 0; i < state.channels.length; i++) state.channelMap[state.channels[i].id] = state.channels[i];
    renderCategories(dom.categoriesContainer, state.categories, state.channels, 'live');
    selectCategory('live', -1);
  }

  function parseM3u(content) {
    var lines = String(content).split(/\r?\n/);
    var channels = [], catMap = {}, catOrder = [], cur = null;

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i].trim();
      if (!line) continue;
      if (line.indexOf('#EXTINF:') === 0) {
        var group = line.match(/group-title="([^"]*)"/i);
        var logo = line.match(/tvg-logo="([^"]*)"/i);
        var tvgId = line.match(/tvg-id="([^"]*)"/i);
        var chno = line.match(/tvg-chno="(\d+)"/i);
        // title = text after the first comma that is OUTSIDE quotes
        var title = '', inQ = false;
        for (var c = 0; c < line.length; c++) {
          var ch = line.charAt(c);
          if (ch === '"') inQ = !inQ;
          else if (ch === ',' && !inQ) { title = line.substring(c + 1).trim(); break; }
        }
        var category = group && group[1].trim() ? group[1].trim() : 'عامة';
        if (!catMap.hasOwnProperty(category)) { catMap[category] = String(catOrder.length + 1); catOrder.push(category); }
        cur = {
          live: true,
          name: title || ('قناة ' + (channels.length + 1)),
          categoryId: catMap[category],
          logo: logo ? logo[1].trim() : '',
          epgId: tvgId ? tvgId[1] : '',
          chno: chno ? parseInt(chno[1], 10) : 0
        };
      } else if (line.charAt(0) !== '#' && cur) {
        cur.url = line;
        cur.id = String(channels.length + 1);
        cur.num = cur.chno || (channels.length + 1);
        channels.push(cur);
        cur = null;
      }
    }
    state.categories = catOrder.map(function (name) { return { category_id: catMap[name], category_name: name }; });
    state.channels = channels;
  }

  // ======================================================================
  // Lazy chunked rendering (fixes the 80-channel cut-off)
  // ======================================================================
  function mountList(el, items, build, step) {
    el.innerHTML = '';
    var ctl = { el: el, items: items, build: build, step: step || 60, rendered: 0 };
    lazy[el.id] = ctl;
    appendChunk(ctl);
    return ctl;
  }

  function appendChunk(ctl) {
    var frag = document.createDocumentFragment();
    var end = Math.min(ctl.rendered + ctl.step, ctl.items.length);
    for (var i = ctl.rendered; i < end; i++) frag.appendChild(ctl.build(ctl.items[i], i));
    ctl.el.appendChild(frag);
    ctl.rendered = end;
  }

  function checkLazy() {
    for (var id in lazy) {
      if (!lazy.hasOwnProperty(id)) continue;
      var ctl = lazy[id];
      if (ctl.rendered >= ctl.items.length || !isVisible(ctl.el)) continue;
      var last = ctl.el.lastElementChild;
      if (last && last.getBoundingClientRect().top < window.innerHeight + 600) appendChunk(ctl);
    }
  }

  var lazyScheduled = false;
  function scheduleLazy() {
    if (lazyScheduled) return;
    lazyScheduled = true;
    setTimeout(function () { lazyScheduled = false; checkLazy(); }, 80);
  }

  function logoImg(url, cls) {
    if (!url || !/^https?:/i.test(url)) return null;
    var img = document.createElement('img');
    img.className = cls;
    img.src = url;
    img.onerror = function () { if (img.parentNode) img.parentNode.removeChild(img); };
    return img;
  }

  function buildLiveCard(ch, subtitle) {
    var card = document.createElement('button');
    card.className = 'channel-card focusable';
    card.dataset.action = 'play-channel';
    card.dataset.id = ch.id;
    card.innerHTML =
      '<span class="ch-number">' + esc(ch.num) + '</span>' +
      '<div class="ch-body"><div class="ch-title">' + esc(ch.name) + '</div>' +
      '<div class="ch-epg">' + esc(subtitle || 'بث مباشر') + '</div></div>' +
      '<span class="ch-star">' + (isFav(ch.id) ? '⭐' : '') + '</span>';
    var img = logoImg(ch.logo, 'ch-logo');
    if (img) card.insertBefore(img, card.firstChild);
    return card;
  }

  function buildVodCard(item, action) {
    var card = document.createElement('button');
    card.className = 'vod-card focusable';
    card.dataset.action = action;
    card.dataset.id = item.id;
    card.innerHTML =
      '<div class="vod-info"><div class="vod-title">' + esc(item.name) + '</div>' +
      (item.rating ? '<div class="vod-rating">⭐ ' + esc(item.rating) + '</div>' : '') + '</div>';
    var img = logoImg(item.logo, 'vod-poster');
    if (img) card.insertBefore(img, card.firstChild);
    return card;
  }

  // ======================================================================
  // Categories (live / movies / series)
  // ======================================================================
  function renderCategories(container, cats, items, kind) {
    container.innerHTML = '';
    var counts = {}, i;
    for (i = 0; i < items.length; i++) { var k = items[i].categoryId; counts[k] = (counts[k] || 0) + 1; }

    var frag = document.createDocumentFragment();
    function add(label, count, idx, active) {
      var b = document.createElement('button');
      b.className = 'cat-btn focusable' + (active ? ' active' : '');
      b.dataset.action = 'select-category';
      b.dataset.kind = kind;
      b.dataset.index = String(idx);
      b.innerHTML = '<span>' + esc(label) + '</span><span class="cat-badge">' + count + '</span>';
      frag.appendChild(b);
    }
    add('🌟 الكل', items.length, -1, true);
    for (i = 0; i < cats.length; i++) {
      var locked = ADULT_RE.test(cats[i].category_name);
      add((locked ? '🔒 ' : '') + cats[i].category_name, counts[cats[i].category_id] || 0, i, false);
    }
    container.appendChild(frag);
  }

  function setActiveCat(kind, index) {
    var cont = kind === 'live' ? dom.categoriesContainer : (kind === 'movies' ? dom.vodCats : dom.seriesCats);
    var btns = cont.querySelectorAll('.cat-btn');
    for (var i = 0; i < btns.length; i++) btns[i].classList.toggle('active', parseInt(btns[i].dataset.index, 10) === index);
  }

  function isLockedCategory(kind, cat) {
    return !!(cat && ADULT_RE.test(cat.category_name) && !state.unlocked[kind + ':' + cat.category_id]);
  }

  function selectCategory(kind, index) {
    var cats = kind === 'live' ? state.categories : (state.vod[kind] ? state.vod[kind].cats : []);
    var cat = index >= 0 ? cats[index] : null;

    if (cat && isLockedCategory(kind, cat)) {
      requirePin(function () {
        state.unlocked[kind + ':' + cat.category_id] = true;
        selectCategory(kind, index);
      });
      return;
    }

    setActiveCat(kind, index);
    if (kind === 'live') {
      state.selectedCategoryIndex = index;
      state.filteredChannels = cat ? state.channels.filter(function (c) { return c.categoryId === cat.category_id; }) : state.channels;
      dom.activeCategoryTitle.textContent = cat ? cat.category_name : '🌟 جميع القنوات';
      renderLive();
    } else {
      var v = state.vod[kind];
      v.filtered = cat ? v.items.filter(function (c) { return c.categoryId === cat.category_id; }) : v.items;
      (kind === 'movies' ? dom.moviesTitle : dom.seriesTitle).textContent = cat ? cat.category_name : (kind === 'movies' ? 'مكتبة الأفلام' : 'المسلسلات');
      renderVod(kind);
    }
  }

  function renderLive() {
    dom.channelsCountBadge.textContent = state.filteredChannels.length + ' قناة';
    mountList(dom.channelsGrid, state.filteredChannels, function (ch) {
      return buildLiveCard(ch, ch.epgId ? 'دليل البرامج متاح' : 'بث مباشر');
    }, 60);
    dom.channelsViewport.scrollTop = 0;
  }

  // ======================================================================
  // Movies & Series (Xtream)
  // ======================================================================
  function ensureVod(kind) {
    var grid = kind === 'movies' ? dom.moviesGrid : dom.seriesGrid;
    if (!state.activeAccount) return;
    if (state.activeAccount.type !== 'xtream') {
      grid.innerHTML = '<div class="empty-state-card"><h3>غير متاح</h3><p>الأفلام والمسلسلات تتطلب حساب Xtream Codes</p></div>';
      return;
    }
    if (state.vod[kind] && state.vod[kind].loaded) return;

    grid.innerHTML = '<div class="empty-state-card"><h3>جارٍ التحميل...</h3></div>';
    var acc = state.activeAccount;
    var catAction = kind === 'movies' ? 'get_vod_categories' : 'get_series_categories';
    var listAction = kind === 'movies' ? 'get_vod_streams' : 'get_series';

    Promise.all([xtreamApi(acc, catAction), xtreamApi(acc, listAction)]).then(function (res) {
      if (state.activeAccount !== acc) return;
      var cats = (Array.isArray(res[0]) ? res[0] : []).map(function (c) {
        return { category_id: String(c.category_id), category_name: c.category_name || 'بدون اسم' };
      });
      var items = (Array.isArray(res[1]) ? res[1] : []).map(function (m) {
        var id = String(kind === 'movies' ? m.stream_id : m.series_id);
        return {
          live: false, kind: kind, id: id, name: m.name || m.title || '—',
          categoryId: m.category_id === null || m.category_id === undefined ? '' : String(m.category_id),
          logo: (kind === 'movies' ? m.stream_icon : m.cover) || '',
          rating: m.rating && parseFloat(m.rating) > 0 ? m.rating : '',
          ext: m.container_extension || 'mp4'
        };
      });
      state.vod[kind] = { cats: cats, items: items, filtered: items, loaded: true };
      renderCategories(kind === 'movies' ? dom.vodCats : dom.seriesCats, cats, items, kind);
      renderVod(kind);
    }).catch(function (err) {
      grid.innerHTML = '<div class="empty-state-card"><h3>تعذر التحميل</h3><p>' + esc(err.message) + '</p></div>';
    });
  }

  function renderVod(kind) {
    var v = state.vod[kind];
    var grid = kind === 'movies' ? dom.moviesGrid : dom.seriesGrid;
    (kind === 'movies' ? dom.moviesBadge : dom.seriesBadge).textContent = v.filtered.length + (kind === 'movies' ? ' فيلم' : ' مسلسل');
    state.seriesMode = 'list';
    mountList(grid, v.filtered, function (item) {
      return buildVodCard(item, kind === 'movies' ? 'play-movie' : 'open-series');
    }, 40);
  }

  function playMovie(id) {
    var v = state.vod.movies;
    if (!v) return;
    var item = null;
    for (var i = 0; i < v.items.length; i++) if (v.items[i].id === id) { item = v.items[i]; break; }
    if (!item) return;
    var acc = state.activeAccount;
    item.url = acc.host + '/movie/' + encodeURIComponent(acc.user) + '/' + encodeURIComponent(acc.pass) + '/' + item.id + '.' + item.ext;
    playItem(item, [item]);
  }

  function openSeries(id) {
    var acc = state.activeAccount;
    dom.seriesGrid.innerHTML = '<div class="empty-state-card"><h3>جارٍ تحميل الحلقات...</h3></div>';
    xtreamApi(acc, 'get_series_info', '&series_id=' + encodeURIComponent(id)).then(function (data) {
      var episodes = [], seasons = (data && data.episodes) || {};
      var keys = Object.keys(seasons).sort(function (a, b) { return parseInt(a, 10) - parseInt(b, 10); });
      keys.forEach(function (s) {
        (seasons[s] || []).forEach(function (ep) {
          var ext = ep.container_extension || 'mp4';
          episodes.push({
            live: false, kind: 'episode', id: String(ep.id),
            name: 'S' + pad2(s) + ' E' + pad2(ep.episode_num) + ' - ' + (ep.title || ''),
            logo: (ep.info && ep.info.movie_image) || '', rating: '',
            url: acc.host + '/series/' + encodeURIComponent(acc.user) + '/' + encodeURIComponent(acc.pass) + '/' + ep.id + '.' + ext
          });
        });
      });
      state.seriesMode = 'episodes';
      var list = [{ id: '__back__', name: '⬅ رجوع للمسلسلات', back: true }].concat(episodes);
      mountList(dom.seriesGrid, list, function (item) {
        return buildVodCard(item, item.back ? 'series-back' : 'play-episode');
      }, 40);
      state.currentEpisodes = episodes;
      var first = dom.seriesGrid.querySelector('.focusable');
      if (first) first.focus();
    }).catch(function (err) {
      state.seriesMode = 'list';
      toast(err.message);
      renderVod('series');
    });
  }

  // ======================================================================
  // Search
  // ======================================================================
  function runSearch() {
    var q = dom.globalSearchInput.value.trim().toLowerCase();
    var t = state.activeNavTarget;
    if (t !== 'live' && t !== 'movies' && t !== 'series') { switchNavTarget('live'); dom.globalSearchInput.value = q; t = 'live'; }
    function match(x) { return x.name.toLowerCase().indexOf(q) !== -1; }

    if (t === 'live') {
      state.filteredChannels = q ? state.channels.filter(match) : (state.selectedCategoryIndex >= 0
        ? state.channels.filter(function (c) { return c.categoryId === state.categories[state.selectedCategoryIndex].category_id; })
        : state.channels);
      if (q) dom.activeCategoryTitle.textContent = 'نتائج البحث: ' + q;
      renderLive();
    } else if (state.vod[t]) {
      state.vod[t].filtered = q ? state.vod[t].items.filter(match) : state.vod[t].items;
      renderVod(t);
    }
  }

  // ======================================================================
  // Parental PIN
  // ======================================================================
  function requirePin(cb) {
    state.pendingPin = cb;
    dom.modalPinInput.value = '';
    dom.pinModalError.textContent = '';
    dom.modalPin.classList.remove('hidden');
    dom.modalPinInput.focus();
  }

  function closePinModal() {
    dom.modalPin.classList.add('hidden');
    state.pendingPin = null;
    var last = state.lastFocused[state.currentScreen];
    if (last && isVisible(last)) last.focus();
  }

  function submitPin() {
    if (dom.modalPinInput.value === state.parentalPin) {
      var cb = state.pendingPin;
      closePinModal();
      if (cb) cb();
    } else {
      dom.pinModalError.textContent = 'رمز PIN غير صحيح';
      dom.pinModalError.style.color = '#FF5252';
      dom.modalPinInput.value = '';
    }
  }

  // ======================================================================
  // Playback
  // ======================================================================
  function playLiveById(channelId, list) {
    var ch = state.channelMap[channelId];
    if (!ch) return;
    var cat = null;
    for (var i = 0; i < state.categories.length; i++) if (state.categories[i].category_id === ch.categoryId) cat = state.categories[i];
    if (cat && isLockedCategory('live', cat)) {
      requirePin(function () { state.unlocked['live:' + cat.category_id] = true; playLiveById(channelId, list); });
      return;
    }
    playItem(ch, list || state.filteredChannels);
  }

  function playItem(item, list) {
    state.currentItem = item;
    state.playList = list && list.length ? list : [item];
    state.playIndex = -1;
    for (var i = 0; i < state.playList.length; i++) if (state.playList[i].id === item.id) { state.playIndex = i; break; }
    if (state.playIndex === -1) { state.playList = [item]; state.playIndex = 0; }

    switchScreen('screen-player');
    state.resumeItem = null;

    if (item.live) {
      state.history = [{ id: item.id, accId: accId() }].concat(state.history.filter(function (h) {
        return !(h.id === item.id && h.accId === accId());
      })).slice(0, 30);
      saveHistory();
      safeSet(STORAGE_KEYS.LAST_CHANNEL, item.id);
    }

    var cat = '';
    for (var c = 0; c < state.categories.length; c++) if (state.categories[c].category_id === item.categoryId) cat = state.categories[c].category_name;
    dom.osdChannelNum.textContent = item.live ? pad2(item.num) : '▶';
    dom.osdChannelName.textContent = item.name;
    dom.osdCategoryName.textContent = item.live ? cat : (item.kind === 'movies' ? 'فيلم' : 'مسلسل');
    dom.osdFavIcon.classList.toggle('hidden', !(item.live && isFav(item.id)));
    dom.osdEpgNow.textContent = item.live ? 'البث المباشر للقناة' : '';
    if (dom.osdLiveTag) dom.osdLiveTag.classList.toggle('hidden', !item.live);
    showPlayerOsd();

    state.rounds = 0;
    buildCandidates(item);
    state.candIdx = 0;
    runCandidate();
    if (item.live) loadEpg(item);
  }

  function enginesFor(url) {
    var pref = state.preferredEngine;
    var isM3u8 = /\.m3u8(\?|$)/i.test(url);
    var isTs = /\.ts(\?|$)/i.test(url);
    var hlsOk = !!(window.Hls && Hls.isSupported());
    var tsOk = !!(window.mpegts && mpegts.isSupported());
    if (pref === 'native') return ['native'];
    if (pref === 'hls') return hlsOk ? ['hls', 'native'] : ['native'];
    if (pref === 'mpegts') return tsOk ? ['mpegts', 'native'] : ['native'];
    // auto: webOS native HLS first (hardware decode), then JS engines
    if (isM3u8) return hlsOk ? ['native', 'hls'] : ['native'];
    if (isTs) return tsOk ? ['mpegts', 'native'] : ['native'];
    return ['native'];
  }

  function buildCandidates(item) {
    var urls = [item.url];
    if (item.altUrl) urls.push(item.altUrl);
    var out = [];
    urls.forEach(function (u) {
      enginesFor(u).forEach(function (e) { out.push({ url: u, engine: e }); });
    });
    state.candidates = out.slice(0, 6);
  }

  function stopEngines() {
    clearTimeout(state.retryTimer);
    clearTimeout(state.watchdog);
    if (state.hlsPlayer) { try { state.hlsPlayer.destroy(); } catch (_) {} state.hlsPlayer = null; }
    if (state.mpegtsPlayer) {
      try { state.mpegtsPlayer.pause(); state.mpegtsPlayer.unload(); state.mpegtsPlayer.detachMediaElement(); state.mpegtsPlayer.destroy(); } catch (_) {}
      state.mpegtsPlayer = null;
    }
    var v = dom.videoElement;
    try { v.pause(); } catch (_) {}
    v.removeAttribute('src');
    try { v.load(); } catch (_) {}
  }

  function cleanUpPlayback() {
    state.playToken++;
    state.playing = false;
    stopEngines();
  }

  function guardedPlay(token) {
    var p;
    try { p = dom.videoElement.play(); } catch (e) { handlePlaybackError(e, token); return; }
    if (p && p.catch) {
      p.catch(function (e) {
        if (e && e.name === 'AbortError') return;      // caused by a quick channel switch
        if (e && e.name === 'NotAllowedError') return;
        handlePlaybackError(e, token);
      });
    }
  }

  function runCandidate() {
    stopEngines();
    var token = ++state.playToken;
    state.playing = true;
    state.hlsNetRetries = 0;
    state.hlsMediaRetries = 0;
    var cand = state.candidates[state.candIdx];
    if (!cand) { handlePlaybackError(new Error('لا يوجد مصدر'), token); return; }

    showSpinner(true);
    hideErrorBanner();
    var video = dom.videoElement;
    dom.osdEngineBadge.textContent = cand.engine === 'native' ? 'NATIVE' : (cand.engine === 'hls' ? 'HLS.JS' : 'MPEG-TS');

    state.watchdog = setTimeout(function () {
      handlePlaybackError(new Error('انتهت مهلة بدء البث'), token);
    }, 20000);

    if (cand.engine === 'hls') {
      var hls = new Hls({ enableWorker: true, lowLatencyMode: false, backBufferLength: 30, maxBufferSize: 30 * 1000 * 1000 });
      state.hlsPlayer = hls;
      hls.on(Hls.Events.MANIFEST_PARSED, function () { guardedPlay(token); });
      hls.on(Hls.Events.ERROR, function (event, data) {
        if (token !== state.playToken || !data || !data.fatal) return;
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR && ++state.hlsNetRetries <= 3) { hls.startLoad(); return; }
        if (data.type === Hls.ErrorTypes.MEDIA_ERROR && ++state.hlsMediaRetries <= 2) { hls.recoverMediaError(); return; }
        handlePlaybackError(new Error(data.details || 'فشل تشغيل HLS'), token);
      });
      hls.loadSource(cand.url);
      hls.attachMedia(video);
    } else if (cand.engine === 'mpegts') {
      var mp = mpegts.createPlayer({ type: 'mpegts', isLive: !!state.currentItem.live, url: cand.url },
        { enableWorker: false, liveBufferLatencyChasing: true });
      state.mpegtsPlayer = mp;
      mp.on(mpegts.Events.ERROR, function (type, detail) {
        if (token !== state.playToken) return;
        handlePlaybackError(new Error(detail || type || 'فشل MPEG-TS'), token);
      });
      mp.attachMediaElement(video);
      mp.load();
      try {
        var r = mp.play();
        if (r && r.catch) r.catch(function (e) { if (!e || e.name !== 'AbortError') handlePlaybackError(e, token); });
      } catch (e) { handlePlaybackError(e, token); }
    } else {
      video.src = cand.url;
      guardedPlay(token);
    }
  }

  function handlePlaybackError(err, token) {
    if (token !== undefined && token !== state.playToken) return;   // stale event
    if (!state.playing) return;
    clearTimeout(state.watchdog);
    showSpinner(false);
    var msg = (err && err.message) || 'خطأ في الشبكة';
    state.candIdx++;

    if (state.candIdx < state.candidates.length) {
      showErrorBanner('تعذر التشغيل (' + msg + '). تجربة مصدر/محرك بديل...', state.candIdx, state.candidates.length);
      state.retryTimer = setTimeout(function () {
        if (state.playing && state.currentScreen === 'screen-player') runCandidate();
      }, 1000);
    } else if (state.rounds < state.maxRounds) {
      state.rounds++;
      state.candIdx = 0;
      var delay = Math.pow(2, state.rounds) * 1000;
      showErrorBanner('انقطع البث. إعادة المحاولة خلال ' + (delay / 1000) + ' ثوانٍ...', state.rounds, state.maxRounds);
      state.retryTimer = setTimeout(function () {
        if (state.playing && state.currentScreen === 'screen-player') runCandidate();
      }, delay);
    } else {
      state.playing = false;
      stopEngines();
      showErrorBanner('تعذر تشغيل القناة. جرّب قناة أخرى أو تحقق من حالة السيرفر والاشتراك.', state.maxRounds, state.maxRounds);
    }
  }

  function bindVideoEvents() {
    var v = dom.videoElement;
    v.addEventListener('playing', function () {
      clearTimeout(state.watchdog);
      showSpinner(false);
      hideErrorBanner();
      state.rounds = 0;
    });
    v.addEventListener('waiting', function () { if (state.playing) showSpinner(true); });
    v.addEventListener('canplay', function () { if (state.playing && !v.paused) showSpinner(false); });
    v.addEventListener('error', function () {
      if (!state.playing) return;
      var e = v.error;
      handlePlaybackError(new Error(e ? 'MediaError ' + e.code : 'خطأ في الوسائط'), state.playToken);
    });
    v.addEventListener('ended', function () {
      if (!state.playing) return;
      if (state.currentItem && state.currentItem.live) handlePlaybackError(new Error('انتهى البث'), state.playToken);
      else if (state.currentItem && state.currentItem.kind === 'episode') stepItem(1);
    });
    dom.screenPlayer.addEventListener('click', function (e) {
      if (!e.target.closest('.focusable')) showPlayerOsd();
    });
  }

  function loadEpg(item) {
    var acc = state.activeAccount;
    if (!acc || acc.type !== 'xtream') return;
    var token = state.playToken;
    xtreamApi(acc, 'get_short_epg', '&stream_id=' + encodeURIComponent(item.id) + '&limit=2').then(function (data) {
      if (token !== state.playToken || !data || !data.epg_listings || !data.epg_listings.length) return;
      var t = data.epg_listings[0].title || '';
      try { t = decodeURIComponent(escape(atob(t))); } catch (_) {}
      if (t) dom.osdEpgNow.textContent = t;
    }).catch(function () {});
  }

  function showSpinner(show) { dom.playerSpinner.classList.toggle('hidden', !show); }

  function showErrorBanner(msg, n, total) {
    dom.playerErrorMsg.textContent = msg;
    dom.retryCounterText.textContent = 'المحاولة ' + n + ' من ' + total;
    dom.playerErrorBanner.classList.remove('hidden');
  }

  function hideErrorBanner() { dom.playerErrorBanner.classList.add('hidden'); }

  function showPlayerOsd() {
    dom.playerOsd.classList.remove('hidden');
    clearTimeout(state.osdTimer);
    state.osdTimer = setTimeout(function () { dom.playerOsd.classList.add('hidden'); }, 5000);
  }

  function stepItem(offset) {
    var list = state.playList;
    if (!list || list.length < 2) return;
    if (state.currentItem && !state.currentItem.live && state.currentItem.kind !== 'episode') return;
    var next = state.playIndex + offset;
    if (next < 0) next = list.length - 1;
    if (next >= list.length) next = 0;
    var item = list[next];
    if (item.live) playLiveById(item.id, list);
    else playItem(item, list);
  }

  // ======================================================================
  // Favorites
  // ======================================================================
  function toggleFavorite(id) {
    var k = favKey(id);
    if (state.favorites.has(k)) state.favorites.delete(k); else state.favorites.add(k);
    saveFavorites();
    return state.favorites.has(k);
  }

  function toggleCurrentFavorite() {
    if (state.currentScreen === 'screen-player') {
      if (!state.currentItem || !state.currentItem.live) return;
      var now = toggleFavorite(state.currentItem.id);
      dom.osdFavIcon.classList.toggle('hidden', !now);
      showPlayerOsd();
    } else {
      var a = document.activeElement;
      if (a && a.dataset && a.dataset.action === 'play-channel') {
        var on = toggleFavorite(a.dataset.id);
        var star = a.querySelector('.ch-star');
        if (star) star.textContent = on ? '⭐' : '';
        if (state.activeNavTarget === 'favorites') renderFavoritesView();
        toast(on ? 'أُضيفت إلى المفضلة' : 'أُزيلت من المفضلة');
      }
    }
  }

  // ======================================================================
  // Remote control
  // ======================================================================
  function bindRemoteEvents() {
    window.addEventListener('keydown', handleKeyDown);
    document.addEventListener('click', function (e) {
      var btn = e.target.closest ? e.target.closest('.focusable') : null;
      if (btn) handleAction(btn);
    });
    document.addEventListener('focusin', function (e) {
      if (e.target && e.target.classList && e.target.classList.contains('focusable')) {
        state.lastFocused[state.currentScreen] = e.target;
        scheduleLazy();
      }
    });
    document.addEventListener('scroll', scheduleLazy, true);
  }

  function handleKeyDown(e) {
    var code = e.keyCode;
    var typing = isTyping();
    var modalOpen = !dom.modalPin.classList.contains('hidden');

    // PIN modal has priority
    if (modalOpen) {
      if (code === RC_KEYS.BACK_WEBOS || code === RC_KEYS.BACK_ESC) { e.preventDefault(); closePinModal(); return; }
      if (code === RC_KEYS.ENTER && typing) { e.preventDefault(); submitPin(); return; }
    }

    // Digits: only channel-jump when NOT typing into a field
    if (!typing && !modalOpen && ((code >= 48 && code <= 57) || (code >= 96 && code <= 105))) {
      if (state.currentScreen === 'screen-player' || (state.currentScreen === 'screen-dashboard' && state.activeNavTarget === 'live')) {
        handleNumericJump(String(code >= 96 ? code - 96 : code - 48));
      }
      return;
    }

    if (code === RC_KEYS.RED) { e.preventDefault(); handleBackOrExit(); return; }
    if (code === RC_KEYS.GREEN) {
      e.preventDefault();
      if (state.currentScreen === 'screen-dashboard' && state.activeAccount) connectToAccount(state.activeAccount, false);
      return;
    }
    if (code === RC_KEYS.YELLOW) { e.preventDefault(); toggleCurrentFavorite(); return; }
    if (code === RC_KEYS.BLUE) {
      e.preventDefault();
      if (state.currentScreen === 'screen-dashboard') dom.globalSearchInput.focus();
      return;
    }

    if (code === RC_KEYS.CH_UP || code === RC_KEYS.PAGE_UP) { e.preventDefault(); if (state.currentScreen === 'screen-player') stepItem(-1); return; }
    if (code === RC_KEYS.CH_DOWN || code === RC_KEYS.PAGE_DOWN) { e.preventDefault(); if (state.currentScreen === 'screen-player') stepItem(1); return; }

    if (code === RC_KEYS.BACK_WEBOS || code === RC_KEYS.BACK_ESC || code === RC_KEYS.BACKSPACE) {
      if (typing && code === RC_KEYS.BACKSPACE && document.activeElement.value.length > 0) return;
      e.preventDefault();
      handleBackOrExit();
      return;
    }

    if (state.currentScreen === 'screen-player') {
      var item = state.currentItem, live = item && item.live, v = dom.videoElement;
      if (code === RC_KEYS.UP) { e.preventDefault(); stepItem(-1); }
      else if (code === RC_KEYS.DOWN) { e.preventDefault(); stepItem(1); }
      else if (code === RC_KEYS.LEFT && !live) { e.preventDefault(); seekBy(-10); }
      else if (code === RC_KEYS.RIGHT && !live) { e.preventDefault(); seekBy(10); }
      else if (code === RC_KEYS.REWIND && !live) { e.preventDefault(); seekBy(-30); }
      else if (code === RC_KEYS.FF && !live) { e.preventDefault(); seekBy(30); }
      else if (code === RC_KEYS.ENTER) { e.preventDefault(); showPlayerOsd(); }
      else if (code === RC_KEYS.PLAY) { v.play(); }
      else if (code === RC_KEYS.PAUSE) { v.pause(); }
      else if (code === RC_KEYS.STOP) { handleBackOrExit(); }
      else { showPlayerOsd(); }
      return;
    }

    if (code === RC_KEYS.UP || code === RC_KEYS.DOWN || code === RC_KEYS.LEFT || code === RC_KEYS.RIGHT) {
      // keep caret movement inside text fields
      if (typing && (code === RC_KEYS.LEFT || code === RC_KEYS.RIGHT)) return;
      e.preventDefault();
      navigateSpatial(code);
      return;
    }

    if (code === RC_KEYS.ENTER) {
      var active = document.activeElement;
      if (active && active.id === 'global-search-input') { e.preventDefault(); runSearch(); focusFirstResult(); return; }
      if (typing) return;                      // let webOS open the on-screen keyboard
      if (active && active.classList.contains('focusable')) { e.preventDefault(); handleAction(active); }
    }
  }

  function focusFirstResult() {
    var view = document.getElementById('section-' + state.activeNavTarget);
    var first = view && view.querySelector('.channel-card, .vod-card');
    if (first) first.focus();
  }

  function seekBy(sec) {
    var v = dom.videoElement;
    if (!isFinite(v.duration)) return;
    v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + sec));
    showPlayerOsd();
  }

  function exitApp() {
    if (window.webOS && window.webOS.platformBack) window.webOS.platformBack();
    else window.close();
  }

  function handleBackOrExit() {
    if (state.currentScreen === 'screen-player') {
      cleanUpPlayback();
      hideErrorBanner();
      showSpinner(false);
      switchScreen('screen-dashboard');
    } else if (state.currentScreen === 'screen-dashboard') {
      if (state.activeNavTarget === 'series' && state.seriesMode === 'episodes') {
        renderVod('series');
      } else if (state.activeNavTarget !== 'live') {
        switchNavTarget('live');
      } else {
        switchScreen('screen-portal');
        renderSavedAccounts();
      }
    } else {
      exitApp();
    }
  }

  function handleNumericJump(digit) {
    state.numBuffer += digit;
    if (state.numBuffer.length > 5) state.numBuffer = digit;
    dom.jumpDigits.textContent = state.numBuffer;
    dom.numberJumpOsd.classList.remove('hidden');
    clearTimeout(state.numTimer);
    state.numTimer = setTimeout(function () {
      var n = parseInt(state.numBuffer, 10);
      dom.numberJumpOsd.classList.add('hidden');
      state.numBuffer = '';
      for (var i = 0; i < state.channels.length; i++) {
        if (Number(state.channels[i].num) === n) { playLiveById(state.channels[i].id, state.channels); return; }
      }
      toast('لا توجد قناة برقم ' + n);
    }, 1800);
  }

  function navigateSpatial(keyCode) {
    var scope = !dom.modalPin.classList.contains('hidden') ? dom.modalPin : document.querySelector('.screen.active');
    if (!scope) return;
    var all = scope.querySelectorAll('.focusable:not([disabled])');
    var focusables = [];
    for (var i = 0; i < all.length; i++) if (isVisible(all[i])) focusables.push(all[i]);
    if (!focusables.length) return;

    var current = document.activeElement;
    if (focusables.indexOf(current) === -1) { focusables[0].focus(); return; }

    var cr = current.getBoundingClientRect();
    var cx = cr.left + cr.width / 2, cy = cr.top + cr.height / 2;
    var best = null, bestScore = Infinity;

    for (var j = 0; j < focusables.length; j++) {
      var t = focusables[j];
      if (t === current) continue;
      var r = t.getBoundingClientRect();
      var dx = (r.left + r.width / 2) - cx, dy = (r.top + r.height / 2) - cy;
      var primary, secondary;
      if (keyCode === RC_KEYS.RIGHT && dx > 4) { primary = dx; secondary = Math.abs(dy); }
      else if (keyCode === RC_KEYS.LEFT && dx < -4) { primary = -dx; secondary = Math.abs(dy); }
      else if (keyCode === RC_KEYS.DOWN && dy > 4) { primary = dy; secondary = Math.abs(dx); }
      else if (keyCode === RC_KEYS.UP && dy < -4) { primary = -dy; secondary = Math.abs(dx); }
      else continue;
      var score = primary + secondary * 3;
      if (score < bestScore) { bestScore = score; best = t; }
    }

    if (best) {
      best.focus();
      if (best.scrollIntoViewIfNeeded) best.scrollIntoViewIfNeeded(false);
      else best.scrollIntoView(false);
      scheduleLazy();
    }
  }

  // ======================================================================
  // Actions
  // ======================================================================
  function handleAction(el) {
    var now = Date.now();
    if (state.lastActionEl === el && now - state.lastActionAt < 250) return;   // de-dupe key + click
    state.lastActionEl = el; state.lastActionAt = now;

    var action = el.dataset.action, i;
    if (!action) return;

    switch (action) {
      case 'portal-tab':
        var tabs = document.querySelectorAll('.tab-btn');
        for (i = 0; i < tabs.length; i++) tabs[i].classList.remove('active');
        el.classList.add('active');
        var forms = document.querySelectorAll('.tab-form');
        for (i = 0; i < forms.length; i++) forms[i].classList.remove('active');
        document.getElementById('form-' + el.dataset.tab).classList.add('active');
        break;
      case 'toggle-pass':
        var p = document.getElementById('xtream-pass');
        p.type = p.type === 'password' ? 'text' : 'password';
        break;
      case 'login-xtream': submitXtreamLogin(); break;
      case 'login-m3u': submitM3uLogin(); break;
      case 'switch-nav': switchNavTarget(el.dataset.target); break;
      case 'select-category': selectCategory(el.dataset.kind || 'live', parseInt(el.dataset.index, 10)); break;
      case 'play-channel': playLiveById(el.dataset.id, listForCurrentView()); break;
      case 'play-movie': playMovie(el.dataset.id); break;
      case 'open-series': openSeries(el.dataset.id); break;
      case 'series-back': renderVod('series'); break;
      case 'play-episode':
        var eps = state.currentEpisodes || [];
        for (i = 0; i < eps.length; i++) if (eps[i].id === el.dataset.id) { playItem(eps[i], eps); break; }
        break;
      case 'add-new-account': switchScreen('screen-portal'); break;
      case 'connect-saved':
      case 'switch-account':
        var acc = findAccount(el.dataset.id);
        if (acc) connectToAccount(acc, false);
        break;
      case 'delete-account':
        state.accounts = state.accounts.filter(function (a) { return a.id !== el.dataset.id; });
        saveAccounts();
        if (state.activeAccount && state.activeAccount.id === el.dataset.id) {
          state.activeAccount = null;
          try { localStorage.removeItem(STORAGE_KEYS.ACTIVE_ACCOUNT); } catch (_) {}
        }
        renderAccountsManagement();
        break;
      case 'clear-history':
        state.history = state.history.filter(function (h) { return h.accId !== accId(); });
        saveHistory(); renderHistoryView();
        break;
      case 'set-engine':
        state.preferredEngine = el.dataset.engine;
        safeSet(STORAGE_KEYS.ENGINE, state.preferredEngine);
        var en = document.querySelectorAll('[data-action="set-engine"]');
        for (i = 0; i < en.length; i++) en[i].classList.toggle('active', en[i].dataset.engine === state.preferredEngine);
        dom.streamEngineBadge.textContent = engineLabel(state.preferredEngine);
        break;
      case 'set-lang':
        state.language = el.dataset.lang;
        safeSet(STORAGE_KEYS.LANG, state.language);
        document.documentElement.setAttribute('lang', state.language);
        var ln = document.querySelectorAll('[data-action="set-lang"]');
        for (i = 0; i < ln.length; i++) ln[i].classList.toggle('active', ln[i].dataset.lang === state.language);
        break;
      case 'save-parental-pin':
        var inp = document.getElementById('parental-pin-input');
        if (/^\d{4}$/.test(inp.value)) {
          state.parentalPin = inp.value; safeSet(STORAGE_KEYS.PIN, inp.value); inp.value = '';
          toast('تم حفظ رمز القفل');
        } else toast('الرمز يجب أن يكون 4 أرقام');
        break;
      case 'submit-pin': submitPin(); break;
      case 'cancel-pin': closePinModal(); break;
      case 'clear-app-cache':
        state.favorites.clear(); state.history = [];
        try { localStorage.removeItem(STORAGE_KEYS.FAVORITES); localStorage.removeItem(STORAGE_KEYS.HISTORY); } catch (_) {}
        toast('تم مسح المفضلة والسجل');
        break;
      case 'logout-current':
        cleanUpPlayback();
        state.activeAccount = null;
        resetData();
        try { localStorage.removeItem(STORAGE_KEYS.ACTIVE_ACCOUNT); } catch (_) {}
        switchScreen('screen-portal');
        renderSavedAccounts();
        break;
    }
  }

  function listForCurrentView() {
    if (state.activeNavTarget === 'favorites') return state.channels.filter(function (c) { return isFav(c.id); });
    if (state.activeNavTarget === 'history') return historyChannels();
    return state.filteredChannels;
  }

  function historyChannels() {
    var out = [];
    state.history.forEach(function (h) {
      if (h.accId === accId() && state.channelMap[h.id]) out.push(state.channelMap[h.id]);
    });
    return out;
  }

  function findAccount(id) {
    for (var i = 0; i < state.accounts.length; i++) if (state.accounts[i].id === id) return state.accounts[i];
    return null;
  }

  // ======================================================================
  // Forms & account lists
  // ======================================================================
  function submitXtreamLogin() {
    var host = document.getElementById('xtream-host').value.trim();
    var user = document.getElementById('xtream-user').value.trim();
    var pass = document.getElementById('xtream-pass').value.trim();
    var label = document.getElementById('xtream-label').value.trim() || user;

    if (!host || !user || !pass) { showPortalStatus('يرجى ملء جميع الحقول المطلوبة', true); return; }
    if (!/^https?:\/\//i.test(host)) host = 'http://' + host;
    host = host.replace(/\/+$/, '').replace(/\/player_api\.php.*$/i, '');

    var existing = null;
    state.accounts.forEach(function (a) { if (a.type === 'xtream' && a.host === host && a.user === user) existing = a; });
    var account = existing || { id: 'acc_' + Date.now(), type: 'xtream', name: label, host: host, user: user, pass: pass };
    account.pass = pass; account.name = label;
    connectToAccount(account, true);
  }

  function submitM3uLogin() {
    var url = document.getElementById('m3u-url').value.trim();
    var label = document.getElementById('m3u-label').value.trim() || 'قائمة M3U';
    if (!url) { showPortalStatus('يرجى إدخال رابط ملف M3U', true); return; }
    var existing = null;
    state.accounts.forEach(function (a) { if (a.type === 'm3u' && a.url === url) existing = a; });
    var account = existing || { id: 'acc_' + Date.now(), type: 'm3u', name: label, url: url };
    account.name = label;
    connectToAccount(account, true);
  }

  function saveNewAccount(acc) {
    state.accounts = [acc].concat(state.accounts.filter(function (a) { return a.id !== acc.id; }));
    saveAccounts();
  }

  function renderSavedAccounts() {
    if (!state.accounts.length) { dom.savedAccountsSection.classList.add('hidden'); return; }
    dom.savedAccountsSection.classList.remove('hidden');
    dom.savedAccountsList.innerHTML = '';
    state.accounts.forEach(function (acc) {
      var btn = document.createElement('button');
      btn.className = 'btn-sub focusable';
      btn.dataset.action = 'connect-saved';
      btn.dataset.id = acc.id;
      btn.textContent = '📺 ' + acc.name + ' (' + acc.type.toUpperCase() + ')';
      dom.savedAccountsList.appendChild(btn);
    });
  }

  function renderFavoritesView() {
    var list = state.channels.filter(function (c) { return isFav(c.id); });
    document.getElementById('favorites-count-badge').textContent = list.length + ' قناة';
    document.getElementById('favorites-empty-msg').classList.toggle('hidden', list.length > 0);
    mountList(dom.favGrid, list, function (ch) { return buildLiveCard(ch, 'قناة مفضلة'); }, 60);
  }

  function renderHistoryView() {
    var list = historyChannels();
    document.getElementById('history-empty-msg').classList.toggle('hidden', list.length > 0);
    mountList(dom.histGrid, list, function (ch) { return buildLiveCard(ch, 'شوهدت مؤخراً'); }, 60);
  }

  function renderAccountsManagement() {
    var list = document.getElementById('accounts-management-list');
    list.innerHTML = '';
    state.accounts.forEach(function (acc) {
      var isCur = state.activeAccount && state.activeAccount.id === acc.id;
      var box = document.createElement('div');
      box.className = 'account-box' + (isCur ? ' active' : '');
      box.innerHTML =
        '<h3>' + esc(acc.name) + '</h3>' +
        '<p class="setting-hint">' + esc(acc.type === 'xtream' ? acc.host : acc.url) + '</p>' +
        '<div style="display:flex;gap:10px;margin-top:10px;">' +
        '<button class="btn-main-action focusable" style="height:48px;font-size:18px;" data-action="switch-account" data-id="' + esc(acc.id) + '">' +
        (isCur ? '✓ الحساب النشط' : 'تبديل لهذا الحساب') + '</button>' +
        '<button class="btn-danger focusable" style="padding:10px;" data-action="delete-account" data-id="' + esc(acc.id) + '">حذف</button>' +
        '</div>';
      list.appendChild(box);
    });
  }

  function showPortalStatus(msg, isErr) {
    if (!dom.portalStatus) return;
    dom.portalStatus.textContent = msg;
    dom.portalStatus.style.color = isErr ? '#FF5252' : '#FFD54F';
  }

  // ======================================================================
  // Network & webOS lifecycle
  // ======================================================================
  function bindNetworkListeners() {
    window.addEventListener('offline', function () { dom.networkOfflineBanner.classList.remove('hidden'); });
    window.addEventListener('online', function () {
      dom.networkOfflineBanner.classList.add('hidden');
      if (state.currentScreen === 'screen-player' && state.currentItem) {
        state.rounds = 0; state.candIdx = 0; state.playing = true;
        runCandidate();
      }
    });
  }

  function bindLifecycle() {
    // Required because appinfo.json has handlesRelaunch:true
    document.addEventListener('webOSRelaunch', function () {
      if (window.PalmSystem && window.PalmSystem.activate) window.PalmSystem.activate();
    });
    // Stop streaming while the app is in background, resume afterwards
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        if (state.playing && state.currentScreen === 'screen-player') {
          state.resumeItem = state.currentItem;
          cleanUpPlayback();
        }
      } else if (state.resumeItem && state.currentScreen === 'screen-player') {
        var it = state.resumeItem; state.resumeItem = null;
        playItem(it, state.playList);
      }
    });
  }

})();
