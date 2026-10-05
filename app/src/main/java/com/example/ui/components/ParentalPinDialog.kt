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
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.session.FavoritesHistoryManager
import com.example.ui.theme.TvAccentEmerald
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

@Composable
fun ParentalPinDialog(
    favHistoryManager: FavoritesHistoryManager,
    categoryTitle: String? = null,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TvSurface)
                .border(1.5.dp, Color(0xFFFF5252), RoundedCornerShape(20.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A1417))
                        .border(1.dp, Color(0xFFFF5252), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "قفل أبوي",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = "القفل الأبوي (Parental Control)",
                    color = TvTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (categoryTitle != null) {
                        "فئة \"$categoryTitle\" مقفلة لحماية العائلة. يرجى إدخال رمز PIN (الافتراضي 0000):"
                    } else {
                        "يرجى إدخال رمز PIN للمتابعة:"
                    },
                    color = TvTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                // 4-Dot PIN Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) TvAccentEmerald else Color(0xFF1B232E))
                                .border(1.dp, if (isFilled) TvAccentEmerald else TvBorder, CircleShape)
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Numeric Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "DEL")
                    )

                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                val interaction = remember { MutableInteractionSource() }
                                val isFocused by interaction.collectIsFocusedAsState()

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isFocused) TvAccentEmerald else Color(0xFF131922))
                                        .border(1.dp, if (isFocused) Color.White else TvBorder, RoundedCornerShape(10.dp))
                                        .clickable(interactionSource = interaction, indication = null) {
                                            handleKey(key, enteredPin, { newPin ->
                                                enteredPin = newPin
                                                errorMessage = null
                                                if (newPin.length == 4) {
                                                    if (favHistoryManager.verifyParentalPin(newPin)) {
                                                        favHistoryManager.isAdultSessionUnlocked = true
                                                        onSuccess()
                                                    } else {
                                                        errorMessage = "رمز PIN غير صحيح!"
                                                        enteredPin = ""
                                                    }
                                                }
                                            })
                                        }
                                        .focusable(interactionSource = interaction)
                                        .onKeyEvent {
                                            if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                                 it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                                            ) {
                                                handleKey(key, enteredPin, { newPin ->
                                                    enteredPin = newPin
                                                    errorMessage = null
                                                    if (newPin.length == 4) {
                                                        if (favHistoryManager.verifyParentalPin(newPin)) {
                                                            favHistoryManager.isAdultSessionUnlocked = true
                                                            onSuccess()
                                                        } else {
                                                            errorMessage = "رمز PIN غير صحيح!"
                                                            enteredPin = ""
                                                        }
                                                    }
                                                })
                                                true
                                            } else false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "DEL") {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "مسح",
                                            tint = if (isFocused) Color.Black else TvTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            color = if (isFocused) Color.Black else Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Cancel Button
                val cancelInteraction = remember { MutableInteractionSource() }
                val isCancelFocused by cancelInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCancelFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (isCancelFocused) Color.White else TvBorder, RoundedCornerShape(10.dp))
                        .clickable(interactionSource = cancelInteraction, indication = null) { onDismiss() }
                        .focusable(interactionSource = cancelInteraction)
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
                        text = "إلغاء",
                        color = if (isCancelFocused) Color.White else TvTextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun handleKey(key: String, current: String, onUpdate: (String) -> Unit) {
    when (key) {
        "C" -> onUpdate("")
        "DEL" -> if (current.isNotEmpty()) onUpdate(current.dropLast(1))
        else -> if (current.length < 4) onUpdate(current + key)
    }
}
