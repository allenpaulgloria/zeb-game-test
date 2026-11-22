package com.sunsetgrove.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunsetgrove.game.R

object IntroMusicConfig {
    const val YOUTUBE_REFERENCE_URL = "https://youtu.be/IeIUm-aqaCg?si=vXmCyZb0KGrciZXf"
    const val YOUTUBE_VIDEO_ID = "IeIUm-aqaCg"
}

/**
 * Pure Audio-Only San Andreas Loading / Intro Screen (NO broken YouTube video box!).
 * Plays music via native Android MediaPlayer immediately on launch and keeps playing
 * smoothly in-game when toggled ON.
 */
@Composable
fun IntroMusicScreen(
    trackLabel: String,
    playMusicInGame: Boolean,
    onTogglePlayMusicInGame: (Boolean) -> Unit,
    onOpenYouTubeReference: () -> Unit,
    onEnterGame: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF140804), Color(0xFF3E1C09), Color(0xFF0E0A08))
                )
            )
            .padding(horizontal = 28.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(540.dp)
                .background(Color(0xDD111115), RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo_banner),
                contentDescription = "Temu Theft Auto",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(240.dp)
                    .height(126.dp),
            )
            Text(
                text = "GROVE STREET  •  IDLEWOOD  •  VINEWOOD  •  VERONA BEACH",
                color = Color(0xFFFFB74D),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
            )

            // Pure Audio-Only Now Playing Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .background(Color(0xFF1A1E24), RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color(0xFF66BB6A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "♫ NOW PLAYING (AUDIO ONLY): $trackLabel",
                    color = Color(0xFFA5D6A7),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Toggle: Keep Music Playing In-Game
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .background(Color(0xFF171920), RoundedCornerShape(8.dp))
                    .border(1.5.dp, if (playMusicInGame) Color(0xFF4CAF50) else Color(0xFF555B66), RoundedCornerShape(8.dp))
                    .clickable { onTogglePlayMusicInGame(!playMusicInGame) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "KEEP MUSIC PLAYING WHILE PLAYING",
                    color = if (playMusicInGame) Color.White else Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Switch(
                    checked = playMusicInGame,
                    onCheckedChange = onTogglePlayMusicInGame,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50),
                    ),
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2E7D32), RoundedCornerShape(8.dp))
                        .border(2.dp, Color(0xFFA5D6A7), RoundedCornerShape(8.dp))
                        .clickable(onClick = onEnterGame)
                        .padding(horizontal = 30.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = if (playMusicInGame) "PLAY GAME (MUSIC ON) ▶" else "PLAY GAME ▶",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF21242D), RoundedCornerShape(8.dp))
                        .border(1.5.dp, Color(0xFFEF5350), RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenYouTubeReference)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "OPEN YT LINK",
                        color = Color(0xFFFFCDD2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}
