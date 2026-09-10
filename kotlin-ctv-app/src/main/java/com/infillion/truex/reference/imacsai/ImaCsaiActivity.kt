package com.infillion.truex.reference.imacsai

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.ads.interactivemedia.v3.api.Ad
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.ads.interactivemedia.v3.api.AdsManager
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import com.infillion.truex.reference.BuildConfig
import com.infillion.truex.reference.databinding.ActivityImaCsaiBinding
import com.truex.adrenderer.IEventEmitter
import com.truex.adrenderer.TruexAdEvent
import com.truex.adrenderer.TruexAdOptions
import com.truex.adrenderer.TruexAdRenderer

class ImaCsaiActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImaCsaiBinding
    private lateinit var videoPlayer: ImaCsaiVideoPlayer
    private lateinit var adsLoader: AdsLoader
    private var adsManager: AdsManager? = null
    private var truexAdRenderer: TruexAdRenderer? = null
    private var truexAdCreditReceived = false
    private var truexAdTerminalEvent = false
    private var currentInteractiveType: ImaCsaiAdType? = null
    private var adsRequested = false

    private val midrollCheck = object : Runnable {
        override fun run() {
            if (!adsRequested && videoPlayer.contentPositionMs >= MIDROLL_MS) {
                Log.i(TAG, "Midroll trigger reached at ${videoPlayer.contentPositionMs}ms")
                requestAds()
            }
            if (!isFinishing) {
                binding.root.postDelayed(this, 250L)
            }
        }
    }

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
                // Do not skip immediately; wait for a terminal event (AD_COMPLETED) before discarding the pod.
                Log.i(TAG, "TrueX credit earned (AD_FREE_POD); waiting for terminal event to skip pod")
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
        Log.i(TAG, "onCreate: initializing ImaCsaiActivity")
        binding = ActivityImaCsaiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoPlayer = ImaCsaiVideoPlayer(
            playerView = binding.playerView,
            onContentEnded = {
                Log.i(TAG, "Content ended; notifying AdsLoader")
                adsLoader.contentComplete()
            },
            onPlaybackError = { error ->
                Log.e(TAG, "Content playback error: ${error.errorCodeName}")
                showStatus("Playback error: ${error.errorCodeName}")
                recoverContent()
            },
        )
        configureIma()
        Log.i(TAG, "Starting content playback from $CONTENT_URL")
        videoPlayer.playContent(CONTENT_URL)
        showStatus("Content • IMA request at ${MIDROLL_MS / 1_000}s")
        binding.root.post(midrollCheck)
    }

    private fun configureIma() {
        Log.i(TAG, "configureIma: setting up ImaSdkFactory and AdsLoader")
        val factory = ImaSdkFactory.getInstance()
        val settings = factory.createImaSdkSettings().apply {
            language = "en"
            isDebugMode = true
        }
        val displayContainer = ImaSdkFactory.createAdDisplayContainer(binding.adContainer, videoPlayer)
        adsLoader = factory.createAdsLoader(this, settings, displayContainer).also { loader ->
            loader.addAdErrorListener { event ->
                Log.e(TAG, "IMA AdsLoader error: ${event.error.message}")
                showStatus("IMA error: ${event.error.message}. Resuming content.")
                destroyAdsManager()
                recoverContent()
            }
            loader.addAdsLoadedListener { event ->
                Log.i(TAG, "IMA AdsLoader loaded AdsManager successfully")
                adsManager = event.adsManager.also(::configureAdsManager)
            }
        }
    }

    // [1] The host requests a real client-side pod at its content midroll.
    private fun requestAds() {
        adsRequested = true
        Log.i(TAG, "requestAds: requesting Google IMA client-side ads from $AD_TAG_URL")
        showStatus("Requesting Google IMA client-side ads")
        val request = ImaSdkFactory.getInstance().createAdsRequest().apply {
            adTagUrl = AD_TAG_URL
            contentProgressProvider = videoPlayer.contentProgressProvider
            setAdWillAutoPlay(true)
            setAdWillPlayMuted(false)
        }
        adsLoader.requestAds(request)
    }

    private fun configureAdsManager(manager: AdsManager) {
        Log.i(TAG, "configureAdsManager: attaching listeners and initializing AdsManager")
        manager.addAdErrorListener(
            AdErrorEvent.AdErrorListener { event ->
                Log.e(TAG, "IMA AdsManager playback error: ${event.error.message}")
                showStatus("IMA playback error: ${event.error.message}. Resuming content.")
                destroyAdsManager()
                recoverContent()
            },
        )
        manager.addAdEventListener(AdEvent.AdEventListener(::onAdEvent))
        val renderingSettings: AdsRenderingSettings =
            ImaSdkFactory.getInstance().createAdsRenderingSettings().apply {
                focusSkipButtonWhenAvailable = true
            }
        manager.init(renderingSettings)
    }

    private fun onAdEvent(event: AdEvent) {
        Log.i(TAG, "onAdEvent: ${event.type}")
        when (event.type) {
            AdEvent.AdEventType.LOADED -> {
                Log.i(TAG, "onAdEvent: LOADED - starting AdsManager")
                adsManager?.start()
            }
            AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED -> {
                Log.i(TAG, "onAdEvent: CONTENT_PAUSE_REQUESTED - pausing content for ad break")
                videoPlayer.pauseContentForAds()
                showStatus("IMA ad break")
            }
            AdEvent.AdEventType.STARTED -> {
                onAdStarted(event.ad)
            }
            AdEvent.AdEventType.PAUSED -> {
                Log.i(TAG, "onAdEvent: PAUSED")
                showStatus("IMA ad paused")
            }
            AdEvent.AdEventType.RESUMED -> {
                Log.i(TAG, "onAdEvent: RESUMED")
                showStatus("IMA ad resumed")
            }
            AdEvent.AdEventType.CONTENT_RESUME_REQUESTED -> {
                Log.i(TAG, "onAdEvent: CONTENT_RESUME_REQUESTED - recovering content")
                recoverContent()
            }
            AdEvent.AdEventType.ALL_ADS_COMPLETED -> {
                Log.i(TAG, "onAdEvent: ALL_ADS_COMPLETED - destroying AdsManager")
                destroyAdsManager()
            }
            else -> Unit
        }
    }

    // [2] AdSystem and trafficking parameters identify the interactive placeholder.
    private fun onAdStarted(ad: Ad?) {
        Log.i(TAG, "onAdStarted: adSystem=${ad?.adSystem}, position=${ad?.adPodInfo?.adPosition}, trafficking=${ad?.traffickingParameters}, desc=${ad?.description}")
        val type = classifyImaCsaiAd(ad?.adSystem)
        if (type == ImaCsaiAdType.LINEAR) {
            Log.i(TAG, "onAdStarted: linear ad detected; keeping player visible")
            binding.playerView.visibility = View.VISIBLE
            showStatus("IMA linear ad")
            return
        }

        // TrueX interactive engagement must run as the first ad in the pod.
        // If received at a later position, it continues as normal linear playback.
        val podInfo = ad?.adPodInfo
        if (type == ImaCsaiAdType.TRUEX && !canPlayTruex(podInfo?.adPosition ?: 1)) {
            Log.w(TAG, "onAdStarted: TrueX ad at position ${podInfo?.adPosition} != 1; falling back to linear")
            binding.playerView.visibility = View.VISIBLE
            showStatus("TrueX ad must be first in pod (position: ${podInfo?.adPosition}) • playing as linear")
            return
        }

        val payload = extractImaCsaiPayload(ad?.companionAds, ad?.traffickingParameters)
        if (payload == null) {
            Log.e(TAG, "onAdStarted: interactive payload is invalid for $type ad; continuing fallback pod")
            showStatus("Interactive payload is invalid • continuing IMA fallback")
            videoPlayer.finishCurrentAd()
            adsManager?.resume()
            return
        }

        // Pause IMA ad playback and hide player so TrueX overlay has exclusive focus.
        currentInteractiveType = type
        truexAdCreditReceived = false
        truexAdTerminalEvent = false
        Log.i(TAG, "onAdStarted: pausing IMA AdsManager and video player for $type interactive ad")
        adsManager?.pause()
        videoPlayer.pause()
        binding.playerView.visibility = View.INVISIBLE
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
            Log.i(TAG, "Initializing TruexAdRenderer with JSON parameters")
            tar.init(payload, options)
        }
        truexAdRenderer = newRenderer
        Log.i(TAG, "Starting TruexAdRenderer inside adContainer")
        runCatching { newRenderer.start(binding.adContainer) }
            .onFailure { error ->
                Log.e(TAG, "Failed to start TruexAdRenderer: ${error.message}")
                showStatus("Renderer setup failed: ${error.message} • continuing IMA")
                finishInteractive(shouldSkipPod = false)
            }
    }

    // [3] Terminal event processing: AD_FREE_POD credit skips the pod only on AD_COMPLETED.
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
        binding.playerView.visibility = View.VISIBLE

        if (type == ImaCsaiAdType.TRUEX && shouldSkipPod) {
            // TrueX credit earned: discard the remainder of this client-side ad break.
            Log.i(TAG, "finishInteractive: TrueX credit earned; discarding ad break via adsManager.discardAdBreak()")
            showStatus("TrueX credit earned • IMA pod discarded")
            adsManager?.discardAdBreak()
            adsManager?.resume()
        } else {
            // Opt-out, error, cancel, or IDVx: seek to near end of current placeholder so IMA finishes it and plays fallback ads.
            Log.i(TAG, "finishInteractive: $type did not skip pod; seeking placeholder to end and continuing fallback ads")
            showStatus("$type complete • continuing IMA pod")
            videoPlayer.finishCurrentAd()
            adsManager?.resume()
        }
    }

    private fun recoverContent() {
        Log.i(TAG, "recoverContent: disposing renderer and resuming content stream")
        disposeRenderer()
        currentInteractiveType = null
        binding.playerView.visibility = View.VISIBLE
        videoPlayer.resumeContent()
        showStatus("Content resumed")
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

    private fun destroyAdsManager() {
        Log.i(TAG, "destroyAdsManager: destroying AdsManager")
        adsManager?.destroy()
        adsManager = null
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume: truexAdRendererActive=${truexAdRenderer != null}")
        if (truexAdRenderer != null) {
            truexAdRenderer?.resume()
        } else {
            videoPlayer.resume()
        }
    }

    override fun onPause() {
        Log.i(TAG, "onPause: truexAdRendererActive=${truexAdRenderer != null}")
        if (truexAdRenderer != null) {
            truexAdRenderer?.pause()
        } else {
            videoPlayer.pause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: cleaning up callbacks, renderer, AdsManager, and player")
        binding.root.removeCallbacks(midrollCheck)
        disposeRenderer()
        destroyAdsManager()
        adsLoader.release()
        videoPlayer.releasePlayer()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ImaCsai"
        const val MIDROLL_MS = 10_000L
        const val CONTENT_URL = "https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4"
        const val AD_TAG_URL = "https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/ima-csai/fire-tv/vast-preroll.xml"
    }
}
