package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

private val ColorNeonGreenBorder = Color(0xFF00E676)

/**
 * App icon / logo component displaying the 3D green origami play button.
 */
@Composable
fun MluonaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    animated: Boolean = true,
    leftColor: Color = Color.Unspecified,
    rightColor: Color = Color.Unspecified
) {
    var startAnim by remember { mutableStateOf(!animated) }

    LaunchedEffect(Unit) {
        if (animated) {
            startAnim = true
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.88f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "logoScale"
    )

    val cornerRadius = size * 0.22f

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.5.dp, ColorNeonGreenBorder.copy(alpha = alpha), RoundedCornerShape(cornerRadius))
            .testTag("mluona_logo_container"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_icon_asset),
            contentDescription = "Mluona Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
