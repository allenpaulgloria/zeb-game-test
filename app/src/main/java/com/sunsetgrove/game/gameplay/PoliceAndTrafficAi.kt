package com.sunsetgrove.game.gameplay

import com.sunsetgrove.game.world.CollisionWorld
import com.sunsetgrove.game.world.Math3D
import com.sunsetgrove.game.world.RoadGraph
import com.sunsetgrove.game.world.Vec2
import com.sunsetgrove.game.world.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.random.Random

/**
 * Drives:
 * - Always-active Roaming LSPD Patrol Cruisers across the entire extended map (4 patrols even at 0 stars!)
 * - Escalating Police A* pursuit + intercept PIT steering + 3+ star roadblocks when wanted > 0
 * - Ambient civilian traffic cruising RoadGraph lanes (respecting YLTEICZ aggressive drivers)
 */
class PoliceAndTrafficAi(
    private val roadGraph: RoadGraph,
    private val physics: ArcadeVehiclePhysics,
) {
    companion object {
        const val BASE_PATROL_POLICE_COUNT = 4
    }

    private val rng = Random(1992)

    fun updatePoliceSpawning(
        vehicles: MutableList<VehicleState>,
        desiredWantedPolice: Int,
        playerPos: Vec3,
        nextId: () -> Int,
    ) {
        // Keep at least BASE_PATROL_POLICE_COUNT roaming around the map at all times!
        val targetPoliceCount = max(BASE_PATROL_POLICE_COUNT, desiredWantedPolice)
        val activePolice = vehicles.filter { it.isPoliceAi && !it.isWrecked && !it.isPlayerControlled }

        // Despawn excess pursuit units once wanted level clears (keeping the 4 roaming patrols)
        if (activePolice.size > targetPoliceCount) {
            var toRemove = activePolice.size - targetPoliceCount
            val iter = vehicles.iterator()
            while (iter.hasNext() && toRemove > 0) {
                val v = iter.next()
                if (v.isPoliceAi && !v.isPlayerControlled && v.pos.horizontalDistanceTo(playerPos) > 55f) {
                    iter.remove()
                    toRemove--
                }
            }
        }

        val needed = targetPoliceCount - vehicles.count { it.isPoliceAi && !it.isWrecked && !it.isPlayerControlled }
        if (needed > 0) {
            val candidates = roadGraph.nodes.filter { node ->
                val d = node.pos.distanceTo(playerPos.xz())
                d in 44f..170f
            }.shuffled(rng)

            var spawned = 0
            for (node in candidates) {
                if (spawned >= needed) break
                val occupied = vehicles.any { it.pos.xz().distanceTo(node.pos) < 10f }
                if (occupied) continue
                val nextNode = node.neighbors.firstOrNull() ?: node.id
                val yaw = Math3D.yawFromDirection(playerPos.x - node.pos.x, playerPos.z - node.pos.z)
                val cruiser = VehicleState(
                    id = nextId(),
                    archetype = VehicleArchetype.POLICE,
                    pos = Vec3(node.pos.x, 0.25f, node.pos.z),
                    yawRad = yaw,
                    isPoliceAi = true,
                    hasNpcDriver = true,
                    sirenActive = desiredWantedPolice > 0,
                    aiTargetNodeId = nextNode,
                    isRoadblock = desiredWantedPolice >= 3 && spawned == needed - 1 && rng.nextFloat() < 0.35f,
                )
                vehicles.add(cruiser)
                spawned++
            }
        }
    }

    fun stepAiVehicles(
        vehicles: List<VehicleState>,
        playerPos: Vec3,
        playerVel: Vec3,
        wantedStars: Int,
        dt: Float,
        world: CollisionWorld,
        cheats: CheatCodeManager,
    ) {
        val aggressiveTraffic = cheats.isActive(CheatCodeManager.CODE_AGGRESSIVE_DRIVERS)

        for (v in vehicles) {
            if (v.isPlayerControlled || v.isWrecked) continue

            if (v.isPoliceAi) {
                v.sirenActive = wantedStars > 0
                if (v.sirenActive) v.sirenPhase = (v.sirenPhase + dt * 6.5f) % 2f
                stepPoliceCruiser(v, playerPos, playerVel, wantedStars, dt, world, cheats)
            } else if (v.isTrafficAi) {
                stepTrafficCar(v, playerPos, aggressiveTraffic, dt, world, cheats)
            }
        }
    }

    private fun stepPoliceCruiser(
        v: VehicleState,
        playerPos: Vec3,
        playerVel: Vec3,
        wantedStars: Int,
        dt: Float,
        world: CollisionWorld,
        cheats: CheatCodeManager,
    ) {
        if (wantedStars == 0) {
            // Roam around the extended map calmly on patrol!
            v.isRoadblock = false
            stepTrafficCar(v, playerPos, aggressive = false, dt, world, cheats, patrolSpeed = 13.5f)
            return
        }

        val distToPlayer = v.pos.horizontalDistanceTo(playerPos)
        if (v.isRoadblock && distToPlayer > 28f && distToPlayer < 85f) {
            physics.stepVehicle(v, 0f, 0f, 1f, true, false, false, v.yawRad, dt, world, cheats)
            return
        }

        val targetPos: Vec2 = if (distToPlayer < 42f) {
            val lead = (distToPlayer / 30f).coerceIn(0f, 1.2f)
            Vec2(playerPos.x + playerVel.x * lead, playerPos.z + playerVel.z * lead)
        } else {
            v.aiRepathTimer -= dt
            if (v.aiRepathTimer <= 0f || v.aiPath.isEmpty()) {
                val startId = roadGraph.nearestNode(v.pos.xz()).id
                val goalId = roadGraph.nearestNode(playerPos.xz()).id
                v.aiPath = roadGraph.findPath(startId, goalId)
                v.aiPathIndex = 0
                v.aiRepathTimer = 1.6f
            }
            while (v.aiPathIndex < v.aiPath.lastIndex && v.pos.xz().distanceTo(v.aiPath[v.aiPathIndex]) < 11f) {
                v.aiPathIndex++
            }
            v.aiPath.getOrElse(v.aiPathIndex) { playerPos.xz() }
        }

        val desiredYaw = atan2(targetPos.x - v.pos.x, targetPos.z - v.pos.z)
        val angleErr = Math3D.normalizeAngleRad(desiredYaw - v.yawRad)
        val steer = Math3D.clamp(-angleErr * 1.8f, -1f, 1f)
        val chaseThrottle = (0.58f + wantedStars * 0.07f).coerceAtMost(1.0f)
        val gas = if (abs(angleErr) > 1.3f && v.speed > 9f) 0.25f else chaseThrottle
        val brake = if (abs(angleErr) > 1.6f && v.speed > 10f) 0.8f else 0f

        physics.stepVehicle(v, steer, gas, brake, false, false, false, v.yawRad, dt, world, cheats)
    }

    private fun stepTrafficCar(
        v: VehicleState,
        playerPos: Vec3,
        aggressive: Boolean,
        dt: Float,
        world: CollisionWorld,
        cheats: CheatCodeManager,
        patrolSpeed: Float = 11.5f,
    ) {
        val targetNode = roadGraph.nodes[v.aiTargetNodeId.coerceIn(0, roadGraph.nodes.lastIndex)]
        val dx = targetNode.pos.x - v.pos.x
        val dz = targetNode.pos.z - v.pos.z
        val dist = kotlin.math.sqrt(dx * dx + dz * dz)
        if (dist < 10f) {
            val neighbors = targetNode.neighbors
            if (neighbors.isNotEmpty()) {
                v.aiTargetNodeId = neighbors[rng.nextInt(neighbors.size)]
            }
        }

        val desiredYaw = atan2(dx, dz)
        val angleErr = Math3D.normalizeAngleRad(desiredYaw - v.yawRad)
        val steer = Math3D.clamp(-angleErr * 1.5f, -1f, 1f)

        val distToPlayer = v.pos.horizontalDistanceTo(playerPos)
        val targetSpeed = if (aggressive) 22f else patrolSpeed
        val shouldBrake = !aggressive && distToPlayer < 8.5f && v.speed > 3f
        val gas = if (!shouldBrake && v.speed < targetSpeed) (if (aggressive) 0.92f else 0.48f) else 0f
        val brake = if (shouldBrake) 0.85f else 0f

        physics.stepVehicle(v, steer, gas, brake, false, false, false, v.yawRad, dt, world, cheats)
    }
}
