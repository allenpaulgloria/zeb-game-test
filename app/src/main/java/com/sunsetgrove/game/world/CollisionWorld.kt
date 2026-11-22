package com.sunsetgrove.game.world

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class AabbCollider(
    val id: String,
    val minX: Float,
    val maxX: Float,
    val minZ: Float,
    val maxZ: Float,
    val height: Float,
    val roofLandable: Boolean,
)

data class CircleCollider(
    val x: Float,
    val z: Float,
    val radius: Float,
    val height: Float = 8f,
)

data class CollisionResolution(
    val x: Float,
    val z: Float,
    val hit: Boolean,
    val normalX: Float = 0f,
    val normalZ: Float = 0f,
)

/**
 * Fast 3D world collision solver supporting:
 * - Sliding wall collisions for on-foot & vehicles
 * - Landable building rooftops for Jetpack (YECGAA) & Super Jump (KANGAROO)
 * - Stunt ramp elevation sampling
 * - 2D line-of-sight raycasts for camera pullback & police evasion
 */
class CollisionWorld(
    buildings: List<BuildingSpec> = DistrictMap.buildings,
    props: List<PropSpec> = DistrictMap.props,
    private val ramps: List<StuntRampSpec> = DistrictMap.stuntRamps,
) {
    val buildingBoxes: List<AabbCollider> = buildings.map { b ->
        // Approximate rotated footprint conservatively for 90-degree increments
        val swap = abs(b.yawDeg % 180f) in 45f..135f
        val hw = (if (swap) b.depth else b.width) * 0.5f
        val hd = (if (swap) b.width else b.depth) * 0.5f
        AabbCollider(
            id = b.id,
            minX = b.x - hw,
            maxX = b.x + hw,
            minZ = b.z - hd,
            maxZ = b.z + hd,
            height = b.height,
            roofLandable = b.roofLandable,
        )
    }

    val propCircles: List<CircleCollider> = props.map { p ->
        CircleCollider(p.x, p.z, p.colliderRadius)
    }

    /** Returns ground / ramp / rooftop elevation directly underneath (x, z) for an actor at altitude y. */
    fun supportHeightAt(x: Float, z: Float, actorY: Float): Float {
        var ground = 0.25f // standard road/terrain height
        // Check stunt ramps (facing +Z)
        for (ramp in ramps) {
            val dx = x - ramp.x
            val dz = z - ramp.z
            if (abs(dx) <= ramp.width * 0.5f && abs(dz) <= ramp.length * 0.5f) {
                val t = Math3D.clamp((dz + ramp.length * 0.5f) / ramp.length, 0f, 1f)
                ground = max(ground, 0.25f + t * ramp.height)
            }
        }
        // Check landable flat rooftops if actor is at or above roof deck
        for (box in buildingBoxes) {
            if (box.roofLandable &&
                x >= box.minX - 0.3f && x <= box.maxX + 0.3f &&
                z >= box.minZ - 0.3f && z <= box.maxZ + 0.3f &&
                actorY >= box.height - 0.6f
            ) {
                ground = max(ground, box.height)
            }
        }
        return ground
    }

    /** Resolves circle vs building AABBs & prop circles at altitude actorY. */
    fun resolveHorizontal(x: Float, z: Float, actorY: Float, radius: Float): CollisionResolution {
        var rx = Math3D.clamp(x, -DistrictMap.WORLD_HALF_EXTENT + 4f, DistrictMap.WORLD_HALF_EXTENT - 4f)
        var rz = Math3D.clamp(z, -DistrictMap.WORLD_HALF_EXTENT + 4f, DistrictMap.WORLD_HALF_EXTENT - 4f)
        var hit = (rx != x || rz != z)
        var nx = 0f
        var nz = 0f

        for (box in buildingBoxes) {
            // Skip walls if flying cleanly above rooftop
            if (actorY >= box.height - 0.4f) continue
            val closestX = Math3D.clamp(rx, box.minX, box.maxX)
            val closestZ = Math3D.clamp(rz, box.minZ, box.maxZ)
            val dx = rx - closestX
            val dz = rz - closestZ
            val distSq = dx * dx + dz * dz
            if (distSq < radius * radius) {
                hit = true
                if (distSq > 1e-6f) {
                    val dist = sqrt(distSq)
                    val push = radius - dist
                    val ux = dx / dist
                    val uz = dz / dist
                    rx += ux * push
                    rz += uz * push
                    nx = ux
                    nz = uz
                } else {
                    // Inside box center fallback: eject to nearest face
                    val dLeft = abs(rx - box.minX)
                    val dRight = abs(box.maxX - rx)
                    val dBack = abs(rz - box.minZ)
                    val dFront = abs(box.maxZ - rz)
                    val minD = min(min(dLeft, dRight), min(dBack, dFront))
                    when (minD) {
                        dLeft -> { rx = box.minX - radius; nx = -1f; nz = 0f }
                        dRight -> { rx = box.maxX + radius; nx = 1f; nz = 0f }
                        dBack -> { rz = box.minZ - radius; nx = 0f; nz = -1f }
                        else -> { rz = box.maxZ + radius; nx = 0f; nz = 1f }
                    }
                }
            }
        }

        // Prop poles/trees only collide near ground
        if (actorY < 7.5f) {
            for (c in propCircles) {
                val dx = rx - c.x
                val dz = rz - c.z
                val minDist = radius + c.radius
                val distSq = dx * dx + dz * dz
                if (distSq < minDist * minDist && distSq > 1e-6f) {
                    val dist = sqrt(distSq)
                    val push = minDist - dist
                    val ux = dx / dist
                    val uz = dz / dist
                    rx += ux * push
                    rz += uz * push
                    hit = true
                    nx = ux
                    nz = uz
                }
            }
        }

        return CollisionResolution(rx, rz, hit, nx, nz)
    }

    /** Returns true if no building blocks the 2D segment between a and b. */
    fun hasLineOfSight(a: Vec2, b: Vec2, elevation: Float = 1.5f): Boolean {
        for (box in buildingBoxes) {
            if (elevation >= box.height) continue
            if (segmentIntersectsAabb(a.x, a.z, b.x, b.z, box.minX, box.maxX, box.minZ, box.maxZ)) {
                return false
            }
        }
        return true
    }

    /** Raycast fraction [0..1] from origin to target against buildings (for camera pullback). */
    fun raycastBuildingsFraction(origin: Vec2, target: Vec2, elevation: Float): Float {
        var bestT = 1f
        val dx = target.x - origin.x
        val dz = target.z - origin.z
        for (box in buildingBoxes) {
            if (elevation >= box.height) continue
            val t = segmentAabbHitT(origin.x, origin.z, dx, dz, box.minX, box.maxX, box.minZ, box.maxZ)
            if (t != null && t < bestT) bestT = t
        }
        return bestT
    }

    private fun segmentIntersectsAabb(
        x0: Float, z0: Float, x1: Float, z1: Float,
        minX: Float, maxX: Float, minZ: Float, maxZ: Float,
    ): Boolean = segmentAabbHitT(x0, z0, x1 - x0, z1 - z0, minX, maxX, minZ, maxZ) != null

    private fun segmentAabbHitT(
        ox: Float, oz: Float, dx: Float, dz: Float,
        minX: Float, maxX: Float, minZ: Float, maxZ: Float,
    ): Float? {
        var tMin = 0f
        var tMax = 1f

        if (abs(dx) < 1e-6f) {
            if (ox < minX || ox > maxX) return null
        } else {
            val inv = 1f / dx
            var t1 = (minX - ox) * inv
            var t2 = (maxX - ox) * inv
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return null
        }

        if (abs(dz) < 1e-6f) {
            if (oz < minZ || oz > maxZ) return null
        } else {
            val inv = 1f / dz
            var t1 = (minZ - oz) * inv
            var t2 = (maxZ - oz) * inv
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return null
        }

        return tMin
    }
}
