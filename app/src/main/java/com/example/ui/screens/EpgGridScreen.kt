package com.example.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveChannel
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgGridScreen(
    viewModel: IptvViewModel,
    onSelectChannel: (LiveChannel) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val channels by viewModel.allLiveChannels.collectAsState()
    val epgMap by viewModel.epgMap.collectAsState()

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val now = System.currentTimeMillis()
    // Generate 6 time slots of 30 minutes starting from nearest past hour
    val baseHour = (now / 3_600_000L) * 3_600_000L
    val timeSlots = (0..5).map { Date(baseHour + it * 1_800_000L) }

    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(24.dp)
            .testTag("epg_grid_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    val isBackFocused by backInteraction.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isBackFocused) TvAccentEmerald else TvSurfaceHighlight)
                            .border(1.dp, if (isBackFocused) Color.White else TvBorder, CircleShape)
                            .clickable(interactionSource = backInteraction, indication = null) { onBack() }
                            .focusable(interactionSource = backInteraction)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = if (isBackFocused) Color.Black else TvTextPrimary
                        )
                    }

                    Column {
                        Text(
                            text = "دليل البرامج التلفزيونية الشبكي (EPG Grid)",
                            color = TvTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "جدول زمني أفقي يعرض البرامج الحالية والقادمة لكافة القنوات",
                            color = TvTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TvAccentEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "الوقت الآن: ${timeFormat.format(Date(now))}",
                        color = TvAccentEmerald,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Timeline Header Row
            Row(modifier = Modifier.fillMaxWidth()) {
                // Channel column header spacer
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .height(36.dp)
                        .background(TvSurface)
                        .border(0.5.dp, TvBorder),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "القناة",
                        color = TvTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                // Horizontal Time Slots
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    timeSlots.forEach { slot ->
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(36.dp)
                                .background(Color(0xFF0C131A))
                                .border(0.5.dp, TvBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = timeFormat.format(slot),
                                color = TvAccentGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Channels & Program Grid Table
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(channels.take(80), key = { it.streamId }) { channel ->
                    val channelEpg = epgMap[channel.streamId]
                    val curProg = channelEpg?.currentProgram?.title ?: "البث الحي المباشر"
                    val nextProg = channelEpg?.upcomingProgram?.title ?: "برنامج ترفيهي عام"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        // Channel Cell on the Left
                        val channelInteraction = remember { MutableInteractionSource() }
                        val isChFocused by channelInteraction.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isChFocused) Color(0xFF0B291B) else TvSurface)
                                .border(1.dp, if (isChFocused) TvAccentEmerald else TvBorder)
                                .clickable(interactionSource = channelInteraction, indication = null) {
                                    onSelectChannel(channel)
                                }
                                .focusable(interactionSource = channelInteraction)
                                .onKeyEvent {
                                    if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                        (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                         it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                                    ) {
                                        onSelectChannel(channel)
                                        true
                                    } else false
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LiveTv,
                                    contentDescription = null,
                                    tint = if (isChFocused) TvAccentEmerald else TvAccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = channel.name,
                                    color = if (isChFocused) Color.White else TvTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Programs Grid
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            // Slot 1 & 2: Current Program
                            Box(
                                modifier = Modifier
                                    .width(400.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF081410))
                                    .border(0.5.dp, Color(0xFF162E20))
                                    .clickable { onSelectChannel(channel) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = curProg,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "الآن • جارٍ البث",
                                        color = TvAccentEmerald,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Slot 3 & 4: Next Program
                            Box(
                                modifier = Modifier
                                    .width(400.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF0C1217))
                                    .border(0.5.dp, TvBorder)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = nextProg,
                                        color = TvTextSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "القادم",
                                        color = TvTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Slot 5 & 6: Later Program
                            Box(
                                modifier = Modifier
                                    .width(400.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF080D11))
                                    .border(0.5.dp, TvBorder)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "بث فضائي متواصل",
                                    color = TvTextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
