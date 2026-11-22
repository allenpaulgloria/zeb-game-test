package com.sunsetgrove.game.engine

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.sunsetgrove.game.world.Math3D
import java.nio.ByteBuffer

data class LoadedModelHandle(
    val key: String,
    val asset: FilamentAsset,
    val normMatrix: FloatArray,
    val turretEntity: Int,
    val turretBaseTransform: FloatArray?,
    private val animMap: Map<String, Int>,
) {
    private var animTimeSec: Float = 0f

    fun updateAnimation(stateName: String, dt: Float) {
        val animator = asset.instance.animator
        if (animator.animationCount <= 0) return
        val idx = animMap[stateName] ?: animMap["Idle"] ?: 0
        val duration = animator.getAnimationDuration(idx)
        if (duration > 1e-4f) {
            animTimeSec = (animTimeSec + dt) % duration
            animator.applyAnimation(idx, animTimeSec)
            animator.updateBoneMatrices()
        }
    }
}

/**
 * Loads and caches glTF 2.0 (.glb) assets from app assets (checking models/overrides/
 * first for custom Sketchfab / Mixamo drop-in swaps), normalizes bounding box & ground
 * pivot, and exposes Filament instances with skeletal Animator & Rhino Tank Turret hooks.
 */
class GltfModelManager(
    private val context: Context,
    private val engine: Engine,
    private val scene: Scene,
) {
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)

    private val manifest: Map<String, ModelAssetSpec> = AssetManifest.loadFromAssets(context)
    private val byteCache = HashMap<String, ByteArray>()
    private val activeHandles = mutableListOf<LoadedModelHandle>()

    private fun readModelBytes(spec: ModelAssetSpec): ByteArray {
        return byteCache.getOrPut(spec.key) {
            // Check zero-code override directory first (e.g., models/overrides/vehicle_tank.glb)
            val fileName = spec.path.substringAfterLast('/')
            val overridePath = "models/overrides/$fileName"
            val stream = try {
                context.assets.open(overridePath)
            } catch (_: Exception) {
                context.assets.open(spec.path)
            }
            stream.use { it.readBytes() }
        }
    }

    fun spawnModel(key: String, useTargetHeightNormalization: Boolean = true): LoadedModelHandle? {
        val spec = manifest[key] ?: return null
        val bytes = readModelBytes(spec)
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            flip()
        }
        val asset = assetLoader.createAsset(buffer) ?: return null
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()

        // Enable shadow casting & receiving across all renderable primitives
        val rm = engine.renderableManager
        for (entity in asset.entities) {
            val ri = rm.getInstance(entity)
            if (ri != 0) {
                rm.setCastShadows(ri, true)
                rm.setReceiveShadows(ri, true)
            }
        }

        val normMat = if (useTargetHeightNormalization) {
            val box = asset.boundingBox
            val norm = AssetManifest.computeNormalization(
                center = box.center,
                halfExtent = box.halfExtent,
                targetHeight = spec.targetHeight,
                yawOffsetDeg = spec.yawOffsetDeg,
            )
            Math3D.composeMatrix(
                tx = norm.offsetX,
                ty = norm.offsetY,
                tz = norm.offsetZ,
                yawRad = norm.yawOffsetRad,
                sx = norm.scale,
                sy = norm.scale,
                sz = norm.scale,
            )
        } else {
            Math3D.composeMatrix(0f, 0f, 0f, yawRad = spec.yawOffsetDeg * Math3D.DEG_TO_RAD)
        }

        // Map animation clips by friendly names (Soldier.glb has Idle, Walk, Run!)
        val animator = asset.instance.animator
        val animMap = HashMap<String, Int>()
        for (i in 0 until animator.animationCount) {
            val name = animator.getAnimationName(i)
            when {
                name.contains("idle", ignoreCase = true) -> animMap["Idle"] = i
                name.contains("walk", ignoreCase = true) -> animMap["Walk"] = i
                name.contains("run", ignoreCase = true) -> animMap["Run"] = i
            }
        }

        // Locate Turret node for Rhino Army Tank independent turret rotation
        val turretEntity = asset.getFirstEntityByName("Turret")
        val turretBase = if (turretEntity != 0) {
            val tm = engine.transformManager
            val inst = tm.getInstance(turretEntity)
            if (inst != 0) FloatArray(16).also { tm.getTransform(inst, it) } else null
        } else {
            null
        }

        scene.addEntities(asset.entities)
        val handle = LoadedModelHandle(key, asset, normMat, turretEntity, turretBase, animMap)
        activeHandles.add(handle)
        return handle
    }

    fun setWorldTransform(
        handle: LoadedModelHandle,
        x: Float, y: Float, z: Float,
        yawRad: Float = 0f,
        pitchRad: Float = 0f,
        rollRad: Float = 0f,
        scale: Float = 1f,
    ) {
        val worldMat = Math3D.composeMatrix(x, y, z, yawRad, pitchRad, rollRad, scale, scale, scale)
        val finalMat = Math3D.multiplyMat4(worldMat, handle.normMatrix)
        val tm = engine.transformManager
        val inst = tm.getInstance(handle.asset.root)
        if (inst != 0) {
            tm.setTransform(inst, finalMat)
        }
    }

    fun setTurretLocalYaw(handle: LoadedModelHandle, localYawRad: Float) {
        if (handle.turretEntity == 0 || handle.turretBaseTransform == null) return
        val tm = engine.transformManager
        val inst = tm.getInstance(handle.turretEntity)
        if (inst != 0) {
            val rot = Math3D.composeMatrix(0f, 0f, 0f, yawRad = localYawRad)
            val combined = Math3D.multiplyMat4(handle.turretBaseTransform, rot)
            tm.setTransform(inst, combined)
        }
    }

    fun setVisible(handle: LoadedModelHandle, visible: Boolean) {
        if (visible) {
            scene.addEntities(handle.asset.entities)
        } else {
            scene.removeEntities(handle.asset.entities)
        }
    }

    fun destroyModel(handle: LoadedModelHandle) {
        scene.removeEntities(handle.asset.entities)
        assetLoader.destroyAsset(handle.asset)
        activeHandles.remove(handle)
    }

    fun destroyAll() {
        for (h in activeHandles.toList()) {
            scene.removeEntities(h.asset.entities)
            assetLoader.destroyAsset(h.asset)
        }
        activeHandles.clear()
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
    }
}
