package com.sunsetgrove.game.gameplay

import kotlin.math.max
import kotlin.math.min

data class WantedTickResult(
    val evadedThisFrame: Boolean = false,
    val evadedRewardCash: Int = 0,
    val bustedTriggered: Boolean = false,
)

/**
 * Classic GTA: San Andreas 1-6 Star Wanted & Police Evasion system.
 * - Escalates from reckless collisions, vehicle theft, police ramming, or Rhino Tank cannon fire.
 * - Supports AEZAKMI (Never Wanted), ASNAEB (Clear), OSRBLHH (+2 Stars), LJSPQK (Max 6 Stars).
 * - Stars flash when out of police line-of-sight; surviving cooldown clears stars & awards RESPECT+.
 */
class WantedSystem {
    var stars: Int = 0
        private set
    var heatPoints: Float = 0f
        private set
    var outOfSightTimer: Float = 0f
        private set
    var arrestTimer: Float = 0f
        private set
    var isFlashing: Boolean = false
        private set

    fun desiredPoliceCount(): Int = when (stars) {
        0 -> 0
        1 -> 1
        2 -> 2
        3 -> 3
        4 -> 4
        5 -> 5
        else -> 6
    }

    fun evasionDurationSeconds(): Float = when (stars) {
        1 -> 10f
        2 -> 14f
        3 -> 18f
        4 -> 22f
        5 -> 25f
        else -> 28f
    }

    fun addCrimeHeat(points: Float, minStars: Int = 1, neverWanted: Boolean) {
        if (neverWanted) return
        heatPoints = min(600f, heatPoints + points)
        val starFromHeat = when {
            heatPoints >= 460f -> 6
            heatPoints >= 340f -> 5
            heatPoints >= 230f -> 4
            heatPoints >= 140f -> 3
            heatPoints >= 65f -> 2
            heatPoints >= 15f -> 1
            else -> 0
        }
        stars = max(stars, max(minStars, starFromHeat)).coerceIn(0, 6)
        outOfSightTimer = 0f
        isFlashing = false
    }

    fun addStars(delta: Int, neverWanted: Boolean) {
        if (neverWanted) return
        stars = (stars + delta).coerceIn(0, 6)
        heatPoints = max(heatPoints, stars * 75f)
        outOfSightTimer = 0f
        isFlashing = false
    }

    fun setMaxWanted(neverWanted: Boolean) {
        if (neverWanted) return
        stars = 6
        heatPoints = 600f
        outOfSightTimer = 0f
        isFlashing = false
    }

    fun clear() {
        stars = 0
        heatPoints = 0f
        outOfSightTimer = 0f
        arrestTimer = 0f
        isFlashing = false
    }

    fun tick(
        dt: Float,
        neverWanted: Boolean,
        anyCopHasLineOfSight: Boolean,
        nearestCopDistance: Float,
        playerSpeed: Float,
        playerAltitude: Float,
        inTank: Boolean,
    ): WantedTickResult {
        if (neverWanted) {
            if (stars > 0) clear()
            return WantedTickResult()
        }
        if (stars == 0) {
            arrestTimer = 0f
            isFlashing = false
            return WantedTickResult()
        }

        // BUSTED check: stopped or slow on ground beside a cop (Rhino Tank & high Jetpack are hard to bust!)
        val canBeBusted = !inTank && playerAltitude < 2.2f && playerSpeed < 2.4f && nearestCopDistance < 5.8f
        if (canBeBusted) {
            arrestTimer += dt
            if (arrestTimer >= 3.0f) {
                return WantedTickResult(bustedTriggered = true)
            }
        } else {
            arrestTimer = max(0f, arrestTimer - dt * 1.8f)
        }

        // Evasion countdown when no cop sees the player
        if (anyCopHasLineOfSight) {
            outOfSightTimer = 0f
            isFlashing = false
        } else {
            outOfSightTimer += dt
            isFlashing = outOfSightTimer > 2.2f
            if (outOfSightTimer >= evasionDurationSeconds()) {
                val reward = stars * 250
                clear()
                return WantedTickResult(evadedThisFrame = true, evadedRewardCash = reward)
            }
        }

        return WantedTickResult()
    }
}
