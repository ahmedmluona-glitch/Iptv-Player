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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.data.model.VodDetail
import com.example.data.model.VodMovie
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
fun MovieDetailScreen(
    movie: VodMovie,
    viewModel: IptvViewModel,
    onPlayMovie: (vodDetail: VodDetail) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var vodDetail by remember { mutableStateOf<VodDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(movie.streamId) {
        isLoading = true
        when (val res = viewModel.fetchVodDetails(movie.streamId)) {
            is IptvResult.Success -> {
                vodDetail = res.data
                isLoading = false
            }
            is IptvResult.Error -> {
                isLoading = false
            }
        }
    }

    val detail = vodDetail ?: VodDetail(
        streamId = movie.streamId,
        name = movie.name,
        cover = movie.streamIcon,
        rating = movie.rating,
        containerExtension = movie.containerExtension ?: "mp4"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 32.dp, vertical = 20.dp)
            .testTag("movie_detail_screen")
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = TvAccentGold, modifier = Modifier.size(44.dp))
                    Text(text = "جارٍ تحميل تفاصيل الفيلم...", color = TvTextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header Row
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
                            .testTag("btn_back_from_movie_detail"),
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

                // Main Details Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Left Poster Card
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val coverUrl = detail.cover ?: movie.streamIcon
                        if (!coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = detail.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = TvTextMuted,
                                modifier = Modifier.size(60.dp)
                            )
                        }
                    }

                    // Right Content & Metadata
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(14.dp))
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = detail.name,
                                color = TvTextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Metadata Badges (Rating, Duration, Year, Format)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val rating = detail.rating ?: movie.rating
                                if (!rating.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF2E2616))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = TvAccentGold, modifier = Modifier.size(13.dp))
                                        Text(text = rating, color = TvAccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (!detail.duration.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1E2633))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, tint = TvTextSecondary, modifier = Modifier.size(13.dp))
                                        Text(text = "${detail.duration} دقيقة", color = TvTextSecondary, fontSize = 12.sp)
                                    }
                                }

                                if (!detail.releaseDate.isNullOrBlank()) {
                                    Text(text = detail.releaseDate ?: "", color = TvTextSecondary, fontSize = 12.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF232B38))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = detail.containerExtension.uppercase(),
                                        color = TvAccentGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Genre, Cast, Director
                            if (!detail.genre.isNullOrBlank()) {
                                Text(
                                    text = "التصنيف: ${detail.genre}",
                                    color = TvTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                            if (!detail.director.isNullOrBlank()) {
                                Text(
                                    text = "المخرج: ${detail.director}",
                                    color = TvTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            if (!detail.cast.isNullOrBlank()) {
                                Text(
                                    text = "الممثلون: ${detail.cast}",
                                    color = TvTextMuted,
                                    fontSize = 12.sp
                                )
                            }

                            // Plot Story
                            if (!detail.plot.isNullOrBlank()) {
                                Text(text = "قصة الفيلم:", color = TvAccentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = detail.plot ?: "",
                                    color = TvTextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Play Button (Remote focused)
                            val playInteractionSource = remember { MutableInteractionSource() }
                            val isPlayFocused by playInteractionSource.collectIsFocusedAsState()

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isPlayFocused) TvAccentGold else Color(0xFFD4AF37))
                                    .clickable(
                                        interactionSource = playInteractionSource,
                                        indication = null
                                    ) { onPlayMovie(detail) }
                                    .focusable(interactionSource = playInteractionSource)
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                            (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                                        ) {
                                            onPlayMovie(detail)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    .padding(horizontal = 28.dp, vertical = 14.dp)
                                    .testTag("btn_play_movie_now"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "تشغيل الآن",
                                    tint = TvBackground,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = "مشاهدة الفيلم الآن",
                                    color = TvBackground,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
