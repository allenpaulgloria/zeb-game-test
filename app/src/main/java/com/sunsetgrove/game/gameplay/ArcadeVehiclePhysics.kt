package com.sunsetgrove.game.gameplay

import com.sunsetgrove.game.world.CollisionWorld
import com.sunsetgrove.game.world.Math3D
import com.sunsetgrove.game.world.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class VehicleStepOutput(
    val crashedWithWorld: Boolean = false,
    val crashSpeed: Float = 0f,
    val firedShell: TankProjectile? = null,
)

/**
 * Arcade vehicle dynamics tuned for GTA: San Andreas feel:
 * - Street sedans, muscle cars, police cruisers, Hotring & Bloodring racers
 * - Monster Truck (bouncy lifted suspension, 4WS feel, car-crushing mass)
 * - Rhino Army Tank (neutral-steer pivot tracks, camera-aimed rotating turret, cannon fire)
 * - Cheat hooks: COXEFGU (Nitro), PGGOMOY (Perfect Grip + Hop), RIPAZHA (Flying Cars)
 */
class ArcadeVehiclePhysics {

    private var nextProjectileId = 1000

    fun stepVehicle(
        v: VehicleState,
        steerInput: Float,
        gasInput: Float,
        brakeInput: Float,
        handbrake: Boolean,
        nitroPressed: Boolean,
        fireCannonPressed: Boolean,
        aimYawRad: Float,
        dt: Float,
        world: CollisionWorld,
        cheats: CheatCodeManager,
    ): VehicleStepOutput {
        if (v.isWrecked) {
            v.speed *= max(0f, 1f - 5f * dt)
            v.vel = Vec3(v.vel.x * max(0f, 1f - 5f * dt), v.vel.y - 18f * dt, v.vel.z * max(0f, 1f - 5f * dt))
            val groundY = world.supportHeightAt(v.pos.x, v.pos.z, v.pos.y)
            v.pos = Vec3(v.pos.x + v.vel.x * dt, max(groundY, v.pos.y + v.vel.y * dt), v.pos.z + v.vel.z * dt)
            if (v.pos.y <= groundY + 0.01f) v.vel = Vec3(v.vel.x, 0f, v.vel.z)
            return VehicleStepOutput()
        }

        val arch = v.archetype
        val nitroActive = v.isPlayerControlled && nitroPressed && cheats.isActive(CheatCodeManager.CODE_NITRO)
        val perfectHandling = v.isPlayerControlled && cheats.isActive(CheatCodeManager.CODE_PERFECT_HANDLING)
        val flyingCars = v.isPlayerControlled && cheats.isActive(CheatCodeManager.CODE_FLYING_CARS)

        val maxSpd = arch.maxSpeed * (if (nitroActive) 1.55f else 1f)
        val accel = arch.acceleration * (if (nitroActive) 1.75f else 1f)

        // Longitudinal acceleration / braking / reverse
        var targetAccel = 0f
        if (gasInput > 0.05f) {
            targetAccel += accel * gasInput
        }
        if (brakeInput > 0.05f) {
            if (v.speed > 1.2f) {
                targetAccel -= accel * 1.9f * brakeInput
            } else {
                targetAccel -= accel * 0.75f * brakeInput
            }
        }
        if (handbrake) {
            targetAccel -= v.speed * 1.6f
        }

        // Rolling resistance & aerodynamic drag
        val drag = if (abs(gasInput) < 0.05f && abs(brakeInput) < 0.05f) 2.2f else 0.35f
        v.speed += (targetAccel - v.speed * drag * 0.35f) * dt
        v.speed = Math3D.clamp(v.speed, -arch.reverseSpeed, maxSpd)

        // Steering: Rhino Tank can neutral-steer in place; regular cars steer proportionally to speed
        val steerFactor = if (arch.isTank) {
            1.0f
        } else {
            val speedNorm = Math3D.clamp(abs(v.speed) / 6.5f, 0f, 1f)
            speedNorm * (if (v.speed >= -0.1f) 1f else -1f)
        }
        val gripMult = if (perfectHandling) 1.45f else 1f
        val driftBoost = if (handbrake && !arch.isTank) 1.38f else 1f
        // Positive steerInput = right turn = decrease yaw (right-handed Y-up where +Z is forward, +X is left or right)
        // Wait: forwardFromYaw = (sin(yaw), cos(yaw)). Turning right means increasing X -> increasing yaw!
        // Let's keep convention consistent: forward = (sin(yaw), cos(yaw)), right = (-cos(yaw), sin(yaw)) or (+cos, -sin).
        // With forward=(sin(yaw), cos(yaw)), rotating yaw by -delta moves +Z toward -X (right on screen when camera is behind -Z looking +Z!).
        // Check: Camera at (0, h, -d) looking at (0, 0, 0) with up=(0,1,0): forward is +Z, right is -X!
        // Wait: cross(forward=(0,0,1), up=(0,1,0)) = (-1, 0, 0)! YES! In right-handed coords looking +Z, screen-right is -X!
        // So turning RIGHT decreases yawRad (sin(-delta) < 0 -> -X!).
        v.yawRad = Math3D.normalizeAngleRad(
            v.yawRad - steerInput * arch.turnRate * steerFactor * gripMult * driftBoost * dt
        )

        // Lateral velocity blending (gives juicy San Andreas tail-out drift!)
        val fwdX = sin(v.yawRad)
        val fwdZ = cos(v.yawRad)
        val desiredVx = fwdX * v.speed
        val desiredVz = fwdZ * v.speed
        val lateralGrip = when {
            perfectHandling -> arch.grip * 2.4f
            handbrake && !arch.isTank -> arch.grip * 0.32f
            else -> arch.grip
        }
        val blend = Math3D.clamp(lateralGrip * dt, 0f, 1f)
        var vx = Math3D.lerp(v.vel.x, desiredVx, blend)
        var vz = Math3D.lerp(v.vel.z, desiredVz, blend)

        // Vertical dynamics: gravity, ramps, PGGOMOY hop, RIPAZHA flying cars!
        var vy = v.vel.y
        val groundY = world.supportHeightAt(v.pos.x, v.pos.z, v.pos.y)
        val onGround = v.pos.y <= groundY + 0.18f

        if (flyingCars && v.speed > 16f) {
            val liftRatio = Math3D.clamp((v.speed - 16f) / 14f, 0f, 1f)
            val desiredClimb = if (gasInput > 0.2f) 7.5f * liftRatio else -2.5f
            vy = Math3D.lerp(vy, desiredClimb, 3.2f * dt)
            if (v.pos.y > 55f && vy > 0f) vy = 0f
        } else {
            if (onGround) {
                if (perfectHandling && handbrake && abs(v.speed) > 4f && vy <= 0.1f) {
                    vy = 7.5f // Classic San Andreas STICKLIKEGLUE car hop!
                } else if (vy < 0f) {
                    vy = 0f
                }
            } else {
                vy -= 21.0f * dt
            }
        }

        val nextX = v.pos.x + vx * dt
        val nextZ = v.pos.z + vz * dt
        val nextY = max(world.supportHeightAt(nextX, nextZ, v.pos.y), v.pos.y + vy * dt)

        // Resolve horizontal world collisions
        val res = world.resolveHorizontal(nextX, nextZ, nextY, arch.colliderRadius)
        var crashed = false
        var crashSpd = 0f
        if (res.hit) {
            val impactDot = vx * res.normalX + vz * res.normalZ
            if (impactDot < -3.5f) {
                crashed = true
                crashSpd = -impactDot
                // Reflect lightly & damp speed
                vx -= res.normalX * impactDot * 1.25f
                vz -= res.normalZ * impactDot * 1.25f
                v.speed *= 0.45f
                if (!cheats.isActive(CheatCodeManager.CODE_SMASH_N_BOOM) || !v.isPlayerControlled) {
                    val dmg = (crashSpd - 3.5f) * (if (arch.isTank) 0.15f else if (arch.isMonsterTruck) 0.6f else 2.2f)
                    v.health = max(0f, v.health - dmg)
                }
            }
        }

        // Launch off stunt ramp lip if moving fast up a ramp
        val prevGround = groundY
        val newGround = world.supportHeightAt(res.x, res.z, nextY)
        if (onGround && prevGround > 1.8f && newGround < 0.6f && abs(v.speed) > 10f) {
            vy = max(vy, abs(v.speed) * 0.42f)
        }

        v.pos = Vec3(res.x, nextY, res.z)
        v.vel = Vec3(vx, if (nextY <= newGround + 0.02f && vy < 0f) 0f else vy, vz)

        // Visual body pitch/roll & Monster Truck bouncy suspension
        val targetRoll = Math3D.clamp(steerInput * (v.speed / 24f) * (if (arch.isMonsterTruck) 0.22f else 0.11f), -0.26f, 0.26f)
        val targetPitch = when {
            flyingCars && !onGround -> Math3D.clamp(-vy * 0.035f, -0.32f, 0.25f)
            else -> Math3D.clamp(-targetAccel * (if (arch.isMonsterTruck) 0.008f else 0.004f), -0.14f, 0.14f)
        }
        v.rollRad = Math3D.lerp(v.rollRad, targetRoll, 7f * dt)
        v.pitchRad = Math3D.lerp(v.pitchRad, targetPitch, 7f * dt)
        if (arch.isMonsterTruck) {
            v.suspensionBounce = sin((v.pos.x + v.pos.z) * 0.8f) * min(0.14f, abs(v.speed) * 0.007f)
        }

        // Rhino Army Tank rotating turret & explosive cannon fire
        var firedShell: TankProjectile? = null
        if (arch.isTank) {
            v.cannonCooldown = max(0f, v.cannonCooldown - dt)
            val targetTurretYaw = if (v.isPlayerControlled) aimYawRad else v.yawRad
            v.turretYawRad = Math3D.lerpAngleRad(v.turretYawRad, targetTurretYaw, 4.5f * dt)

            if (v.isPlayerControlled && fireCannonPressed && v.cannonCooldown <= 0f) {
                v.cannonCooldown = 1.6f
                val tx = sin(v.turretYawRad)
                val tz = cos(v.turretYawRad)
                val muzzlePos = Vec3(v.pos.x + tx * 4.6f, v.pos.y + 1.95f, v.pos.z + tz * 4.6f)
                val shellVel = Vec3(tx * 58f + v.vel.x * 0.3f, 2.2f, tz * 58f + v.vel.z * 0.3f)
                firedShell = TankProjectile(nextProjectileId++, muzzlePos, shellVel)
                // Barrel recoil kick!
                v.speed -= 1.8f
                v.pitchRad -= 0.09f
            }
        }

        if (v.health <= 0f && !v.isWrecked) {
            v.isWrecked = true
        }

        return VehicleStepOutput(
            crashedWithWorld = crashed,
            crashSpeed = crashSpd,
            firedShell = firedShell,
        )
    }
}
