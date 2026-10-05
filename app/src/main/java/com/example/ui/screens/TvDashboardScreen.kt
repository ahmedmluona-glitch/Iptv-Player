package com.example.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.example.data.model.LiveChannel
import com.example.data.model.SeriesItem
import com.example.data.model.VodMovie
import com.example.ui.components.GlobalSearchDialog
import com.example.ui.components.ProfileSwitcherDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.viewmodel.IptvViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Exact color palette matching the uploaded reference image
private val ColorGreenNeon = Color(0xFF00E676)           // Pure vibrant neon emerald green
private val ColorGreenCardSelected = Color(0xFF00E676)   // Selected card glowing border
private val ColorCardBgUnfocused = Color(0xFF0C1319)     // Dark charcoal glass background
private val ColorCardBgFocused = Color(0xFF082218)       // Selected dark emerald background
private val ColorCardBorderUnfocused = Color(0xFF162029) // Unfocused subtle border
private val ColorCircleBadgeBg = Color(0xFF0F261C)       // Translucent dark circle badge behind icon
private val ColorTopBtnBg = Color(0xFF0D1714)            // Top button background
private val ColorTopBtnBorder = Color(0xFF162D22)        // Top button green border

@Composable
fun TvDashboardScreen(
    viewModel: IptvViewModel,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val activeAccount by viewModel.activeAccount.collectAsState()
    val liveCount by viewModel.liveCount.collectAsState()
    val vodCount by viewModel.vodCount.collectAsState()
    val seriesCount by viewModel.seriesCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val strings by viewModel.appText.collectAsState()

    var selectedIndex by remember { mutableIntStateOf(0) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Focus requesters for TV remote navigation
    val liveTvFocusRequester = remember { FocusRequester() }
    val filmsFocusRequester = remember { FocusRequester() }
    val favoritesFocusRequester = remember { FocusRequester() }
    val seriesFocusRequester = remember { FocusRequester() }
    val addBottomFocusRequester = remember { FocusRequester() }

    val topAddBtnFocusRequester = remember { FocusRequester() }
    val topHelpBtnFocusRequester = remember { FocusRequester() }
    val topSettingsFocusRequester = remember { FocusRequester() }
    val topProfileFocusRequester = remember { FocusRequester() }

    // Ensure default focus is ON LIVE TV immediately upon entering Dashboard
    LaunchedEffect(Unit) {
        delay(80)
        try {
            liveTvFocusRequester.requestFocus()
            selectedIndex = 0
        } catch (_: Exception) {}
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Live clock and date matching phone/device system settings automatically
    var currentTimeString by remember {
        mutableStateOf(android.text.format.DateFormat.getTimeFormat(context).format(Date()))
    }
    var currentDateString by remember {
        mutableStateOf(
            SimpleDateFormat(
                if (android.text.format.DateFormat.is24HourFormat(context)) "EEEE, d MMMM" else "EEEE, MMMM d",
                Locale.getDefault()
            ).format(Date())
        )
    }
    LaunchedEffect(context) {
        while (true) {
            val now = Date()
            currentTimeString = android.text.format.DateFormat.getTimeFormat(context).format(now)
            currentDateString = SimpleDateFormat(
                if (android.text.format.DateFormat.is24HourFormat(context)) "EEEE, d MMMM" else "EEEE, MMMM d",
                Locale.getDefault()
            ).format(now)
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 38.dp, vertical = 20.dp)
            .testTag("tv_dashboard_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ========================================================
            // 1. TOP BAR (MLUONA IPTV | 15:32 + Date | 4 Action Buttons)
            // ========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: "MLUONA IPTV" (MLUONA in green, IPTV in white)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MLUONA",
                        color = ColorGreenNeon,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "IPTV",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    if (isLoading) {
                        Spacer(modifier = Modifier.width(10.dp))
                        CircularProgressIndicator(
                            color = ColorGreenNeon,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Center: Time "15:32" and Date "Wednesday, September 30"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentTimeString,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = currentDateString,
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Right: 4 Buttons (+ Add | ? | Settings | Profile)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Button 1: "+ Add" Pill Button
                    TopAddPillButton(
                        focusRequester = topAddBtnFocusRequester,
                        onDown = { liveTvFocusRequester.requestFocus() },
                        onRight = { topHelpBtnFocusRequester.requestFocus() },
                        onClick = onNavigateToUsers
                    )

                    // Button 2: "(?)" Help Button
                    TopSquareIconButton(
                        text = "?",
                        contentDescription = "Help",
                        testTag = "btn_help",
                        focusRequester = topHelpBtnFocusRequester,
                        onLeft = { topAddBtnFocusRequester.requestFocus() },
                        onDown = { filmsFocusRequester.requestFocus() },
                        onRight = { topSettingsFocusRequester.requestFocus() },
                        onClick = { showHelpDialog = true }
                    )

                    // Button 3: Settings Button
                    TopSquareIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = strings.settings,
                        testTag = "btn_settings",
                        focusRequester = topSettingsFocusRequester,
                        onLeft = { topHelpBtnFocusRequester.requestFocus() },
                        onDown = { seriesFocusRequester.requestFocus() },
                        onRight = { topProfileFocusRequester.requestFocus() },
                        onClick = onNavigateToSettings
                    )

                    // Button 4: Solid Green Profile Button (as in uploaded screenshot)
                    TopSolidProfileButton(
                        focusRequester = topProfileFocusRequester,
                        testTag = "btn_profile",
                        onLeft = { topSettingsFocusRequester.requestFocus() },
                        onDown = { seriesFocusRequester.requestFocus() },
                        onClick = onNavigateToUsers
                    )
                }
            }

            val activeError = errorMessage ?: loginError
            if (activeError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2E1518))
                        .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚠",
                            color = Color(0xFFFF5252),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeError,
                            color = Color(0xFFFFCDD2),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var isRetryFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isRetryFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isRetryFocused) ColorGreenNeon else Color(0xFFD32F2F))
                                .border(1.dp, if (isRetryFocused) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable {
                                    activeAccount?.let { viewModel.loadAccountContent(it) }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "إعادة المحاولة",
                                color = if (isRetryFocused) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        var isDismissFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isDismissFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDismissFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    viewModel.clearErrors()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✕",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ========================================================
            // 2. CENTER SECTION: 3 Columns Grid
            //    Column 1: Tall "Live TV" Card
            //    Column 2: Top "Films" + Bottom "Favorites"
            //    Column 3: Top "Series" + Bottom "+ Add"
            // ========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // COLUMN 1: TALL "LIVE TV" CARD (Full Height)
                LiveTvTallCard(
                    title = "Live TV",
                    subtitle = if (liveCount > 0) "$liveCount channels" else "120 channels",
                    isSelected = selectedIndex == 0,
                    focusRequester = liveTvFocusRequester,
                    onFocus = { selectedIndex = 0 },
                    onNavigateRight = {
                        selectedIndex = 1
                        filmsFocusRequester.requestFocus()
                    },
                    onNavigateUp = { topAddBtnFocusRequester.requestFocus() },
                    onClick = {
                        selectedIndex = 0
                        if (activeAccount == null) onNavigateToUsers() else onNavigateToLiveTv()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("card_live_tv")
                )

                // COLUMN 2: Stack of "Films" (Top) + "Favorites" (Bottom)
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Films Card
                    CategoryMediumCard(
                        title = strings.movies,
                        subtitle = if (vodCount > 0) "$vodCount" else "",
                        iconType = MediumCardIcon.FILMS,
                        isSelected = selectedIndex == 1,
                        focusRequester = filmsFocusRequester,
                        onFocus = { selectedIndex = 1 },
                        onNavigateLeft = {
                            selectedIndex = 0
                            liveTvFocusRequester.requestFocus()
                        },
                        onNavigateRight = {
                            selectedIndex = 3
                            seriesFocusRequester.requestFocus()
                        },
                        onNavigateDown = {
                            selectedIndex = 2
                            favoritesFocusRequester.requestFocus()
                        },
                        onNavigateUp = { topHelpBtnFocusRequester.requestFocus() },
                        onClick = {
                            selectedIndex = 1
                            if (activeAccount == null) onNavigateToUsers() else onNavigateToMovies()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("card_movies")
                    )

                    // Favorites Pill Card
                    BottomActionPillCard(
                        title = "Favorites",
                        subtitle = "0 items",
                        icon = Icons.Default.Favorite,
                        isSelected = selectedIndex == 2,
                        focusRequester = favoritesFocusRequester,
                        onFocus = { selectedIndex = 2 },
                        onNavigateLeft = {
                            selectedIndex = 0
                            liveTvFocusRequester.requestFocus()
                        },
                        onNavigateRight = {
                            selectedIndex = 4
                            addBottomFocusRequester.requestFocus()
                        },
                        onNavigateUp = {
                            selectedIndex = 1
                            filmsFocusRequester.requestFocus()
                        },
                        onClick = {
                            selectedIndex = 2
                            if (activeAccount == null) onNavigateToUsers() else onNavigateToLiveTv()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .testTag("card_favorites")
                    )
                }

                // COLUMN 3: Stack of "Series" (Top) + "+ Add" (Bottom)
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Series Card
                    CategoryMediumCard(
                        title = "Series",
                        subtitle = if (seriesCount > 0) "$seriesCount seasons" else "3 seasons",
                        iconType = MediumCardIcon.SERIES,
                        isSelected = selectedIndex == 3,
                        focusRequester = seriesFocusRequester,
                        onFocus = { selectedIndex = 3 },
                        onNavigateLeft = {
                            selectedIndex = 1
                            filmsFocusRequester.requestFocus()
                        },
                        onNavigateDown = {
                            selectedIndex = 4
                            addBottomFocusRequester.requestFocus()
                        },
                        onNavigateUp = { topSettingsFocusRequester.requestFocus() },
                        onClick = {
                            selectedIndex = 3
                            if (activeAccount == null) onNavigateToUsers() else onNavigateToSeries()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("card_series")
                    )

                    // "+ Add" Pill Card
                    BottomActionPillCard(
                        title = "Add",
                        subtitle = null,
                        icon = Icons.Default.Add,
                        isSelected = selectedIndex == 4,
                        focusRequester = addBottomFocusRequester,
                        onFocus = { selectedIndex = 4 },
                        onNavigateLeft = {
                            selectedIndex = 2
                            favoritesFocusRequester.requestFocus()
                        },
                        onNavigateUp = {
                            selectedIndex = 3
                            seriesFocusRequester.requestFocus()
                        },
                        onClick = {
                            selectedIndex = 4
                            onNavigateToUsers()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .testTag("card_bottom_add")
                    )
                }
            }

            // ========================================================
            // 3. BOTTOM BAR (Profile 1 | Stats | Device ID | MLUONA PLAYER)
            // ========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Meta row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Playlist:",
                        color = Color(0xFF90A4AE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = activeAccount?.name ?: strings.defaultAccount,
                        color = ColorGreenNeon,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "|",
                        color = Color(0xFF37474F),
                        fontSize = 14.sp
                    )

                    Text(
                        text = "${liveCount} Live • ${vodCount} Movies • ${seriesCount} Series",
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Text(
                        text = "|",
                        color = Color(0xFF37474F),
                        fontSize = 14.sp
                    )

                    Text(
                        text = "Device:",
                        color = Color(0xFF90A4AE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    val deviceIdStr = remember(context) {
                        try {
                            android.provider.Settings.Secure.getString(
                                context.contentResolver,
                                android.provider.Settings.Secure.ANDROID_ID
                            )?.take(12)?.chunked(2)?.joinToString(":") ?: "Android TV"
                        } catch (_: Exception) {
                            "Android TV"
                        }
                    }
                    Text(
                        text = deviceIdStr,
                        color = ColorGreenNeon,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Right Badge: "MLUONA PLAYER" with green border
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.2.dp, ColorGreenNeon, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MLUONA PLAYER",
                        color = ColorGreenNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F1813),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, ColorGreenNeon),
                modifier = Modifier
                    .width(420.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MLUONA",
                            color = ColorGreenNeon,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "IPTV",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "Version 1.0",
                        color = TvTextSecondary,
                        fontSize = 13.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF1B2F24))
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Status", color = TvTextSecondary, fontSize = 13.sp)
                            Text(
                                text = if (activeAccount != null) "Connected" else "No Account",
                                color = if (activeAccount != null) ColorGreenNeon else Color(0xFFFF8888),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Playlist", color = TvTextSecondary, fontSize = 13.sp)
                            Text(
                                text = activeAccount?.name ?: "None",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Channels", color = TvTextSecondary, fontSize = 13.sp)
                            Text(
                                text = if (activeAccount != null) "$liveCount" else "0",
                                color = ColorGreenNeon,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { showHelpDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorGreenNeon,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "OK",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tall Card for "Live TV" spanning full height with retro TV icon and wave background
 */
@Composable
fun LiveTvTallCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit,
    onNavigateRight: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val active = isFocused || isSelected

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (active) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F3223),
                            Color(0xFF0B241A),
                            Color(0xFF071711)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            ColorCardBgUnfocused,
                            Color(0xFF0A1015),
                            Color(0xFF070B0E)
                        )
                    )
                }
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) ColorGreenCardSelected else ColorCardBorderUnfocused,
                shape = RoundedCornerShape(22.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (onNavigateRight != null) {
                                onNavigateRight()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Wave Curves Background
        WaveGraphicOverlay(isActive = active)

        // Content: Centered circle badge with Retro TV icon, title & subtitle
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Circular Icon Badge
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(ColorCircleBadgeBg)
                    .border(1.2.dp, if (active) ColorGreenNeon.copy(alpha = 0.5f) else Color(0xFF143024), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                RetroTvEmblemIcon(
                    modifier = Modifier.size(54.dp),
                    isGreen = true
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                color = if (active) Color(0xFF81C784) else Color(0xFF90A4AE),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

enum class MediumCardIcon {
    FILMS,
    SERIES
}

/**
 * Medium Card for "Films" and "Series"
 */
@Composable
fun CategoryMediumCard(
    title: String,
    subtitle: String,
    iconType: MediumCardIcon,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val active = isFocused || isSelected

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (active) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F3223),
                            Color(0xFF0B241A),
                            Color(0xFF071711)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            ColorCardBgUnfocused,
                            Color(0xFF0A1015),
                            Color(0xFF070B0E)
                        )
                    )
                }
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) ColorGreenCardSelected else ColorCardBorderUnfocused,
                shape = RoundedCornerShape(20.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (onNavigateRight != null) {
                                onNavigateRight()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onNavigateDown != null) {
                                onNavigateDown()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Wave Curves Background
        WaveGraphicOverlay(isActive = active)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Circular Icon Badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(ColorCircleBadgeBg)
                    .border(1.2.dp, if (active) ColorGreenNeon.copy(alpha = 0.5f) else Color(0xFF143024), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (iconType) {
                    MediumCardIcon.FILMS -> ClapperboardEmblemIcon(
                        modifier = Modifier.size(46.dp)
                    )
                    MediumCardIcon.SERIES -> SeriesTvPlayEmblemIcon(
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                color = if (active) Color(0xFF81C784) else Color(0xFF90A4AE),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Bottom Action Pill Card ("Favorites" and "+ Add")
 */
@Composable
fun BottomActionPillCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val active = isFocused || isSelected

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) ColorCardBgFocused else ColorCardBgUnfocused)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) ColorGreenCardSelected else ColorCardBorderUnfocused,
                shape = RoundedCornerShape(18.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (onNavigateRight != null) {
                                onNavigateRight()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Green Circle Badge with Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ColorCircleBadgeBg)
                    .border(1.dp, ColorGreenNeon.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ColorGreenNeon,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Color(0xFF90A4AE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Top "+ Add" Pill Button
 */
@Composable
fun TopAddPillButton(
    focusRequester: FocusRequester? = null,
    onDown: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isFocused) ColorGreenNeon else ColorTopBtnBg)
            .border(
                width = 1.4.dp,
                color = if (isFocused) Color.White else ColorGreenNeon,
                shape = RoundedCornerShape(50)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onDown != null) {
                                onDown()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (onRight != null) {
                                onRight()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add",
            tint = if (isFocused) Color.Black else ColorGreenNeon,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = "Add",
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Top Square Icon Button for Help and Settings
 */
@Composable
fun TopSquareIconButton(
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String,
    testTag: String,
    focusRequester: FocusRequester? = null,
    onLeft: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) ColorGreenNeon else ColorTopBtnBg)
            .border(
                width = 1.2.dp,
                color = if (isFocused) Color.White else ColorTopBtnBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onDown != null) {
                                onDown()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onLeft != null) {
                                onLeft()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (onRight != null) {
                                onRight()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) Color.Black else ColorGreenNeon,
                modifier = Modifier.size(19.dp)
            )
        } else if (text != null) {
            Text(
                text = text,
                color = if (isFocused) Color.Black else ColorGreenNeon,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Top Solid Green Profile Button (as in uploaded screenshot)
 */
@Composable
fun TopSolidProfileButton(
    focusRequester: FocusRequester? = null,
    testTag: String,
    onLeft: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ColorGreenNeon)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onDown != null) {
                                onDown()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onLeft != null) {
                                onLeft()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Profile",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Flowing Wave Graphics Overlay at the bottom of cards
 */
@Composable
fun WaveGraphicOverlay(isActive: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Bottom wave gradient fill
        val waveFill = Path().apply {
            moveTo(0f, h * 0.74f)
            cubicTo(w * 0.30f, h * 0.64f, w * 0.70f, h * 0.88f, w, h * 0.68f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = waveFill,
            brush = Brush.verticalGradient(
                colors = listOf(
                    if (isActive) Color(0x3500E676) else Color(0x1500E676),
                    Color.Transparent
                )
            )
        )

        // Wave line 1
        val waveLine = Path().apply {
            moveTo(0f, h * 0.74f)
            cubicTo(w * 0.30f, h * 0.64f, w * 0.70f, h * 0.88f, w, h * 0.68f)
        }
        drawPath(
            path = waveLine,
            color = if (isActive) Color(0x8000E676) else Color(0x2400E676),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Wave line 2
        val waveLine2 = Path().apply {
            moveTo(0f, h * 0.84f)
            cubicTo(w * 0.35f, h * 0.78f, w * 0.72f, h * 0.94f, w, h * 0.82f)
        }
        drawPath(
            path = waveLine2,
            color = if (isActive) Color(0x4000E676) else Color(0x1400E676),
            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Retro TV Emblem Icon for "Live TV" (Antennas, CRT monitor, rounded body)
 */
@Composable
fun RetroTvEmblemIcon(
    isGreen: Boolean = true,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeColor = ColorGreenNeon
        val strokeW = 2.4.dp.toPx()

        // 1. Antennas
        val topCenter = Offset(w * 0.50f, h * 0.28f)
        val leftEar = Offset(w * 0.28f, h * 0.08f)
        val rightEar = Offset(w * 0.72f, h * 0.08f)

        drawLine(
            color = strokeColor,
            start = topCenter,
            end = leftEar,
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = strokeColor,
            start = topCenter,
            end = rightEar,
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // 2. TV Cabinet Frame
        val bodyLeft = w * 0.12f
        val bodyTop = h * 0.28f
        val bodyWidth = w * 0.76f
        val bodyHeight = h * 0.58f
        val cornerRad = CornerRadius(10.dp.toPx(), 10.dp.toPx())

        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = cornerRad,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 3. TV Screen inside
        val screenLeft = bodyLeft + bodyWidth * 0.12f
        val screenTop = bodyTop + bodyHeight * 0.14f
        val screenWidth = bodyWidth * 0.76f
        val screenHeight = bodyHeight * 0.72f
        val screenCorner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

        drawRoundRect(
            color = strokeColor.copy(alpha = 0.4f),
            topLeft = Offset(screenLeft, screenTop),
            size = Size(screenWidth, screenHeight),
            cornerRadius = screenCorner,
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 4. Stand / Feet
        drawLine(
            color = strokeColor,
            start = Offset(bodyLeft + bodyWidth * 0.25f, bodyTop + bodyHeight),
            end = Offset(bodyLeft + bodyWidth * 0.20f, bodyTop + bodyHeight + 4.dp.toPx()),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = strokeColor,
            start = Offset(bodyLeft + bodyWidth * 0.75f, bodyTop + bodyHeight),
            end = Offset(bodyLeft + bodyWidth * 0.80f, bodyTop + bodyHeight + 4.dp.toPx()),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Clapperboard Emblem Icon for "Films"
 */
@Composable
fun ClapperboardEmblemIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeColor = ColorGreenNeon
        val strokeW = 2.4.dp.toPx()

        // Main Clapper Body
        val bodyLeft = w * 0.14f
        val bodyTop = h * 0.24f
        val bodyWidth = w * 0.72f
        val bodyHeight = h * 0.58f
        val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())

        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = corner,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Clapper Head Divider Line
        drawLine(
            color = strokeColor,
            start = Offset(bodyLeft, bodyTop + bodyHeight * 0.32f),
            end = Offset(bodyLeft + bodyWidth, bodyTop + bodyHeight * 0.32f),
            strokeWidth = strokeW
        )

        // Clapper Slanted Bars on top head
        drawLine(
            color = strokeColor,
            start = Offset(w * 0.34f, bodyTop),
            end = Offset(w * 0.26f, bodyTop + bodyHeight * 0.32f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = strokeColor,
            start = Offset(w * 0.54f, bodyTop),
            end = Offset(w * 0.46f, bodyTop + bodyHeight * 0.32f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = strokeColor,
            start = Offset(w * 0.74f, bodyTop),
            end = Offset(w * 0.66f, bodyTop + bodyHeight * 0.32f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Series TV with Play button emblem for "Series"
 */
@Composable
fun SeriesTvPlayEmblemIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeColor = ColorGreenNeon
        val strokeW = 2.4.dp.toPx()

        // TV Cabinet
        val bodyLeft = w * 0.14f
        val bodyTop = h * 0.24f
        val bodyWidth = w * 0.72f
        val bodyHeight = h * 0.58f
        val corner = CornerRadius(10.dp.toPx(), 10.dp.toPx())

        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = corner,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Play Triangle in center
        val playPath = Path().apply {
            moveTo(w * 0.42f, h * 0.40f)
            lineTo(w * 0.64f, h * 0.53f)
            lineTo(w * 0.42f, h * 0.66f)
            close()
        }
        drawPath(path = playPath, color = strokeColor, style = Fill)
    }
}
