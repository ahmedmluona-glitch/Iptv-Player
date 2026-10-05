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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.session.FavoritesHistoryManager
import com.example.data.session.UserProfile
import com.example.ui.theme.TvAccentEmerald
import com.example.ui.theme.TvAccentGold
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceHighlight
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

@Composable
fun ProfileSwitcherDialog(
    favHistoryManager: FavoritesHistoryManager,
    onProfileSelected: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var profiles by remember { mutableStateOf(favHistoryManager.getProfiles()) }
    var activeProfile by remember { mutableStateOf(favHistoryManager.getActiveProfile()) }
    var showPinDialogForProfile by remember { mutableStateOf<UserProfile?>(null) }

    if (showPinDialogForProfile != null) {
        ParentalPinDialog(
            favHistoryManager = favHistoryManager,
            categoryTitle = "الملف الشخصي: ${showPinDialogForProfile?.name}",
            onSuccess = {
                val target = showPinDialogForProfile!!
                showPinDialogForProfile = null
                favHistoryManager.setActiveProfile(target.id)
                onProfileSelected(target)
            },
            onDismiss = {
                showPinDialogForProfile = null
            }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TvSurface)
                .border(1.dp, TvBorder, RoundedCornerShape(20.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "تبديل الملف الشخصي (Profiles)",
                    color = TvTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "اختر ملفك الشخصي بمفضلة وسجل ومستوى مشاهدة مستقل لكل فرد:",
                    color = TvTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Profile Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    profiles.forEach { profile ->
                        val isSelected = profile.id == activeProfile.id
                        val interaction = remember { MutableInteractionSource() }
                        val isFocused by interaction.collectIsFocusedAsState()

                        val icon = when {
                            profile.isKids -> Icons.Default.ChildCare
                            profile.pinProtected -> Icons.Default.Lock
                            else -> Icons.Default.Person
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Color(0xFF0C2419) else if (isFocused) TvSurfaceHighlight else Color(0xFF101720))
                                .border(
                                    width = if (isSelected || isFocused) 1.8.dp else 1.dp,
                                    color = if (isFocused) TvAccentEmerald else if (isSelected) TvAccentEmerald else TvBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable(interactionSource = interaction, indication = null) {
                                    if (profile.pinProtected && !favHistoryManager.isAdultSessionUnlocked) {
                                        showPinDialogForProfile = profile
                                    } else {
                                        favHistoryManager.setActiveProfile(profile.id)
                                        onProfileSelected(profile)
                                    }
                                }
                                .focusable(interactionSource = interaction)
                                .onKeyEvent {
                                    if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                        (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                         it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                                    ) {
                                        if (profile.pinProtected && !favHistoryManager.isAdultSessionUnlocked) {
                                            showPinDialogForProfile = profile
                                        } else {
                                            favHistoryManager.setActiveProfile(profile.id)
                                            onProfileSelected(profile)
                                        }
                                        true
                                    } else false
                                }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) TvAccentEmerald.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = profile.name,
                                        tint = if (isSelected) TvAccentEmerald else TvTextPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Text(
                                    text = profile.name,
                                    color = if (isSelected) Color.White else TvTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (isSelected) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = TvAccentEmerald,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "الحالي",
                                            color = TvAccentEmerald,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Close Button
                val closeInteraction = remember { MutableInteractionSource() }
                val isCloseFocused by closeInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCloseFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (isCloseFocused) Color.White else TvBorder, RoundedCornerShape(10.dp))
                        .clickable(interactionSource = closeInteraction, indication = null) { onDismiss() }
                        .focusable(interactionSource = closeInteraction)
                        .onKeyEvent {
                            if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                 it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                            ) {
                                onDismiss()
                                true
                            } else false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "إغلاق",
                        color = if (isCloseFocused) Color.White else TvTextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
