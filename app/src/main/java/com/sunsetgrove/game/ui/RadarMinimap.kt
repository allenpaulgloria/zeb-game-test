package com.sunsetgrove.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunsetgrove.game.gameplay.BlipKind
import com.sunsetgrove.game.gameplay.HudSnapshot
import com.sunsetgrove.game.world.DistrictMap
import com.sunsetgrove.game.world.Math3D
import kotlin.math.cos
import kotlin.math.sin

/**
 * Iconic GTA: San Andreas circular rotating radar minimap (bottom-left):
 * - Thick black bezel with orbiting white 'N' North badge
 * - Live roads, beachfront ocean coastline, building footprints
 * - Player directional arrow, special Tank/Monster icons, & flashing red/blue police blips
 */
@Composable
fun RadarMinimap(
    snapshot: HudSnapshot,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
) {
    val worldRange = 115f // meters from center to radar rim

    Box(
        modifier = modifier.size(diameter + 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(Color(0xFF2B3A26))
                .border(4.dp, Color(0xFF0A0A0A), CircleShape)
        ) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            val radius = size.minDimension * 0.5f
            val scale = radius / worldRange

            // Rotate map so camera forward (cameraYawRad) points UP on the radar!
            // World forward = (sin(yaw), cos(yaw)); Screen up = (0, -1).
            // Helper to project world (wx, wz) into radar space relative to player
            val camYaw = snapshot.cameraYawRad
            val fwdX = sin(camYaw)
            val fwdZ = cos(camYaw)
            val rightX = -cos(camYaw)
            val rightZ = sin(camYaw)

            fun worldToRadar(wx: Float, wz: Float): Offset {
                val dx = wx - snapshot.playerX
                val dz = wz - snapshot.playerZ
                val localRight = dx * rightX + dz * rightZ
                val localFwd = dx * fwdX + dz * fwdZ
                return Offset(cx + localRight * scale, cy - localFwd * scale)
            }

            // 1. Verona Beach ocean & sand zones (west of x = -85)
            val beachTop = worldToRadar(-135f, 220f)
            val beachBot = worldToRadar(-135f, -220f)
            drawLine(
                color = Color(0xFFD6B97A),
                start = beachTop,
                end = beachBot,
                strokeWidth = 85f * scale,
            )
            val oceanTop = worldToRadar(-220f, 240f)
            val oceanBot = worldToRadar(-220f, -240f)
            drawLine(
                color = Color(0xFF285F8A),
                start = oceanTop,
                end = oceanBot,
                strokeWidth = 110f * scale,
            )

            // 2. Street grid lines
            val roadColor = Color(0xFF4F545A)
            val roadW = 14f * scale
            for (x in listOf(-70f, 0f, 70f)) {
                drawLine(roadColor, worldToRadar(x, -150f), worldToRadar(x, 150f), strokeWidth = roadW, cap = StrokeCap.Square)
            }
            for (z in listOf(-80f, 0f, 80f)) {
                drawLine(roadColor, worldToRadar(-100f, z), worldToRadar(120f, z), strokeWidth = roadW, cap = StrokeCap.Square)
            }
            // Grove Circle cul-de-sac bulb
            drawCircle(
                color = roadColor,
                radius = 15f * scale,
                center = worldToRadar(118f, 0f),
            )

            // 3. Building footprints
            for (b in DistrictMap.buildings) {
                val p = worldToRadar(b.x, b.z)
                val bw = b.width * scale
                val bd = b.depth * scale
                drawRect(
                    color = if (b.roofLandable) Color(0xFF6E757C) else Color(0xFF8C7B65),
                    topLeft = Offset(p.x - bw * 0.5f, p.y - bd * 0.5f),
                    size = Size(bw, bd),
                )
            }

            // 4. Vehicle & Police blips
            for (blip in snapshot.radarBlips) {
                var p = worldToRadar(blip.x, blip.z)
                // Clamp police blips to radar rim so you always see pursuit direction!
                val vx = p.x - cx
                val vy = p.y - cy
                val d = kotlin.math.sqrt(vx * vx + vy * vy)
                val maxR = radius - 8f
                if (d > maxR) {
                    if (blip.kind == BlipKind.POLICE || blip.kind == BlipKind.TANK || blip.kind == BlipKind.MONSTER) {
                        p = Offset(cx + vx / d * maxR, cy + vy / d * maxR)
                    } else {
                        continue
                    }
                }
                when (blip.kind) {
                    BlipKind.POLICE -> {
                        val policeColor = if (blip.sirenPhase < 1f) Color(0xFFFF2B2B) else Color(0xFF2B7FFF)
                        drawCircle(Color.Black, radius = 6.5f, center = p)
                        drawCircle(policeColor, radius = 4.8f, center = p)
                    }
                    BlipKind.TANK -> {
                        drawRect(Color.Black, topLeft = Offset(p.x - 6f, p.y - 6f), size = Size(12f, 12f))
                        drawRect(Color(0xFF84CC16), topLeft = Offset(p.x - 4.5f, p.y - 4.5f), size = Size(9f, 9f))
                    }
                    BlipKind.MONSTER -> {
                        drawRect(Color.Black, topLeft = Offset(p.x - 5.5f, p.y - 5.5f), size = Size(11f, 11f))
                        drawRect(Color(0xFF38BDF8), topLeft = Offset(p.x - 4f, p.y - 4f), size = Size(8f, 8f))
                    }
                    BlipKind.VEHICLE -> {
                        drawCircle(Color(0xCCFFE082), radius = 3.2f, center = p)
                    }
                }
            }

            // 5. Player heading triangle at center
            val relYawDeg = Math3D.normalizeAngleRad(snapshot.cameraYawRad - snapshot.playerYawRad) * Math3D.RAD_TO_DEG
            rotate(degrees = relYawDeg, pivot = Offset(cx, cy)) {
                val arrow = Path().apply {
                    moveTo(cx, cy - 9f)
                    lineTo(cx + 6.5f, cy + 7f)
                    lineTo(cx, cy + 3.5f)
                    lineTo(cx - 6.5f, cy + 7f)
                    close()
                }
                drawPath(arrow, color = Color.White)
                drawPath(arrow, color = Color.Black, style = Stroke(width = 2.2f))
            }

            // Inner ring highlight
            drawCircle(
                color = Color(0x44FFFFFF),
                radius = radius - 3f,
                style = Stroke(width = 1.5f),
            )
        }

        // Orbiting North ('N') badge along the radar bezel
        val northAngleRad = snapshot.cameraYawRad // +Z is North; when camYaw=0, North is top
        val badgeRadiusDp = diameter.value * 0.5f + 1f
        val bx = (-sin(northAngleRad) * badgeRadiusDp).dp
        val by = (-cos(northAngleRad) * badgeRadiusDp).dp
        Box(
            modifier = Modifier
                .offset(x = bx, y = by)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .border(1.5f.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "N",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
