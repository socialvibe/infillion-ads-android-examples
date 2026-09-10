package com.infillion.truex.reference.imassai

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.ads.interactivemedia.v3.api.Ad
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import com.google.ads.interactivemedia.v3.api.StreamManager
import com.infillion.truex.reference.BuildConfig
import com.infillion.truex.reference.databinding.ActivityImaSsaiBinding
import com.truex.adrenderer.IEventEmitter
import com.truex.adrenderer.TruexAdEvent
import com.truex.adrenderer.TruexAdOptions
import com.truex.adrenderer.TruexAdRenderer

class ImaSsaiActivity : AppCompatActivity(), ImaSsaiVideoStreamPlayer.Listener {
    private lateinit var binding: ActivityImaSsaiBinding
    private lateinit var videoStreamPlayer: ImaSsaiVideoStreamPlayer
    private lateinit var adsLoader: AdsLoader
    private var streamManager: StreamManager? = null
    private var truexAdRenderer: TruexAdRenderer? = null
    private var truexAdCreditReceived = false
    private var truexAdTerminalEvent = false
    private var currentInteractiveType: ImaSsaiAdType? = null
    private var currentAdEndMs = 0L
    private var resumeAfterSnapbackMs = 0L

    // Handles lifecycle and user-interaction events emitted by TruexAdRenderer.
    private val truexAdEventHandler = IEventEmitter.IEventHandler { event, data ->
        Log.i(TAG, "TruexAdEvent $event data=$data")
        when (event) {
            // Main flow events
            TruexAdEvent.AD_FETCH_COMPLETED -> {
                Log.i(TAG, "TrueX renderer finished fetching ad payload")
            }
            TruexAdEvent.AD_STARTED -> {
                // Interactive unit has started displaying to the viewer
                showStatus("Interactive ad • $event")
            }
            TruexAdEvent.AD_DISPLAYED -> {
                Log.i(TAG, "TrueX interactive assets loaded and visible")
            }
            TruexAdEvent.AD_FREE_POD -> {
                // The viewer completed the requirements to earn the ad-free pod reward.
                // Do not skip immediately; wait for a terminal event (AD_COMPLETED) before seeking past the break.
                Log.i(TAG, "TrueX credit earned (AD_FREE_POD); waiting for terminal event to skip break")
                truexAdCreditReceived = true
            }
            TruexAdEvent.USER_CANCEL_STREAM -> {
                // The viewer pressed Back on the choice card or exit prompt to leave playback entirely.
                Log.i(TAG, "Viewer cancelled stream via USER_CANCEL_STREAM")
                showStatus("Viewer cancelled the stream")
                finish()
            }
            // Terminal events
            TruexAdEvent.AD_COMPLETED,
            TruexAdEvent.AD_ERROR,
            TruexAdEvent.NO_ADS_AVAILABLE -> {
                Log.i(TAG, "Terminal TrueX event received: $event")
                finishTruexAd(event)
            }
            // Informative events
            TruexAdEvent.OPT_IN -> {
                // Viewer selected the interactive engagement over regular linear ads
                Log.i(TAG, "Viewer opted in to interactive experience")
                showStatus("Interactive ad • $event")
            }
            TruexAdEvent.OPT_OUT -> {
                // Viewer chose linear fallback or the choice-card timer expired
                Log.i(TAG, "Viewer opted out of interactive experience")
                showStatus("Interactive ad • $event")
            }
            TruexAdEvent.USER_CANCEL -> {
                // Viewer backed out of the interactive engagement after opting in
                Log.i(TAG, "Viewer backed out of engagement (USER_CANCEL)")
            }
            else -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: initializing ImaSsaiActivity")
        binding = ActivityImaSsaiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        videoStreamPlayer = ImaSsaiVideoStreamPlayer(binding.playerView, this)
        requestStream()
    }

    // [1] IMA DAI resolves a VOD asset into one stitched stream URL.
    private fun requestStream() {
        Log.i(TAG, "requestStream: requesting Google DAI stream (contentSourceId=$CONTENT_SOURCE_ID, videoId=$VIDEO_ID)")
        val factory = ImaSdkFactory.getInstance()
        val settings = factory.createImaSdkSettings().apply {
            language = "en"
            isDebugMode = true
        }
        val displayContainer =
            ImaSdkFactory.createStreamDisplayContainer(binding.adContainer, videoStreamPlayer)
        adsLoader = factory.createAdsLoader(this, settings, displayContainer).also { loader ->
            loader.addAdErrorListener { event ->
                Log.e(TAG, "DAI request error: ${event.error.message}")
                showStatus("DAI request failed: ${event.error.message}")
            }
            loader.addAdsLoadedListener { event ->
                Log.i(TAG, "DAI stream loaded; configuring StreamManager")
                streamManager = event.streamManager.also(::configureStreamManager)
            }
        }
        showStatus("Requesting Google DAI VOD stream")
        adsLoader.requestStream(factory.createVodStreamRequest(CONTENT_SOURCE_ID, VIDEO_ID, null))
    }

    private fun configureStreamManager(manager: StreamManager) {
        Log.i(TAG, "configureStreamManager: attaching listeners and initializing StreamManager")
        manager.addAdErrorListener(
            AdErrorEvent.AdErrorListener { event ->
                Log.e(TAG, "DAI playback error: ${event.error.message}")
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
        Log.i(TAG, "onAdEvent: ${event.type}")
        when (event.type) {
            AdEvent.AdEventType.CUEPOINTS_CHANGED -> {
                val cuePoints = streamManager?.cuePoints
                Log.i(TAG, "onAdEvent: CUEPOINTS_CHANGED (${cuePoints?.size ?: 0} cue points)")
                cuePoints?.let(videoStreamPlayer::setAdMarkers)
            }
            AdEvent.AdEventType.STARTED -> {
                onAdStarted(event.ad)
            }
            else -> Unit
        }
    }

    // [2] Stitched ad timing is tracked in stream time before TAR takes the screen.
    private fun onAdStarted(ad: Ad?) {
        Log.i(TAG, "onAdStarted: adSystem=${ad?.adSystem}, position=${ad?.adPodInfo?.adPosition}, trafficking=${ad?.traffickingParameters}, desc=${ad?.description}")
        if (ad == null) {
            return
        }
        val pod = ad.adPodInfo
        if (currentAdEndMs == 0L) {
            currentAdEndMs = (pod.timeOffset * 1_000.0).toLong()
        }
        currentAdEndMs += (ad.duration * 1_000.0).toLong()

        val type = classifyImaSsaiAd(ad.adSystem)
        if (type == ImaSsaiAdType.LINEAR) {
            Log.i(TAG, "onAdStarted: stitched linear ad; continuing stream playback")
            showStatus("DAI stitched linear ad")
            return
        }

        // TrueX interactive engagement must run as the first ad in the pod.
        // If received at a later position, it continues as normal stitched linear playback.
        if (type == ImaSsaiAdType.TRUEX && !canPlayTruex(pod.adPosition)) {
            Log.w(TAG, "onAdStarted: TrueX ad at position ${pod.adPosition} != 1; playing as stitched linear")
            showStatus("TrueX ad must be first in pod (position: ${pod.adPosition}) • playing as linear")
            return
        }

        val payload = extractImaSsaiPayload(ad.traffickingParameters, ad.description)
        if (payload == null) {
            Log.e(TAG, "onAdStarted: interactive payload is invalid for $type ad; continuing stream")
            showStatus("Interactive payload is invalid • continuing stitched stream")
            videoStreamPlayer.seekTo((currentAdEndMs - 100L).coerceAtLeast(0L))
            videoStreamPlayer.resume()
            return
        }

        // Pause and hide the video player so TrueX overlay has exclusive focus.
        currentInteractiveType = type
        truexAdCreditReceived = false
        truexAdTerminalEvent = false
        Log.i(TAG, "onAdStarted: pausing and hiding stitched video stream for $type interactive ad")
        videoStreamPlayer.pause()
        videoStreamPlayer.hide()
        showStatus("$type interactive ad")

        // Configure TrueX ad options:
        // - supportsUserCancelStream: enables Back button to trigger USER_CANCEL_STREAM
        // - appId: package name for telemetry attribution
        // - enableWebViewDebugging: allows inspect via chrome://inspect in debug builds
        val options = TruexAdOptions().apply {
            supportsUserCancelStream = true
            appId = packageName
            enableWebViewDebugging = BuildConfig.DEBUG
        }
        val newRenderer = TruexAdRenderer(this).also { tar ->
            tar.addEventListener(null, truexAdEventHandler)
            when (payload) {
                is ImaSsaiAdPayload.Url -> {
                    Log.i(TAG, "Initializing TruexAdRenderer with URL: ${payload.value}")
                    tar.init(payload.value, options)
                }
                is ImaSsaiAdPayload.Parameters -> {
                    Log.i(TAG, "Initializing TruexAdRenderer with JSON parameters")
                    tar.init(payload.value, options)
                }
            }
        }
        truexAdRenderer = newRenderer
        Log.i(TAG, "Starting TruexAdRenderer inside adContainer")
        runCatching { newRenderer.start(binding.adContainer) }
            .onFailure { error ->
                Log.e(TAG, "Failed to start TruexAdRenderer: ${error.message}")
                showStatus("Renderer setup failed: ${error.message} • continuing stream")
                finishInteractive(shouldSkipPod = false)
            }
    }

    // [3] Terminal event processing: AD_FREE_POD credit seeks past break only on AD_COMPLETED.
    private fun finishTruexAd(event: TruexAdEvent) {
        if (truexAdTerminalEvent) {
            return
        }
        truexAdTerminalEvent = true
        Log.i(TAG, "finishTruexAd: event=$event, creditReceived=$truexAdCreditReceived")
        showStatus("Renderer finished: $event")
        finishInteractive(shouldSkipPod = truexAdCreditReceived && event == TruexAdEvent.AD_COMPLETED)
    }

    private fun finishInteractive(shouldSkipPod: Boolean) {
        val type = currentInteractiveType
        Log.i(TAG, "finishInteractive: type=$type, shouldSkipPod=$shouldSkipPod")
        disposeRenderer()
        currentInteractiveType = null
        videoStreamPlayer.show()

        if (type == ImaSsaiAdType.TRUEX && shouldSkipPod) {
            // TrueX credit earned: Google DAI has no discardAdBreak(), so we seek the stream past the break duration.
            val currentAd = streamManager?.currentAd
            val pod = currentAd?.adPodInfo
            val progress = streamManager?.adProgressInfo
            if (pod != null && progress != null) {
                val target = calculateSsaiSkipTargetMs(pod.timeOffset, progress.adBreakDuration)
                Log.i(TAG, "finishInteractive: TrueX credit earned; seeking past stitched break to ${target}ms")
                videoStreamPlayer.seekTo(target)
                videoStreamPlayer.setControlsEnabled(true)
                showStatus("TrueX credit earned • stitched break skipped")
            } else {
                Log.w(TAG, "finishInteractive: cannot resolve ad break timing; resuming at placeholder end")
                showStatus("Could not resolve the stitched break • resuming at placeholder end")
                videoStreamPlayer.seekTo((currentAdEndMs - 100L).coerceAtLeast(0L))
            }
        } else {
            // Opt-out, error, cancel, or IDVx: seek to near end of current placeholder so stitched fallback ads continue.
            Log.i(TAG, "finishInteractive: $type did not skip break; seeking to placeholder end=${(currentAdEndMs - 100L).coerceAtLeast(0L)}ms")
            videoStreamPlayer.seekTo((currentAdEndMs - 100L).coerceAtLeast(0L))
            showStatus("$type complete • continuing stitched pod")
        }
        videoStreamPlayer.resume()
    }

    // [4] Seeking over an unplayed stitched break snaps back before returning to the target.
    override fun onUserSeek(positionMs: Long) {
        Log.i(TAG, "onUserSeek: requested seek to ${positionMs}ms")
        val cuePoint = streamManager?.getPreviousCuePointForStreamTimeMs(positionMs)
        if (cuePoint != null && !cuePoint.isPlayed) {
            Log.i(TAG, "onUserSeek: unplayed cuePoint at ${cuePoint.startTimeMs}ms; snapping back and saving destination ${positionMs}ms")
            resumeAfterSnapbackMs = positionMs
            videoStreamPlayer.seekTo(cuePoint.startTimeMs)
            videoStreamPlayer.setControlsEnabled(false)
            showStatus("Ad snapback • break at ${cuePoint.startTimeMs / 1_000}s")
        }
    }

    override fun onStreamLoaded() {
        Log.i(TAG, "onStreamLoaded: DAI stitched content ready")
        showStatus("DAI stitched content")
    }

    override fun onAdBreakStarted() {
        Log.i(TAG, "onAdBreakStarted: DAI ad break started")
        currentAdEndMs = 0L
        showStatus("DAI ad break")
    }

    override fun onAdBreakEnded() {
        Log.i(TAG, "onAdBreakEnded: DAI ad break ended; resumeAfterSnapbackMs=$resumeAfterSnapbackMs")
        currentAdEndMs = 0L
        if (resumeAfterSnapbackMs > 0L) {
            Log.i(TAG, "onAdBreakEnded: resuming to original seek destination ${resumeAfterSnapbackMs}ms")
            videoStreamPlayer.seekTo(resumeAfterSnapbackMs)
            resumeAfterSnapbackMs = 0L
        }
        streamManager?.cuePoints?.let(videoStreamPlayer::setAdMarkers)
        showStatus("DAI content resumed")
    }

    override fun onPlaybackError(error: androidx.media3.common.PlaybackException) {
        Log.e(TAG, "onPlaybackError: ${error.errorCodeName}")
        showStatus("Stream playback error: ${error.errorCodeName}")
    }

    private fun showStatus(message: String) {
        Log.i(TAG, "Status: $message")
        binding.statusText.text = message
    }

    private fun disposeRenderer() {
        Log.i(TAG, "disposeRenderer: releasing TruexAdRenderer instance")
        truexAdRenderer?.removeEventListener(null, truexAdEventHandler)
        truexAdRenderer?.stop()
        truexAdRenderer = null
        truexAdCreditReceived = false
        truexAdTerminalEvent = false
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume: truexAdRendererActive=${truexAdRenderer != null}")
        if (truexAdRenderer != null) {
            truexAdRenderer?.resume()
        } else {
            videoStreamPlayer.resume()
        }
    }

    override fun onPause() {
        Log.i(TAG, "onPause: truexAdRendererActive=${truexAdRenderer != null}")
        if (truexAdRenderer != null) {
            truexAdRenderer?.pause()
        } else {
            videoStreamPlayer.pause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: cleaning up renderer, StreamManager, loader, and player")
        disposeRenderer()
        streamManager?.destroy()
        streamManager = null
        adsLoader.release()
        videoStreamPlayer.releasePlayer()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ImaSsai"
        const val CONTENT_SOURCE_ID = "2496857"
        const val VIDEO_ID = "truex-content22-4k"
    }
}
