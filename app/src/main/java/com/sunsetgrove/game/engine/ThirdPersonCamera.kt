package com.sunsetgrove.game.engine

import com.sunsetgrove.game.gameplay.PlayerMode
import com.sunsetgrove.game.gameplay.VehicleArchetype
import com.sunsetgrove.game.world.CollisionWorld
import com.sunsetgrove.game.world.Math3D
import com.sunsetgrove.game.world.Vec2
import com.sunsetgrove.game.world.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class CameraPose(
    val eye: Vec3,
    val target: Vec3,
    val fovDeg: Double,
)

/**
 * Smooth third-person spring-arm camera tailored for GTA: San Andreas:
 * - On-foot orbit camera with touch-drag yaw/pitch
 * - Jetpack aerial follow camera
 * - Driving chase camera scaling distance for street cars vs Monster Truck / Rhino Tank
 * - Speed-reactive FOV, recoil/explosion shake, and building collision pullback
 */
class ThirdPersonCamera {
    var yawRad: Float = -1.57f
        private set
    var pitchRad: Float = 0.24f
        private set
    var closeMode: Boolean = false

    private var smoothedTarget = Vec3(84f, 1.5f, 8f)
    private var smoothedFov = 58.0
    private var userOrbitIdleTimer = 0f

    fun toggleDistancePreset() {
        closeMode = !closeMode
    }

    fun applyUserDrag(deltaYaw: Float, deltaPitch: Float) {
        if (deltaYaw != 0f || deltaPitch != 0f) {
            yawRad = Math3D.normalizeAngleRad(yawRad - deltaYaw)
            pitchRad = Math3D.clamp(pitchRad + deltaPitch, -0.10f, 0.92f)
            userOrbitIdleTimer = 0f
        }
    }

    fun update(
        dt: Float,
        mode: PlayerMode,
        actorPos: Vec3,
        actorYawRad: Float,
        speedMetersPerSec: Float,
        vehicleArchetype: VehicleArchetype?,
        shakeIntensity: Float,
        world: CollisionWorld,
    ): CameraPose {
        userOrbitIdleTimer += dt

        // Gently swing camera behind driving/flying direction after user stops dragging
        if (mode == PlayerMode.DRIVING && speedMetersPerSec > 2.5f && userOrbitIdleTimer > 1.2f) {
            yawRad = Math3D.lerpAngleRad(yawRad, actorYawRad, 3.2f * dt)
        }

        val baseDist = when (mode) {
            PlayerMode.ON_FOOT -> if (closeMode) 3.6f else 4.9f
            PlayerMode.JETPACK -> if (closeMode) 5.2f else 6.8f
            PlayerMode.DRIVING -> when {
                vehicleArchetype?.isTank == true -> if (closeMode) 8.4f else 10.6f
                vehicleArchetype?.isMonsterTruck == true -> if (closeMode) 7.8f else 9.6f
                else -> if (closeMode) 5.8f else 7.6f
            }
        }
        val focusHeight = when (mode) {
            PlayerMode.ON_FOOT -> 1.48f
            PlayerMode.JETPACK -> 1.65f
            PlayerMode.DRIVING -> when {
                vehicleArchetype?.isTank == true -> 2.3f
                vehicleArchetype?.isMonsterTruck == true -> 2.4f
                else -> 1.45f
            }
        }

        val desiredTarget = Vec3(actorPos.x, actorPos.y + focusHeight, actorPos.z)
        smoothedTarget = Vec3(
            Math3D.lerp(smoothedTarget.x, desiredTarget.x, 12f * dt),
            Math3D.lerp(smoothedTarget.y, desiredTarget.y, 10f * dt),
            Math3D.lerp(smoothedTarget.z, desiredTarget.z, 12f * dt),
        )

        // Camera sits BEHIND forwardFromYaw(yawRad)
        val horizDist = baseDist * cos(pitchRad)
        val vertOffset = baseDist * sin(pitchRad) + 0.55f
        val rawEyeX = smoothedTarget.x - sin(yawRad) * horizDist
        val rawEyeZ = smoothedTarget.z - cos(yawRad) * horizDist
        val rawEyeY = (smoothedTarget.y + vertOffset).coerceAtLeast(0.8f)

        // Raycast pullback so walls don't clip the camera
        val hitFrac = world.raycastBuildingsFraction(
            origin = Vec2(smoothedTarget.x, smoothedTarget.z),
            target = Vec2(rawEyeX, rawEyeZ),
            elevation = rawEyeY,
        )
        val safeFrac = (hitFrac * 0.90f).coerceIn(0.28f, 1.0f)
        var eyeX = Math3D.lerp(smoothedTarget.x, rawEyeX, safeFrac)
        var eyeZ = Math3D.lerp(smoothedTarget.z, rawEyeZ, safeFrac)
        var eyeY = rawEyeY

        // Recoil / Explosion shake
        if (shakeIntensity > 0.01f) {
            val s = shakeIntensity * 0.35f
            eyeX += (Random.nextFloat() * 2f - 1f) * s
            eyeY += (Random.nextFloat() * 2f - 1f) * s
            eyeZ += (Random.nextFloat() * 2f - 1f) * s
        }

        val targetFov = if (mode == PlayerMode.DRIVING) {
            56.0 + (speedMetersPerSec.coerceIn(0f, 40f) / 40.0) * 16.0
        } else {
            58.0
        }
        smoothedFov = Math3D.lerp(smoothedFov.toFloat(), targetFov.toFloat(), 5f * dt).toDouble()

        return CameraPose(Vec3(eyeX, eyeY, eyeZ), smoothedTarget, smoothedFov)
    }
}
