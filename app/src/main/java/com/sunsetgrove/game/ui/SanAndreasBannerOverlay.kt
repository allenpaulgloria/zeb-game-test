package com.sunsetgrove.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunsetgrove.game.gameplay.BannerType

/**
 * Full-screen iconic San Andreas dramatic banners:
 * - WASTED (crimson/gold slow-motion vignette)
 * - BUSTED (police steel-blue vignette)
 * - WANTED EVADED / RESPECT + (classic golden-amber banner)
 */
@Composable
fun SanAndreasBannerOverlay(
    banner: BannerType,
    subtext: String,
    onTapRespawn: () -> Unit,
) {
    if (banner == BannerType.NONE) return

    val isEndState = banner == BannerType.WASTED || banner == BannerType.BUSTED
    val title = when (banner) {
        BannerType.WASTED -> "WASTED"
        BannerType.BUSTED -> "BUSTED"
        BannerType.WANTED_EVADED -> "WANTED EVADED!"
        BannerType.NONE -> ""
    }
    val titleColor = when (banner) {
        BannerType.WASTED -> Color(0xFFE53935)
        BannerType.BUSTED -> Color(0xFF42A5F5)
        BannerType.WANTED_EVADED -> Color(0xFFFFCA28)
        BannerType.NONE -> Color.White
    }
    val scrimColor = when (banner) {
        BannerType.WASTED -> Color(0x881A0404)
        BannerType.BUSTED -> Color(0x88041022)
        BannerType.WANTED_EVADED -> Color.Transparent
        BannerType.NONE -> Color.Transparent
    }

    val shadow = Shadow(color = Color.Black, offset = Offset(5f, 5f), blurRadius = 4f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
            .then(if (isEndState) Modifier.clickable(onClick = onTapRespawn) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(Color(0xAA000000))
                .padding(horizontal = 42.dp, vertical = 18.dp),
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = titleColor,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 4.sp,
                    shadow = shadow,
                ),
            )
            if (subtext.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtext,
                    style = TextStyle(
                        color = Color(0xFFFFE082),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        shadow = shadow,
                    ),
                )
            }
            if (isEndState) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "TAP TO RESPAWN AT GROVE CIRCLE",
                    color = Color(0xFFBDBDBD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
