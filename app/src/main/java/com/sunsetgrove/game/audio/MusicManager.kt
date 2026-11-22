package com.sunsetgrove.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Pure Audio-Only Music Player (using native Android MediaPlayer, NO YouTube video box!).
 *
 * - Plays immediately on the Intro screen and toggles seamlessly ON/OFF while playing!
 * - Checks assets/audio/custom_intro_music.mp3 (or .ogg) first so you can drop in your own
 *   exported audio file for https://youtu.be/IeIUm-aqaCg, falling back to the bundled
 *   90s West Coast G-Funk San Andreas-style instrumental track (audio/music_intro_theme.wav).
 */
class MusicManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var prepared: Boolean = false
    private var desiredPlaying: Boolean = true

    val activeTrackLabel: String

    init {
        val (assetPath, label) = resolveTrackAsset(context)
        activeTrackLabel = label
        initPlayer(assetPath)
    }

    private fun resolveTrackAsset(context: Context): Pair<String, String> {
        val candidates = listOf(
            "audio/custom_intro_music.mp3" to "Custom Intro Theme",
            "audio/custom_intro_music.ogg" to "Custom Intro Theme",
            "audio/music_intro_theme.wav" to "Sunset Grove Radio (92 BPM West Coast Theme)",
        )
        for ((path, label) in candidates) {
            try {
                context.assets.openFd(path).close()
                return path to label
            } catch (_: Exception) {
                // try next
            }
        }
        return "audio/music_intro_theme.wav" to "Sunset Grove Radio"
    }

    private fun initPlayer(assetPath: String) {
        try {
            val afd = context.assets.openFd(assetPath)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = true
                setVolume(0.55f, 0.55f)
                prepare()
            }
            afd.close()
            mediaPlayer = mp
            prepared = true
            if (desiredPlaying) {
                mp.start()
            }
        } catch (_: Exception) {
            prepared = false
        }
    }

    fun play() {
        desiredPlaying = true
        val mp = mediaPlayer ?: return
        if (prepared && !mp.isPlaying) {
            mp.start()
        }
    }

    fun pause() {
        desiredPlaying = false
        val mp = mediaPlayer ?: return
        if (prepared && mp.isPlaying) {
            mp.pause()
        }
    }

    fun restartAndPlay() {
        desiredPlaying = true
        val mp = mediaPlayer ?: return
        if (prepared) {
            mp.seekTo(0)
            if (!mp.isPlaying) mp.start()
        }
    }

    fun onActivityPause() {
        val mp = mediaPlayer ?: return
        if (prepared && mp.isPlaying) {
            mp.pause()
        }
    }

    fun onActivityResume(shouldBePlaying: Boolean) {
        desiredPlaying = shouldBePlaying
        val mp = mediaPlayer ?: return
        if (prepared) {
            if (shouldBePlaying && !mp.isPlaying) mp.start()
            else if (!shouldBePlaying && mp.isPlaying) mp.pause()
        }
    }

    fun release() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        prepared = false
    }
}
