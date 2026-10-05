package com.example.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.model.ChannelEpg
import com.example.data.model.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.LiveCategory
import com.example.data.model.LiveChannel
import com.example.ui.components.TvTouchButton
import com.example.ui.viewmodel.IptvViewModel

private val ColorThemeNeonGreen = Color(0xFF00E676)
private val ColorThemeDarkBg = Color(0xFF050B08)
private val ColorThemeSurface = Color(0xFF0C1611)
private val ColorThemeSurfaceFocused = Color(0xFF0A2618)
private val ColorThemeBorder = Color(0xFF14271E)

@Composable
fun LiveTvScreen(
    viewModel: IptvViewModel,
    onPlayChannel: (channel: LiveChannel) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val categories by viewModel.liveCategories.collectAsState()
    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()
    val channels by viewModel.liveChannels.collectAsState()
    val selectedChannel by viewModel.selectedLiveChannel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLiveChannelsLoading by viewModel.isLiveChannelsLoading.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val strings by viewModel.appText.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val categoriesListState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.liveCategoriesScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.liveCategoriesScrollOffset
    )
    val channelsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.liveChannelsScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.liveChannelsScrollOffset
    )

    LaunchedEffect(categoriesListState) {
        snapshotFlow {
            categoriesListState.firstVisibleItemIndex to categoriesListState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.liveCategoriesScrollIndex = index
            viewModel.liveCategoriesScrollOffset = offset
        }
    }

    LaunchedEffect(channelsListState) {
        snapshotFlow {
            channelsListState.firstVisibleItemIndex to channelsListState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.liveChannelsScrollIndex = index
            viewModel.liveChannelsScrollOffset = offset
        }
    }

    val backFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val categoriesFocusRequester = remember { FocusRequester() }
    val channelsFocusRequester = remember { FocusRequester() }

    // Auto-select first category only if none selected
    LaunchedEffect(categories) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            viewModel.selectLiveCategory(categories.first(), resetScroll = false)
        }
    }

    val epgMap by viewModel.epgMap.collectAsState()
    val selectedChannelEpg by viewModel.selectedChannelEpg.collectAsState()
    val isEpgLoading by viewModel.isEpgLoading.collectAsState()

    val displayedChannels = remember(channels, searchQuery) {
        if (searchQuery.isBlank()) {
            channels
        } else {
            channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val selectedCategoryIndex = remember(categories, selectedCategory) {
        if (selectedCategory != null) {
            val idx = categories.indexOfFirst { it.categoryId == selectedCategory!!.categoryId }
            if (idx >= 0) idx else 0
        } else 0
    }

    val selectedChannelIndex = remember(displayedChannels, selectedChannel) {
        if (selectedChannel != null) {
            val idx = displayedChannels.indexOfFirst { it.streamId == selectedChannel!!.streamId }
            if (idx >= 0) idx else 0
        } else 0
    }

    // Scroll to position & focus on entry
    LaunchedEffect(Unit) {
        if (viewModel.liveChannelsScrollIndex > 0) {
            try {
                channelsListState.scrollToItem(viewModel.liveChannelsScrollIndex, viewModel.liveChannelsScrollOffset)
            } catch (_: Exception) {}
        } else if (selectedChannelIndex > 0) {
            try {
                channelsListState.scrollToItem(selectedChannelIndex)
            } catch (_: Exception) {}
        }
        delay(100)
        try {
            channelsFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(displayedChannels) {
        if (displayedChannels.isNotEmpty()) {
            viewModel.preloadEpgForChannels(displayedChannels)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var numberInputBuffer by remember { mutableStateOf("") }
    var numberTuneJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorThemeDarkBg)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    val digit = when (keyCode) {
                        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (keyCode - KeyEvent.KEYCODE_0).toString()
                        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> (keyCode - KeyEvent.KEYCODE_NUMPAD_0).toString()
                        else -> ""
                    }
                    if (digit.isNotEmpty() && numberInputBuffer.length < 5) {
                        numberInputBuffer += digit
                        numberTuneJob?.cancel()
                        numberTuneJob = coroutineScope.launch {
                            delay(650)
                            val target = numberInputBuffer.toIntOrNull()
                            if (target != null) {
                                val matched = viewModel.playChannelByNumber(target)
                                if (matched != null) {
                                    val idx = displayedChannels.indexOfFirst { it.streamId == matched.streamId }
                                    if (idx >= 0) {
                                        try { channelsListState.animateScrollToItem(idx) } catch (_: Exception) {}
                                    }
                                    onPlayChannel(matched)
                                }
                            }
                            numberInputBuffer = ""
                        }
                        true
                    } else if (numberInputBuffer.isNotEmpty() && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                        numberTuneJob?.cancel()
                        val target = numberInputBuffer.toIntOrNull()
                        if (target != null) {
                            val matched = viewModel.playChannelByNumber(target)
                            if (matched != null) {
                                onPlayChannel(matched)
                            }
                        }
                        numberInputBuffer = ""
                        true
                    } else false
                } else false
            }
            .testTag("live_tv_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ========================================================
            // PANE 1: LEFT SIDEBAR (Larger Categories & Controls)
            // ========================================================
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back Button: "← Back" (Enlarged)
                val backInteraction = remember { MutableInteractionSource() }
                val isBackFocused by backInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(backFocusRequester)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isBackFocused) ColorThemeNeonGreen else ColorThemeSurface)
                        .border(
                            1.4.dp,
                            if (isBackFocused) Color.White else ColorThemeBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .focusable(interactionSource = backInteraction)
                        .clickable(interactionSource = backInteraction, indication = null) { onBack() }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_BUTTON_A -> {
                                        onBack()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        searchFocusRequester.requestFocus()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        channelsFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isBackFocused) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Back",
                            color = if (isBackFocused) Color.Black else Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Search Channel Input (Enlarged)
                val searchInteraction = remember { MutableInteractionSource() }
                val isSearchFocused by searchInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(searchFocusRequester)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ColorThemeSurface)
                        .border(
                            1.4.dp,
                            if (isSearchFocused) ColorThemeNeonGreen else ColorThemeBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchFocused) ColorThemeNeonGreen else Color(0xFF6B7280),
                            modifier = Modifier.size(20.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search channel...",
                                    color = Color(0xFF6B7280),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(ColorThemeNeonGreen),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Categories Vertical List (Enlarged Cards)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (categories.isEmpty() && isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ColorThemeNeonGreen, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                    } else {
                        LazyColumn(
                            state = categoriesListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = categories,
                                key = { _, cat -> cat.categoryId },
                                contentType = { _, _ -> "category_card" }
                            ) { index, cat ->
                                val isSelected = selectedCategory?.categoryId == cat.categoryId
                                SidebarCategoryCardEnlarged(
                                    category = cat,
                                    isSelected = isSelected,
                                    modifier = if (index == selectedCategoryIndex) Modifier.focusRequester(categoriesFocusRequester) else Modifier,
                                    onSelect = {
                                        viewModel.selectLiveCategory(cat, resetScroll = true)
                                    },
                                    onNavigateRight = {
                                        viewModel.selectLiveCategory(cat, resetScroll = false)
                                        try {
                                            channelsFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ========================================================
            // PANE 2: MIDDLE CHANNELS LIST (Enlarged Channel Cards)
            // ========================================================
            Column(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Category Header: Green Vertical Bar + Category Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ColorThemeNeonGreen)
                    )
                    Text(
                        text = selectedCategory?.categoryName ?: "All Channels",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${displayedChannels.size} channels",
                        color = ColorThemeNeonGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Channels LazyColumn (Enlarged Cards)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isLiveChannelsLoading && displayedChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ColorThemeNeonGreen, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        }
                    } else if (displayedChannels.isEmpty() && !isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = errorMsg ?: strings.noChannels,
                                    color = if (errorMsg != null) Color(0xFFFF8888) else Color(0xFF6B7280),
                                    fontSize = 15.sp,
                                    fontWeight = if (errorMsg != null) FontWeight.Medium else FontWeight.Normal
                                )
                                if (errorMsg != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ColorThemeNeonGreen)
                                            .clickable {
                                                viewModel.activeAccount.value?.let { viewModel.loadAccountContent(it) }
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(text = "إعادة المحاولة", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = channelsListState,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = displayedChannels,
                                key = { _, it -> it.streamId },
                                contentType = { _, _ -> "channel_row" }
                            ) { index, ch ->
                                val isSelected = selectedChannel?.streamId == ch.streamId
                                val isFav = viewModel.isChannelFavorite(ch.streamId)
                                val displayName = viewModel.getChannelDisplayName(ch)
                                val chEpg = epgMap[ch.streamId]

                                ChannelRowCardEnlarged(
                                    channel = ch,
                                    displayName = displayName,
                                    epg = chEpg,
                                    isSelected = isSelected,
                                    isFavorite = isFav,
                                    modifier = if (index == selectedChannelIndex) Modifier.focusRequester(channelsFocusRequester) else Modifier,
                                    onFocus = { viewModel.selectLiveChannel(ch) },
                                    onClick = {
                                        viewModel.selectLiveChannel(ch)
                                        onPlayChannel(ch)
                                    },
                                    onToggleFav = { viewModel.toggleChannelFavorite(ch) },
                                    onDpadLeft = {
                                        try {
                                            categoriesFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ========================================================
            // PANE 3: RIGHT CHANNEL DETAILS & EPG INFO
            // ========================================================
            Box(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ColorThemeSurface)
                    .border(1.2.dp, ColorThemeBorder, RoundedCornerShape(18.dp))
                    .padding(20.dp)
                    .testTag("live_channel_preview_pane")
            ) {
                if (selectedChannel != null) {
                    val ch = selectedChannel!!
                    val displayName = viewModel.getChannelDisplayName(ch)
                    val isFav = viewModel.isChannelFavorite(ch.streamId)
                    val selectedEpg = epgMap[ch.streamId] ?: selectedChannelEpg

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Header: Channel Logo + Number + Name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF0F1E16)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!ch.streamIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.streamIcon,
                                            contentDescription = displayName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Tv,
                                            contentDescription = null,
                                            tint = ColorThemeNeonGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "CH #${ch.num ?: ch.streamId}",
                                        color = ColorThemeNeonGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = displayName,
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // EPG Schedule Content
                            if (isEpgLoading && selectedEpg == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            color = ColorThemeNeonGreen,
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            text = "جاري تحميل جدول البرامج (EPG)...",
                                            color = Color(0xFF90A4AE),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            } else if (selectedEpg?.currentProgram != null || selectedEpg?.listings?.isNotEmpty() == true) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // 1. Current program card
                                    val currentProg = selectedEpg.currentProgram
                                    if (currentProg != null) {
                                        val startTime = formatEpgTime(currentProg.startTimestamp, currentProg.start)
                                        val endTime = formatEpgTime(currentProg.stopTimestamp, currentProg.end)
                                        val timeRange = if (startTime.isNotEmpty() && endTime.isNotEmpty()) "$startTime - $endTime" else startTime

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF0D2418))
                                                .border(1.dp, ColorThemeNeonGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(ColorThemeNeonGreen)
                                                )
                                                Text(
                                                    text = "البرنامج المعروض الآن",
                                                    color = ColorThemeNeonGreen,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (timeRange.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    Text(timeRange, color = Color(0xFFA5D6A7), fontSize = 11.5.sp)
                                                }
                                            }
                                            Text(
                                                text = currentProg.title,
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (currentProg.progress > 0f) {
                                                LinearProgressIndicator(
                                                    progress = { currentProg.progress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .clip(RoundedCornerShape(2.dp)),
                                                    color = ColorThemeNeonGreen,
                                                    trackColor = Color(0xFF1E3A2B)
                                                )
                                            }
                                            if (!currentProg.description.isNullOrBlank()) {
                                                Text(
                                                    text = currentProg.description,
                                                    color = Color(0xFFB0BEC5),
                                                    fontSize = 12.sp,
                                                    maxLines = 3,
                                                    lineHeight = 16.sp,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // 2. Upcoming programs list
                                    val upcomingList = selectedEpg.listings.filter { it != selectedEpg.currentProgram }.take(4)
                                    if (upcomingList.isNotEmpty()) {
                                        Text(
                                            text = "البرامج القادمة",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(5.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        ) {
                                            items(upcomingList.size) { idx ->
                                                val prog = upcomingList[idx]
                                                val progTime = formatEpgTime(prog.startTimestamp, prog.start)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF091611))
                                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (progTime.isNotEmpty()) {
                                                        Text(
                                                            text = progTime,
                                                            color = ColorThemeNeonGreen,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    Text(
                                                        text = prog.title,
                                                        color = Color(0xFFECEFF1),
                                                        fontSize = 12.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (prog.durationMinutes > 0) {
                                                        Text(
                                                            text = "${prog.durationMinutes}m",
                                                            color = Color(0xFF78909C),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(ColorThemeNeonGreen)
                                        )
                                        Text(
                                            text = "البث المباشر (Live TV)",
                                            color = ColorThemeNeonGreen,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "دليل البرامج غير متوفر من مزود البث لهذه القناة حالياً.",
                                        color = Color(0xFF90A4AE),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }

                        // Bottom Actions: Play Channel Button
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvTouchButton(
                                text = strings.playChannel,
                                icon = Icons.Default.PlayArrow,
                                isPrimary = true,
                                onClick = { onPlayChannel(ch) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                testTag = "btn_play_channel"
                            )

                            TvTouchButton(
                                text = if (isFav) strings.favorite else strings.addToFavorite,
                                icon = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                isPrimary = false,
                                onClick = { viewModel.toggleChannelFavorite(ch) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                testTag = "btn_fav_channel"
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = strings.selectChannelPrompt,
                            color = Color(0xFF6B7280),
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        if (numberInputBuffer.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.5.dp, ColorThemeNeonGreen, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "CH: $numberInputBuffer -",
                    color = ColorThemeNeonGreen,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * Enlarged Category Item Card (Height 56dp, Font 16sp)
 */
@Composable
private fun SidebarCategoryCardEnlarged(
    category: LiveCategory,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onNavigateRight: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val isAll = category.categoryId == IptvViewModel.ID_ALL
    val isFav = category.categoryId == IptvViewModel.ID_FAVORITES
    val isRecent = category.categoryId == IptvViewModel.ID_RECENTS

    val active = isFocused || isSelected

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) ColorThemeSurfaceFocused else ColorThemeSurface)
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.4.dp else 1.dp,
                color = if (active) ColorThemeNeonGreen else ColorThemeBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onSelect() }
            .focusable(interactionSource = interaction)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onSelect()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onNavigateRight()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (isAll) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = ColorThemeNeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isFav) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = ColorThemeNeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isRecent) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = ColorThemeNeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = category.categoryName,
                    color = if (active) Color.White else Color(0xFFD1D5DB),
                    fontSize = 16.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatEpgTime(timestampSec: Long, fallbackStr: String?): String {
    if (timestampSec > 0) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampSec * 1000L))
    }
    if (!fallbackStr.isNullOrBlank()) {
        val parts = fallbackStr.trim().split(" ")
        if (parts.size >= 2) {
            val timePart = parts[1]
            val timeParts = timePart.split(":")
            if (timeParts.size >= 2) {
                return "${timeParts[0]}:${timeParts[1]}"
            }
        }
    }
    return ""
}

/**
 * Enlarged Channel Row Card with EPG (Height 84dp, Logo 52dp)
 */
@Composable
private fun ChannelRowCardEnlarged(
    channel: LiveChannel,
    displayName: String,
    epg: ChannelEpg?,
    isSelected: Boolean,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    onDpadLeft: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val active = isFocused || isSelected

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) ColorThemeSurfaceFocused else ColorThemeSurface)
            .border(
                width = if (active) 2.2.dp else 1.dp,
                color = if (active) ColorThemeNeonGreen else ColorThemeBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .focusable(interactionSource = interaction)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onDpadLeft()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Large Logo + Number & Title & EPG
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Channel Square Logo (52dp x 52dp)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F1E16)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.streamIcon.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(channel.streamIcon)
                                .size(128, 128)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build(),
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = ColorThemeNeonGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Channel Info Column with EPG
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Channel Number + Channel Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "#${channel.num ?: channel.streamId}",
                            color = ColorThemeNeonGreen,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Current Program
                    val currentProg = epg?.currentProgram
                    if (currentProg != null) {
                        val curTime = formatEpgTime(currentProg.startTimestamp, currentProg.start)
                        val timeLabel = if (curTime.isNotEmpty()) "[$curTime] " else ""
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ColorThemeNeonGreen)
                            )
                            Text(
                                text = "الآن: $timeLabel${currentProg.title}",
                                color = Color(0xFFE0E0E0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (currentProg.progress > 0f) {
                            LinearProgressIndicator(
                                progress = { currentProg.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 12.dp, top = 1.dp, bottom = 1.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = ColorThemeNeonGreen,
                                trackColor = Color(0xFF1E3A2B)
                            )
                        }
                    } else {
                        Text(
                            text = "البث المباشر (Live TV)",
                            color = Color(0xFF757575),
                            fontSize = 11.5.sp,
                            maxLines = 1
                        )
                    }

                    // Upcoming Program
                    val upcomingProg = epg?.upcomingProgram
                    if (upcomingProg != null) {
                        val nextTime = formatEpgTime(upcomingProg.startTimestamp, upcomingProg.start)
                        val timePrefix = if (nextTime.isNotEmpty()) "[$nextTime] " else ""
                        Text(
                            text = "التالي: $timePrefix${upcomingProg.title}",
                            color = Color(0xFF9E9E9E),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right: Heart Icon for Favorite
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onToggleFav() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) ColorThemeNeonGreen else Color(0xFF6B7280),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

