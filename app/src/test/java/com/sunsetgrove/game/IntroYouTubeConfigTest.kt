package com.sunsetgrove.game

import com.sunsetgrove.game.ui.IntroYouTubeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroYouTubeConfigTest {

    @Test
    fun embedHtmlTargetsRequestedYouTubeVideoWithAutoplayAndLoop() {
        assertEquals("IeIUm-aqaCg", IntroYouTubeConfig.VIDEO_ID)
        val html = IntroYouTubeConfig.buildEmbedHtml()
        assertTrue(html.contains("videoId: 'IeIUm-aqaCg'"))
        assertTrue(html.contains("playlist: 'IeIUm-aqaCg'"))
        assertTrue(html.contains("autoplay: 1"))
        assertTrue(html.contains("playsinline: 1"))
        assertTrue(html.contains("loop: 1"))
        assertTrue(html.contains("sgPlay"))
        assertTrue(html.contains("sgPause"))
        assertTrue(html.contains("sgRestart"))
    }
}
