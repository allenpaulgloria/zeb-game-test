package com.sunsetgrove.game.gameplay

import com.sunsetgrove.game.world.CollisionWorld
import com.sunsetgrove.game.world.DistrictMap
import com.sunsetgrove.game.world.Math3D
import com.sunsetgrove.game.world.RoadGraph
import com.sunsetgrove.game.world.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Master 60Hz gameplay simulation for Sunset Grove:
 * - On-foot walking, sprinting, jumping (KANGAROO), shooting back, and Jetpack flight (YECGAA)
 * - Ocean Swimming in the Pacific Ocean west of Verona Beach!
 * - Carjacking pull-out animation (with 20% chance pulled-out driver is armed with a Gun or Bat!)
 * - Seamless car / Monster Truck / Rhino Army Tank driving, crushing, & cannon ballistics
 * - Roaming Police Patrols across the whole map + 1-6 star Wanted pursuit
 * - Walking street pedestrians + Armed Purple GTA Gang (auto-shoot player + 2-6 min shootouts!)
 * - 22 authentic GTA: San Andreas cheat codes & iconic WASTED / BUSTED / RESPECT+ flows
 */
class GameSimulation(
    val world: CollisionWorld = CollisionWorld(),
    val roadGraph: RoadGraph = RoadGraph(),
    val cheats: CheatCodeManager = CheatCodeManager(),
    val wanted: WantedSystem = WantedSystem(),
    val npcSystem: NpcSystem = NpcSystem(),
) {
    private val vehiclePhysics = ArcadeVehiclePhysics()
    private val aiSystem = PoliceAndTrafficAi(roadGraph, vehiclePhysics)

    private var nextEntityId = 1

    // Player state
    var playerMode: PlayerMode = PlayerMode.ON_FOOT
        private set
    var playerPos: Vec3 = DistrictMap.PLAYER_SPAWN
        private set
    var playerVel: Vec3 = Vec3()
        private set
    var playerYawRad: Float = -1.57f
        private set
    var playerPitchVisualRad: Float = 0f
        private set
    var playerAnimState: String = "Idle"
        private set
    var playerHealth: Float = 100f
        private set
    var playerArmor: Float = 50f
        private set
    var playerCash: Int = 500
        private set
    var activeVehicleId: Int? = null
        private set
    var jetpackThrusting: Boolean = false
        private set
    var droppedJetpackPos: Vec3? = null
        private set

    // Carjacking pull-out animation state
    private var carjackVehicleId: Int? = null
    private var carjackTimer: Float = 0f
    private var carjackDriverPulled: Boolean = false

    // Swimming bobbing timer & player gun cooldown
    private var swimTime: Float = 0f
    private var playerShootCooldown: Float = 0f

    // World entities
    val vehicles = mutableListOf<VehicleState>()
    val projectiles = mutableListOf<TankProjectile>()
    val explosions = mutableListOf<ExplosionFx>()

    // UI & flow state
    var isPaused: Boolean = false
    var weatherPreset: WeatherPreset = WeatherPreset.GOLDEN_HOUR
        private set
    var activeBanner: BannerType = BannerType.NONE
        private set
    private var bannerSubtext: String = ""
    private var bannerTimer: Float = 0f

    private var currentDistrict: String = DistrictMap.districtNameAt(playerPos.x, playerPos.z)
    private var districtToastTimer: Float = 3.5f
    private var vehicleToastName: String = ""
    private var vehicleToastTimer: Float = 0f
    private var cheatToastMessage: String = ""
    private var cheatToastTimer: Float = 0f

    var cameraShake: Float = 0f
        private set

    private var pendingSoundEvents = SoundEvents()

    init {
        spawnInitialWorldEntities()
    }

    private fun spawnInitialWorldEntities() {
        vehicles.clear()
        for (spec in DistrictMap.initialParkedCars) {
            val arch = when (spec.archetype) {
                "muscle" -> VehicleArchetype.MUSCLE
                "police" -> VehicleArchetype.POLICE
                else -> VehicleArchetype.SEDAN
            }
            vehicles.add(
                VehicleState(
                    id = nextEntityId++,
                    archetype = arch,
                    pos = Vec3(spec.x, 0.25f, spec.z),
                    yawRad = spec.yawDeg * Math3D.DEG_TO_RAD,
                    isPoliceAi = arch.isPolice,
                    hasNpcDriver = spec.hasDriverInside,
                )
            )
        }
        // 8 Ambient civilian traffic cars cruising the extended street grid (all with drivers inside!)
        val trafficNodes = listOf(1, 3, 6, 8, 11, 13, 16, 18)
        for ((i, nodeIdx) in trafficNodes.withIndex()) {
            val node = roadGraph.nodes[nodeIdx % roadGraph.nodes.size]
            val nextNode = node.neighbors.firstOrNull() ?: 0
            val arch = if (i % 3 == 0) VehicleArchetype.MUSCLE else VehicleArchetype.SEDAN
            vehicles.add(
                VehicleState(
                    id = nextEntityId++,
                    archetype = arch,
                    pos = Vec3(node.pos.x, 0.25f, node.pos.z),
                    isTrafficAi = true,
                    hasNpcDriver = true,
                    aiTargetNodeId = nextNode,
                )
            )
        }
        // Spawn 4 initial roaming LSPD patrol cruisers around the map!
        val patrolNodes = listOf(2, 7, 12, 17)
        for (nodeIdx in patrolNodes) {
            val node = roadGraph.nodes[nodeIdx % roadGraph.nodes.size]
            val nextNode = node.neighbors.firstOrNull() ?: 0
            vehicles.add(
                VehicleState(
                    id = nextEntityId++,
                    archetype = VehicleArchetype.POLICE,
                    pos = Vec3(node.pos.x, 0.25f, node.pos.z),
                    isPoliceAi = true,
                    hasNpcDriver = true,
                    sirenActive = false,
                    aiTargetNodeId = nextNode,
                )
            )
        }
        // Populate street pedestrians + armed Purple GTA Gang members!
        npcSystem.spawnInitialPopulation { nextEntityId++ }
    }

    fun activeVehicle(): VehicleState? = activeVehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }

    fun triggerCheatCode(code: String): CheatActivationResult? {
        val res = cheats.trigger(code) ?: return null
        cheatToastMessage = "${res.bannerMessage}: ${res.code}"
        cheatToastTimer = 3.2f
        pendingSoundEvents = pendingSoundEvents.copy(cheatActivated = true)

        if (res.healAndCash) {
            playerHealth = 100f
            playerArmor = 100f
            playerCash += 250_000
            activeVehicle()?.let {
                it.health = it.archetype.maxHealth
                it.isWrecked = false
            }
        }
        if (res.equipJetpack) {
            if (playerMode == PlayerMode.DRIVING) exitCurrentVehicle()
            playerMode = PlayerMode.JETPACK
            droppedJetpackPos = null
            playerVel = Vec3(0f, 3.2f, 0f)
            vehicleToastName = "Jetpack"
            vehicleToastTimer = 3.0f
        }
        res.spawnVehicle?.let { arch -> spawnCheatVehicleInFront(arch) }
        if (res.clearWanted) wanted.clear()
        if (res.addWantedStars > 0) wanted.addStars(res.addWantedStars, cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED))
        if (res.setMaxWanted) wanted.setMaxWanted(cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED))
        if (res.blowUpNearbyCars) detonateNearbyVehicles()
        res.weatherPreset?.let { weatherPreset = it }
        return res
    }

    fun clearAllCheats() {
        cheats.clearAllToggles()
        cheatToastMessage = "All toggle cheats cleared"
        cheatToastTimer = 2.5f
    }

    fun respawnAtGroveCircle(chargeHospitalFee: Boolean = false) {
        if (chargeHospitalFee) playerCash = max(0, playerCash - 100)
        activeVehicle()?.let { it.isPlayerControlled = false }
        activeVehicleId = null
        carjackVehicleId = null
        playerMode = PlayerMode.ON_FOOT
        playerPos = DistrictMap.PLAYER_SPAWN
        playerVel = Vec3()
        playerYawRad = -1.57f
        playerPitchVisualRad = 0f
        playerHealth = 100f
        playerArmor = 50f
        wanted.clear()
        activeBanner = BannerType.NONE
        bannerTimer = 0f
        isPaused = false
        currentDistrict = "Grove Street"
        districtToastTimer = 3.5f
    }

    private fun spawnCheatVehicleInFront(arch: VehicleArchetype) {
        val fwd = Math3D.forwardFromYaw(playerYawRad)
        val dist = if (arch.isTank || arch.isMonsterTruck) 8.5f else 6.8f
        val rawX = max(DistrictMap.OCEAN_SHORELINE_X + 8f, playerPos.x + fwd.x * dist)
        val rawZ = playerPos.z + fwd.z * dist
        val resolved = world.resolveHorizontal(rawX, rawZ, 0.5f, arch.colliderRadius)
        val groundY = world.supportHeightAt(resolved.x, resolved.z, 1.0f)

        val spawned = VehicleState(
            id = nextEntityId++,
            archetype = arch,
            pos = Vec3(resolved.x, groundY + 0.2f, resolved.z),
            yawRad = playerYawRad,
            turretYawRad = playerYawRad,
            hasNpcDriver = false,
        )
        vehicles.add(spawned)
        vehicleToastName = "${arch.displayName} Spawned!"
        vehicleToastTimer = 3.0f
    }

    private fun detonateNearbyVehicles() {
        var blastedAny = false
        for (v in vehicles) {
            if (v.isPlayerControlled || v.isWrecked) continue
            if (v.pos.horizontalDistanceTo(playerPos) <= 85f) {
                wreckVehicleWithBlast(v)
                blastedAny = true
            }
        }
        if (blastedAny) {
            cameraShake = 1.0f
            pendingSoundEvents = pendingSoundEvents.copy(explosionTriggered = true)
            wanted.addCrimeHeat(120f, minStars = 2, neverWanted = cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED))
            npcSystem.panicNearbyCivilians(playerPos, 65f)
        }
    }

    private fun wreckVehicleWithBlast(v: VehicleState) {
        v.health = 0f
        v.isWrecked = true
        v.vel = Vec3(v.vel.x, 9.5f, v.vel.z)
        explosions.add(ExplosionFx(nextEntityId++, Vec3(v.pos.x, v.pos.y + 1.2f, v.pos.z)))
    }

    fun step(rawDt: Float, input: ControlInput, cameraYawRad: Float): HudSnapshot {
        val clampedDt = Math3D.clamp(rawDt, 0.001f, 0.05f)
        var frameEvents = pendingSoundEvents
        pendingSoundEvents = SoundEvents()

        if (isPaused) {
            return buildSnapshot(cameraYawRad, frameEvents)
        }

        districtToastTimer = max(0f, districtToastTimer - clampedDt)
        vehicleToastTimer = max(0f, vehicleToastTimer - clampedDt)
        cheatToastTimer = max(0f, cheatToastTimer - clampedDt)
        cameraShake = max(0f, cameraShake - clampedDt * 2.4f)
        playerShootCooldown = max(0f, playerShootCooldown - clampedDt)

        val bannerSlowMo = if (activeBanner == BannerType.WASTED || activeBanner == BannerType.BUSTED) {
            bannerTimer += clampedDt
            if (bannerTimer >= 3.6f) respawnAtGroveCircle(chargeHospitalFee = true)
            0.25f
        } else {
            if (activeBanner == BannerType.WANTED_EVADED) {
                bannerTimer += clampedDt
                if (bannerTimer >= 3.2f) {
                    activeBanner = BannerType.NONE
                    bannerTimer = 0f
                }
            }
            1.0f
        }

        val dt = clampedDt * cheats.timeScale() * bannerSlowMo

        // Handle Enter/Exit Vehicle / Carjacking / Drop Jetpack
        if (input.enterExitPressed && activeBanner != BannerType.WASTED && activeBanner != BannerType.BUSTED) {
            when (playerMode) {
                PlayerMode.DRIVING -> {
                    exitCurrentVehicle()
                    frameEvents = frameEvents.copy(carDoorSlam = true)
                }
                PlayerMode.JETPACK -> {
                    droppedJetpackPos = Vec3(playerPos.x, world.supportHeightAt(playerPos.x, playerPos.z, playerPos.y) + 0.6f, playerPos.z)
                    playerMode = if (DistrictMap.isInSwimmableOcean(playerPos.x)) PlayerMode.SWIMMING else PlayerMode.ON_FOOT
                }
                PlayerMode.ON_FOOT -> {
                    val nearby = findNearestEnterableVehicle()
                    if (nearby != null) {
                        if (nearby.hasNpcDriver || nearby.isTrafficAi || nearby.isPoliceAi) {
                            beginCarjacking(nearby)
                            frameEvents = frameEvents.copy(carDoorSlam = true)
                        } else {
                            completeEnterVehicle(nearby)
                            frameEvents = frameEvents.copy(carDoorSlam = true)
                        }
                    } else if (droppedJetpackPos != null && playerPos.distanceTo(droppedJetpackPos!!) < 2.8f) {
                        playerMode = PlayerMode.JETPACK
                        droppedJetpackPos = null
                    }
                }
                PlayerMode.SWIMMING, PlayerMode.CARJACKING -> { /* ignore */ }
            }
        }

        // Player firing back on foot!
        if (playerMode == PlayerMode.ON_FOOT && input.shootPressed && playerShootCooldown <= 0f) {
            playerShootCooldown = 0.28f
            playerYawRad = cameraYawRad
            npcSystem.firePlayerBullet(playerPos, cameraYawRad)
            npcSystem.panicNearbyCivilians(playerPos, 38f)
            frameEvents = frameEvents.copy(gunshotFired = true)
        }

        // Step active player mode
        when (playerMode) {
            PlayerMode.ON_FOOT -> stepOnFoot(dt, input, cameraYawRad)
            PlayerMode.SWIMMING -> stepSwimming(dt, input, cameraYawRad)
            PlayerMode.CARJACKING -> {
                if (stepCarjacking(dt)) {
                    frameEvents = frameEvents.copy(carDoorSlam = true)
                }
            }
            PlayerMode.JETPACK -> stepJetpack(dt, input, cameraYawRad)
            PlayerMode.DRIVING -> {
                val veh = activeVehicle()
                if (veh != null) {
                    val out = vehiclePhysics.stepVehicle(
                        v = veh,
                        steerInput = input.moveX,
                        gasInput = input.gas,
                        brakeInput = input.brake,
                        handbrake = input.handbrake,
                        nitroPressed = input.nitro,
                        fireCannonPressed = input.fireCannonPressed,
                        aimYawRad = cameraYawRad,
                        dt = dt,
                        world = world,
                        cheats = cheats,
                    )
                    playerPos = veh.pos
                    playerVel = veh.vel
                    playerYawRad = veh.yawRad
                    playerPitchVisualRad = 0f

                    // Driving into deep Pacific Ocean stalls the car and lets you swim out!
                    if (veh.pos.x < DistrictMap.OCEAN_SHORELINE_X - 10f && veh.pos.y < 1.2f) {
                        veh.isWrecked = true
                        exitCurrentVehicle()
                        playerMode = PlayerMode.SWIMMING
                        vehicleToastName = "Vehicle Sunk — Swimming!"
                        vehicleToastTimer = 2.6f
                    }

                    if (out.crashedWithWorld && out.crashSpeed > 5f) {
                        frameEvents = frameEvents.copy(crashIntensity = Math3D.clamp(out.crashSpeed / 20f, 0.2f, 1f))
                        cameraShake = max(cameraShake, Math3D.clamp(out.crashSpeed / 26f, 0.15f, 0.6f))
                    }
                    out.firedShell?.let { shell ->
                        projectiles.add(shell)
                        cameraShake = max(cameraShake, 0.65f)
                        frameEvents = frameEvents.copy(tankCannonFired = true)
                        wanted.addCrimeHeat(75f, minStars = 2, neverWanted = cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED))
                        npcSystem.panicNearbyCivilians(playerPos, 60f)
                    }
                    if (veh.isWrecked && playerMode == PlayerMode.DRIVING) {
                        applyPlayerDamage(150f, bypassSemiGodMode = true)
                    }
                } else {
                    playerMode = PlayerMode.ON_FOOT
                }
            }
        }

        // Step Roaming Police Patrols & Civilian Traffic
        aiSystem.updatePoliceSpawning(vehicles, wanted.desiredPoliceCount(), playerPos) { nextEntityId++ }
        aiSystem.stepAiVehicles(vehicles, playerPos, playerVel, wanted.stars, dt, world, cheats)

        // Step Street Pedestrians, Armed Purple Gang, & Carjacked Drivers!
        val npcEvents = npcSystem.step(
            dt = dt,
            playerPos = playerPos,
            playerInVehicle = playerMode == PlayerMode.DRIVING,
            playerInTank = activeVehicle()?.archetype?.isTank == true,
            world = world,
        )
        if (npcEvents.gunshotsFired > 0) frameEvents = frameEvents.copy(gunshotFired = true)
        if (npcEvents.batHitPlayer) frameEvents = frameEvents.copy(batHit = true)
        if (npcEvents.damageToPlayer > 0f) {
            applyPlayerDamage(npcEvents.damageToPlayer, bypassSemiGodMode = false)
            cameraShake = max(cameraShake, 0.22f)
        }
        if (npcEvents.gangShootoutStarted) {
            cheatToastMessage = "Ballas Gang Shootout in the Streets!"
            cheatToastTimer = 3.2f
        }

        // Vehicle-to-vehicle collisions
        val vehCollisionEvents = resolveVehicleToVehicleCollisions()
        if (vehCollisionEvents.crashIntensity > frameEvents.crashIntensity) {
            frameEvents = frameEvents.copy(crashIntensity = vehCollisionEvents.crashIntensity)
        }
        if (vehCollisionEvents.explosionTriggered) {
            frameEvents = frameEvents.copy(explosionTriggered = true)
        }

        // Tank cannon shells & explosions
        if (stepProjectilesAndExplosions(dt)) {
            frameEvents = frameEvents.copy(explosionTriggered = true)
        }

        // District toast updates
        val newDistrict = DistrictMap.districtNameAt(playerPos.x, playerPos.z)
        if (newDistrict != currentDistrict) {
            currentDistrict = newDistrict
            districtToastTimer = 3.2f
        }

        // Step Wanted System
        var anyCopSeesPlayer = false
        var nearestCopDist = Float.MAX_VALUE
        for (v in vehicles) {
            if (!v.isPoliceAi || v.isWrecked || v.isPlayerControlled) continue
            val d = v.pos.horizontalDistanceTo(playerPos)
            if (d < nearestCopDist) nearestCopDist = d
            if (d < 68f && playerPos.y < 14f && world.hasLineOfSight(v.pos.xz(), playerPos.xz(), max(1.5f, playerPos.y))) {
                anyCopSeesPlayer = true
            }
        }

        val wantedRes = wanted.tick(
            dt = dt,
            neverWanted = cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED),
            anyCopHasLineOfSight = anyCopSeesPlayer,
            nearestCopDistance = nearestCopDist,
            playerSpeed = playerVel.xz().length(),
            playerAltitude = playerPos.y,
            inTank = activeVehicle()?.archetype?.isTank == true,
        )

        if (wantedRes.evadedThisFrame) {
            playerCash += wantedRes.evadedRewardCash
            activeBanner = BannerType.WANTED_EVADED
            bannerSubtext = "RESPECT +  (\$${wantedRes.evadedRewardCash})"
            bannerTimer = 0f
            frameEvents = frameEvents.copy(respectTriggered = true)
        } else if (wantedRes.bustedTriggered && activeBanner != BannerType.BUSTED && activeBanner != BannerType.WASTED) {
            activeBanner = BannerType.BUSTED
            bannerSubtext = "RELEASED AT PRECINCT (-\$100)"
            bannerTimer = 0f
            frameEvents = frameEvents.copy(bustedTriggered = true)
        }

        if (playerHealth <= 0f && activeBanner != BannerType.WASTED) {
            activeBanner = BannerType.WASTED
            bannerSubtext = "HOSPITAL BILL (-\$100)"
            bannerTimer = 0f
            frameEvents = frameEvents.copy(wastedTriggered = true)
        }

        return buildSnapshot(cameraYawRad, frameEvents)
    }

    private fun beginCarjacking(v: VehicleState) {
        // Freeze the target car while the player yanks the driver out!
        v.speed = 0f
        v.vel = Vec3()
        v.isTrafficAi = false
        v.isPoliceAi = false
        carjackVehicleId = v.id
        carjackTimer = 0f
        carjackDriverPulled = false
        playerMode = PlayerMode.CARJACKING
        playerVel = Vec3()
        vehicleToastName = "Carjacking ${v.archetype.displayName}..."
        vehicleToastTimer = 1.6f
    }

    /** Animates player stepping to the driver's door, yanking the driver out (20% armed!), then boarding. */
    private fun stepCarjacking(dt: Float): Boolean {
        val v = vehicles.firstOrNull { it.id == carjackVehicleId }
        if (v == null || v.isWrecked) {
            playerMode = PlayerMode.ON_FOOT
            carjackVehicleId = null
            return false
        }
        carjackTimer += dt
        val leftX = cos(v.yawRad)
        val leftZ = -sin(v.yawRad)
        val doorPos = Vec3(v.pos.x + leftX * (v.archetype.colliderRadius + 0.55f), v.pos.y, v.pos.z + leftZ * (v.archetype.colliderRadius + 0.55f))

        playerPos = Vec3(
            Math3D.lerp(playerPos.x, doorPos.x, 10f * dt),
            doorPos.y,
            Math3D.lerp(playerPos.z, doorPos.z, 10f * dt),
        )
        playerYawRad = Math3D.lerpAngleRad(playerYawRad, Math3D.yawFromDirection(v.pos.x - playerPos.x, v.pos.z - playerPos.z), 12f * dt)
        playerAnimState = "Run"

        var doorSlammed = false
        // At t = 0.35s, yank the driver out onto the street with 20% chance of Gun or Bat!
        if (!carjackDriverPulled && carjackTimer >= 0.35f) {
            carjackDriverPulled = true
            v.hasNpcDriver = false
            val driver = npcSystem.spawnCarjackedDriver(v.pos, v.yawRad, { nextEntityId++ })
            if (driver.hostileToPlayer) {
                val wLabel = if (driver.weapon == NpcWeapon.GUN) "GUN" else "BASEBALL BAT"
                cheatToastMessage = "Driver is armed with a $wLabel!"
                cheatToastTimer = 2.6f
            }
            doorSlammed = true
        }

        // At t = 0.85s, hop into the driver's seat!
        if (carjackTimer >= 0.85f) {
            carjackVehicleId = null
            completeEnterVehicle(v, wasCarjacked = true)
            doorSlammed = true
        }
        return doorSlammed
    }

    private fun stepOnFoot(dt: Float, input: ControlInput, cameraYawRad: Float) {
        jetpackThrusting = false
        playerPitchVisualRad = Math3D.lerp(playerPitchVisualRad, 0f, 10f * dt)

        val fwdX = sin(cameraYawRad)
        val fwdZ = cos(cameraYawRad)
        val rightX = -cos(cameraYawRad)
        val rightZ = sin(cameraYawRad)

        val rawDx = fwdX * input.moveY + rightX * input.moveX
        val rawDz = fwdZ * input.moveY + rightZ * input.moveX
        val mag = sqrt(rawDx * rawDx + rawDz * rawDz)

        val moveSpeed = if (input.sprint) 8.4f else 4.6f
        val vx: Float
        val vz: Float
        if (mag > 0.08f) {
            val nx = rawDx / mag
            val nz = rawDz / mag
            val scale = min(1f, mag) * moveSpeed
            vx = nx * scale
            vz = nz * scale
            playerYawRad = Math3D.lerpAngleRad(playerYawRad, Math3D.yawFromDirection(nx, nz), 10f * dt)
            playerAnimState = if (input.sprint) "Run" else "Walk"
        } else {
            vx = 0f
            vz = 0f
            playerAnimState = "Idle"
        }

        val groundY = world.supportHeightAt(playerPos.x, playerPos.z, playerPos.y)
        val onGround = playerPos.y <= groundY + 0.08f
        var vy = playerVel.y
        if (onGround) {
            vy = if (input.jumpPressed) {
                if (cheats.isActive(CheatCodeManager.CODE_KANGAROO)) 14.8f else 5.8f
            } else 0f
        } else {
            vy -= 18.5f * dt
        }

        val nextX = playerPos.x + vx * dt
        val nextZ = playerPos.z + vz * dt
        val res = world.resolveHorizontal(nextX, nextZ, playerPos.y, 0.45f)
        val nextGround = world.supportHeightAt(res.x, res.z, playerPos.y)
        val nextY = max(nextGround, playerPos.y + vy * dt)
        if (nextY <= nextGround + 0.02f && vy < 0f) vy = 0f

        playerPos = Vec3(res.x, nextY, res.z)
        playerVel = Vec3(vx, vy, vz)

        // Walking into the Pacific Ocean automatically transitions to SWIMMING!
        if (DistrictMap.isInSwimmableOcean(playerPos.x) && playerPos.y <= 0.65f) {
            playerMode = PlayerMode.SWIMMING
            playerVel = Vec3(vx * 0.5f, 0f, vz * 0.5f)
            vehicleToastName = "Swimming"
            vehicleToastTimer = 1.8f
        }
    }

    private fun stepSwimming(dt: Float, input: ControlInput, cameraYawRad: Float) {
        swimTime += dt
        jetpackThrusting = false

        val fwdX = sin(cameraYawRad)
        val fwdZ = cos(cameraYawRad)
        val rightX = -cos(cameraYawRad)
        val rightZ = sin(cameraYawRad)

        val rawDx = fwdX * input.moveY + rightX * input.moveX
        val rawDz = fwdZ * input.moveY + rightZ * input.moveX
        val mag = sqrt(rawDx * rawDx + rawDz * rawDz)

        val swimSpeed = if (input.sprint) 5.8f else 3.5f
        val targetVx: Float
        val targetVz: Float
        if (mag > 0.08f) {
            val nx = rawDx / mag
            val nz = rawDz / mag
            targetVx = nx * min(1f, mag) * swimSpeed
            targetVz = nz * min(1f, mag) * swimSpeed
            playerYawRad = Math3D.lerpAngleRad(playerYawRad, Math3D.yawFromDirection(nx, nz), 6f * dt)
            playerAnimState = "Walk"
            // Horizontal prone swimming posture!
            playerPitchVisualRad = Math3D.lerp(playerPitchVisualRad, 1.15f, 6f * dt)
        } else {
            targetVx = 0f
            targetVz = 0f
            playerAnimState = "Idle"
            // Upright treading water!
            playerPitchVisualRad = Math3D.lerp(playerPitchVisualRad, 0.20f, 5f * dt)
        }

        val vx = Math3D.lerp(playerVel.x, targetVx, 4.5f * dt)
        val vz = Math3D.lerp(playerVel.z, targetVz, 4.5f * dt)
        val nextX = Math3D.clamp(playerPos.x + vx * dt, -DistrictMap.WORLD_HALF_EXTENT + 6f, DistrictMap.WORLD_HALF_EXTENT - 6f)
        val nextZ = Math3D.clamp(playerPos.z + vz * dt, -DistrictMap.WORLD_HALF_EXTENT + 6f, DistrictMap.WORLD_HALF_EXTENT - 6f)
        val bobY = DistrictMap.WATER_SURFACE_Y - 0.55f + sin(swimTime * 3.4f) * 0.10f

        playerPos = Vec3(nextX, bobY, nextZ)
        playerVel = Vec3(vx, 0f, vz)

        // Swimming back up onto Verona Beach sand returns smoothly to ON_FOOT!
        if (playerPos.x > DistrictMap.OCEAN_SHORELINE_X + 1.5f) {
            playerMode = PlayerMode.ON_FOOT
            playerPos = Vec3(playerPos.x, 0.25f, playerPos.z)
            playerPitchVisualRad = 0f
        }
    }

    private fun stepJetpack(dt: Float, input: ControlInput, cameraYawRad: Float) {
        val fwdX = sin(cameraYawRad)
        val fwdZ = cos(cameraYawRad)
        val rightX = -cos(cameraYawRad)
        val rightZ = sin(cameraYawRad)

        val rawDx = fwdX * input.moveY + rightX * input.moveX
        val rawDz = fwdZ * input.moveY + rightZ * input.moveX
        val mag = sqrt(rawDx * rawDx + rawDz * rawDz)

        val targetVx: Float
        val targetVz: Float
        if (mag > 0.08f) {
            val nx = rawDx / mag
            val nz = rawDz / mag
            val spd = 14.5f * min(1f, mag)
            targetVx = nx * spd
            targetVz = nz * spd
            playerYawRad = Math3D.lerpAngleRad(playerYawRad, Math3D.yawFromDirection(nx, nz), 7f * dt)
        } else {
            targetVx = 0f
            targetVz = 0f
        }

        val targetVy = when {
            input.jetpackAscend -> 10.5f
            input.jetpackDescend -> -8.5f
            else -> 0f
        }
        jetpackThrusting = input.jetpackAscend || mag > 0.1f || playerPos.y > world.supportHeightAt(playerPos.x, playerPos.z, playerPos.y) + 0.35f
        playerPitchVisualRad = Math3D.clamp(playerVel.xz().length() * 0.025f, 0f, 0.28f)

        val vx = Math3D.lerp(playerVel.x, targetVx, 4.5f * dt)
        val vz = Math3D.lerp(playerVel.z, targetVz, 4.5f * dt)
        var vy = Math3D.lerp(playerVel.y, targetVy, 5.5f * dt)

        val nextX = playerPos.x + vx * dt
        val nextZ = playerPos.z + vz * dt
        val res = world.resolveHorizontal(nextX, nextZ, playerPos.y, 0.55f)
        val supportY = world.supportHeightAt(res.x, res.z, playerPos.y)
        val nextY = Math3D.clamp(playerPos.y + vy * dt, supportY, 72f)
        if (nextY <= supportY + 0.02f && vy < 0f) vy = 0f

        playerPos = Vec3(res.x, nextY, res.z)
        playerVel = Vec3(vx, vy, vz)
        playerAnimState = "Idle"
    }

    private fun findNearestEnterableVehicle(): VehicleState? {
        var best: VehicleState? = null
        var bestDist = 4.4f
        for (v in vehicles) {
            if (v.isWrecked) continue
            val d = v.pos.horizontalDistanceTo(playerPos)
            if (d < bestDist) {
                bestDist = d
                best = v
            }
        }
        return best
    }

    private fun completeEnterVehicle(v: VehicleState, wasCarjacked: Boolean = false) {
        v.isPlayerControlled = true
        v.isTrafficAi = false
        v.hasNpcDriver = false
        activeVehicleId = v.id
        playerMode = PlayerMode.DRIVING
        playerPos = v.pos
        playerYawRad = v.yawRad
        playerVel = Vec3()
        vehicleToastName = v.archetype.displayName
        vehicleToastTimer = 2.8f

        if (v.archetype.isPolice) {
            wanted.addCrimeHeat(85f, minStars = 2, neverWanted = cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED))
        } else if (wasCarjacked) {
            wanted.addCrimeHeat(32f, minStars = 1, neverWanted = cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED))
        }
    }

    private fun exitCurrentVehicle() {
        val v = activeVehicle() ?: return
        v.isPlayerControlled = false
        v.speed *= 0.25f
        v.vel = v.vel * 0.25f
        val sideOffset = v.archetype.colliderRadius + 1.1f
        val leftX = cos(v.yawRad)
        val leftZ = -sin(v.yawRad)
        val outX = v.pos.x + leftX * sideOffset
        val outZ = v.pos.z + leftZ * sideOffset
        val res = world.resolveHorizontal(outX, outZ, v.pos.y, 0.5f)
        playerPos = Vec3(res.x, world.supportHeightAt(res.x, res.z, v.pos.y), res.z)
        playerVel = Vec3()
        activeVehicleId = null
        playerMode = if (DistrictMap.isInSwimmableOcean(playerPos.x)) PlayerMode.SWIMMING else PlayerMode.ON_FOOT
    }

    private fun resolveVehicleToVehicleCollisions(): SoundEvents {
        var maxCrash = 0f
        var exploded = false
        val smashCheat = cheats.isActive(CheatCodeManager.CODE_SMASH_N_BOOM)
        val neverWanted = cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED)

        for (i in vehicles.indices) {
            val a = vehicles[i]
            for (j in i + 1 until vehicles.size) {
                val b = vehicles[j]
                val dx = b.pos.x - a.pos.x
                val dz = b.pos.z - a.pos.z
                val minDist = a.archetype.colliderRadius + b.archetype.colliderRadius
                val distSq = dx * dx + dz * dz
                if (distSq >= minDist * minDist || distSq < 1e-5f) continue
                if (abs(a.pos.y - b.pos.y) > 2.8f) continue

                val dist = sqrt(distSq)
                val nx = dx / dist
                val nz = dz / dist
                val overlap = minDist - dist

                val totalMass = a.archetype.mass + b.archetype.mass
                val ratioA = b.archetype.mass / totalMass
                val ratioB = a.archetype.mass / totalMass

                a.pos = Vec3(a.pos.x - nx * overlap * ratioA, a.pos.y, a.pos.z - nz * overlap * ratioA)
                b.pos = Vec3(b.pos.x + nx * overlap * ratioB, b.pos.y, b.pos.z + nz * overlap * ratioB)

                val rvx = b.vel.x - a.vel.x
                val rvz = b.vel.z - a.vel.z
                val velAlongNormal = rvx * nx + rvz * nz
                if (velAlongNormal > 0f) continue

                val impactSpd = -velAlongNormal
                if (impactSpd > 3.5f) maxCrash = max(maxCrash, Math3D.clamp(impactSpd / 20f, 0.2f, 1f))

                val aCrushesB = (a.isPlayerControlled && smashCheat) ||
                    (a.archetype.isTank && !b.archetype.isTank) ||
                    (a.archetype.isMonsterTruck && !b.archetype.isTank && !b.archetype.isMonsterTruck && impactSpd > 5f)
                val bCrushesA = (b.isPlayerControlled && smashCheat) ||
                    (b.archetype.isTank && !a.archetype.isTank) ||
                    (b.archetype.isMonsterTruck && !a.archetype.isTank && !a.archetype.isMonsterTruck && impactSpd > 5f)

                if (aCrushesB && !b.isWrecked) {
                    if (a.archetype.isTank || (a.isPlayerControlled && smashCheat)) {
                        wreckVehicleWithBlast(b)
                        b.vel = Vec3(nx * 16f, 8.5f, nz * 16f)
                        exploded = true
                        cameraShake = max(cameraShake, 0.55f)
                    } else {
                        b.health = max(0f, b.health - impactSpd * 7.5f)
                        b.vel = Vec3(nx * 11f, 3.5f, nz * 11f)
                        a.vel = Vec3(a.vel.x, max(a.vel.y, 2.8f), a.vel.z)
                        if (b.health <= 0f) {
                            wreckVehicleWithBlast(b)
                            exploded = true
                        }
                    }
                    if (a.isPlayerControlled) {
                        wanted.addCrimeHeat(if (b.isPoliceAi) 90f else 45f, minStars = if (b.isPoliceAi) 2 else 1, neverWanted = neverWanted)
                    }
                } else if (bCrushesA && !a.isWrecked) {
                    if (b.archetype.isTank || (b.isPlayerControlled && smashCheat)) {
                        wreckVehicleWithBlast(a)
                        a.vel = Vec3(-nx * 16f, 8.5f, -nz * 16f)
                        exploded = true
                    } else {
                        a.health = max(0f, a.health - impactSpd * 7.5f)
                        a.vel = Vec3(-nx * 11f, 3.5f, -nz * 11f)
                    }
                } else {
                    val jImpulse = -(1.35f) * velAlongNormal
                    a.vel = Vec3(a.vel.x - nx * jImpulse * ratioA, a.vel.y, a.vel.z - nz * jImpulse * ratioA)
                    b.vel = Vec3(b.vel.x + nx * jImpulse * ratioB, b.vel.y, b.vel.z + nz * jImpulse * ratioB)
                    a.speed *= 0.65f
                    b.speed *= 0.65f

                    if (impactSpd > 6f && (a.isPlayerControlled || b.isPlayerControlled)) {
                        val otherIsPolice = (a.isPlayerControlled && b.isPoliceAi) || (b.isPlayerControlled && a.isPoliceAi)
                        wanted.addCrimeHeat(if (otherIsPolice) 80f else 28f, minStars = if (otherIsPolice) 2 else 1, neverWanted = neverWanted)
                        cameraShake = max(cameraShake, Math3D.clamp(impactSpd / 24f, 0.15f, 0.55f))
                        if (!smashCheat && !a.archetype.isTank && !b.archetype.isTank) {
                            applyPlayerDamage((impactSpd - 6f) * 1.1f, bypassSemiGodMode = false)
                        }
                    }
                }
            }
        }
        return SoundEvents(crashIntensity = maxCrash, explosionTriggered = exploded)
    }

    private fun stepProjectilesAndExplosions(dt: Float): Boolean {
        var exploded = false
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.lifetime -= dt
            p.vel = Vec3(p.vel.x, p.vel.y - 9.8f * dt, p.vel.z)
            p.pos = p.pos + p.vel * dt

            val groundY = world.supportHeightAt(p.pos.x, p.pos.z, p.pos.y)
            val hitWall = world.resolveHorizontal(p.pos.x, p.pos.z, p.pos.y, 0.35f).hit
            val hitVehicle = vehicles.firstOrNull { !it.isPlayerControlled && it.pos.distanceTo(p.pos) < it.archetype.colliderRadius + 0.9f }

            if (p.lifetime <= 0f || p.pos.y <= groundY || hitWall || hitVehicle != null) {
                detonateShellAt(p.pos)
                exploded = true
                iter.remove()
            }
        }

        val expIter = explosions.iterator()
        while (expIter.hasNext()) {
            val e = expIter.next()
            e.age += dt
            if (e.age >= e.maxAge) expIter.remove()
        }
        return exploded
    }

    private fun detonateShellAt(pos: Vec3) {
        explosions.add(ExplosionFx(nextEntityId++, Vec3(pos.x, max(0.5f, pos.y), pos.z)))
        cameraShake = max(cameraShake, 0.85f)
        val blastRadius = 10.5f
        for (v in vehicles) {
            if (v.isPlayerControlled) continue
            if (v.pos.distanceTo(pos) < blastRadius) {
                val push = (v.pos.xz() - pos.xz()).normalized()
                wreckVehicleWithBlast(v)
                v.vel = Vec3(push.x * 18f, 10f, push.z * 18f)
                wanted.addCrimeHeat(if (v.isPoliceAi) 110f else 65f, minStars = 2, neverWanted = cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED))
            }
        }
        for (npc in npcSystem.npcs) {
            if (!npc.isDead && npc.pos.distanceTo(pos) < blastRadius) {
                npc.health = 0f
                npc.isDead = true
            }
        }
    }

    private fun applyPlayerDamage(amount: Float, bypassSemiGodMode: Boolean) {
        if (amount <= 0f) return
        if (!bypassSemiGodMode && cheats.isActive(CheatCodeManager.CODE_BAGUVIX)) return
        var rem = amount
        if (playerArmor > 0f) {
            val absorbed = min(playerArmor, rem * 0.7f)
            playerArmor -= absorbed
            rem -= absorbed
        }
        playerHealth = max(0f, playerHealth - rem)
    }

    private fun buildSnapshot(cameraYawRad: Float, soundEvents: SoundEvents): HudSnapshot {
        val activeVeh = activeVehicle()
        val nearbyVeh = if (playerMode == PlayerMode.ON_FOOT) findNearestEnterableVehicle() else null
        val speedKmh = (playerVel.xz().length() * 3.6f).toInt()
        val blips = ArrayList<RadarBlip>(vehicles.size + 12)
        for (v in vehicles) {
            if (v.isPlayerControlled || v.isWrecked) continue
            val kind = when {
                v.isPoliceAi -> BlipKind.POLICE
                v.archetype.isTank -> BlipKind.TANK
                v.archetype.isMonsterTruck -> BlipKind.MONSTER
                else -> BlipKind.VEHICLE
            }
            blips.add(RadarBlip(v.pos.x, v.pos.z, kind, v.sirenPhase))
        }
        for (npc in npcSystem.npcs) {
            if (!npc.isDead && npc.role == NpcRole.PURPLE_GANG) {
                blips.add(RadarBlip(npc.pos.x, npc.pos.z, BlipKind.PURPLE_GANG))
            }
        }

        val sirenOn = vehicles.any { it.isPoliceAi && it.sirenActive && !it.isWrecked }
        val tankReady = activeVeh?.let {
            if (it.archetype.isTank) 1f - (it.cannonCooldown / 1.6f).coerceIn(0f, 1f) else 1f
        } ?: 1f

        return HudSnapshot(
            cash = playerCash,
            health = playerHealth,
            armor = playerArmor,
            vehicleHealthRatio = activeVeh?.let { (it.health / it.archetype.maxHealth).coerceIn(0f, 1f) } ?: 1f,
            wantedStars = wanted.stars,
            wantedFlashing = wanted.isFlashing,
            neverWantedActive = cheats.isActive(CheatCodeManager.CODE_NEVER_WANTED),
            playerMode = playerMode,
            activeVehicleArchetype = activeVeh?.archetype,
            speedKmh = speedKmh,
            altitudeMeters = max(0, (playerPos.y - 0.25f).toInt()),
            nearbyVehicleName = nearbyVeh?.archetype?.displayName
                ?: if (playerMode == PlayerMode.ON_FOOT && droppedJetpackPos != null && playerPos.distanceTo(droppedJetpackPos!!) < 2.8f) "Jetpack" else null,
            districtName = currentDistrict,
            districtToastAlpha = Math3D.clamp(districtToastTimer, 0f, 1f),
            vehicleToastName = vehicleToastName,
            vehicleToastAlpha = Math3D.clamp(vehicleToastTimer, 0f, 1f),
            cheatToastMessage = cheatToastMessage,
            cheatToastAlpha = Math3D.clamp(cheatToastTimer, 0f, 1f),
            activeCheatCodes = cheats.activeCodes(),
            nitroEnabled = cheats.isActive(CheatCodeManager.CODE_NITRO),
            tankCannonReadyRatio = tankReady,
            banner = activeBanner,
            bannerSubtext = bannerSubtext,
            isPaused = isPaused,
            weatherPreset = weatherPreset,
            playerX = playerPos.x,
            playerZ = playerPos.z,
            playerYawRad = playerYawRad,
            cameraYawRad = cameraYawRad,
            radarBlips = blips,
            arrestedProgress = (wanted.arrestTimer / 3.0f).coerceIn(0f, 1f),
            sirenActive = sirenOn,
            jetpackActive = playerMode == PlayerMode.JETPACK && jetpackThrusting,
            isSwimming = playerMode == PlayerMode.SWIMMING,
            soundEvents = soundEvents,
        )
    }
}
