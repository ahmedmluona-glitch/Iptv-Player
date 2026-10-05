package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MluonaLogo
import com.example.ui.components.TvTouchButton
import com.example.ui.components.TvTouchInputField
import com.example.ui.theme.TvAccentGold
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.viewmodel.IptvViewModel

/**
 * Load M3U / M3U8 Playlist Screen supporting TV remote and touch screen.
 */
@Composable
fun M3uLoadScreen(
    viewModel: IptvViewModel,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        viewModel.clearErrors()
        onBack()
    }

    val isLoggingIn by viewModel.loginInProgress.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()

    var m3uUrl by remember { mutableStateOf("") }
    var playlistName by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val submitLoadM3u = {
        viewModel.loadM3u(
            url = m3uUrl,
            name = playlistName,
            onSuccess = onSuccess
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 48.dp, vertical = 24.dp)
            .testTag("m3u_load_screen"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(620.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TvSurface)
                .border(1.dp, TvBorder, RoundedCornerShape(16.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MluonaLogo(size = 48.dp, animated = false)
                    Column {
                        Text(
                            text = "Load M3U Playlist",
                            color = TvTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enter direct M3U / M3U8 link to fetch real channels",
                            color = TvTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (loginError != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF331414))
                            .border(1.dp, Color(0xFF882222), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFFF6666),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = loginError ?: "",
                            color = Color(0xFFFFB4B4),
                            fontSize = 13.sp
                        )
                    }
                } else if (statusMsg != null) {
                    Text(
                        text = statusMsg ?: "",
                        color = TvAccentGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                TvTouchInputField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = "Playlist Name",
                    placeholder = "e.g. Arabic & Sports M3U",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    testTag = "input_m3u_name"
                )

                TvTouchInputField(
                    value = m3uUrl,
                    onValueChange = { m3uUrl = it },
                    label = "M3U / M3U8 Playlist URL",
                    placeholder = "http://example.com/playlist.m3u",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitLoadM3u() }),
                    testTag = "input_m3u_url"
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TvTouchButton(
                        text = "Back",
                        onClick = {
                            viewModel.clearErrors()
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_m3u_back"
                    )

                    TvTouchButton(
                        text = "Download & Load",
                        isPrimary = true,
                        isLoading = isLoggingIn,
                        onClick = submitLoadM3u,
                        modifier = Modifier.weight(1.5f),
                        testTag = "btn_m3u_submit"
                    )
                }
            }
        }
    }
}
