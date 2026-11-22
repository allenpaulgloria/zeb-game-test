package com.sunsetgrove.game.engine

import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.View
import com.sunsetgrove.game.gameplay.WeatherPreset

/**
 * Configures the signature GTA: San Andreas golden-hour California atmosphere,
 * plus live weather/sky presets triggered by cheat codes (AFZLLQLL, OFVIAC)
 * and dramatic desaturation during WASTED / BUSTED slow-motion.
 */
class GoldenHourLighting(
    private val engine: Engine,
    private val scene: Scene,
    private val view: View,
) {
    private val sunEntity: Int = EntityManager.get().create()
    private var indirectLight: IndirectLight? = null
    private var skybox: Skybox? = null
    private var colorGrading: ColorGrading? = null

    private var currentPreset: WeatherPreset? = null
    private var currentDesaturated: Boolean = false

    init {
        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 0.72f, 0.42f)
            .intensity(115_000f)
            .direction(-0.62f, -0.42f, -0.66f)
            .castShadows(true)
            .build(engine, sunEntity)
        scene.addEntity(sunEntity)

        applyPreset(WeatherPreset.GOLDEN_HOUR, desaturated = false)
    }

    fun applyPreset(preset: WeatherPreset, desaturated: Boolean) {
        if (preset == currentPreset && desaturated == currentDesaturated) return
        currentPreset = preset
        currentDesaturated = desaturated

        val lm = engine.lightManager
        val sunInst = lm.getInstance(sunEntity)

        val skyColor: FloatArray
        val fogColor: FloatArray
        val fogDist: Float
        val fogDensity: Float
        val shBands: FloatArray
        val iblIntensity: Float

        when (preset) {
            WeatherPreset.GOLDEN_HOUR -> {
                lm.setColor(sunInst, 1.0f, 0.70f, 0.38f)
                lm.setIntensity(sunInst, 120_000f)
                lm.setDirection(sunInst, -0.66f, -0.36f, -0.65f)
                skyColor = floatArrayOf(0.95f, 0.56f, 0.28f, 1.0f)
                fogColor = floatArrayOf(0.92f, 0.54f, 0.28f)
                fogDist = 72f
                fogDensity = 0.045f
                iblIntensity = 36_000f
                shBands = floatArrayOf(
                    0.78f, 0.56f, 0.38f,
                    0.18f, 0.14f, 0.16f,
                    -0.12f, -0.08f, -0.05f,
                    0.14f, 0.08f, 0.03f,
                    0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                )
            }
            WeatherPreset.SUNNY_DAY -> {
                lm.setColor(sunInst, 1.0f, 0.96f, 0.88f)
                lm.setIntensity(sunInst, 130_000f)
                lm.setDirection(sunInst, -0.25f, -0.88f, -0.40f)
                skyColor = floatArrayOf(0.32f, 0.64f, 0.96f, 1.0f)
                fogColor = floatArrayOf(0.68f, 0.82f, 0.95f)
                fogDist = 115f
                fogDensity = 0.025f
                iblIntensity = 42_000f
                shBands = floatArrayOf(
                    0.75f, 0.82f, 0.92f,
                    0.12f, 0.18f, 0.28f,
                    0f, 0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                )
            }
            WeatherPreset.ORANGE_SKY_2100 -> {
                lm.setColor(sunInst, 1.0f, 0.48f, 0.16f)
                lm.setIntensity(sunInst, 105_000f)
                lm.setDirection(sunInst, -0.78f, -0.22f, -0.58f)
                skyColor = floatArrayOf(0.88f, 0.36f, 0.12f, 1.0f)
                fogColor = floatArrayOf(0.86f, 0.34f, 0.12f)
                fogDist = 52f
                fogDensity = 0.072f
                iblIntensity = 30_000f
                shBands = floatArrayOf(
                    0.82f, 0.40f, 0.18f,
                    0.18f, 0.08f, 0.04f,
                    -0.14f, -0.06f, -0.03f,
                    0.18f, 0.07f, 0.02f,
                    0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                )
            }
        }

        indirectLight?.let { engine.destroyIndirectLight(it) }
        indirectLight = IndirectLight.Builder()
            .irradiance(3, shBands)
            .intensity(iblIntensity)
            .build(engine)
        scene.indirectLight = indirectLight

        skybox?.let { engine.destroySkybox(it) }
        skybox = Skybox.Builder()
            .color(skyColor[0], skyColor[1], skyColor[2], skyColor[3])
            .build(engine)
        scene.skybox = skybox

        val fog = View.FogOptions().apply {
            enabled = true
            distance = fogDist
            density = fogDensity
            maximumOpacity = 0.88f
            height = 2.0f
            heightFalloff = 0.08f
            color = fogColor
            inScatteringStart = 15f
            inScatteringSize = 40f
        }
        view.fogOptions = fog

        colorGrading?.let { engine.destroyColorGrading(it) }
        colorGrading = ColorGrading.Builder()
            .toneMapping(ColorGrading.ToneMapping.ACES)
            .saturation(if (desaturated) 0.18f else 1.12f)
            .contrast(if (desaturated) 1.18f else 1.04f)
            .build(engine)
        view.colorGrading = colorGrading
    }

    fun destroy() {
        scene.removeEntity(sunEntity)
        engine.destroyEntity(sunEntity)
        EntityManager.get().destroy(sunEntity)
        indirectLight?.let { engine.destroyIndirectLight(it) }
        skybox?.let { engine.destroySkybox(it) }
        colorGrading?.let { engine.destroyColorGrading(it) }
    }
}
