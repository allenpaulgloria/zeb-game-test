package com.sunsetgrove.game.gameplay

import com.sunsetgrove.game.world.CollisionWorld
import com.sunsetgrove.game.world.Math3D
import com.sunsetgrove.game.world.RoadGraph
import com.sunsetgrove.game.world.Vec2
import com.sunsetgrove.game.world.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Drives both ambient civilian traffic along RoadGraph lanes and pursuing LSPD police cruisers
 * with A* road routing, direct intercept PIT steering, flashing lightbars, and 3+ star roadblocks.
 */
class PoliceAndTrafficAi(
    private val roadGraph: RoadGraph,
    private val physics: ArcadeVehiclePhysics,
) {
    private val rng = Random(1992)

    fun updatePoliceSpawning(
        vehicles: MutableList<VehicleState>,
        desiredPolice: Int,
        playerPos: Vec3,
        nextId: () -> Int,
    ) {
        val activePolice = vehicles.filter { it.isPoliceAi && !it.isWrecked && !it.isPlayerControlled }

        // Despawn excess police far from player when wanted level drops
        if (desiredPolice == 0) {
            vehicles.removeAll { it.isPoliceAi && !it.isPlayerControlled && it.pos.horizontalDistanceTo(playerPos) > 45f }
            for (v in activePolice) {
                v.sirenActive = false
            }
            return
        }

        // Spawn missing police cruisers on road nodes 48m..130m away from player
        val needed = desiredPolice - activePolice.size
        if (needed > 0) {
            val candidates = roadGraph.nodes.filter { node ->
                val d = node.pos.distanceTo(playerPos.xz())
                d in 46f..135f
            }.shuffled(rng)

            var spawned = 0
            for (node in candidates) {
                if (spawned >= needed) break
                val occupied = vehicles.any { it.pos.xz().distanceTo(node.pos) < 10f }
                if (occupied) continue
                val yaw = Math3D.yawFromDirection(playerPos.x - node.pos.x, playerPos.z - node.pos.z)
                val cruiser = VehicleState(
                    id = nextId(),
                    archetype = VehicleArchetype.POLICE,
                    pos = Vec3(node.pos.x, 0.25f, node.pos.z),
                    yawRad = yaw,
                    isPoliceAi = true,
                    sirenActive = true,
                    aiTargetNodeId = node.id,
                    isRoadblock = desiredPolice >= 3 && spawned == needed - 1 && rng.nextFloat() < 0.4f,
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
            // Cruise calmly after chase ends
            stepTrafficCar(v, playerPos, aggressive = false, dt, world, cheats)
            return
        }

        val distToPlayer = v.pos.horizontalDistanceTo(playerPos)
        // Roadblock unit holds across street until player gets close
        if (v.isRoadblock && distToPlayer > 28f && distToPlayer < 85f) {
            physics.stepVehicle(v, 0f, 0f, 1f, true, false, false, v.yawRad, dt, world, cheats)
            return
        }

        val targetPos: Vec2 = if (distToPlayer < 40f) {
            // Direct intercept lead
            val lead = (distToPlayer / 30f).coerceIn(0f, 1.2f)
            Vec2(playerPos.x + playerVel.x * lead, playerPos.z + playerVel.z * lead)
        } else {
            // A* street pursuit
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
        // Recall: turning right (steer > 0) decreases yawRad; turning left (steer < 0) increases yawRad
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
    ) {
        val targetNode = roadGraph.nodes[v.aiTargetNodeId.coerceIn(0, roadGraph.nodes.lastIndex)]
        // Offset to right-hand lane
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

        // Slow down if directly approaching player on foot
        val distToPlayer = v.pos.horizontalDistanceTo(playerPos)
        val targetSpeed = if (aggressive) 22f else 11.5f
        val shouldBrake = !aggressive && distToPlayer < 8.5f && v.speed > 3f
        val gas = if (!shouldBrake && v.speed < targetSpeed) (if (aggressive) 0.92f else 0.45f) else 0f
        val brake = if (shouldBrake) 0.85f else 0f

        physics.stepVehicle(v, steer, gas, brake, false, false, false, v.yawRad, dt, world, cheats)
    }
}
