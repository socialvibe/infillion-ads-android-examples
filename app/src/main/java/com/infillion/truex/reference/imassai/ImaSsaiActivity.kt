package com.infillion.truex.reference.imassai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.ads.interactivemedia.v3.api.Ad
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import com.google.ads.interactivemedia.v3.api.StreamManager
import com.infillion.truex.reference.databinding.ActivityImaSsaiBinding
import com.truex.adrenderer.TruexAdEvent

class ImaSsaiActivity : AppCompatActivity(),
    ImaSsaiVideoStreamPlayer.Listener,
    ImaSsaiTruexRenderer.Listener {
    private lateinit var binding: ActivityImaSsaiBinding
    private lateinit var videoStreamPlayer: ImaSsaiVideoStreamPlayer
    private lateinit var adsLoader: AdsLoader
    private var streamManager: StreamManager? = null
    private var renderer: ImaSsaiTruexRenderer? = null
    private var currentInteractiveType: ImaSsaiAdType? = null
    private var currentAdEndMs = 0L
    private var resumeAfterSnapbackMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImaSsaiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        videoStreamPlayer = ImaSsaiVideoStreamPlayer(binding.playerView, this)
        requestStream()
    }

    // [1] IMA DAI resolves a VOD asset into one stitched stream URL.
    private fun requestStream() {
        val factory = ImaSdkFactory.getInstance()
        val settings = factory.createImaSdkSettings().apply {
            language = "en"
            isDebugMode = true
        }
        val displayContainer =
            ImaSdkFactory.createStreamDisplayContainer(binding.adContainer, videoStreamPlayer)
        adsLoader = factory.createAdsLoader(this, settings, displayContainer).also { loader ->
            loader.addAdErrorListener { event ->
                showStatus("DAI request failed: ${event.error.message}")
            }
            loader.addAdsLoadedListener { event ->
                streamManager = event.streamManager.also(::configureStreamManager)
            }
        }
        showStatus("Requesting Google DAI VOD stream")
        adsLoader.requestStream(factory.createVodStreamRequest(CONTENT_SOURCE_ID, VIDEO_ID, null))
    }

    private fun configureStreamManager(manager: StreamManager) {
        manager.addAdErrorListener(
            AdErrorEvent.AdErrorListener { event ->
                showStatus("DAI playback error: ${event.error.message}")
            },
        )
        manager.addAdEventListener(AdEvent.AdEventListener(::onAdEvent))
        val settings: AdsRenderingSettings =
            ImaSdkFactory.getInstance().createAdsRenderingSettings().apply {
                focusSkipButtonWhenAvailable = true
            }
        manager.init(settings)
    }

    private fun onAdEvent(event: AdEvent) {
        when (event.type) {
            AdEvent.AdEventType.CUEPOINTS_CHANGED -> {
                streamManager?.cuePoints?.let(videoStreamPlayer::setAdMarkers)
            }
            AdEvent.AdEventType.STARTED -> onAdStarted(event.ad)
            else -> Unit
        }
    }

    // [2] Stitched ad timing is tracked in stream time before TAR takes the screen.
    private fun onAdStarted(ad: Ad?) {
        ad ?: return
        val pod = ad.adPodInfo ?: return
        if (currentAdEndMs == 0L) currentAdEndMs = (pod.timeOffset * 1_000.0).toLong()
        currentAdEndMs += (ad.duration * 1_000.0).toLong()

        val type = classifyImaSsaiAd(ad.adSystem)
        if (type == ImaSsaiAdType.LINEAR) {
            showStatus("DAI stitched linear ad")
            return
        }
        val payload = extractImaSsaiPayload(ad.traffickingParameters, ad.description)
        if (payload == null) {
            showStatus("Interactive payload is invalid • continuing stitched stream")
            videoStreamPlayer.seekTo((currentAdEndMs - 100L).coerceAtLeast(0L))
            videoStreamPlayer.resume()
            return
        }

        currentInteractiveType = type
        videoStreamPlayer.pause()
        videoStreamPlayer.hide()
        showStatus("$type interactive ad")
        val newRenderer = ImaSsaiTruexRenderer(this, this)
        renderer = newRenderer
        runCatching { newRenderer.start(binding.adContainer, payload, type) }
            .onFailure { error ->
                showStatus("Renderer setup failed: ${error.message} • continuing stream")
                finishInteractive(receivedCredit = false)
            }
    }

    // [3] TrueX credit seeks beyond the stitched break; IDVx and fallback resume its pod.
    override fun onTerminal(receivedCredit: Boolean, event: TruexAdEvent) {
        showStatus("Renderer finished: $event")
        finishInteractive(receivedCredit)
    }

    private fun finishInteractive(receivedCredit: Boolean) {
        val type = currentInteractiveType
        disposeRenderer()
        currentInteractiveType = null
        videoStreamPlayer.show()

        if (type == ImaSsaiAdType.TRUEX && receivedCredit) {
            val currentAd = streamManager?.currentAd
            val pod = currentAd?.adPodInfo
            val progress = streamManager?.adProgressInfo
            if (pod != null && progress != null) {
                val target = calculateSsaiSkipTargetMs(pod.timeOffset, progress.adBreakDuration)
                videoStreamPlayer.seekTo(target)
                videoStreamPlayer.setControlsEnabled(true)
                showStatus("TrueX credit earned • stitched break skipped")
            } else {
                showStatus("Could not resolve the stitched break • resuming at placeholder end")
                videoStreamPlayer.seekTo((currentAdEndMs - 100L).coerceAtLeast(0L))
            }
        } else {
            videoStreamPlayer.seekTo((currentAdEndMs - 100L).coerceAtLeast(0L))
            showStatus("$type complete • continuing stitched pod")
        }
        videoStreamPlayer.resume()
    }

    // [4] Seeking over an unplayed stitched break snaps back before returning to the target.
    override fun onUserSeek(positionMs: Long) {
        val cuePoint = streamManager?.getPreviousCuePointForStreamTimeMs(positionMs)
        if (cuePoint != null && !cuePoint.isPlayed) {
            resumeAfterSnapbackMs = positionMs
            videoStreamPlayer.seekTo(cuePoint.startTimeMs)
            videoStreamPlayer.setControlsEnabled(false)
            showStatus("Ad snapback • break at ${cuePoint.startTimeMs / 1_000}s")
        }
    }

    override fun onStreamLoaded() {
        showStatus("DAI stitched content")
    }

    override fun onAdBreakStarted() {
        currentAdEndMs = 0L
        showStatus("DAI ad break")
    }

    override fun onAdBreakEnded() {
        currentAdEndMs = 0L
        if (resumeAfterSnapbackMs > 0L) {
            videoStreamPlayer.seekTo(resumeAfterSnapbackMs)
            resumeAfterSnapbackMs = 0L
        }
        streamManager?.cuePoints?.let(videoStreamPlayer::setAdMarkers)
        showStatus("DAI content resumed")
    }

    override fun onPlaybackError(error: androidx.media3.common.PlaybackException) {
        showStatus("Stream playback error: ${error.errorCodeName}")
    }

    override fun onPopup(uri: Uri) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { showStatus("No browser can open ${uri.host}") }
    }

    override fun onCancelStream() {
        showStatus("Viewer cancelled the stream")
        finish()
    }

    override fun onEvent(event: TruexAdEvent) {
        if (event == TruexAdEvent.AD_STARTED || event == TruexAdEvent.OPT_IN || event == TruexAdEvent.OPT_OUT) {
            showStatus("Interactive ad • $event")
        }
    }

    private fun showStatus(message: String) {
        binding.statusText.text = message
    }

    private fun disposeRenderer() {
        renderer?.destroy()
        renderer = null
    }

    override fun onResume() {
        super.onResume()
        if (renderer != null) renderer?.resume() else videoStreamPlayer.resume()
    }

    override fun onPause() {
        if (renderer != null) renderer?.pause() else videoStreamPlayer.pause()
        super.onPause()
    }

    override fun onDestroy() {
        disposeRenderer()
        streamManager?.destroy()
        streamManager = null
        adsLoader.release()
        videoStreamPlayer.releasePlayer()
        super.onDestroy()
    }

    private companion object {
        const val CONTENT_SOURCE_ID = "2496857"
        const val VIDEO_ID = "truex-content22-4k"
    }
}
