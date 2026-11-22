package com.sunsetgrove.game.gameplay

import com.sunsetgrove.game.world.CollisionWorld
import com.sunsetgrove.game.world.DistrictMap
import com.sunsetgrove.game.world.Math3D
import com.sunsetgrove.game.world.Vec2
import com.sunsetgrove.game.world.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class NpcRole {
    CIVILIAN,
    PURPLE_GANG,      // Armed Purple GTA Ballas Gang member!
    CARJACKED_DRIVER, // Pulled out of a stolen car (20% chance armed with Gun or Bat!)
}

enum class NpcWeapon {
    NONE,
    GUN,
    BAT,
}

data class NpcState(
    val id: Int,
    val role: NpcRole,
    var pos: Vec3,
    var vel: Vec3 = Vec3(),
    var yawRad: Float = 0f,
    var health: Float = 100f,
    var weapon: NpcWeapon = NpcWeapon.NONE,
    var animState: String = "Walk",
    var hostileToPlayer: Boolean = false,
    var fleeing: Boolean = false,
    var attackCooldown: Float = 0f,
    var staggerTimer: Float = 0f,
    var patrolTarget: Vec2 = Vec2(),
    var shootoutTargetNpcId: Int? = null,
    var shootoutDuration: Float = 0f,
    var isDead: Boolean = false,
)

data class BulletProjectile(
    val id: Int,
    var pos: Vec3,
    var vel: Vec3,
    val fromPlayer: Boolean,
    val damage: Float = 11f,
    var lifetime: Float = 1.6f,
)

data class NpcStepEvents(
    val gunshotsFired: Int = 0,
    val batHitPlayer: Boolean = false,
    val damageToPlayer: Float = 0f,
    val gangShootoutStarted: Boolean = false,
)

/**
 * Manages living street life across the extended San Andreas map:
 * - Walking civilian pedestrians patrolling sidewalks & fleeing gunfire
 * - Armed Purple GTA Gang (Ballas) patrolling the map, auto-shooting the player on sight,
 *   and starting street shootouts against other people every 2-6 minutes (120-360 seconds)
 * - Carjacked drivers pulled out of stolen cars with an exact 20% chance to be armed
 *   with a Gun or Baseball Bat and fight back!
 */
class NpcSystem(
    private val rng: Random = Random(2004),
) {
    companion object {
        const val CARJACK_ARMED_PROBABILITY = 0.20f
        const val GANG_SHOOTOUT_MIN_SEC = 120f // 2 minutes
        const val GANG_SHOOTOUT_MAX_SEC = 360f // 6 minutes
    }

    val npcs = mutableListOf<NpcState>()
    val bullets = mutableListOf<BulletProjectile>()

    var nextGangShootoutTimer: Float = randomShootoutInterval()
        private set

    private var nextBulletId = 50000

    fun randomShootoutInterval(): Float =
        GANG_SHOOTOUT_MIN_SEC + rng.nextFloat() * (GANG_SHOOTOUT_MAX_SEC - GANG_SHOOTOUT_MIN_SEC)

    fun spawnInitialPopulation(nextId: () -> Int) {
        npcs.clear()
        bullets.clear()
        nextGangShootoutTimer = randomShootoutInterval()

        // 18 Walking street pedestrians across sidewalks in all districts
        val civSpawns = listOf(
            Vec2(82f, -12f), Vec2(96f, 12f), Vec2(62f, 24f), Vec2(62f, -34f),
            Vec2(10f, -18f), Vec2(-10f, 16f), Vec2(10f, 64f), Vec2(-10f, -64f),
            Vec2(-62f, 20f), Vec2(-62f, -28f), Vec2(-84f, 45f), Vec2(-84f, -45f),
            Vec2(32f, 92f), Vec2(-32f, 92f), Vec2(32f, -92f), Vec2(-32f, -92f),
            Vec2(142f, -65f), Vec2(142f, 65f),
        )
        for (p in civSpawns) {
            npcs.add(
                NpcState(
                    id = nextId(),
                    role = NpcRole.CIVILIAN,
                    pos = Vec3(p.x, 0.25f, p.z),
                    weapon = NpcWeapon.NONE,
                    patrolTarget = pickSidewalkTarget(p),
                )
            )
        }

        // 10 Armed Purple GTA Gang members (Ballas) patrolling Idlewood, Grove borders, Vinewood & Downtown
        val ballasSpawns = listOf(
            Vec2(130f, -86f), Vec2(148f, -110f), Vec2(115f, -72f), // Idlewood / Ballas Turf
            Vec2(62f, -92f), Vec2(62f, 92f),                       // Grove Ave borders
            Vec2(-12f, -90f), Vec2(12f, 90f),                      // Vinewood North/South
            Vec2(-62f, 88f), Vec2(-62f, -88f),                     // Beach Blvd corners
            Vec2(148f, 88f),                                        // East Downtown
        )
        for (p in ballasSpawns) {
            npcs.add(
                NpcState(
                    id = nextId(),
                    role = NpcRole.PURPLE_GANG,
                    pos = Vec3(p.x, 0.25f, p.z),
                    weapon = NpcWeapon.GUN,
                    hostileToPlayer = true, // Purple gang ALWAYS shoots the player automatically!
                    patrolTarget = pickSidewalkTarget(p),
                )
            )
        }
    }

    /**
     * Spawns the NPC driver pulled out of a car during carjacking.
     * 20% chance to be armed (50/50 Gun or Baseball Bat) and fight back!
     */
    fun spawnCarjackedDriver(
        vehiclePos: Vec3,
        vehicleYawRad: Float,
        nextId: () -> Int,
        rollOverride: Float? = null,
    ): NpcState {
        // Eject to the left side of the car door
        val leftX = cos(vehicleYawRad)
        val leftZ = -sin(vehicleYawRad)
        val spawnPos = Vec3(vehiclePos.x + leftX * 1.4f, vehiclePos.y, vehiclePos.z + leftZ * 1.4f)

        val roll = rollOverride ?: rng.nextFloat()
        val isArmed = roll < CARJACK_ARMED_PROBABILITY
        val weapon = if (isArmed) {
            if (rng.nextBoolean()) NpcWeapon.GUN else NpcWeapon.BAT
        } else {
            NpcWeapon.NONE
        }

        val npc = NpcState(
            id = nextId(),
            role = NpcRole.CARJACKED_DRIVER,
            pos = spawnPos,
            vel = Vec3(leftX * 4.5f, 0f, leftZ * 4.5f),
            yawRad = vehicleYawRad,
            weapon = weapon,
            hostileToPlayer = isArmed,
            fleeing = !isArmed,
            staggerTimer = 0.75f, // Visible pulled-out stumble animation!
            attackCooldown = 0.9f,
            patrolTarget = Vec2(spawnPos.x + leftX * 30f, spawnPos.z + leftZ * 30f),
        )
        npcs.add(npc)
        return npc
    }

    fun firePlayerBullet(origin: Vec3, aimYawRad: Float) {
        val dx = sin(aimYawRad)
        val dz = cos(aimYawRad)
        bullets.add(
            BulletProjectile(
                id = nextBulletId++,
                pos = Vec3(origin.x + dx * 0.7f, origin.y + 1.38f, origin.z + dz * 0.7f),
                vel = Vec3(dx * 65f, 0f, dz * 65f),
                fromPlayer = true,
                damage = 38f,
            )
        )
    }

    fun panicNearbyCivilians(epicenter: Vec3, radius: Float = 38f) {
        for (n in npcs) {
            if (n.isDead || n.role != NpcRole.CIVILIAN) continue
            if (n.pos.horizontalDistanceTo(epicenter) <= radius) {
                n.fleeing = true
                val away = (n.pos.xz() - epicenter.xz()).normalized()
                n.patrolTarget = Vec2(
                    Math3D.clamp(n.pos.x + away.x * 55f, -125f, 240f),
                    Math3D.clamp(n.pos.z + away.z * 55f, -240f, 240f),
                )
            }
        }
    }

    fun step(
        dt: Float,
        playerPos: Vec3,
        playerInVehicle: Boolean,
        playerInTank: Boolean,
        world: CollisionWorld,
    ): NpcStepEvents {
        var gunshots = 0
        var batHit = false
        var damageToPlayer = 0f
        var shootoutStarted = false

        // 1. Autonomous 2-6 minute Purple Gang shootout trigger against nearby people!
        nextGangShootoutTimer -= dt
        if (nextGangShootoutTimer <= 0f) {
            nextGangShootoutTimer = randomShootoutInterval()
            if (triggerAmbientGangShootout()) {
                shootoutStarted = true
            }
        }

        // 2. Step each NPC
        for (npc in npcs) {
            if (npc.isDead) continue
            npc.attackCooldown = max(0f, npc.attackCooldown - dt)

            // Pulled-out carjacking tumble/stagger motion
            if (npc.staggerTimer > 0f) {
                npc.staggerTimer = max(0f, npc.staggerTimer - dt)
                val nextX = npc.pos.x + npc.vel.x * dt
                val nextZ = npc.pos.z + npc.vel.z * dt
                val res = world.resolveHorizontal(nextX, nextZ, npc.pos.y, 0.42f)
                npc.pos = Vec3(res.x, world.supportHeightAt(res.x, res.z, npc.pos.y), res.z)
                npc.vel = Vec3(npc.vel.x * max(0f, 1f - 5f * dt), 0f, npc.vel.z * max(0f, 1f - 5f * dt))
                npc.animState = "Idle"
                continue
            }

            val distToPlayer = npc.pos.horizontalDistanceTo(playerPos)
            val seesPlayer = distToPlayer < 32f &&
                playerPos.y < 14f &&
                world.hasLineOfSight(npc.pos.xz(), playerPos.xz(), 1.5f)

            // Purple Gang auto-targets player whenever in range, OR shoots other NPCs during shootouts!
            if (npc.hostileToPlayer && seesPlayer) {
                val targetYaw = atan2(playerPos.x - npc.pos.x, playerPos.z - npc.pos.z)
                npc.yawRad = Math3D.lerpAngleRad(npc.yawRad, targetYaw, 9f * dt)

                when (npc.weapon) {
                    NpcWeapon.GUN -> {
                        if (distToPlayer > 14f) {
                            moveNpcToward(npc, playerPos.xz(), 4.2f, dt, world)
                            npc.animState = "Run"
                        } else {
                            npc.animState = "Idle"
                        }
                        if (npc.attackCooldown <= 0f) {
                            npc.attackCooldown = if (npc.role == NpcRole.PURPLE_GANG) 1.05f else 1.25f
                            fireNpcBullet(npc, playerPos)
                            gunshots++
                            panicNearbyCivilians(npc.pos, 32f)
                        }
                    }
                    NpcWeapon.BAT -> {
                        if (distToPlayer > 2.3f) {
                            moveNpcToward(npc, playerPos.xz(), 5.4f, dt, world)
                            npc.animState = "Run"
                        } else {
                            npc.animState = "Idle"
                            if (npc.attackCooldown <= 0f && !playerInVehicle) {
                                npc.attackCooldown = 0.95f
                                batHit = true
                                damageToPlayer += 14f
                            }
                        }
                    }
                    NpcWeapon.NONE -> {
                        npc.fleeing = true
                    }
                }
            } else if (npc.role == NpcRole.PURPLE_GANG && npc.shootoutDuration > 0f) {
                npc.shootoutDuration = max(0f, npc.shootoutDuration - dt)
                val targetNpc = npcs.firstOrNull { it.id == npc.shootoutTargetNpcId && !it.isDead }
                if (targetNpc != null && npc.pos.horizontalDistanceTo(targetNpc.pos) < 38f) {
                    val targetYaw = atan2(targetNpc.pos.x - npc.pos.x, targetNpc.pos.z - npc.pos.z)
                    npc.yawRad = Math3D.lerpAngleRad(npc.yawRad, targetYaw, 8f * dt)
                    npc.animState = "Idle"
                    if (npc.attackCooldown <= 0f) {
                        npc.attackCooldown = 0.95f
                        fireNpcBullet(npc, targetNpc.pos)
                        gunshots++
                        panicNearbyCivilians(npc.pos, 36f)
                    }
                } else {
                    npc.shootoutDuration = 0f
                    npc.shootoutTargetNpcId = null
                }
            } else {
                // Normal sidewalk walking / fleeing
                val speed = if (npc.fleeing) 5.8f else 2.2f
                npc.animState = if (npc.fleeing) "Run" else "Walk"
                if (npc.pos.xz().distanceTo(npc.patrolTarget) < 2.8f) {
                    npc.fleeing = false
                    npc.patrolTarget = pickSidewalkTarget(npc.pos.xz())
                }
                moveNpcToward(npc, npc.patrolTarget, speed, dt, world)
            }
        }

        // 3. Step Bullet Projectiles
        val bIter = bullets.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            b.lifetime -= dt
            b.pos = b.pos + b.vel * dt

            if (b.lifetime <= 0f || world.resolveHorizontal(b.pos.x, b.pos.z, b.pos.y, 0.12f).hit) {
                bIter.remove()
                continue
            }

            if (b.fromPlayer) {
                val hitNpc = npcs.firstOrNull { !it.isDead && it.pos.horizontalDistanceTo(b.pos) < 0.85f }
                if (hitNpc != null) {
                    hitNpc.health -= b.damage
                    if (hitNpc.health <= 0f) hitNpc.isDead = true
                    bIter.remove()
                    continue
                }
            } else {
                // Check hit on player
                val hitRadius = if (playerInVehicle) 1.7f else 0.75f
                if (b.pos.horizontalDistanceTo(playerPos) < hitRadius && kotlin.math.abs(b.pos.y - (playerPos.y + 1.1f)) < 1.5f) {
                    if (!playerInTank) {
                        damageToPlayer += if (playerInVehicle) b.damage * 0.45f else b.damage
                    }
                    bIter.remove()
                    continue
                }
                // Check hit on civilian NPCs during Purple Gang street shootouts
                val hitCiv = npcs.firstOrNull { !it.isDead && it.role == NpcRole.CIVILIAN && it.pos.horizontalDistanceTo(b.pos) < 0.85f }
                if (hitCiv != null) {
                    hitCiv.health -= b.damage
                    hitCiv.fleeing = true
                    if (hitCiv.health <= 0f) hitCiv.isDead = true
                    bIter.remove()
                    continue
                }
            }
        }

        return NpcStepEvents(
            gunshotsFired = gunshots,
            batHitPlayer = batHit,
            damageToPlayer = damageToPlayer,
            gangShootoutStarted = shootoutStarted,
        )
    }

    fun triggerAmbientGangShootout(): Boolean {
        val shooters = npcs.filter { it.role == NpcRole.PURPLE_GANG && !it.isDead }
        val civilians = npcs.filter { it.role == NpcRole.CIVILIAN && !it.isDead }
        if (shooters.isEmpty() || civilians.isEmpty()) return false

        var started = false
        for (gang in shooters) {
            val victim = civilians.minByOrNull { it.pos.horizontalDistanceTo(gang.pos) } ?: continue
            if (victim.pos.horizontalDistanceTo(gang.pos) < 42f) {
                gang.shootoutTargetNpcId = victim.id
                gang.shootoutDuration = 12f
                started = true
            }
        }
        // If none were within 42m, pick one gang member to target nearest civilian anyway
        if (!started) {
            val gang = shooters[rng.nextInt(shooters.size)]
            val victim = civilians.minByOrNull { it.pos.horizontalDistanceTo(gang.pos) }
            if (victim != null) {
                gang.shootoutTargetNpcId = victim.id
                gang.shootoutDuration = 12f
                started = true
            }
        }
        return started
    }

    private fun fireNpcBullet(shooter: NpcState, targetPos: Vec3) {
        val dx = targetPos.x - shooter.pos.x
        val dz = targetPos.z - shooter.pos.z
        val dist = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
        // Slight realistic spread
        val spread = (rng.nextFloat() - 0.5f) * 0.14f
        val baseYaw = atan2(dx, dz) + spread
        val vx = sin(baseYaw) * 48f
        val vz = cos(baseYaw) * 48f
        bullets.add(
            BulletProjectile(
                id = nextBulletId++,
                pos = Vec3(shooter.pos.x + sin(baseYaw) * 0.65f, shooter.pos.y + 1.35f, shooter.pos.z + cos(baseYaw) * 0.65f),
                vel = Vec3(vx, (targetPos.y - shooter.pos.y) / (dist / 48f).coerceAtLeast(0.2f), vz),
                fromPlayer = false,
                damage = 8f,
            )
        )
    }

    private fun moveNpcToward(npc: NpcState, target: Vec2, speed: Float, dt: Float, world: CollisionWorld) {
        val dx = target.x - npc.pos.x
        val dz = target.z - npc.pos.z
        val dist = kotlin.math.sqrt(dx * dx + dz * dz)
        if (dist < 0.2f) return
        val ux = dx / dist
        val uz = dz / dist
        npc.yawRad = Math3D.lerpAngleRad(npc.yawRad, atan2(ux, uz), 7f * dt)
        val nextX = npc.pos.x + ux * speed * dt
        val nextZ = npc.pos.z + uz * speed * dt
        // Keep walking NPCs out of the ocean!
        val safeX = max(DistrictMap.OCEAN_SHORELINE_X + 6f, nextX)
        val res = world.resolveHorizontal(safeX, nextZ, npc.pos.y, 0.42f)
        npc.pos = Vec3(res.x, world.supportHeightAt(res.x, res.z, npc.pos.y), res.z)
    }

    private fun pickSidewalkTarget(near: Vec2): Vec2 {
        val sidewalkXs = listOf(-82f, -60f, -10f, 10f, 60f, 82f, 130f, 150f)
        val sidewalkZs = listOf(-150f, -92f, -70f, -12f, 12f, 70f, 92f, 150f)
        val tx = sidewalkXs[rng.nextInt(sidewalkXs.size)]
        val tz = sidewalkZs[rng.nextInt(sidewalkZs.size)]
        return if (rng.nextBoolean()) Vec2(tx, Math3D.clamp(near.z + (rng.nextFloat() - 0.5f) * 90f, -180f, 180f))
        else Vec2(Math3D.clamp(near.x + (rng.nextFloat() - 0.5f) * 90f, -95f, 180f), tz)
    }
}
