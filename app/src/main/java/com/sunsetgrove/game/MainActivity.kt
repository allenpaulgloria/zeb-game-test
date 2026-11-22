package com.sunsetgrove.game

import android.os.Bundle
import android.view.Choreographer
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sunsetgrove.game.audio.SoundManager
import com.sunsetgrove.game.engine.FilamentRenderer
import com.sunsetgrove.game.gameplay.ControlInput
import com.sunsetgrove.game.gameplay.GameSimulation
import com.sunsetgrove.game.gameplay.HudSnapshot
import com.sunsetgrove.game.gameplay.SoundEvents
import com.sunsetgrove.game.ui.PauseAndCheatsOverlay
import com.sunsetgrove.game.ui.PersistentIntroAndTrackLayer
import com.sunsetgrove.game.ui.SanAndreasBannerOverlay
import com.sunsetgrove.game.ui.SanAndreasStatusHud
import com.sunsetgrove.game.ui.TouchControlsOverlay
import com.sunsetgrove.game.ui.TouchInputBridge
import com.sunsetgrove.game.ui.YouTubeTrackController
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity(), Choreographer.FrameCallback {

    private lateinit var sim: GameSimulation
    private lateinit var soundManager: SoundManager
    private lateinit var youTubeController: YouTubeTrackController
    private var filamentRenderer: FilamentRenderer? = null
    private val touchBridge = TouchInputBridge()

    private val hudState = MutableStateFlow(HudSnapshot())
    private var showIntroUi by mutableStateOf(true)
    private var playTrackInGameUi by mutableStateOf(true)
    private var miniPlayerExpandedUi by mutableStateOf(false)
    private var audioMutedUi by mutableStateOf(false)
    private var lastCheatBannerUi by mutableStateOf("")

    // Hardware keyboard keys for seamless emulator testing alongside touch controls
    private val pressedKeys = HashSet<Int>()
    private var lastFrameNanos: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive fullscreen landscape gaming window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        sim = GameSimulation()
        sim.isPaused = true // Hold simulation paused while the YouTube intro plays
        soundManager = SoundManager(this)
        youTubeController = YouTubeTrackController(this)

        setContent {
            SunsetGroveGameScreen()
        }
    }

    private fun setPlayTrackInGame(enabled: Boolean) {
        playTrackInGameUi = enabled
        if (!showIntroUi) {
            if (enabled) youTubeController.play() else youTubeController.pause()
        }
    }

    private fun enterGameFromIntro() {
        showIntroUi = false
        sim.isPaused = false
        if (playTrackInGameUi) {
            youTubeController.play()
        } else {
            youTubeController.pause()
        }
    }

    private fun replayIntro() {
        showIntroUi = true
        sim.isPaused = true
        soundManager.stopLoops()
        youTubeController.restartAndPlay()
    }

    @Composable
    private fun SunsetGroveGameScreen() {
        val snapshot by hudState.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Native Google Filament 3D PBR Surface
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).also { sv ->
                        filamentRenderer?.destroy()
                        filamentRenderer = FilamentRenderer(ctx, sv)
                    }
                },
            )

            // 2. Live San Andreas HUD & Multi-Touch Controls (when playing & not paused)
            if (!showIntroUi && !snapshot.isPaused) {
                TouchControlsOverlay(
                    snapshot = snapshot,
                    bridge = touchBridge,
                    trackPlayingInGame = playTrackInGameUi,
                    onPauseClick = {
                        sim.isPaused = true
                        soundManager.stopLoops()
                    },
                    onCameraToggleClick = {
                        filamentRenderer?.cameraController?.toggleDistancePreset()
                    },
                    onToggleTrackInGame = {
                        setPlayTrackInGame(!playTrackInGameUi)
                    },
                )

                SanAndreasStatusHud(
                    snapshot = snapshot,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 16.dp),
                )

                SanAndreasBannerOverlay(
                    banner = snapshot.banner,
                    subtext = snapshot.bannerSubtext,
                    onTapRespawn = { sim.respawnAtGroveCircle(chargeHospitalFee = true) },
                )
            }

            // 3. Persistent YouTube Intro & In-Game Track Layer (IeIUm-aqaCg)
            PersistentIntroAndTrackLayer(
                controller = youTubeController,
                showIntro = showIntroUi,
                playTrackInGame = playTrackInGameUi,
                miniPlayerExpanded = miniPlayerExpandedUi,
                onTogglePlayInGame = { enabled -> setPlayTrackInGame(enabled) },
                onToggleMiniExpanded = { miniPlayerExpandedUi = !miniPlayerExpandedUi },
                onEnterGame = { enterGameFromIntro() },
            )

            // 4. San Andreas Pause Menu & Double-Tap Cheat Codes Overlay (above mini-player)
            if (!showIntroUi && snapshot.isPaused) {
                PauseAndCheatsOverlay(
                    activeCheatCodes = snapshot.activeCheatCodes,
                    audioMuted = audioMutedUi,
                    playTrackInGame = playTrackInGameUi,
                    lastCheatMessage = lastCheatBannerUi,
                    onResume = { sim.isPaused = false },
                    onRespawn = { sim.respawnAtGroveCircle(chargeHospitalFee = false) },
                    onClearCheats = {
                        sim.clearAllCheats()
                        lastCheatBannerUi = "All toggle cheats cleared"
                    },
                    onToggleAudio = {
                        audioMutedUi = !audioMutedUi
                        soundManager.muted = audioMutedUi
                    },
                    onToggleTrackInGame = {
                        setPlayTrackInGame(!playTrackInGameUi)
                    },
                    onReplayIntro = { replayIntro() },
                    onCheatDoubleTapped = { code ->
                        val res = sim.triggerCheatCode(code)
                        if (res != null) {
                            lastCheatBannerUi = "${res.bannerMessage}: ${res.code}"
                            if (!soundManager.muted) {
                                soundManager.update(
                                    hudState.value.copy(
                                        isPaused = false,
                                        soundEvents = SoundEvents(cheatActivated = true),
                                    )
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        Choreographer.getInstance().postFrameCallback(this)

        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
        }
        lastFrameNanos = frameTimeNanos

        val (camDx, camDy) = touchBridge.consumeCameraDrag()
        val camController = filamentRenderer?.cameraController
        camController?.applyUserDrag(camDx, camDy)
        val camYaw = camController?.yawRad ?: 0f

        val input = buildMergedInput(camYaw)
        val snapshot = sim.step(dt, input, camYaw)
        hudState.value = snapshot
        soundManager.update(snapshot)
        filamentRenderer?.renderFrame(frameTimeNanos, dt, sim)
    }

    private fun buildMergedInput(camYaw: Float): ControlInput {
        val keyLeft = pressedKeys.contains(KeyEvent.KEYCODE_A) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)
        val keyRight = pressedKeys.contains(KeyEvent.KEYCODE_D) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)
        val keyFwd = pressedKeys.contains(KeyEvent.KEYCODE_W) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP)
        val keyBack = pressedKeys.contains(KeyEvent.KEYCODE_S) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)

        val steerFromButtons = (if (touchBridge.steerRight || keyRight) 1f else 0f) - (if (touchBridge.steerLeft || keyLeft) 1f else 0f)
        val moveX = if (steerFromButtons != 0f) steerFromButtons else touchBridge.stickX
        val moveY = if (keyFwd || keyBack) ((if (keyFwd) 1f else 0f) - (if (keyBack) 1f else 0f)) else touchBridge.stickY

        val jump = touchBridge.jumpQueued
        val enterExit = touchBridge.enterExitQueued
        val fireCannon = touchBridge.fireCannonQueued
        touchBridge.jumpQueued = false
        touchBridge.enterExitQueued = false
        touchBridge.fireCannonQueued = false

        return ControlInput(
            moveX = moveX,
            moveY = moveY,
            sprint = touchBridge.sprint || pressedKeys.contains(KeyEvent.KEYCODE_SHIFT_LEFT),
            jumpPressed = jump,
            gas = if (touchBridge.gas || keyFwd) 1f else 0f,
            brake = if (touchBridge.brake || keyBack) 1f else 0f,
            handbrake = touchBridge.handbrake || pressedKeys.contains(KeyEvent.KEYCODE_SPACE),
            nitro = touchBridge.nitro || pressedKeys.contains(KeyEvent.KEYCODE_N),
            jetpackAscend = touchBridge.jetpackAscend || pressedKeys.contains(KeyEvent.KEYCODE_SPACE),
            jetpackDescend = touchBridge.jetpackDescend || pressedKeys.contains(KeyEvent.KEYCODE_C),
            fireCannonPressed = fireCannon,
            enterExitPressed = enterExit,
            cameraAimYaw = camYaw,
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.add(keyCode)
        when (keyCode) {
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_F -> touchBridge.enterExitQueued = true
            KeyEvent.KEYCODE_SPACE -> touchBridge.jumpQueued = true
            KeyEvent.KEYCODE_ENTER -> {
                if (showIntroUi) enterGameFromIntro() else touchBridge.fireCannonQueued = true
            }
            KeyEvent.KEYCODE_M -> setPlayTrackInGame(!playTrackInGameUi)
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P -> {
                if (!showIntroUi) {
                    sim.isPaused = !sim.isPaused
                    if (sim.isPaused) soundManager.stopLoops()
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.remove(keyCode)
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
        youTubeController.onActivityResume(shouldBePlaying = showIntroUi || playTrackInGameUi)
    }

    override fun onPause() {
        super.onPause()
        Choreographer.getInstance().removeFrameCallback(this)
        soundManager.stopLoops()
        youTubeController.onActivityPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        Choreographer.getInstance().removeFrameCallback(this)
        soundManager.release()
        youTubeController.destroy()
        filamentRenderer?.destroy()
        filamentRenderer = null
    }
}
