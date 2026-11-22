package com.sunsetgrove.game.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunsetgrove.game.gameplay.CheatCategory
import com.sunsetgrove.game.gameplay.CheatCodeManager
import com.sunsetgrove.game.gameplay.CheatDefinition
import com.sunsetgrove.game.gameplay.CheatMode
import kotlinx.coroutines.delay

/**
 * Classic GTA: San Andreas Pause Menu with dedicated CHEAT CODES button
 * opening the 22 authentic cheat codes screen (double-tap to activate/toggle).
 */
@Composable
fun PauseAndCheatsOverlay(
    activeCheatCodes: Set<String>,
    audioMuted: Boolean,
    playTrackInGame: Boolean,
    lastCheatMessage: String,
    onResume: () -> Unit,
    onRespawn: () -> Unit,
    onClearCheats: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleTrackInGame: () -> Unit,
    onReplayIntro: () -> Unit,
    onCheatDoubleTapped: (String) -> Unit,
) {
    var showCheatScreen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xE60B0B0D), Color(0xEB1A120A), Color(0xF00B0B0D))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!showCheatScreen) {
            PauseMenuCard(
                activeCheatCount = activeCheatCodes.size,
                audioMuted = audioMuted,
                playTrackInGame = playTrackInGame,
                onResume = onResume,
                onOpenCheats = { showCheatScreen = true },
                onRespawn = onRespawn,
                onClearCheats = onClearCheats,
                onToggleAudio = onToggleAudio,
                onToggleTrackInGame = onToggleTrackInGame,
                onReplayIntro = onReplayIntro,
            )
        } else {
            CheatCodesScreen(
                activeCheatCodes = activeCheatCodes,
                lastCheatMessage = lastCheatMessage,
                onBackToPause = { showCheatScreen = false },
                onResumeGame = onResume,
                onCheatDoubleTapped = onCheatDoubleTapped,
            )
        }
    }
}

@Composable
private fun PauseMenuCard(
    activeCheatCount: Int,
    audioMuted: Boolean,
    playTrackInGame: Boolean,
    onResume: () -> Unit,
    onOpenCheats: () -> Unit,
    onRespawn: () -> Unit,
    onClearCheats: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleTrackInGame: () -> Unit,
    onReplayIntro: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(380.dp)
            .background(Color(0xEE121215), RoundedCornerShape(10.dp))
            .border(2.dp, Color(0xFFFF9800), RoundedCornerShape(10.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "TEMU THEFT AUTO",
            color = Color(0xFFFFB74D),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Text(
            text = "PAUSED  //  SAN ANDREAS SANDBOX",
            color = Color(0xFF9E9E9E),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(4.dp))

        PauseMenuButton(label = "RESUME GAME", accent = Color(0xFF4CAF50), onClick = onResume)
        PauseMenuButton(
            label = if (activeCheatCount > 0) "CHEAT CODES  ($activeCheatCount ACTIVE)" else "CHEAT CODES  (23 CHEATS)",
            accent = Color(0xFFFFB300),
            onClick = onOpenCheats,
        )
        PauseMenuButton(
            label = if (playTrackInGame) "♫ INTRO TRACK IN-GAME: ON" else "♫ INTRO TRACK IN-GAME: OFF",
            accent = if (playTrackInGame) Color(0xFF66BB6A) else Color(0xFF90A4AE),
            onClick = onToggleTrackInGame,
        )
        PauseMenuButton(label = "REPLAY INTRO VIDEO", accent = Color(0xFFFF8A65), onClick = onReplayIntro)
        PauseMenuButton(label = "RESPAWN AT GROVE CIRCLE", accent = Color(0xFF64B5F6), onClick = onRespawn)
        PauseMenuButton(
            label = if (audioMuted) "GAME SFX: MUTED" else "GAME SFX: ON",
            accent = Color(0xFFB0BEC5),
            onClick = onToggleAudio,
        )
        if (activeCheatCount > 0) {
            PauseMenuButton(label = "CLEAR ALL ACTIVE CHEATS", accent = Color(0xFFEF5350), onClick = onClearCheats)
        }
    }
}

@Composable
private fun PauseMenuButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1F24), RoundedCornerShape(6.dp))
            .border(1.5.dp, accent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
    }
}

/**
 * Categorized scrollable list of 22 authentic GTA: San Andreas cheat codes.
 * Double-tap any row to activate or toggle immediately!
 */
@Composable
private fun CheatCodesScreen(
    activeCheatCodes: Set<String>,
    lastCheatMessage: String,
    onBackToPause: () -> Unit,
    onResumeGame: () -> Unit,
    onCheatDoubleTapped: (String) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf<CheatCategory?>(null) }
    var recentlyTriggeredCode by remember { mutableStateOf<String?>(null) }
    var singleTapHintCode by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(recentlyTriggeredCode) {
        if (recentlyTriggeredCode != null) {
            delay(650)
            recentlyTriggeredCode = null
        }
    }
    LaunchedEffect(singleTapHintCode) {
        if (singleTapHintCode != null) {
            delay(1400)
            singleTapHintCode = null
        }
    }

    val filteredCheats = remember(selectedCategory) {
        if (selectedCategory == null) CheatCodeManager.catalog
        else CheatCodeManager.catalog.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .fillMaxHeight(0.90f)
            .background(Color(0xF2111216), RoundedCornerShape(10.dp))
            .border(2.dp, Color(0xFFFFB300), RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "GTA: SAN ANDREAS CHEAT CODES",
                    color = Color(0xFFFFC107),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = if (lastCheatMessage.isNotBlank()) lastCheatMessage.uppercase()
                    else "DOUBLE-TAP ANY CHEAT CODE BELOW TO ACTIVATE OR TOGGLE",
                    color = if (lastCheatMessage.isNotBlank()) Color(0xFF69F0AE) else Color(0xFFBDBDBD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallActionChip("BACK", Color(0xFF90A4AE), onBackToPause)
                SmallActionChip("RESUME", Color(0xFF4CAF50), onResumeGame)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Category Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterTab("ALL (${CheatCodeManager.catalog.size})", selectedCategory == null) { selectedCategory = null }
            CheatCategory.entries.forEach { cat ->
                FilterTab(cat.label, selectedCategory == cat) { selectedCategory = cat }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Cheat list with double-tap activation
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(filteredCheats, key = { it.code }) { cheat ->
                val isActive = activeCheatCodes.contains(cheat.code)
                val justTriggered = recentlyTriggeredCode == cheat.code
                val showTapHint = singleTapHintCode == cheat.code

                CheatRowCard(
                    cheat = cheat,
                    isActive = isActive,
                    justTriggered = justTriggered,
                    showTapHint = showTapHint,
                    onSingleTap = { singleTapHintCode = cheat.code },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        singleTapHintCode = null
                        recentlyTriggeredCode = cheat.code
                        onCheatDoubleTapped(cheat.code)
                    },
                )
            }
        }
    }
}

@Composable
private fun CheatRowCard(
    cheat: CheatDefinition,
    isActive: Boolean,
    justTriggered: Boolean,
    showTapHint: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            justTriggered -> Color(0xFF1F4B2C)
            isActive -> Color(0xFF182A1E)
            else -> Color(0xFF1B1D23)
        },
        label = "cheatRowBg",
    )
    val borderColor = when {
        justTriggered -> Color(0xFF69F0AE)
        isActive -> Color(0xFF4CAF50)
        else -> Color(0xFF323642)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(7.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(7.dp))
            .pointerInput(cheat.code) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { onDoubleTap() },
                )
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = cheat.code,
                    color = Color(0xFFFFCA28),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
                if (cheat.alias != null) {
                    Text(
                        text = "/ ${cheat.alias}",
                        color = Color(0xFF90CAF9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    text = "• ${cheat.title}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (showTapHint) "Double-tap to activate ${cheat.code}!" else cheat.description,
                color = if (showTapHint) Color(0xFFFFD54F) else Color(0xFFB0B8C4),
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.width(10.dp))

        val badgeText = when {
            justTriggered -> "ACTIVATED!"
            cheat.mode == CheatMode.INSTANT -> "INSTANT"
            isActive -> "ON"
            else -> "OFF"
        }
        val badgeColor = when {
            justTriggered -> Color(0xFF69F0AE)
            cheat.mode == CheatMode.INSTANT -> Color(0xFFFFB74D)
            isActive -> Color(0xFF66BB6A)
            else -> Color(0xFF78909C)
        }
        Text(
            text = badgeText,
            color = badgeColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .background(Color(0xAA0A0A0D), RoundedCornerShape(4.dp))
                .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) Color(0xFFFFB300) else Color(0xFF23262F), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color(0xFFD0D5DD),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun SmallActionChip(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF1F222A), RoundedCornerShape(5.dp))
            .border(1.5.dp, accent, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}
