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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SeriesDetail
import com.example.data.model.SeriesEpisode
import com.example.data.model.SeriesItem
import com.example.data.repository.IptvResult
import com.example.ui.theme.TvAccentGold
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceHighlight
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.viewmodel.IptvViewModel

@Composable
fun SeriesDetailScreen(
    seriesItem: SeriesItem,
    viewModel: IptvViewModel,
    onPlayEpisode: (episode: SeriesEpisode, seriesTitle: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var seriesDetail by remember { mutableStateOf<SeriesDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(seriesItem.seriesId) {
        isLoading = true
        when (val res = viewModel.fetchSeriesDetails(seriesItem.seriesId)) {
            is IptvResult.Success -> {
                seriesDetail = res.data
                isLoading = false
            }
            is IptvResult.Error -> {
                errorMessage = res.message
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 32.dp, vertical = 20.dp)
            .testTag("series_detail_screen")
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = TvAccentGold, modifier = Modifier.size(44.dp))
                    Text(text = "جارٍ تحميل مواسم وحلقات المسلسل...", color = TvTextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            val detail = seriesDetail ?: SeriesDetail(
                seriesId = seriesItem.seriesId,
                name = seriesItem.name,
                cover = seriesItem.cover,
                plot = seriesItem.plot,
                genre = seriesItem.genre,
                releaseDate = seriesItem.releaseDate,
                rating = seriesItem.rating
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(TvSurfaceHighlight)
                            .clickable { onBack() }
                            .testTag("btn_back_from_series_detail"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = TvTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = detail.name,
                        color = TvTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Main Content: Left Poster & Meta, Right Seasons & Episodes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Column: Series Info & Poster Card
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Poster
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B2230)),
                                contentAlignment = Alignment.Center
                            ) {
                                val coverUrl = detail.cover ?: seriesItem.cover
                                if (!coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = detail.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = TvTextMuted,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }

                            // Meta Pills: Rating, Genre, Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val rating = detail.rating ?: seriesItem.rating
                                if (!rating.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E2616))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = TvAccentGold, modifier = Modifier.size(12.dp))
                                        Text(text = rating, color = TvAccentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                val release = detail.releaseDate ?: seriesItem.releaseDate
                                if (!release.isNullOrBlank()) {
                                    Text(text = release, color = TvTextSecondary, fontSize = 11.sp)
                                }
                            }

                            val genre = detail.genre ?: seriesItem.genre
                            if (!genre.isNullOrBlank()) {
                                Text(
                                    text = "التصنيف: $genre",
                                    color = TvTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            val cast = detail.cast
                            if (!cast.isNullOrBlank()) {
                                Text(
                                    text = "طاقم العمل: $cast",
                                    color = TvTextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Story / Plot
                            val plot = detail.plot ?: seriesItem.plot
                            if (!plot.isNullOrBlank()) {
                                Text(text = "القصة:", color = TvAccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = plot,
                                    color = TvTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    // Right Column: Seasons Picker + Episodes List
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        val seasons = detail.seasons
                        if (seasons.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = TvTextMuted, modifier = Modifier.size(40.dp))
                                    Text(text = "لم يتم العثور على حلقات مسجلة لهذا المسلسل", color = TvTextMuted, fontSize = 13.sp)
                                }
                            }
                        } else {
                            val activeSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.first()
                            val episodes = activeSeason.episodes

                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Seasons Row Tabs
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(seasons.size) { idx ->
                                        val season = seasons[idx]
                                        val isSelected = selectedSeasonIndex == idx
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isFocused by interactionSource.collectIsFocusedAsState()

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected || isFocused) TvSurfaceHighlight else Color(0xFF1E2430)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected || isFocused) TvAccentGold else TvBorder,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null
                                                ) {
                                                    selectedSeasonIndex = idx
                                                }
                                                .focusable(interactionSource = interactionSource)
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = season.name,
                                                color = if (isSelected || isFocused) TvAccentGold else TvTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Episodes Grid / List
                                if (episodes.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(text = "لا توجد حلقات متاحة في هذا الموسم", color = TvTextMuted, fontSize = 13.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 12.dp)
                                    ) {
                                        items(episodes, key = { it.id }) { episode ->
                                            EpisodeItemRow(
                                                episode = episode,
                                                seriesTitle = detail.name,
                                                onPlay = { onPlayEpisode(episode, detail.name) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeItemRow(
    episode: SeriesEpisode,
    seriesTitle: String,
    onPlay: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TvSurfaceHighlight else Color(0xFF1A212B))
            .border(
                width = 1.dp,
                color = if (isFocused) TvAccentGold else TvBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onPlay() }
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onPlay()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) TvAccentGold else Color(0xFF263242)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "تشغيل",
                    tint = if (isFocused) TvBackground else TvAccentGold,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "الحلقة ${episode.episodeNum}: ${episode.title}",
                    color = if (isFocused) TvAccentGold else TvTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!episode.info.isNullOrBlank()) {
                    Text(
                        text = episode.info,
                        color = TvTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF242C38))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = episode.containerExtension.uppercase(),
                color = TvTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
