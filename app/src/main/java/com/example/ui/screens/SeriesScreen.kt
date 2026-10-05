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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.example.data.model.SeriesItem
import com.example.ui.viewmodel.IptvViewModel

private val ColorThemeNeonGreen = Color(0xFF00E676)
private val ColorThemeDarkBg = Color(0xFF050B08)
private val ColorThemeSurface = Color(0xFF0C1611)
private val ColorThemeSurfaceFocused = Color(0xFF0A2618)
private val ColorThemeBorder = Color(0xFF14271E)

@Composable
fun SeriesScreen(
    viewModel: IptvViewModel,
    onSelectSeries: (SeriesItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val categories by viewModel.seriesCategories.collectAsState()
    val selectedCategory by viewModel.selectedSeriesCategory.collectAsState()
    val seriesList by viewModel.seriesList.collectAsState()
    val isSeriesLoading by viewModel.isSeriesLoading.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val strings by viewModel.appText.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Auto-select first category if none selected
    LaunchedEffect(categories) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            viewModel.selectSeriesCategory(categories.first())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSeriesContentIfNeeded(forceRefresh = false)
    }

    val displayedSeries = remember(seriesList, searchQuery) {
        if (searchQuery.isBlank()) {
            seriesList
        } else {
            seriesList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorThemeDarkBg)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .testTag("series_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // LEFT SIDEBAR (Enlarged for TV)
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBackFocused) ColorThemeNeonGreen else ColorThemeSurface)
                        .border(
                            1.5.dp,
                            if (isBackFocused) Color.White else ColorThemeBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .focusable(interactionSource = backInteraction)
                        .clickable(interactionSource = backInteraction, indication = null) { onBack() }
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
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Back",
                            color = if (isBackFocused) Color.Black else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Search Series Input (Enlarged)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorThemeSurface)
                        .border(1.2.dp, ColorThemeBorder, RoundedCornerShape(12.dp))
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
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(20.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search series...",
                                    color = Color(0xFF6B7280),
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(ColorThemeNeonGreen),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Categories List (Enlarged items)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(categories, key = { it.categoryId }) { cat ->
                            val isSelected = selectedCategory?.categoryId == cat.categoryId
                            val catInteraction = remember { MutableInteractionSource() }
                            val isCatFocused by catInteraction.collectIsFocusedAsState()
                            val active = isCatFocused || isSelected

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) ColorThemeSurfaceFocused else ColorThemeSurface)
                                    .border(
                                        width = if (isCatFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
                                        color = if (active) ColorThemeNeonGreen else ColorThemeBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .focusable(interactionSource = catInteraction)
                                    .clickable(interactionSource = catInteraction, indication = null) {
                                        viewModel.selectSeriesCategory(cat)
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cat.categoryName,
                                    color = if (active) Color.White else Color(0xFFD1D5DB),
                                    fontSize = 15.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // RIGHT: POSTER GRID with Category Header (Enlarged)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with Green Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ColorThemeNeonGreen)
                    )
                    Text(
                        text = selectedCategory?.categoryName ?: "Series",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${displayedSeries.size} series",
                        color = ColorThemeNeonGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Grid (Enlarged Poster Cards)
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isSeriesLoading && displayedSeries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ColorThemeNeonGreen, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        }
                    } else if (displayedSeries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = errorMsg ?: "لا توجد مسلسلات متوفرة في هذه الفئة",
                                    color = if (errorMsg != null) Color(0xFFFF8888) else Color(0xFF6B7280),
                                    fontSize = 15.sp,
                                    fontWeight = if (errorMsg != null) FontWeight.Medium else FontWeight.Normal
                                )
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
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 175.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayedSeries, key = { it.seriesId }) { item ->
                                SeriesPosterCard(series = item, onClick = { onSelectSeries(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesPosterCard(
    series: SeriesItem,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) ColorThemeSurfaceFocused else ColorThemeSurface)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) ColorThemeNeonGreen else ColorThemeBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F1E16)),
            contentAlignment = Alignment.Center
        ) {
            if (!series.cover.isNullOrBlank()) {
                AsyncImage(
                    model = series.cover,
                    contentDescription = series.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = ColorThemeNeonGreen,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Text(
            text = series.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
