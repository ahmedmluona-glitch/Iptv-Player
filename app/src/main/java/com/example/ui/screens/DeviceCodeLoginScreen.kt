package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.subscription.DeviceCodeInfo
import com.example.ui.components.MluonaLogo
import com.example.ui.components.QrCodeCanvas
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
import kotlinx.coroutines.delay

enum class LoginTab {
    QR_CODE,
    MANUAL
}

@Composable
fun DeviceCodeLoginScreen(
    viewModel: IptvViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToManualXtream: () -> Unit = {},
    onNavigateToManualM3u: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(LoginTab.QR_CODE) }

    var deviceInfo by remember { mutableStateOf<DeviceCodeInfo?>(null) }
    var isPolling by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(600) }
    var showEmailLogin by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Email login inputs
    var emailInput by remember { mutableStateOf("demo@mluona.com") }
    var passwordInput by remember { mutableStateOf("") }
    var isSubmittingEmail by remember { mutableStateOf(false) }

    val primaryFocusRequester = remember { FocusRequester() }

    // Fetch Device Code initially
    LaunchedEffect(Unit) {
        statusMessage = "جارٍ إنشاء كود الاقتران للتلفاز..."
        val res = viewModel.subscriptionManager.requestDeviceCode()
        res.onSuccess { info ->
            deviceInfo = info
            countdownSeconds = info.expiresInSeconds
            isPolling = true
            statusMessage = "امسح الرمز أو أدخل الكود على الهاتف للاقتران"
        }.onFailure {
            statusMessage = "تعذر الاتصال، يمكنك استخدام التسجيل اليدوي أو التجربة المجانية"
        }
        delay(200)
        try {
            primaryFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Poll /v1/device/poll every 3 seconds
    LaunchedEffect(isPolling, deviceInfo?.code) {
        val code = deviceInfo?.code
        if (isPolling && !code.isNullOrBlank()) {
            while (countdownSeconds > 0) {
                delay(3000)
                countdownSeconds -= 3
                val token = viewModel.subscriptionManager.pollDeviceCode(code)
                if (token != null) {
                    statusMessage = "تم الاقتران بنجاح! جارٍ الدخول..."
                    viewModel.onAuthSuccess()
                    onLoginSuccess()
                    break
                }
            }
        }
    }

    val pairUrl = deviceInfo?.verificationUrl ?: "https://mluona-iptv.com/pair"
    val finalPairUrl = if (!deviceInfo?.code.isNullOrBlank()) "$pairUrl?code=${deviceInfo?.code}" else pairUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 28.dp, vertical = 18.dp)
            .testTag("device_code_login_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ==========================================
            // TOP BAR: Dual Mode Selector (QR vs Manual)
            // ==========================================
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(TvSurface)
                    .border(1.dp, TvBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Option 1: QR Code Mode
                TabSelectorButton(
                    text = "اقتران سريع (QR Code)",
                    icon = Icons.Default.QrCode,
                    isSelected = selectedTab == LoginTab.QR_CODE,
                    onClick = { selectedTab = LoginTab.QR_CODE }
                )

                // Option 2: Manual Mode
                TabSelectorButton(
                    text = "تسجيل يدوي (Xtream / M3U)",
                    icon = Icons.Default.Tv,
                    isSelected = selectedTab == LoginTab.MANUAL,
                    onClick = { selectedTab = LoginTab.MANUAL }
                )
            }

            // ==========================================
            // MAIN CONTENT PANELS (Based on selected mode)
            // ==========================================
            if (selectedTab == LoginTab.QR_CODE) {
                // ==========================
                // MODE 1: QR CODE PAIRING
                // ==========================
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Panel: Code & Instructions
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MluonaLogo(size = 80.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "تسجيل الدخول واقتران التلفاز",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvTextPrimary
                        )
                        Text(
                            text = "امسح رمز QR بكاميرا الهاتف أو أدخل الكود على الموقع:",
                            fontSize = 14.sp,
                            color = TvTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 6-Character Code Display
                        val codeText = deviceInfo?.code ?: "------"
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            codeText.forEach { ch ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF071F15))
                                        .border(2.dp, TvAccentEmerald, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ch.toString(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TvAccentEmerald
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = TvAccentEmerald,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "بانتظار التأكيد على الهاتف... (${countdownSeconds / 60}:${"%02d".format(countdownSeconds % 60)})",
                                fontSize = 12.sp,
                                color = TvTextMuted
                            )
                        }

                        if (statusMessage != null) {
                            Text(
                                text = statusMessage!!,
                                fontSize = 12.sp,
                                color = TvAccentEmerald,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick switch to Manual option
                        TvActionButton(
                            text = "التبديل إلى الإدخال اليدوي (Manual)",
                            icon = Icons.Default.Tv,
                            isHighlight = false,
                            modifier = Modifier.fillMaxWidth(0.9f),
                            onClick = { selectedTab = LoginTab.MANUAL }
                        )
                    }

                    // Right Panel: Real QR Code + Clickable Direct Link
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // High-definition scan-compliant QR Code
                        val qrData = deviceInfo?.qrPayload ?: finalPairUrl
                        QrCodeCanvas(
                            content = qrData,
                            sizeDp = 150.dp,
                            qrColor = Color(0xFF000000),
                            backgroundColor = Color.White
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Clickable link directly under QR Code to open subscription & add playlist page
                        TvActionButton(
                            text = "🌐 فتح صفحة الاشتراك وإضافة Playlist",
                            icon = Icons.Default.Refresh,
                            isHighlight = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(primaryFocusRequester),
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalPairUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Clickable Text Link
                        Text(
                            text = finalPairUrl,
                            fontSize = 11.sp,
                            color = TvAccentGold,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalPairUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 7-Day Free Trial Button
                        TvActionButton(
                            text = "بدء التجربة المجانية 7 أيام",
                            icon = Icons.Default.Tv,
                            isHighlight = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                statusMessage = "جارٍ تفعيل التجربة المجانية 7 أيام..."
                                viewModel.startTrial(
                                    email = "trial-${System.currentTimeMillis() % 10000}@mluona.com",
                                    onSuccess = {
                                        onLoginSuccess()
                                    }
                                )
                            }
                        )
                    }
                }
            } else {
                // ==========================
                // MODE 2: MANUAL LOGIN
                // ==========================
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Panel: Manual Xtream & M3U Options
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(24.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MluonaLogo(size = 80.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "التسجيل والإدخال اليدوي",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvTextPrimary
                        )
                        Text(
                            text = "اختر طريقة إدخال بيانات اشتراكك أو ملف القنوات يدوياً:",
                            fontSize = 14.sp,
                            color = TvTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 1. Xtream Codes Manual Login
                        TvActionButton(
                            text = "تسجيل الدخول عبر سيرفر Xtream Codes",
                            icon = Icons.Default.Tv,
                            isHighlight = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onNavigateToManualXtream() }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. M3U Playlist Manual Login
                        TvActionButton(
                            text = "إضافة رابط قائمة تشغيل M3U Playlist",
                            icon = Icons.Default.Refresh,
                            isHighlight = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onNavigateToManualM3u() }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch back to QR Code
                        TvActionButton(
                            text = "العودة إلى اقتران رمز QR كود",
                            icon = Icons.Default.QrCode,
                            isHighlight = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selectedTab = LoginTab.QR_CODE }
                        )
                    }

                    // Right Panel: Email Login & Quick Trial
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(24.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "الدخول بالبريد الإلكتروني",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvTextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("البريد الإلكتروني", color = TvTextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TvAccentEmerald,
                                unfocusedBorderColor = TvBorder,
                                focusedTextColor = TvTextPrimary,
                                unfocusedTextColor = TvTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("كلمة المرور (اختياري)", color = TvTextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TvAccentEmerald,
                                unfocusedBorderColor = TvBorder,
                                focusedTextColor = TvTextPrimary,
                                unfocusedTextColor = TvTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        TvActionButton(
                            text = if (isSubmittingEmail) "جارٍ التحقق..." else "دخول فوري بالبريد",
                            icon = Icons.Default.Email,
                            isHighlight = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (emailInput.isNotBlank()) {
                                    isSubmittingEmail = true
                                    viewModel.loginWithEmail(
                                        email = emailInput,
                                        password = passwordInput,
                                        onSuccess = {
                                            isSubmittingEmail = false
                                            onLoginSuccess()
                                        },
                                        onError = { err ->
                                            isSubmittingEmail = false
                                            statusMessage = err
                                        }
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Instant 7-Day Free Trial
                        TvActionButton(
                            text = "بدء التجربة المجانية 7 أيام",
                            icon = Icons.Default.Tv,
                            isHighlight = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                statusMessage = "جارٍ تفعيل التجربة المجانية 7 أيام..."
                                viewModel.startTrial(
                                    email = "trial-${System.currentTimeMillis() % 10000}@mluona.com",
                                    onSuccess = {
                                        onLoginSuccess()
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabSelectorButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> TvAccentEmerald
        isSelected -> Color(0xFF0F3D26)
        else -> Color.Transparent
    }

    val contentColor = when {
        isFocused -> Color.Black
        isSelected -> TvAccentEmerald
        else -> TvTextSecondary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.2.dp else 0.dp,
                color = if (isFocused) Color.White else if (isSelected) TvAccentEmerald else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable { onClick() }
            .onKeyEvent {
                if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                color = contentColor,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun TvActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isHighlight: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> if (isHighlight) TvAccentEmerald else TvSurfaceHighlight
        isHighlight -> Color(0xFF0B2E1E)
        else -> Color(0xFF141F29)
    }

    val contentColor = when {
        isFocused && isHighlight -> Color.Black
        isFocused -> TvTextPrimary
        isHighlight -> TvAccentEmerald
        else -> TvTextSecondary
    }

    val borderStroke = when {
        isFocused -> 2.dp
        isHighlight -> 1.dp
        else -> 1.dp
    }

    val borderColor = when {
        isFocused -> if (isHighlight) Color.White else TvAccentEmerald
        isHighlight -> TvAccentEmerald
        else -> TvBorder
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(borderStroke, borderColor, RoundedCornerShape(12.dp))
            .focusable(interactionSource = interactionSource)
            .clickable { onClick() }
            .onKeyEvent {
                if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
