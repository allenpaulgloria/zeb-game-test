package com.sunsetgrove.game.gameplay

import com.sunsetgrove.game.world.Vec2
import com.sunsetgrove.game.world.Vec3

enum class PlayerMode {
    ON_FOOT,
    SWIMMING,
    CARJACKING,
    JETPACK,
    DRIVING,
}

enum class WeatherPreset(val label: String) {
    GOLDEN_HOUR("Los Santos Sunset"),
    SUNNY_DAY("California Sunny"),
    ORANGE_SKY_2100("21:00 Orange Sky"),
}

enum class BannerType {
    NONE,
    WASTED,
    BUSTED,
    WANTED_EVADED,
}

enum class VehicleArchetype(
    val displayName: String,
    val modelKey: String,
    val maxSpeed: Float,
    val acceleration: Float,
    val reverseSpeed: Float,
    val turnRate: Float,
    val grip: Float,
    val mass: Float,
    val maxHealth: Float,
    val colliderRadius: Float,
    val isPolice: Boolean = false,
    val isTank: Boolean = false,
    val isMonsterTruck: Boolean = false,
) {
    SEDAN("Greenwood Sedan", "car_sedan", 28f, 15f, 10f, 2.3f, 6.2f, 1.3f, 100f, 1.9f),
    MUSCLE("Sabre GT", "car_muscle", 34f, 19f, 11f, 2.4f, 5.2f, 1.45f, 115f, 1.95f),
    POLICE("LSPD Cruiser 94", "car_police", 36f, 21f, 12f, 2.5f, 6.8f, 1.7f, 130f, 1.95f, isPolice = true),
    HOTRING("Hotring Racer #1", "car_hotring", 44f, 26f, 13f, 2.75f, 7.6f, 1.35f, 125f, 1.95f),
    BLOODRING("Bloodring Banger", "car_bloodring", 33f, 18f, 11f, 2.35f, 5.8f, 2.4f, 220f, 2.05f),
    MONSTER_TRUCK(
        "Monster Truck", "vehicle_monster_truck",
        maxSpeed = 30f, acceleration = 18f, reverseSpeed = 11f,
        turnRate = 2.45f, grip = 5.4f, mass = 4.6f, maxHealth = 260f,
        colliderRadius = 2.35f, isMonsterTruck = true,
    ),
    RHINO_TANK(
        "Rhino Army Tank", "vehicle_tank",
        maxSpeed = 20f, acceleration = 11f, reverseSpeed = 9f,
        turnRate = 1.45f, grip = 10.0f, mass = 12.0f, maxHealth = 500f,
        colliderRadius = 2.75f, isTank = true,
    ),
}

data class ControlInput(
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val sprint: Boolean = false,
    val jumpPressed: Boolean = false,
    val shootPressed: Boolean = false,
    val gas: Float = 0f,
    val brake: Float = 0f,
    val handbrake: Boolean = false,
    val nitro: Boolean = false,
    val jetpackAscend: Boolean = false,
    val jetpackDescend: Boolean = false,
    val fireCannonPressed: Boolean = false,
    val enterExitPressed: Boolean = false,
    val cameraYawDelta: Float = 0f,
    val cameraPitchDelta: Float = 0f,
    val cameraAimYaw: Float = 0f,
)

data class VehicleState(
    val id: Int,
    val archetype: VehicleArchetype,
    var pos: Vec3,
    var vel: Vec3 = Vec3(),
    var yawRad: Float = 0f,
    var pitchRad: Float = 0f,
    var rollRad: Float = 0f,
    var turretYawRad: Float = 0f,
    var cannonCooldown: Float = 0f,
    var speed: Float = 0f,
    var health: Float = archetype.maxHealth,
    var isWrecked: Boolean = false,
    var isPlayerControlled: Boolean = false,
    var isTrafficAi: Boolean = false,
    var isPoliceAi: Boolean = false,
    var hasNpcDriver: Boolean = false,
    var sirenActive: Boolean = false,
    var sirenPhase: Float = 0f,
    var suspensionBounce: Float = 0f,
    var aiTargetNodeId: Int = 0,
    var aiPath: List<Vec2> = emptyList(),
    var aiPathIndex: Int = 0,
    var aiRepathTimer: Float = 0f,
    var isRoadblock: Boolean = false,
)

data class TankProjectile(
    val id: Int,
    var pos: Vec3,
    var vel: Vec3,
    var lifetime: Float = 3.5f,
)

data class ExplosionFx(
    val id: Int,
    val pos: Vec3,
    var age: Float = 0f,
    val maxAge: Float = 0.85f,
    val maxRadius: Float = 7.5f,
)

enum class BlipKind { VEHICLE, TANK, MONSTER, POLICE, PURPLE_GANG }

data class RadarBlip(
    val x: Float,
    val z: Float,
    val kind: BlipKind,
    val sirenPhase: Float = 0f,
)

data class SoundEvents(
    val crashIntensity: Float = 0f,
    val tankCannonFired: Boolean = false,
    val explosionTriggered: Boolean = false,
    val carDoorSlam: Boolean = false,
    val cheatActivated: Boolean = false,
    val wastedTriggered: Boolean = false,
    val bustedTriggered: Boolean = false,
    val respectTriggered: Boolean = false,
    val gunshotFired: Boolean = false,
    val batHit: Boolean = false,
    val chickenCluck: Boolean = false,
)

data class HudSnapshot(
    val cash: Int = 500,
    val health: Float = 100f,
    val armor: Float = 50f,
    val vehicleHealthRatio: Float = 1f,
    val wantedStars: Int = 0,
    val wantedFlashing: Boolean = false,
    val neverWantedActive: Boolean = false,
    val playerMode: PlayerMode = PlayerMode.ON_FOOT,
    val activeVehicleArchetype: VehicleArchetype? = null,
    val speedKmh: Int = 0,
    val altitudeMeters: Int = 0,
    val nearbyVehicleName: String? = null,
    val districtName: String = "Grove Street",
    val districtToastAlpha: Float = 0f,
    val vehicleToastName: String = "",
    val vehicleToastAlpha: Float = 0f,
    val cheatToastMessage: String = "",
    val cheatToastAlpha: Float = 0f,
    val activeCheatCodes: Set<String> = emptySet(),
    val nitroEnabled: Boolean = false,
    val tankCannonReadyRatio: Float = 1f,
    val banner: BannerType = BannerType.NONE,
    val bannerSubtext: String = "",
    val isPaused: Boolean = false,
    val weatherPreset: WeatherPreset = WeatherPreset.GOLDEN_HOUR,
    val playerX: Float = 0f,
    val playerZ: Float = 0f,
    val playerYawRad: Float = 0f,
    val cameraYawRad: Float = 0f,
    val radarBlips: List<RadarBlip> = emptyList(),
    val arrestedProgress: Float = 0f,
    val sirenActive: Boolean = false,
    val jetpackActive: Boolean = false,
    val isSwimming: Boolean = false,
    val isChickenMode: Boolean = false,
    val soundEvents: SoundEvents = SoundEvents(),
)
