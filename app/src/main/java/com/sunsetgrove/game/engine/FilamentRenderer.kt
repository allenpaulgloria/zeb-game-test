package com.sunsetgrove.game.engine

import android.content.Context
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Gltfio
import com.sunsetgrove.game.gameplay.BannerType
import com.sunsetgrove.game.gameplay.GameSimulation
import com.sunsetgrove.game.gameplay.PlayerMode
import com.sunsetgrove.game.world.DistrictMap
import com.sunsetgrove.game.world.Math3D
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native Google Filament PBR 3D renderer for Sunset Grove.
 * Owns the SurfaceView lifecycle, static West Coast district models, dynamic player /
 * Jetpack / vehicle / Rhino Tank turret / projectile / explosion meshes, and dynamic lights.
 */
class FilamentRenderer(
    private val context: Context,
    private val surfaceView: SurfaceView,
    val cameraController: ThirdPersonCamera = ThirdPersonCamera(),
) {
    companion object {
        @Volatile
        private var initialized = false

        fun ensureInitialized() {
            if (!initialized) {
                Filament.init()
                Gltfio.init()
                initialized = true
            }
        }
    }

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val camera: Camera
    private val cameraEntity: Int
    private var swapChain: SwapChain? = null

    private val uiHelper: UiHelper
    private val displayHelper: DisplayHelper
    private val lighting: GoldenHourLighting
    private val models: GltfModelManager

    // Dynamic handles synchronized with GameSimulation
    private var playerHandle: LoadedModelHandle? = null
    private var jetpackHandle: LoadedModelHandle? = null
    private val vehicleHandles = HashMap<Int, LoadedModelHandle>()
    private val projectileHandles = HashMap<Int, LoadedModelHandle>()
    private val explosionHandles = HashMap<Int, LoadedModelHandle>()

    // Dynamic point lights: jetpack thruster glow + police flashing siren light
    private val jetpackLightEntity: Int
    private val sirenLightEntity: Int

    private var viewportAspect: Double = 16.0 / 9.0
    private var isDestroyed: Boolean = false

    init {
        ensureInitialized()
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()

        cameraEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)
        view.camera = camera
        view.scene = scene
        view.antiAliasing = View.AntiAliasing.FXAA
        view.dynamicResolutionOptions = View.DynamicResolutionOptions().apply {
            enabled = true
            minScale = 0.65f
            maxScale = 1.0f
        }

        lighting = GoldenHourLighting(engine, scene, view)
        models = GltfModelManager(context, engine, scene)

        // Create dynamic point lights
        jetpackLightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.POINT)
            .color(1.0f, 0.56f, 0.16f)
            .intensity(0f)
            .falloff(9.0f)
            .position(0f, -50f, 0f)
            .build(engine, jetpackLightEntity)
        scene.addEntity(jetpackLightEntity)

        sirenLightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.POINT)
            .color(1.0f, 0.1f, 0.1f)
            .intensity(0f)
            .falloff(16.0f)
            .position(0f, -50f, 0f)
            .build(engine, sirenLightEntity)
        scene.addEntity(sirenLightEntity)

        buildStaticWorld()
        spawnPlayerAndJetpack()

        displayHelper = DisplayHelper(context)
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
                displayHelper.attach(renderer, surfaceView.display)
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                viewportAspect = width.toDouble() / height.coerceAtLeast(1).toDouble()
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    private fun buildStaticWorld() {
        // Base California ground, Verona Beach sand strip, Pacific ocean horizon
        models.spawnModel("world_ground_grass", useTargetHeightNormalization = false)?.let {
            models.setWorldTransform(it, 20f, 0f, 0f)
        }
        models.spawnModel("world_ground_beach", useTargetHeightNormalization = false)?.let {
            models.setWorldTransform(it, -125f, 0.02f, 0f)
        }
        models.spawnModel("world_ground_ocean", useTargetHeightNormalization = false)?.let {
            models.setWorldTransform(it, -238f, -0.02f, 0f)
        }

        // Asphalt road grid & cul-de-sac
        for (seg in DistrictMap.roadSegments) {
            models.spawnModel("world_road_strip", useTargetHeightNormalization = false)?.let {
                val scaleX = seg.width / 14f
                val scaleZ = seg.length / 20f
                val worldMat = Math3D.composeMatrix(
                    seg.x, 0.05f, seg.z,
                    yawRad = seg.yawDeg * Math3D.DEG_TO_RAD,
                    sx = scaleX, sy = 1f, sz = scaleZ,
                )
                val tm = engine.transformManager
                tm.setTransform(tm.getInstance(it.asset.root), Math3D.multiplyMat4(worldMat, it.normMatrix))
            }
        }

        // Buildings (Grove Circle bungalows, Vinewood storefronts, Downtown landable towers)
        for (b in DistrictMap.buildings) {
            models.spawnModel(b.modelKey, useTargetHeightNormalization = false)?.let {
                models.setWorldTransform(it, b.x, 0.12f, b.z, yawRad = b.yawDeg * Math3D.DEG_TO_RAD)
            }
        }

        // Stunt ramps
        for (r in DistrictMap.stuntRamps) {
            models.spawnModel("world_stunt_ramp", useTargetHeightNormalization = false)?.let {
                models.setWorldTransform(it, r.x, 0.16f, r.z, yawRad = r.yawDeg * Math3D.DEG_TO_RAD)
            }
        }

        // Palm trees, Poly Haven CC0 street lamps, fire hydrants, concrete barriers
        for (p in DistrictMap.props) {
            models.spawnModel(p.modelKey, useTargetHeightNormalization = true)?.let {
                models.setWorldTransform(it, p.x, 0.16f, p.z, yawRad = p.yawDeg * Math3D.DEG_TO_RAD)
            }
        }
    }

    private fun spawnPlayerAndJetpack() {
        playerHandle = models.spawnModel("character_player", useTargetHeightNormalization = true)
        jetpackHandle = models.spawnModel("gear_jetpack", useTargetHeightNormalization = true)
        jetpackHandle?.let { models.setVisible(it, false) }
    }

    fun renderFrame(frameTimeNanos: Long, dt: Float, sim: GameSimulation) {
        if (isDestroyed) return
        val sc = swapChain ?: return
        if (!uiHelper.isReadyToRender) return

        // Update sky/weather/desaturation
        val desaturated = sim.activeBanner == BannerType.WASTED || sim.activeBanner == BannerType.BUSTED
        lighting.applyPreset(sim.weatherPreset, desaturated)

        syncDynamicEntities(dt, sim)

        // Update third-person camera
        val speed = sim.playerVel.xz().length()
        val pose = cameraController.update(
            dt = dt,
            mode = sim.playerMode,
            actorPos = sim.playerPos,
            actorYawRad = sim.playerYawRad,
            speedMetersPerSec = speed,
            vehicleArchetype = sim.activeVehicle()?.archetype,
            shakeIntensity = sim.cameraShake,
            world = sim.world,
        )
        camera.setProjection(pose.fovDeg, viewportAspect, 0.25, 480.0, Camera.Fov.VERTICAL)
        camera.lookAt(
            pose.eye.x.toDouble(), pose.eye.y.toDouble(), pose.eye.z.toDouble(),
            pose.target.x.toDouble(), pose.target.y.toDouble(), pose.target.z.toDouble(),
            0.0, 1.0, 0.0,
        )

        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    private fun syncDynamicEntities(dt: Float, sim: GameSimulation) {
        val lm = engine.lightManager

        // 1. Player & Jetpack
        playerHandle?.let { ph ->
            val showPlayer = sim.playerMode != PlayerMode.DRIVING
            models.setVisible(ph, showPlayer)
            if (showPlayer) {
                val tiltPitch = if (sim.playerMode == PlayerMode.JETPACK) {
                    Math3D.clamp(sim.playerVel.xz().length() * 0.025f, 0f, 0.28f)
                } else 0f
                models.setWorldTransform(
                    ph,
                    sim.playerPos.x, sim.playerPos.y, sim.playerPos.z,
                    yawRad = sim.playerYawRad,
                    pitchRad = tiltPitch,
                )
                ph.updateAnimation(sim.playerAnimState, dt)
            }
        }

        jetpackHandle?.let { jh ->
            val jpInst = lm.getInstance(jetpackLightEntity)
            when {
                sim.playerMode == PlayerMode.JETPACK -> {
                    models.setVisible(jh, true)
                    // Mount cleanly on player's upper back (-forward offset)
                    val bx = sim.playerPos.x - sin(sim.playerYawRad) * 0.24f
                    val bz = sim.playerPos.z - cos(sim.playerYawRad) * 0.24f
                    val by = sim.playerPos.y + 0.85f
                    models.setWorldTransform(jh, bx, by, bz, yawRad = sim.playerYawRad)
                    lm.setPosition(jpInst, bx, by - 0.55f, bz)
                    lm.setIntensity(jpInst, if (sim.jetpackThrusting) 120_000f else 28_000f)
                }
                sim.droppedJetpackPos != null -> {
                    val dp = sim.droppedJetpackPos!!
                    models.setVisible(jh, true)
                    models.setWorldTransform(jh, dp.x, dp.y, dp.z)
                    lm.setIntensity(jpInst, 0f)
                }
                else -> {
                    models.setVisible(jh, false)
                    lm.setIntensity(jpInst, 0f)
                }
            }
        }

        // 2. Vehicles (street cars, police cruisers, Monster Trucks, Rhino Army Tanks)
        val liveIds = HashSet<Int>(sim.vehicles.size)
        var nearestSirenVehX = 0f
        var nearestSirenVehY = -50f
        var nearestSirenVehZ = 0f
        var nearestSirenDist = Float.MAX_VALUE
        var nearestSirenPhase = 0f

        for (v in sim.vehicles) {
            liveIds.add(v.id)
            val handle = vehicleHandles[v.id]
                ?: models.spawnModel(v.archetype.modelKey, useTargetHeightNormalization = true)
                    ?.also { vehicleHandles[v.id] = it }
                ?: continue
            models.setWorldTransform(
                handle,
                x = v.pos.x,
                y = v.pos.y + v.suspensionBounce,
                z = v.pos.z,
                yawRad = v.yawRad,
                pitchRad = v.pitchRad,
                rollRad = v.rollRad,
            )
            if (v.archetype.isTank) {
                val localTurretYaw = Math3D.normalizeAngleRad(v.turretYawRad - v.yawRad)
                models.setTurretLocalYaw(handle, localTurretYaw)
            }
            if (v.isPoliceAi && v.sirenActive && !v.isWrecked) {
                val d = v.pos.horizontalDistanceTo(sim.playerPos)
                if (d < nearestSirenDist) {
                    nearestSirenDist = d
                    nearestSirenVehX = v.pos.x
                    nearestSirenVehY = v.pos.y + 1.95f
                    nearestSirenVehZ = v.pos.z
                    nearestSirenPhase = v.sirenPhase
                }
            }
        }
        val vehIter = vehicleHandles.entries.iterator()
        while (vehIter.hasNext()) {
            val entry = vehIter.next()
            if (!liveIds.contains(entry.key)) {
                models.destroyModel(entry.value)
                vehIter.remove()
            }
        }

        // Update flashing red/blue police lightbar point light on closest pursuing cruiser
        val sirenInst = lm.getInstance(sirenLightEntity)
        if (nearestSirenDist < 95f) {
            lm.setPosition(sirenInst, nearestSirenVehX, nearestSirenVehY, nearestSirenVehZ)
            if (nearestSirenPhase < 1f) {
                lm.setColor(sirenInst, 1.0f, 0.08f, 0.08f)
            } else {
                lm.setColor(sirenInst, 0.08f, 0.38f, 1.0f)
            }
            lm.setIntensity(sirenInst, 160_000f)
        } else {
            lm.setIntensity(sirenInst, 0f)
        }

        // 3. Rhino Tank Cannon Shells
        val liveProjIds = HashSet<Int>(sim.projectiles.size)
        for (p in sim.projectiles) {
            liveProjIds.add(p.id)
            val handle = projectileHandles[p.id]
                ?: models.spawnModel("fx_tank_shell", useTargetHeightNormalization = true)
                    ?.also { projectileHandles[p.id] = it }
                ?: continue
            models.setWorldTransform(handle, p.pos.x, p.pos.y, p.pos.z)
        }
        val pIter = projectileHandles.entries.iterator()
        while (pIter.hasNext()) {
            val e = pIter.next()
            if (!liveProjIds.contains(e.key)) {
                models.destroyModel(e.value)
                pIter.remove()
            }
        }

        // 4. Explosions (expanding fiery spheres)
        val liveExpIds = HashSet<Int>(sim.explosions.size)
        for (ex in sim.explosions) {
            liveExpIds.add(ex.id)
            val handle = explosionHandles[ex.id]
                ?: models.spawnModel("fx_explosion", useTargetHeightNormalization = true)
                    ?.also { explosionHandles[ex.id] = it }
                ?: continue
            val progress = (ex.age / ex.maxAge).coerceIn(0f, 1f)
            val radiusScale = (0.4f + progress * ex.maxRadius * 0.55f) * (1f - progress * 0.45f)
            models.setWorldTransform(handle, ex.pos.x, ex.pos.y, ex.pos.z, scale = radiusScale.coerceAtLeast(0.1f))
        }
        val eIter = explosionHandles.entries.iterator()
        while (eIter.hasNext()) {
            val e = eIter.next()
            if (!liveExpIds.contains(e.key)) {
                models.destroyModel(e.value)
                eIter.remove()
            }
        }
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        uiHelper.detach()
        displayHelper.detach()
        models.destroyAll()
        lighting.destroy()
        scene.removeEntity(jetpackLightEntity)
        engine.destroyEntity(jetpackLightEntity)
        EntityManager.get().destroy(jetpackLightEntity)
        scene.removeEntity(sirenLightEntity)
        engine.destroyEntity(sirenLightEntity)
        EntityManager.get().destroy(sirenLightEntity)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }
}
