package com.example.ui.screens

import android.view.KeyEvent
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MluonaLogo
import com.example.ui.components.TvTouchButton
import com.example.ui.components.TvTouchInputField
import com.example.ui.theme.TvAccentGold
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.viewmodel.IptvViewModel

/**
 * Xtream Codes API Login Screen supporting both TV Remote and Touch.
 * Connects directly to the real server, handles authentication errors cleanly,
 * and saves the session permanently.
 */
@Composable
fun XtreamLoginScreen(
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
    val strings by viewModel.appText.collectAsState()

    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val submitLogin = {
        viewModel.loginXtream(
            serverUrl = serverUrl,
            username = username,
            password = password,
            accountName = accountName,
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
            .testTag("xtream_login_screen"),
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Logo and Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MluonaLogo(size = 48.dp, animated = false)
                    Column {
                        Text(
                            text = strings.loginXtreamTitle,
                            color = TvTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.loginXtreamSubtitle,
                            color = TvTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Error / Status Banner
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

                // Input Fields
                TvTouchInputField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = strings.accountNameLabel,
                    placeholder = strings.accountNameLabel,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    testTag = "input_account_name"
                )

                TvTouchInputField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = strings.serverUrlLabel,
                    placeholder = "http://example.com:8080",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    testTag = "input_server_url"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvTouchInputField(
                        value = username,
                        onValueChange = { username = it },
                        label = strings.usernameLabel,
                        placeholder = strings.usernameLabel,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                        testTag = "input_username"
                    )

                    TvTouchInputField(
                        value = password,
                        onValueChange = { password = it },
                        label = strings.passwordLabel,
                        placeholder = strings.passwordLabel,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                        modifier = Modifier.weight(1f),
                        testTag = "input_password"
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TvTouchButton(
                        text = strings.back,
                        onClick = {
                            viewModel.clearErrors()
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_cancel_login"
                    )

                    TvTouchButton(
                        text = strings.loginButton,
                        isPrimary = true,
                        isLoading = isLoggingIn,
                        onClick = submitLogin,
                        modifier = Modifier.weight(1.5f),
                        testTag = "btn_submit_login"
                    )
                }
            }
        }
    }
}
