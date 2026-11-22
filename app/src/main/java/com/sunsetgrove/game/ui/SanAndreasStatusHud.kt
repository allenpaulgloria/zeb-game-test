package com.sunsetgrove.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunsetgrove.game.gameplay.HudSnapshot
import com.sunsetgrove.game.gameplay.PlayerMode
import kotlinx.coroutines.delay

/**
 * Authentic GTA: San Andreas Top-Right Status Cluster + Top-Left Cheat Activated Toast
 * + Bottom-Right Italic District & Vehicle Toasts.
 */
@Composable
fun SanAndreasStatusHud(
    snapshot: HudSnapshot,
    modifier: Modifier = Modifier,
) {
    var flashTick by remember { mutableStateOf(true) }
    LaunchedEffect(snapshot.wantedFlashing) {
        while (snapshot.wantedFlashing) {
            flashTick = !flashTick
            delay(280)
        }
        flashTick = true
    }

    val outlineShadow = Shadow(color = Color.Black, offset = Offset(3f, 3f), blurRadius = 2f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        // 1. Iconic San Andreas Green Cash Readout ($00000500)
        val formattedCash = "$${snapshot.cash.toString().padStart(8, '0')}"
        Text(
            text = formattedCash,
            style = TextStyle(
                color = Color(0xFF43A047),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.sp,
                shadow = outlineShadow,
            ),
        )

        Spacer(Modifier.height(4.dp))

        // 2. Crimson Health Bar & White Armor / Vehicle Integrity Bar
        StatusBar(
            progress = (snapshot.health / 100f).coerceIn(0f, 1f),
            fillColor = Color(0xFFB71C1C),
            trackColor = Color(0xAA4A0C0C),
            widthDp = 128,
        )
        Spacer(Modifier.height(3.dp))
        StatusBar(
            progress = if (snapshot.playerMode == PlayerMode.DRIVING) {
                snapshot.vehicleHealthRatio
            } else {
                (snapshot.armor / 100f).coerceIn(0f, 1f)
            },
            fillColor = if (snapshot.playerMode == PlayerMode.DRIVING) Color(0xFFFFB74D) else Color(0xFFE0E6ED),
            trackColor = Color(0xAA262B30),
            widthDp = 128,
        )

        Spacer(Modifier.height(6.dp))

        // 3. Classic 6 Wanted Stars Row
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 1..6) {
                val active = i <= snapshot.wantedStars
                val visibleAlpha = when {
                    !active -> 0.28f
                    snapshot.wantedFlashing && !flashTick -> 0.25f
                    else -> 1.0f
                }
                Text(
                    text = "★",
                    modifier = Modifier.alpha(visibleAlpha),
                    style = TextStyle(
                        color = if (active) Color(0xFFFFCA28) else Color(0xFF1F1F1F),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        shadow = outlineShadow,
                    ),
                )
            }
        }

        // Speed / Altitude / Active Cheat pills
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (snapshot.playerMode == PlayerMode.DRIVING) {
                HudPill("${snapshot.speedKmh} KM/H", Color(0xFFFFD54F))
            } else if (snapshot.playerMode == PlayerMode.JETPACK) {
                HudPill("JETPACK  ALT ${snapshot.altitudeMeters}M", Color(0xFF4FC3F7))
            } else if (snapshot.playerMode == PlayerMode.SWIMMING) {
                HudPill("SWIMMING", Color(0xFF29B6F6))
            } else if (snapshot.playerMode == PlayerMode.CARJACKING) {
                HudPill("CARJACKING...", Color(0xFFFFB74D))
            }
            if (snapshot.neverWantedActive) {
                HudPill("AEZAKMI", Color(0xFF81C784))
            }
            if (snapshot.activeCheatCodes.isNotEmpty()) {
                HudPill("${snapshot.activeCheatCodes.size} CHEAT${if (snapshot.activeCheatCodes.size > 1) "S" else ""}", Color(0xFFFFB74D))
            }
        }

        if (snapshot.arrestedProgress > 0.05f) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "BUSTING... ${(snapshot.arrestedProgress * 100).toInt()}%",
                style = TextStyle(
                    color = Color(0xFF64B5F6),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    shadow = outlineShadow,
                ),
            )
        }
    }
}

@Composable
private fun StatusBar(
    progress: Float,
    fillColor: Color,
    trackColor: Color,
    widthDp: Int,
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(10.dp)
            .background(trackColor)
            .border(2.dp, Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(10.dp)
                .background(fillColor),
        )
    }
}

@Composable
private fun HudPill(label: String, accent: Color) {
    Text(
        text = label,
        color = accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(Color(0xBB000000), RoundedCornerShape(4.dp))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Authentic GTA: San Andreas top-left black-box "Cheat activated" corner prompt. */
@Composable
fun CheatActivatedCornerToast(
    message: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    if (alpha <= 0.02f || message.isBlank()) return
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(Color(0xD8000000), RoundedCornerShape(4.dp))
            .border(1.5.dp, Color(0xCCFFB74D), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

/** Classic fading bottom-right italic District Name & Vehicle Name toasts. */
@Composable
fun DistrictAndVehicleToasts(
    snapshot: HudSnapshot,
    modifier: Modifier = Modifier,
) {
    val outlineShadow = Shadow(color = Color.Black, offset = Offset(3f, 3f), blurRadius = 3f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        if (snapshot.vehicleToastAlpha > 0.02f && snapshot.vehicleToastName.isNotBlank()) {
            Text(
                text = snapshot.vehicleToastName,
                modifier = Modifier.alpha(snapshot.vehicleToastAlpha),
                style = TextStyle(
                    color = Color(0xFF4CAF50),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    shadow = outlineShadow,
                ),
            )
        }
        if (snapshot.districtToastAlpha > 0.02f && snapshot.districtName.isNotBlank()) {
            Text(
                text = snapshot.districtName,
                modifier = Modifier.alpha(snapshot.districtToastAlpha),
                style = TextStyle(
                    color = Color(0xFFE0E0E0),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    shadow = outlineShadow,
                ),
            )
        }
    }
}
