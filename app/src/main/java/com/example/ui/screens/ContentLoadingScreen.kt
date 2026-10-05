package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.IptvViewModel
import kotlinx.coroutines.delay

private val ColorLoadingDarkBg = Color(0xFF050B08)
private val ColorGreenNeon = Color(0xFF00E676)

enum class LoadingCategoryType {
    LIVE_TV,
    FILMS,
    SERIES
}

/**
 * Dedicated Loading Screen: ONLY a spinning circle without any other elements.
 * Fast check against preloaded content for immediate transition without lag.
 */
@Composable
fun ContentLoadingScreen(
    type: LoadingCategoryType,
    viewModel: IptvViewModel,
    onLoaded: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val isVodLoading by viewModel.isVodLoading.collectAsState()
    val isSeriesLoading by viewModel.isSeriesLoading.collectAsState()

    val liveCats by viewModel.liveCategories.collectAsState()
    val liveChannels by viewModel.liveChannels.collectAsState()
    val vodCats by viewModel.vodCategories.collectAsState()
    val vodMovies by viewModel.vodMovies.collectAsState()
    val seriesCats by viewModel.seriesCategories.collectAsState()
    val seriesList by viewModel.seriesList.collectAsState()

    LaunchedEffect(type) {
        when (type) {
            LoadingCategoryType.LIVE_TV -> {
                if (liveCats.isEmpty() || liveChannels.isEmpty()) {
                    while (isLoading) {
                        delay(40)
                    }
                } else {
                    delay(120)
                }
            }
            LoadingCategoryType.FILMS -> {
                if (vodCats.isEmpty() || vodMovies.isEmpty()) {
                    viewModel.loadVodContentIfNeeded(forceRefresh = false)
                    while (isVodLoading) {
                        delay(40)
                    }
                } else {
                    delay(120)
                }
            }
            LoadingCategoryType.SERIES -> {
                if (seriesCats.isEmpty() || seriesList.isEmpty()) {
                    viewModel.loadSeriesContentIfNeeded(forceRefresh = false)
                    while (isSeriesLoading) {
                        delay(40)
                    }
                } else {
                    delay(120)
                }
            }
        }
        onLoaded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorLoadingDarkBg),
        contentAlignment = Alignment.Center
    ) {
        // Only rotating circle without anything else
        CircularProgressIndicator(
            color = ColorGreenNeon,
            trackColor = Color(0xFF14271E),
            modifier = Modifier.size(56.dp),
            strokeWidth = 4.dp
        )
    }
}
