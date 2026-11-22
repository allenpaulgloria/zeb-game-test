package com.sunsetgrove.game

import com.sunsetgrove.game.gameplay.CheatCodeManager
import com.sunsetgrove.game.gameplay.GameSimulation
import com.sunsetgrove.game.gameplay.PlayerMode
import com.sunsetgrove.game.gameplay.VehicleArchetype
import com.sunsetgrove.game.gameplay.WeatherPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatCodeManagerTest {

    @Test
    fun catalogHasAll22AuthenticSanAndreasCodes() {
        assertEquals(23, CheatCodeManager.catalog.size)
        val codes = CheatCodeManager.catalog.map { it.code }.toSet()
        assertTrue(codes.contains("YECGAA"))   // Jetpack (ROCKETMAN)
        assertTrue(codes.contains("AIWPRTON")) // Rhino Tank
        assertTrue(codes.contains("AGBDLCID")) // Monster Truck (MONSTERMASH)
        assertTrue(codes.contains("CLUCKINBELL")) // Chicken Mode (CHICKEN)
        assertTrue(codes.contains("HESOYAM"))
        assertTrue(codes.contains("AEZAKMI"))
        assertTrue(codes.contains("BAGUVIX"))
        assertTrue(codes.contains("RIPAZHA"))
        assertTrue(codes.contains("LJSPQK"))
        assertTrue(codes.contains("OFVIAC"))
    }

    @Test
    fun chickenCheatTogglesChickenMode() {
        val sim = GameSimulation()
        val on = sim.triggerCheatCode("CHICKEN")
        assertNotNull(on)
        assertEquals("CLUCKINBELL", on!!.code)
        assertTrue(sim.cheats.isActive("CLUCKINBELL"))
        val off = sim.triggerCheatCode("CLUCKINBELL")
        assertEquals("Cheat deactivated", off!!.bannerMessage)
        assertFalse(sim.cheats.isActive("CLUCKINBELL"))
    }

    @Test
    fun jetpackTankAndMonsterTruckAliasesWork() {
        val sim = GameSimulation()
        val initialVehCount = sim.vehicles.size

        // Jetpack via classic ROCKETMAN alias
        val jp = sim.triggerCheatCode("ROCKETMAN")
        assertNotNull(jp)
        assertEquals("Cheat activated", jp!!.bannerMessage)
        assertEquals(PlayerMode.JETPACK, sim.playerMode)

        // Rhino Army Tank via AIWPRTON
        val tank = sim.triggerCheatCode("AIWPRTON")
        assertNotNull(tank)
        assertEquals(VehicleArchetype.RHINO_TANK, tank!!.spawnVehicle)
        assertTrue(sim.vehicles.any { it.archetype == VehicleArchetype.RHINO_TANK })

        // Monster Truck via MONSTERMASH alias
        val monster = sim.triggerCheatCode("MONSTERMASH")
        assertNotNull(monster)
        assertEquals(VehicleArchetype.MONSTER_TRUCK, monster!!.spawnVehicle)
        assertTrue(sim.vehicles.any { it.archetype == VehicleArchetype.MONSTER_TRUCK })
        assertEquals(initialVehCount + 2, sim.vehicles.size)
    }

    @Test
    fun hesoyamAndWeatherAndSpeedMutualExclusion() {
        val sim = GameSimulation()
        val cashBefore = sim.playerCash
        sim.triggerCheatCode("HESOYAM")
        assertEquals(cashBefore + 250_000, sim.playerCash)
        assertEquals(100f, sim.playerHealth, 0.01f)
        assertEquals(100f, sim.playerArmor, 0.01f)

        // Fast & Slow motion are mutually exclusive toggles
        sim.triggerCheatCode("PPGWJHT")
        assertEquals(1.5f, sim.cheats.timeScale(), 0.01f)
        sim.triggerCheatCode("LIYOAAY")
        assertEquals(0.5f, sim.cheats.timeScale(), 0.01f)
        assertFalse(sim.cheats.isActive("PPGWJHT"))
        val offRes = sim.triggerCheatCode("LIYOAAY")
        assertEquals("Cheat deactivated", offRes!!.bannerMessage)
        assertEquals(1.0f, sim.cheats.timeScale(), 0.01f)

        // Weather cheats
        sim.triggerCheatCode("OFVIAC")
        assertEquals(WeatherPreset.ORANGE_SKY_2100, sim.weatherPreset)
        sim.triggerCheatCode("AFZLLQLL")
        assertEquals(WeatherPreset.SUNNY_DAY, sim.weatherPreset)
    }
}
