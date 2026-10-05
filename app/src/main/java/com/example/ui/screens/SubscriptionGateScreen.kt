package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.subscription.SubscriptionStatus
import com.example.ui.components.MluonaLogo
import com.example.ui.components.QrCodeCanvas
import com.example.ui.theme.TvAccentEmerald
import com.example.ui.theme.TvAccentGold
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.viewmodel.IptvViewModel
import kotlinx.coroutines.launch

@Composable
fun SubscriptionGateScreen(
    viewModel: IptvViewModel,
    onSubscriptionActive: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    // Intercept back key to prevent bypassing gate
    BackHandler {
        // Stay on screen or recheck
    }

    val subInfo by viewModel.subscriptionInfo.collectAsState()
    val isChecking by viewModel.isCheckingSubscription.collectAsState()

    var activationCodeInput by remember { mutableStateOf("") }
    var isRedeeming by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val status = subInfo?.status ?: SubscriptionStatus.EXPIRED
    val isSuspended = status == SubscriptionStatus.SUSPENDED

    val titleText = if (isSuspended) "تم إيقاف الاشتراك مؤقتاً" else "انتهى اشتراكك أو لم يتم تفعيله بعد"
    val subText = if (isSuspended) {
        "تم تعليق هذا الحساب من قِبل إدارة السيرفر. يرجى التواصل مع الدعم الفني أو تجديد كود التفعيل."
    } else {
        "عزيزي المشترك، انتهت فترة الاشتراك أو التجربة المجانية. لتجديد البث مباشرة، امسح رمز الاستجابة السريعة بهاتفك أو أدخل كود التفعيل أدناه."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(32.dp)
            .testTag("subscription_gate_screen"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Right Side: Expiry warning, Activation Code entry & Buttons
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(TvSurface)
                    .border(1.dp, TvBorder, RoundedCornerShape(24.dp))
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isSuspended) Icons.Default.Warning else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (isSuspended) Color(0xFFFF4D4D) else TvAccentGold,
                        modifier = Modifier.size(36.dp)
                    )
                    Column {
                        Text(
                            text = titleText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvTextPrimary
                        )
                        if (subInfo != null) {
                            Text(
                                text = "تاريخ الانتهاء المسجل: ${subInfo?.expiresAtFormatted}",
                                fontSize = 13.sp,
                                color = TvTextMuted
                            )
                        }
                    }
                }

                Text(
                    text = subText,
                    fontSize = 14.sp,
                    color = TvTextSecondary,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 21. Activation Code Redemption Input
                Text(
                    text = "هل لديك كود تفعيل؟ (Activation Code)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TvAccentEmerald
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = activationCodeInput,
                        onValueChange = { activationCodeInput = it },
                        placeholder = { Text("أدخل الكود (مثال: MLUONA-30DAYS-PASS)", color = TvTextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TvAccentEmerald,
                            unfocusedBorderColor = TvBorder,
                            focusedTextColor = TvTextPrimary,
                            unfocusedTextColor = TvTextPrimary
                        )
                    )

                    TvActionButton(
                        text = if (isRedeeming) "جارٍ التفعيل..." else "تفعيل الكود",
                        icon = Icons.Default.ConfirmationNumber,
                        isHighlight = true,
                        onClick = {
                            if (activationCodeInput.isNotBlank()) {
                                isRedeeming = true
                                coroutineScope.launch {
                                    val res = viewModel.subscriptionManager.redeemCode(activationCodeInput)
                                    isRedeeming = false
                                    resultMessage = res.message
                                    isSuccessMessage = res.success
                                    if (res.success) {
                                        viewModel.refreshSubscription()
                                        onSubscriptionActive()
                                    }
                                }
                            }
                        }
                    )
                }

                if (resultMessage != null) {
                    Text(
                        text = resultMessage!!,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuccessMessage) TvAccentEmerald else Color(0xFFFF4D4D)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Refresh status button
                    TvActionButton(
                        text = if (isChecking) "جارٍ التحديث..." else "تحديث حالة الاشتراك",
                        icon = Icons.Default.Refresh,
                        isHighlight = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.refreshSubscription { active ->
                                if (active) {
                                    onSubscriptionActive()
                                } else {
                                    resultMessage = "ما زال الاشتراك منتهياً، يرجى التجديد أو إدخال كود."
                                    isSuccessMessage = false
                                }
                            }
                        }
                    )

                    // Switch account / pair new TV code
                    TvActionButton(
                        text = "تبديل الحساب / كود التلفاز",
                        icon = Icons.Default.QrCode,
                        isHighlight = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.subscriptionManager.logout()
                            onNavigateToLogin()
                        }
                    )
                }
            }

            // Left Side: Renewal QR Code & Mobile Link
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(TvSurface)
                    .border(1.dp, TvBorder, RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MluonaLogo()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "امسح للتجديد الفوري",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TvTextPrimary
                )
                Text(
                    text = "امسح الرمز بكاميرا هاتفك للدفع والتجديد عبر الموقع",
                    fontSize = 12.sp,
                    color = TvTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                val context = LocalContext.current
                val renewalUrl = subInfo?.renewalUrl ?: "https://mluona-iptv.com/renew"
                QrCodeCanvas(
                    content = renewalUrl,
                    sizeDp = 150.dp,
                    qrColor = Color.Black,
                    backgroundColor = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))

                TvActionButton(
                    text = "🌐 فتح صفحة الاشتراك وإضافة Playlist",
                    icon = Icons.Default.Refresh,
                    isHighlight = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(renewalUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = renewalUrl,
                    fontSize = 11.sp,
                    color = TvAccentGold,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(renewalUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }
        }
    }
}
