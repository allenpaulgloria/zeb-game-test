package com.sunsetgrove.game

import com.sunsetgrove.game.gameplay.ControlInput
import com.sunsetgrove.game.gameplay.GameSimulation
import com.sunsetgrove.game.gameplay.NpcRole
import com.sunsetgrove.game.gameplay.NpcSystem
import com.sunsetgrove.game.gameplay.NpcWeapon
import com.sunsetgrove.game.gameplay.PlayerMode
import com.sunsetgrove.game.ui.IntroMusicConfig
import com.sunsetgrove.game.ui.TouchInputBridge
import com.sunsetgrove.game.world.DistrictMap
import com.sunsetgrove.game.world.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroYouTubeConfigTest {

    @Test
    fun musicConfigAndTouchResetPreventStuckForwardOnCarExit() {
        assertEquals("IeIUm-aqaCg", IntroMusicConfig.YOUTUBE_VIDEO_ID)

        // Regression test for stuck-forward bug after exiting a car
        val bridge = TouchInputBridge()
        bridge.stickY = 1.0f
        bridge.gas = true
        bridge.steerRight = true
        bridge.resetContinuousInputs()
        assertEquals(0f, bridge.stickY, 0.0001f)
        assertFalse(bridge.gas)
        assertFalse(bridge.steerRight)
    }

    @Test
    fun carjackingAnimationPullsDriverOutWith20PercentArmedChance() {
        val npcSys = NpcSystem()
        // Roll < 0.20 => armed with GUN or BAT and hostile
        val armed = npcSys.spawnCarjackedDriver(Vec3(0f, 0.25f, 0f), 0f, { 101 }, rollOverride = 0.10f)
        assertTrue(armed.hostileToPlayer)
        assertNotEquals(NpcWeapon.NONE, armed.weapon)
        assertTrue(armed.weapon == NpcWeapon.GUN || armed.weapon == NpcWeapon.BAT)

        // Roll >= 0.20 => unarmed and fleeing
        val unarmed = npcSys.spawnCarjackedDriver(Vec3(0f, 0.25f, 0f), 0f, { 102 }, rollOverride = 0.55f)
        assertFalse(unarmed.hostileToPlayer)
        assertEquals(NpcWeapon.NONE, unarmed.weapon)
        assertTrue(unarmed.fleeing)
    }

    @Test
    fun swimmingPurpleGangAutoShootAndRoamingPoliceWork() {
        val sim = GameSimulation()

        // 1. Roaming police patrols exist even at 0 stars
        val policeCount = sim.vehicles.count { it.isPoliceAi }
        assertTrue("Expected at least 4 roaming police patrols", policeCount >= 4)

        // 2. Street pedestrians & armed Purple GTA Gang members exist & shootout timer is 2-6 min
        assertTrue(sim.npcSystem.npcs.any { it.role == NpcRole.CIVILIAN })
        val ballas = sim.npcSystem.npcs.filter { it.role == NpcRole.PURPLE_GANG }
        assertTrue("Expected patrolling Purple Gang members", ballas.size >= 8)
        assertTrue(ballas.all { it.weapon == NpcWeapon.GUN && it.hostileToPlayer })
        assertTrue(sim.npcSystem.nextGangShootoutTimer in 120f..360f)

        // 3. Purple gang automatically shoots when player is nearby
        val shooter = ballas.first()
        shooter.pos = Vec3(sim.playerPos.x + 10f, 0.25f, sim.playerPos.z)
        shooter.attackCooldown = 0f
        var shotsFired = false
        repeat(10) {
            val snap = sim.step(0.05f, ControlInput(), 0f)
            if (snap.soundEvents.gunshotFired) shotsFired = true
        }
        assertTrue("Purple gang should automatically shoot the player on sight", shotsFired)

        // 4. Swimming activates when entering Pacific Ocean west of shoreline
        val swimmer = GameSimulation()
        // Walk player west into the ocean via cheat/teleport-like sprint or direct west steps
        swimmer.triggerCheatCode("YECGAA")
        repeat(600) {
            swimmer.step(0.05f, ControlInput(moveY = 1f), cameraYawRad = -1.57f) // West (-X)
        }
        // Drop jetpack over the ocean -> enters SWIMMING!
        swimmer.step(0.05f, ControlInput(enterExitPressed = true), cameraYawRad = -1.57f)
        assertTrue("Player should be past shoreline in Pacific Ocean", swimmer.playerPos.x <= DistrictMap.OCEAN_SHORELINE_X)
        assertEquals(PlayerMode.SWIMMING, swimmer.playerMode)
    }
}
