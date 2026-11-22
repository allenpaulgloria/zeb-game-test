package com.sunsetgrove.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunsetgrove.game.gameplay.HudSnapshot
import com.sunsetgrove.game.gameplay.PlayerMode
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Shared thread-safe touch control state consumed every 60Hz frame by GameSimulation.
 */
class TouchInputBridge {
    @Volatile var stickX: Float = 0f
    @Volatile var stickY: Float = 0f
    @Volatile var steerLeft: Boolean = false
    @Volatile var steerRight: Boolean = false
    @Volatile var gas: Boolean = false
    @Volatile var brake: Boolean = false
    @Volatile var handbrake: Boolean = false
    @Volatile var nitro: Boolean = false
    @Volatile var sprint: Boolean = false
    @Volatile var jetpackAscend: Boolean = false
    @Volatile var jetpackDescend: Boolean = false

    // One-shot latches consumed on next simulation frame
    @Volatile var jumpQueued: Boolean = false
    @Volatile var enterExitQueued: Boolean = false
    @Volatile var fireCannonQueued: Boolean = false

    // Camera orbit delta accumulator
    private var camDx = 0f
    private var camDy = 0f

    @Synchronized
    fun addCameraDrag(dx: Float, dy: Float) {
        camDx += dx
        camDy += dy
    }

    @Synchronized
    fun consumeCameraDrag(): Pair<Float, Float> {
        val out = camDx to camDy
        camDx = 0f
        camDy = 0f
        return out
    }
}

/**
 * Responsive multi-touch overlay adapting automatically across:
 * - ON_FOOT: Virtual stick + Sprint + Jump + contextual Enter Vehicle
 * - JETPACK: Virtual stick + ASCEND + DESCEND + DROP JETPACK
 * - DRIVING: Left/Right steer + Gas/Brake/Drift + contextual NITRO + Rhino Tank FIRE CANNON
 */
@Composable
fun TouchControlsOverlay(
    snapshot: HudSnapshot,
    bridge: TouchInputBridge,
    trackPlayingInGame: Boolean,
    onPauseClick: () -> Unit,
    onCameraToggleClick: () -> Unit,
    onToggleTrackInGame: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Right-half invisible touch-drag camera orbit surface (behind buttons)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        bridge.addCameraDrag(dragAmount.x * 0.0048f, dragAmount.y * 0.0034f)
                    }
                }
        )

        // Top-Left: PAUSE (||) button, CAM toggle, Music Track toggle, & Cheat Activated corner toast
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TapActionChip("|| PAUSE", Color(0xFFFFB300), onPauseClick)
            TapActionChip("CAM", Color(0xFF90CAF9), onCameraToggleClick)
            TapActionChip(
                label = if (trackPlayingInGame) "♫ TRACK: ON" else "♫ TRACK: OFF",
                accent = if (trackPlayingInGame) Color(0xFF66BB6A) else Color(0xFF78909C),
                onClick = onToggleTrackInGame,
            )
            CheatActivatedCornerToast(
                message = snapshot.cheatToastMessage,
                alpha = snapshot.cheatToastAlpha,
            )
        }

        // Bottom-Left: Radar Minimap + Movement Stick / Driving Steering
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RadarMinimap(snapshot = snapshot, diameter = 124.dp)

            if (snapshot.playerMode == PlayerMode.DRIVING) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldCircleButton("◀", 64.dp, Color(0xFF90CAF9)) { bridge.steerLeft = it }
                    HoldCircleButton("▶", 64.dp, Color(0xFF90CAF9)) { bridge.steerRight = it }
                }
            } else {
                VirtualJoystick(
                    size = 114.dp,
                    onMove = { x, y ->
                        bridge.stickX = x
                        bridge.stickY = y
                    },
                )
            }
        }

        // Bottom-Right: Context-Sensitive Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DistrictAndVehicleToasts(snapshot = snapshot)

            when (snapshot.playerMode) {
                PlayerMode.ON_FOOT -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (snapshot.nearbyVehicleName != null) {
                            TapCircleButton("ENTER\n${snapshot.nearbyVehicleName.take(8).uppercase()}", 70.dp, Color(0xFF66BB6A)) {
                                bridge.enterExitQueued = true
                            }
                        }
                        TapCircleButton("JUMP", 58.dp, Color(0xFFFFCA28)) { bridge.jumpQueued = true }
                        HoldCircleButton("SPRINT", 66.dp, Color(0xFF42A5F5)) { bridge.sprint = it }
                    }
                }

                PlayerMode.JETPACK -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TapCircleButton("DROP\nPACK", 60.dp, Color(0xFFEF5350)) { bridge.enterExitQueued = true }
                        HoldCircleButton("DOWN", 60.dp, Color(0xFF90CAF9)) { bridge.jetpackDescend = it }
                        HoldCircleButton("THRUST\nUP", 74.dp, Color(0xFFFF9800)) { bridge.jetpackAscend = it }
                    }
                }

                PlayerMode.DRIVING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (snapshot.activeVehicleArchetype?.isTank == true) {
                            val ready = snapshot.tankCannonReadyRatio >= 0.99f
                            TapCircleButton(
                                label = if (ready) "FIRE\nCANNON" else "RELOAD",
                                size = 74.dp,
                                accent = if (ready) Color(0xFFFF3D00) else Color(0xFF757575),
                            ) {
                                if (ready) bridge.fireCannonQueued = true
                            }
                        }
                        if (snapshot.nitroEnabled) {
                            HoldCircleButton("NITRO", 60.dp, Color(0xFF00E5FF)) { bridge.nitro = it }
                        }
                        TapCircleButton("EXIT", 56.dp, Color(0xFFEF5350)) { bridge.enterExitQueued = true }
                        HoldCircleButton("DRIFT", 58.dp, Color(0xFFFFCA28)) { bridge.handbrake = it }
                        HoldCircleButton("BRAKE", 62.dp, Color(0xFFFF7043)) { bridge.brake = it }
                        HoldCircleButton("GAS", 74.dp, Color(0xFF66BB6A)) { bridge.gas = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualJoystick(
    size: Dp,
    onMove: (Float, Float) -> Unit,
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0x55000000))
            .border(2.dp, Color(0x88FFFFFF), CircleShape)
            .pointerInput(Unit) {
                val maxR = this.size.width * 0.38f
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val next = knobOffset + dragAmount
                    val dist = sqrt(next.x * next.x + next.y * next.y)
                    val clamped = if (dist > maxR && dist > 1e-4f) next * (maxR / dist) else next
                    knobOffset = clamped
                    // Screen right = +X, Screen up = +Y forward
                    onMove((clamped.x / maxR).coerceIn(-1f, 1f), (-clamped.y / maxR).coerceIn(-1f, 1f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
            drawCircle(
                color = Color(0xCCFFB74D),
                radius = min(this.size.width, this.size.height) * 0.22f,
                center = center + knobOffset,
            )
        }
    }
}

@Composable
private fun HoldCircleButton(
    label: String,
    size: Dp,
    accent: Color,
    onHeldChange: (Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) accent.copy(alpha = 0.65f) else Color(0x88101216))
            .border(2.dp, accent, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val anyDown = ev.changes.any { it.pressed }
                        if (anyDown != pressed) {
                            pressed = anyDown
                            onHeldChange(anyDown)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, lineHeight = 12.sp)
    }
}

@Composable
private fun TapCircleButton(
    label: String,
    size: Dp,
    accent: Color,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0x99101216))
            .border(2.dp, accent, CircleShape)
            .pointerInput(onTap) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.any { it.pressed && !it.previousPressed }) {
                            onTap()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, lineHeight = 11.sp)
    }
}

@Composable
private fun TapActionChip(
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(Color(0xBB0D0E12), RoundedCornerShape(6.dp))
            .border(1.5.dp, accent, RoundedCornerShape(6.dp))
            .pointerInput(onClick) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.any { it.pressed && !it.previousPressed }) {
                            onClick()
                        }
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}
