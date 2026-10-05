package com.example.ui.components

import android.view.KeyEvent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LiveChannel
import com.example.data.model.SeriesItem
import com.example.data.model.VodMovie
import com.example.ui.theme.TvAccentEmerald
import com.example.ui.theme.TvAccentGold
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceHighlight
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.viewmodel.IptvViewModel
import kotlinx.coroutines.delay

@Composable
fun GlobalSearchDialog(
    viewModel: IptvViewModel,
    onPlayChannel: (LiveChannel) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onSelectSeries: (SeriesItem) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val allChannels by viewModel.allLiveChannels.collectAsState()
    val allMovies by viewModel.vodMovies.collectAsState()
    val allSeries by viewModel.seriesList.collectAsState()

    var filteredChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var filteredMovies by remember { mutableStateOf<List<VodMovie>>(emptyList()) }
    var filteredSeries by remember { mutableStateOf<List<SeriesItem>>(emptyList()) }

    val searchInputFocus = remember { FocusRequester() }

    // 23. Debounce 300ms on search query
    LaunchedEffect(searchQuery, allChannels, allMovies, allSeries) {
        if (searchQuery.trim().length >= 2) {
            isSearching = true
            delay(300) // 300ms debounce
            val q = searchQuery.trim().lowercase()
            filteredChannels = allChannels.filter { it.name.lowercase().contains(q) }
            filteredMovies = allMovies.filter { it.name.lowercase().contains(q) }
            filteredSeries = allSeries.filter { it.name.lowercase().contains(q) }
            isSearching = false
        } else {
            filteredChannels = emptyList()
            filteredMovies = emptyList()
            filteredSeries = emptyList()
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            searchInputFocus.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(32.dp)
                .testTag("global_search_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Search Input Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    val isBackFocused by backInteraction.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isBackFocused) TvAccentEmerald else TvSurfaceHighlight)
                            .border(1.dp, if (isBackFocused) Color.White else TvBorder, CircleShape)
                            .clickable(interactionSource = backInteraction, indication = null) { onDismiss() }
                            .focusable(interactionSource = backInteraction)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "إغلاق",
                            tint = if (isBackFocused) Color.Black else Color.White
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث في كافة القنوات والأفلام والمسلسلات (بحث شامل)...", color = TvTextMuted, fontSize = 16.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TvAccentEmerald)
                        },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = TvAccentEmerald,
                                    strokeWidth = 2.dp
                                )
                            } else if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "مسح",
                                    tint = TvTextMuted,
                                    modifier = Modifier.clickable { searchQuery = "" }
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchInputFocus),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TvAccentEmerald,
                            unfocusedBorderColor = TvBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                // Results Container
                val totalResults = filteredChannels.size + filteredMovies.size + filteredSeries.size
                if (searchQuery.trim().length < 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TvTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "اكتب حرفين على الأقل لبدء البحث التلقائي الفوري",
                                color = TvTextSecondary,
                                fontSize = 15.sp
                            )
                        }
                    }
                } else if (totalResults == 0 && !isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد نتائج مطابقة لـ \"$searchQuery\"",
                            color = TvTextMuted,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Live Channels Section
                        if (filteredChannels.isNotEmpty()) {
                            item {
                                SearchCategoryHeader(
                                    title = "القنوات المباشرة",
                                    count = filteredChannels.size,
                                    icon = Icons.Default.LiveTv,
                                    accentColor = TvAccentEmerald
                                )
                            }
                            items(filteredChannels.take(20), key = { "ch_${it.streamId}" }) { channel ->
                                SearchResultItem(
                                    title = channel.name,
                                    subtitle = channel.categoryId ?: "قناة مباشرة",
                                    icon = Icons.Default.LiveTv,
                                    accentColor = TvAccentEmerald,
                                    onClick = {
                                        onDismiss()
                                        onPlayChannel(channel)
                                    }
                                )
                            }
                        }

                        // 2. Movies Section
                        if (filteredMovies.isNotEmpty()) {
                            item {
                                SearchCategoryHeader(
                                    title = "الأفلام (VOD)",
                                    count = filteredMovies.size,
                                    icon = Icons.Default.Movie,
                                    accentColor = TvAccentGold
                                )
                            }
                            items(filteredMovies.take(20), key = { "m_${it.streamId}" }) { movie ->
                                SearchResultItem(
                                    title = movie.name,
                                    subtitle = "فيلم • تقييم: ${movie.rating ?: "N/A"}",
                                    icon = Icons.Default.Movie,
                                    accentColor = TvAccentGold,
                                    onClick = {
                                        onDismiss()
                                        onSelectMovie(movie)
                                    }
                                )
                            }
                        }

                        // 3. Series Section
                        if (filteredSeries.isNotEmpty()) {
                            item {
                                SearchCategoryHeader(
                                    title = "المسلسلات (Series)",
                                    count = filteredSeries.size,
                                    icon = Icons.Default.Tv,
                                    accentColor = Color(0xFF64B5F6)
                                )
                            }
                            items(filteredSeries.take(20), key = { "s_${it.seriesId}" }) { series ->
                                SearchResultItem(
                                    title = series.name,
                                    subtitle = "مسلسل • تقييم: ${series.rating ?: "N/A"}",
                                    icon = Icons.Default.Tv,
                                    accentColor = Color(0xFF64B5F6),
                                    onClick = {
                                        onDismiss()
                                        onSelectSeries(series)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCategoryHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accentColor.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(text = "$count", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SearchResultItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) accentColor.copy(alpha = 0.2f) else TvSurface)
            .border(1.dp, if (isFocused) accentColor else TvBorder, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .focusable(interactionSource = interaction)
            .onKeyEvent {
                if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) accentColor else TvTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = title,
                        color = if (isFocused) Color.White else TvTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = TvTextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Text(
                text = "تشغيل ◀",
                color = if (isFocused) accentColor else Color.Transparent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
