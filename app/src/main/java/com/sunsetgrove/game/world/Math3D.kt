package com.sunsetgrove.game.world

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec2(val x: Float = 0f, val z: Float = 0f) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, z + o.z)
    operator fun minus(o: Vec2) = Vec2(x - o.x, z - o.z)
    operator fun times(s: Float) = Vec2(x * s, z * s)
    fun lengthSq(): Float = x * x + z * z
    fun length(): Float = sqrt(lengthSq())
    fun normalized(): Vec2 {
        val len = length()
        return if (len > 1e-5f) Vec2(x / len, z / len) else Vec2(0f, 0f)
    }
    fun dot(o: Vec2): Float = x * o.x + z * o.z
    fun distanceTo(o: Vec2): Float = (this - o).length()
}

data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun lengthSq(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSq())
    fun xz(): Vec2 = Vec2(x, z)
    fun distanceTo(o: Vec3): Float = (this - o).length()
    fun horizontalDistanceTo(o: Vec3): Float {
        val dx = x - o.x
        val dz = z - o.z
        return sqrt(dx * dx + dz * dz)
    }
}

object Math3D {
    const val DEG_TO_RAD = (PI / 180.0).toFloat()
    const val RAD_TO_DEG = (180.0 / PI).toFloat()

    fun clamp(v: Float, min: Float, max: Float): Float = when {
        v < min -> min
        v > max -> max
        else -> v
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * clamp(t, 0f, 1f)

    fun normalizeAngleRad(a: Float): Float {
        var r = a % (2f * PI.toFloat())
        if (r > PI.toFloat()) r -= 2f * PI.toFloat()
        if (r < -PI.toFloat()) r += 2f * PI.toFloat()
        return r
    }

    fun lerpAngleRad(current: Float, target: Float, t: Float): Float {
        val diff = normalizeAngleRad(target - current)
        return normalizeAngleRad(current + diff * clamp(t, 0f, 1f))
    }

    fun forwardFromYaw(yawRad: Float): Vec2 = Vec2(sin(yawRad), cos(yawRad))

    fun yawFromDirection(dx: Float, dz: Float): Float = atan2(dx, dz)

    /**
     * Build a column-major 4x4 transform matrix (Filament convention):
     * T * Ry(yaw) * Rx(pitch) * Rz(roll) * S
     */
    fun composeMatrix(
        tx: Float, ty: Float, tz: Float,
        yawRad: Float = 0f,
        pitchRad: Float = 0f,
        rollRad: Float = 0f,
        sx: Float = 1f, sy: Float = 1f, sz: Float = 1f,
    ): FloatArray {
        val cy = cos(yawRad); val syr = sin(yawRad)
        val cx = cos(pitchRad); val sxr = sin(pitchRad)
        val cz = cos(rollRad); val szr = sin(rollRad)

        // R = Ry * Rx * Rz
        val r00 = cy * cz + syr * sxr * szr
        val r01 = -cy * szr + syr * sxr * cz
        val r02 = syr * cx

        val r10 = cx * szr
        val r11 = cx * cz
        val r12 = -sxr

        val r20 = -syr * cz + cy * sxr * szr
        val r21 = syr * szr + cy * sxr * cz
        val r22 = cy * cx

        return floatArrayOf(
            r00 * sx, r10 * sx, r20 * sx, 0f,
            r01 * sy, r11 * sy, r21 * sy, 0f,
            r02 * sz, r12 * sz, r22 * sz, 0f,
            tx, ty, tz, 1f,
        )
    }

    fun multiplyMat4(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[k * 4 + row] * b[col * 4 + k]
                }
                out[col * 4 + row] = sum
            }
        }
        return out
    }
}
