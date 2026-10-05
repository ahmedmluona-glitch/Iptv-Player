package com.example.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.IptvViewModel

private val ColorPortalDarkBg = Color(0xFF050B08)
private val ColorPortalSurface = Color(0xFF0C1611)
private val ColorPortalCardBg = Color(0xFF081F15)
private val ColorGreenNeon = Color(0xFF00E676)
private val ColorPortalBorder = Color(0xFF14271E)

/**
 * TV Portal Screen - simplified and redesigned to match the modern emerald green theme.
 * Strictly contains ONLY 2 options: Xtream Codes and M3U.
 */
@Composable
fun TvPortalScreen(
    viewModel: IptvViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToXtreamLogin: () -> Unit,
    onNavigateToM3u: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onBackToSplash: () -> Unit
) {
    BackHandler {
        onNavigateToDashboard()
    }

    val xtreamFocusRequester = remember { FocusRequester() }
    val m3uFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorPortalDarkBg)
            .padding(horizontal = 40.dp, vertical = 24.dp)
            .testTag("tv_portal_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Back Button in top left corner
        val backInteraction = remember { MutableInteractionSource() }
        val isBackFocused by backInteraction.collectIsFocusedAsState()

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isBackFocused) ColorGreenNeon else ColorPortalSurface)
                .border(1.2.dp, if (isBackFocused) Color.White else ColorPortalBorder, RoundedCornerShape(10.dp))
                .clickable(interactionSource = backInteraction, indication = null) { onNavigateToDashboard() }
                .focusable(interactionSource = backInteraction)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (isBackFocused) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Dashboard",
                    color = if (isBackFocused) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Center Container
        Box(
            modifier = Modifier
                .width(660.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ColorPortalSurface,
                            Color(0xFF08120D),
                            ColorPortalDarkBg
                        )
                    )
                )
                .border(1.5.dp, ColorPortalBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 36.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // New App Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.8.dp, ColorGreenNeon, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_asset),
                        contentDescription = "Mluona Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MLUONA",
                        color = ColorGreenNeon,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "IPTV",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "Select your playlist connection method",
                    color = Color(0xFF90A4AE),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ONLY TWO OPTIONS (Xtream Codes API & M3U Playlist)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Option 1: Login with Xtream Codes API
                    PortalOptionCard(
                        title = "Xtream Codes API",
                        subtitle = "Server URL, Username & Password",
                        icon = Icons.Default.Dns,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(xtreamFocusRequester)
                            .testTag("btn_xtream_portal"),
                        onNavigateRight = { m3uFocusRequester.requestFocus() },
                        onClick = onNavigateToXtreamLogin
                    )

                    // Option 2: Load M3U Playlist or URL
                    PortalOptionCard(
                        title = "M3U Playlist",
                        subtitle = "M3U / M3U8 Playlist File or URL",
                        icon = Icons.Default.CloudDownload,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(m3uFocusRequester)
                            .testTag("btn_m3u_portal"),
                        onNavigateLeft = { xtreamFocusRequester.requestFocus() },
                        onClick = onNavigateToM3u
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .height(130.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) ColorPortalCardBg else ColorPortalSurface)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) ColorGreenNeon else ColorPortalBorder,
                shape = RoundedCornerShape(18.dp)
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
                        else -> false
                    }
                } else false
            }
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F261C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ColorGreenNeon,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                color = if (isFocused) ColorGreenNeon else Color(0xFF90A4AE),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
