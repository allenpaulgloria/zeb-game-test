package com.sunsetgrove.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

object IntroYouTubeConfig {
    const val SOURCE_URL = "https://youtu.be/IeIUm-aqaCg?si=vXmCyZb0KGrciZXf"
    const val VIDEO_ID = "IeIUm-aqaCg"

    fun buildEmbedHtml(videoId: String = VIDEO_ID): String = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            html, body {
              margin: 0;
              padding: 0;
              width: 100%;
              height: 100%;
              background: #000;
              overflow: hidden;
            }
            #player {
              position: fixed;
              top: 0;
              left: 0;
              width: 100%;
              height: 100%;
            }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script>
            var player = null;
            var pendingPlay = true;
            var tag = document.createElement('script');
            tag.src = "https://www.youtube.com/iframe_api";
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                videoId: '$videoId',
                playerVars: {
                  autoplay: 1,
                  playsinline: 1,
                  loop: 1,
                  playlist: '$videoId',
                  controls: 1,
                  rel: 0,
                  modestbranding: 1,
                  enablejsapi: 1
                },
                events: {
                  'onReady': function(event) {
                    if (window.AndroidYouTubeBridge) {
                      window.AndroidYouTubeBridge.onPlayerReady();
                    }
                    if (pendingPlay) {
                      event.target.playVideo();
                    }
                  },
                  'onError': function(event) {
                    if (window.AndroidYouTubeBridge) {
                      window.AndroidYouTubeBridge.onPlayerError(event.data);
                    }
                  }
                }
              });
            }

            function sgPlay() {
              pendingPlay = true;
              if (player && player.playVideo) { player.playVideo(); }
            }
            function sgPause() {
              pendingPlay = false;
              if (player && player.pauseVideo) { player.pauseVideo(); }
            }
            function sgRestart() {
              pendingPlay = true;
              if (player && player.seekTo) {
                player.seekTo(0, true);
                player.playVideo();
              }
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Owns a single persistent Android WebView running the YouTube IFrame API for the
 * intro video (IeIUm-aqaCg) so playback continues smoothly with zero reload/stutter
 * when transitioning from the Intro screen into live 3D gameplay.
 */
class YouTubeTrackController(context: Context) {
    val webView: WebView

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var lastErrorCode: Int? = null
        private set

    private var desiredPlaying: Boolean = true

    init {
        webView = createConfiguredWebView(context)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(context: Context): WebView {
        return WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onPlayerReady() {
                        isReady = true
                        post {
                            if (desiredPlaying) play() else pause()
                        }
                    }

                    @JavascriptInterface
                    fun onPlayerError(code: Int) {
                        lastErrorCode = code
                    }
                },
                "AndroidYouTubeBridge",
            )
            loadDataWithBaseURL(
                "https://www.youtube.com",
                IntroYouTubeConfig.buildEmbedHtml(IntroYouTubeConfig.VIDEO_ID),
                "text/html",
                "utf-8",
                null,
            )
        }
    }

    fun play() {
        desiredPlaying = true
        webView.post {
            webView.onResume()
            webView.evaluateJavascript("if (typeof sgPlay === 'function') sgPlay();", null)
        }
    }

    fun pause() {
        desiredPlaying = false
        webView.post {
            webView.evaluateJavascript("if (typeof sgPause === 'function') sgPause();", null)
        }
    }

    fun restartAndPlay() {
        desiredPlaying = true
        webView.post {
            webView.onResume()
            webView.evaluateJavascript("if (typeof sgRestart === 'function') sgRestart();", null)
        }
    }

    fun onActivityPause() {
        webView.post {
            webView.evaluateJavascript("if (typeof sgPause === 'function') sgPause();", null)
            webView.onPause()
        }
    }

    fun onActivityResume(shouldBePlaying: Boolean) {
        desiredPlaying = shouldBePlaying
        webView.post {
            webView.onResume()
            if (shouldBePlaying) {
                webView.evaluateJavascript("if (typeof sgPlay === 'function') sgPlay();", null)
            } else {
                webView.evaluateJavascript("if (typeof sgPause === 'function') sgPause();", null)
            }
        }
    }

    fun destroy() {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
    }
}

/**
 * Renders the single persistent YouTube WebView in either:
 * 1. Full-screen GTA: San Andreas Intro Theater mode (with KEEP PLAYING IN-GAME toggle + PLAY button)
 * 2. Compact sleek Radio Mini-Player in the top-center of gameplay when toggled ON (so YouTube audio
 *    keeps playing uninterrupted while driving, flying the Jetpack, or firing the Rhino Tank!)
 * 3. Hidden & paused when toggled OFF during gameplay.
 */
@Composable
fun PersistentIntroAndTrackLayer(
    controller: YouTubeTrackController,
    showIntro: Boolean,
    playTrackInGame: Boolean,
    miniPlayerExpanded: Boolean,
    onTogglePlayInGame: (Boolean) -> Unit,
    onToggleMiniExpanded: () -> Unit,
    onEnterGame: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Intro chrome background + header + PLAY button (only visible during Intro)
        if (showIntro) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A0806), Color(0xFF1D1007), Color(0xFF0A0806))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "SUNSET GROVE",
                            color = Color(0xFFFFB74D),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = "INTRO  //  SAN ANDREAS VIBES",
                            color = Color(0xFFBDBDBD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .background(Color(0xCC16181E), RoundedCornerShape(8.dp))
                            .border(1.5.dp, if (playTrackInGame) Color(0xFF4CAF50) else Color(0xFF555B66), RoundedCornerShape(8.dp))
                            .clickable { onTogglePlayInGame(!playTrackInGame) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "KEEP PLAYING IN-GAME",
                            color = if (playTrackInGame) Color(0xFF81C784) else Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Switch(
                            checked = playTrackInGame,
                            onCheckedChange = onTogglePlayInGame,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4CAF50),
                            ),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color(0xFF2E7D32), RoundedCornerShape(8.dp))
                        .border(2.dp, Color(0xFFA5D6A7), RoundedCornerShape(8.dp))
                        .clickable(onClick = onEnterGame)
                        .padding(horizontal = 34.dp, vertical = 11.dp),
                ) {
                    Text(
                        text = if (playTrackInGame) "PLAY GAME  (KEEP MUSIC ON)  ▶" else "SKIP INTRO & PLAY  ▶",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                }
            }
        } else if (playTrackInGame) {
            // In-game sleek San Andreas Radio header bar (tapping expands/collapses video preview)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .background(Color(0xCC0D0E12), RoundedCornerShape(7.dp))
                    .border(1.5.dp, Color(0xFFFFB300), RoundedCornerShape(7.dp))
                    .clickable(onClick = onToggleMiniExpanded)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "♫ INTRO RADIO: PLAYING",
                    color = Color(0xFFFFCA28),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = if (miniPlayerExpanded) "HIDE VIDEO" else "SHOW VIDEO",
                    color = Color(0xFF90CAF9),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 2. SINGLE persistent WebView container — never detaches or reloads across Intro -> Gameplay!
        val playerContainerModifier = when {
            showIntro -> Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.74f)
                .fillMaxHeight(0.62f)
                .border(2.dp, Color(0xFFFF9800), RoundedCornerShape(8.dp))
                .background(Color.Black, RoundedCornerShape(8.dp))
            playTrackInGame && miniPlayerExpanded -> Modifier
                .align(Alignment.TopCenter)
                .padding(top = 38.dp)
                .width(192.dp)
                .height(108.dp)
                .border(1.5.dp, Color(0xFFFFB300), RoundedCornerShape(6.dp))
                .background(Color.Black, RoundedCornerShape(6.dp))
            playTrackInGame -> Modifier
                .align(Alignment.TopCenter)
                .padding(top = 38.dp)
                .size(2.dp)
                .alpha(0.01f)
            else -> Modifier
                .align(Alignment.TopCenter)
                .size(1.dp)
                .alpha(0f)
        }

        Box(modifier = playerContainerModifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    (controller.webView.parent as? ViewGroup)?.removeView(controller.webView)
                    controller.webView
                },
            )
        }
    }
}
