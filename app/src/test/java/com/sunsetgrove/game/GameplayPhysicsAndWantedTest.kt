package com.sunsetgrove.game

import com.sunsetgrove.game.engine.AssetManifest
import com.sunsetgrove.game.gameplay.ArcadeVehiclePhysics
import com.sunsetgrove.game.gameplay.BannerType
import com.sunsetgrove.game.gameplay.CheatCodeManager
import com.sunsetgrove.game.gameplay.ControlInput
import com.sunsetgrove.game.gameplay.GameSimulation
import com.sunsetgrove.game.gameplay.PlayerMode
import com.sunsetgrove.game.gameplay.VehicleArchetype
import com.sunsetgrove.game.gameplay.VehicleState
import com.sunsetgrove.game.gameplay.WantedSystem
import com.sunsetgrove.game.world.CollisionWorld
import com.sunsetgrove.game.world.RoadGraph
import com.sunsetgrove.game.world.Vec2
import com.sunsetgrove.game.world.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayPhysicsAndWantedTest {

    @Test
    fun vehicleAccelerationNitroFlyingAndTankCannon() {
        val physics = ArcadeVehiclePhysics()
        val world = CollisionWorld()
        val cheats = CheatCodeManager()

        // 1. Muscle car accelerates & boosts faster with COXEFGU nitro
        val car = VehicleState(1, VehicleArchetype.MUSCLE, Vec3(0f, 0.25f, 0f), isPlayerControlled = true)
        repeat(60) {
            physics.stepVehicle(car, 0f, 1f, 0f, false, false, false, 0f, 1f / 60f, world, cheats)
        }
        val normalSpeed = car.speed
        assertTrue("Car should accelerate smoothly", normalSpeed > 12f)

        cheats.trigger("COXEFGU")
        cheats.trigger("RIPAZHA")
        repeat(90) {
            physics.stepVehicle(car, 0f, 1f, 0f, false, true, false, 0f, 1f / 60f, world, cheats)
        }
        assertTrue("Nitro should push speed above normal", car.speed > normalSpeed + 6f)
        assertTrue("RIPAZHA flying cars should lift off the ground", car.pos.y > 1.0f)

        // 2. Rhino Army Tank neutral-steers, slews turret, and fires explosive cannon shell
        val tank = VehicleState(2, VehicleArchetype.RHINO_TANK, Vec3(0f, 0.25f, 0f), isPlayerControlled = true)
        val out = physics.stepVehicle(tank, steerInput = 1f, gasInput = 0f, brakeInput = 0f, handbrake = false, nitroPressed = false, fireCannonPressed = true, aimYawRad = 1.2f, dt = 0.05f, world = world, cheats = cheats)
        assertNotNull("Rhino Tank should fire explosive shell", out.firedShell)
        assertTrue("Tank should neutral-steer in place", tank.yawRad < 0f)
        assertTrue("Tank turret should slew toward aim yaw", tank.turretYawRad > 0f)
    }

    @Test
    fun jetpackAscendsAndLandsCleanly() {
        val sim = GameSimulation()
        sim.triggerCheatCode("YECGAA")
        assertEquals(PlayerMode.JETPACK, sim.playerMode)

        val startY = sim.playerPos.y
        repeat(90) {
            sim.step(1f / 60f, ControlInput(jetpackAscend = true, moveY = 0.5f), cameraYawRad = 0f)
        }
        assertTrue("Jetpack should climb high into the sky", sim.playerPos.y > startY + 8f)
    }

    @Test
    fun wantedEscalationEvasionBustedAndAezakmi() {
        val wanted = WantedSystem()
        wanted.addCrimeHeat(80f, minStars = 2, neverWanted = false)
        assertEquals(2, wanted.stars)
        assertEquals(2, wanted.desiredPoliceCount())

        // Evasion out of sight clears stars and rewards cash
        var evaded = false
        var reward = 0
        repeat(200) {
            val r = wanted.tick(0.1f, false, anyCopHasLineOfSight = false, nearestCopDistance = 90f, playerSpeed = 12f, playerAltitude = 0.25f, inTank = false)
            if (r.evadedThisFrame) {
                evaded = true
                reward = r.evadedRewardCash
            }
        }
        assertTrue("Wanted should clear after hiding out of sight", evaded)
        assertEquals(500, reward)
        assertEquals(0, wanted.stars)

        // LJSPQK jumps straight to 6 stars, BUSTED triggers if cornered on foot
        wanted.setMaxWanted(false)
        assertEquals(6, wanted.stars)
        var busted = false
        repeat(40) {
            val r = wanted.tick(0.1f, false, anyCopHasLineOfSight = true, nearestCopDistance = 3.0f, playerSpeed = 0.2f, playerAltitude = 0.25f, inTank = false)
            if (r.bustedTriggered) busted = true
        }
        assertTrue("Standing still next to cop should trigger BUSTED", busted)

        // AEZAKMI neverWanted blocks crime heat
        wanted.clear()
        wanted.addCrimeHeat(200f, minStars = 4, neverWanted = true)
        assertEquals(0, wanted.stars)
    }

    @Test
    fun roadGraphAStarAndCollisionWorldAndManifestNormalization() {
        val graph = RoadGraph()
        val start = graph.nearestNode(Vec2(118f, 0f)) // Grove Circle
        val goal = graph.nearestNode(Vec2(-70f, 80f)) // Beachfront North
        val path = graph.findPath(start.id, goal.id)
        assertTrue("A* path should span multiple intersections", path.size >= 4)
        assertEquals(goal.pos, path.last())

        val world = CollisionWorld()
        // Downtown tower at (35, 115), height 26m -> landable rooftop at altitude 26m!
        val roofY = world.supportHeightAt(35f, 115f, actorY = 26.5f)
        assertEquals(26.0f, roofY, 0.05f)
        assertFalse("Building should block ground line of sight", world.hasLineOfSight(Vec2(15f, 115f), Vec2(55f, 115f), elevation = 2f))
        assertTrue("Flying above roof should have clear line of sight", world.hasLineOfSight(Vec2(15f, 115f), Vec2(55f, 115f), elevation = 30f))

        // AssetManifest normalization check
        val norm = AssetManifest.computeNormalization(
            center = floatArrayOf(0f, 1f, 0f),
            halfExtent = floatArrayOf(0.5f, 1f, 0.5f),
            targetHeight = 1.8f,
            yawOffsetDeg = 180f,
        )
        assertEquals(0.9f, norm.scale, 0.001f)
        assertEquals(0f, norm.offsetY, 0.001f)

        val sim = GameSimulation()
        sim.triggerCheatCode("OSRBLHH")
        assertEquals(2, sim.wanted.stars)
        assertEquals(BannerType.NONE, sim.activeBanner)
    }
}
