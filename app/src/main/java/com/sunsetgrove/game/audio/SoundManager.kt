package com.sunsetgrove.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.sunsetgrove.game.gameplay.HudSnapshot
import com.sunsetgrove.game.gameplay.PlayerMode
import com.sunsetgrove.game.world.Math3D

/**
 * Low-latency SoundPool SFX manager for Sunset Grove:
 * engine RPM pitch modulation, police wail sirens, Jetpack thrusters, ocean swimming splashes,
 * gunshots, baseball bat hits, Rhino Tank cannon booms, crash thuds, Cheat chime, & WASTED/RESPECT+.
 */
class SoundManager(context: Context) {
    private val pool: SoundPool
    private val loadedIds = HashSet<Int>()

    private val sfxEngine: Int
    private val sfxSiren: Int
    private val sfxCrash: Int
    private val sfxTankCannon: Int
    private val sfxJetpack: Int
    private val sfxCheat: Int
    private val sfxCarDoor: Int
    private val sfxWasted: Int
    private val sfxRespect: Int
    private val sfxGunshot: Int
    private val sfxBatHit: Int
    private val sfxSwim: Int

    private var engineStreamId: Int = 0
    private var sirenStreamId: Int = 0
    private var jetpackStreamId: Int = 0
    private var swimStreamId: Int = 0

    var muted: Boolean = false
        set(value) {
            field = value
            if (value) stopLoops()
        }

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attrs)
            .build()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds.add(sampleId)
        }

        sfxEngine = loadAsset(context, "audio/sfx_engine.wav")
        sfxSiren = loadAsset(context, "audio/sfx_siren.wav")
        sfxCrash = loadAsset(context, "audio/sfx_crash.wav")
        sfxTankCannon = loadAsset(context, "audio/sfx_tank_cannon.wav")
        sfxJetpack = loadAsset(context, "audio/sfx_jetpack.wav")
        sfxCheat = loadAsset(context, "audio/sfx_cheat.wav")
        sfxCarDoor = loadAsset(context, "audio/sfx_car_door.wav")
        sfxWasted = loadAsset(context, "audio/sfx_wasted.wav")
        sfxRespect = loadAsset(context, "audio/sfx_respect.wav")
        sfxGunshot = loadAsset(context, "audio/sfx_gunshot.wav")
        sfxBatHit = loadAsset(context, "audio/sfx_bat_hit.wav")
        sfxSwim = loadAsset(context, "audio/sfx_swim.wav")
    }

    private fun loadAsset(context: Context, path: String): Int = try {
        context.assets.openFd(path).use { fd -> pool.load(fd, 1) }
    } catch (_: Exception) {
        0
    }

    fun update(snapshot: HudSnapshot) {
        if (muted || snapshot.isPaused) {
            stopLoops()
            return
        }

        val ev = snapshot.soundEvents
        if (ev.cheatActivated) playOneShot(sfxCheat, 0.85f, 1.0f)
        if (ev.carDoorSlam) playOneShot(sfxCarDoor, 0.72f, 1.0f)
        if (ev.gunshotFired) playOneShot(sfxGunshot, 0.78f, 1.0f)
        if (ev.batHit) playOneShot(sfxBatHit, 0.85f, 1.0f)
        if (ev.tankCannonFired) playOneShot(sfxTankCannon, 1.0f, 0.92f)
        if (ev.explosionTriggered) playOneShot(sfxTankCannon, 0.95f, 0.72f)
        if (ev.crashIntensity > 0.15f) playOneShot(sfxCrash, ev.crashIntensity.coerceAtMost(0.95f), 0.9f + ev.crashIntensity * 0.2f)
        if (ev.wastedTriggered || ev.bustedTriggered) playOneShot(sfxWasted, 0.95f, 1.0f)
        if (ev.respectTriggered) playOneShot(sfxRespect, 0.9f, 1.0f)

        // Vehicle Engine loop
        if (snapshot.playerMode == PlayerMode.DRIVING) {
            val basePitch = when {
                snapshot.activeVehicleArchetype?.isTank == true -> 0.58f
                snapshot.activeVehicleArchetype?.isMonsterTruck == true -> 0.68f
                else -> 0.85f
            }
            val pitch = Math3D.clamp(basePitch + (snapshot.speedKmh / 140f) * 0.95f, 0.55f, 1.95f)
            if (engineStreamId == 0 && loadedIds.contains(sfxEngine)) {
                engineStreamId = pool.play(sfxEngine, 0.34f, 0.34f, 1, -1, pitch)
            } else if (engineStreamId != 0) {
                pool.setRate(engineStreamId, pitch)
            }
        } else if (engineStreamId != 0) {
            pool.stop(engineStreamId)
            engineStreamId = 0
        }

        // Jetpack Thruster loop
        if (snapshot.jetpackActive) {
            if (jetpackStreamId == 0 && loadedIds.contains(sfxJetpack)) {
                jetpackStreamId = pool.play(sfxJetpack, 0.40f, 0.40f, 1, -1, 1.05f)
            }
        } else if (jetpackStreamId != 0) {
            pool.stop(jetpackStreamId)
            jetpackStreamId = 0
        }

        // Ocean Swimming Splash loop
        if (snapshot.isSwimming && snapshot.speedKmh > 1) {
            if (swimStreamId == 0 && loadedIds.contains(sfxSwim)) {
                swimStreamId = pool.play(sfxSwim, 0.36f, 0.36f, 1, -1, 1.0f)
            }
        } else if (swimStreamId != 0) {
            pool.stop(swimStreamId)
            swimStreamId = 0
        }

        // Police Siren loop
        if (snapshot.sirenActive) {
            if (sirenStreamId == 0 && loadedIds.contains(sfxSiren)) {
                sirenStreamId = pool.play(sfxSiren, 0.28f, 0.28f, 1, -1, 1.0f)
            }
        } else if (sirenStreamId != 0) {
            pool.stop(sirenStreamId)
            sirenStreamId = 0
        }
    }

    private fun playOneShot(id: Int, vol: Float, rate: Float) {
        if (id != 0 && loadedIds.contains(id)) {
            pool.play(id, vol, vol, 2, 0, rate)
        }
    }

    fun stopLoops() {
        if (engineStreamId != 0) { pool.stop(engineStreamId); engineStreamId = 0 }
        if (sirenStreamId != 0) { pool.stop(sirenStreamId); sirenStreamId = 0 }
        if (jetpackStreamId != 0) { pool.stop(jetpackStreamId); jetpackStreamId = 0 }
        if (swimStreamId != 0) { pool.stop(swimStreamId); swimStreamId = 0 }
    }

    fun release() {
        stopLoops()
        pool.release()
    }
}
