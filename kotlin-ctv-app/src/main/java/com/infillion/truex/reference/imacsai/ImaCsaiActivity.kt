package com.infillion.truex.reference.imacsai

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.ads.interactivemedia.v3.api.Ad
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.ads.interactivemedia.v3.api.AdsManager
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import com.infillion.truex.reference.databinding.ActivityImaCsaiBinding
import com.truex.adrenderer.TruexAdEvent

class ImaCsaiActivity : AppCompatActivity(), ImaCsaiTruexRenderer.Listener {
    private lateinit var binding: ActivityImaCsaiBinding
    private lateinit var videoPlayer: ImaCsaiVideoPlayer
    private lateinit var adsLoader: AdsLoader
    private var adsManager: AdsManager? = null
    private var renderer: ImaCsaiTruexRenderer? = null
    private var currentInteractiveType: ImaCsaiAdType? = null
    private var adsRequested = false

    private val midrollCheck = object : Runnable {
        override fun run() {
            if (!adsRequested && videoPlayer.contentPositionMs >= MIDROLL_MS) requestAds()
            if (!isFinishing) binding.root.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImaCsaiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoPlayer = ImaCsaiVideoPlayer(
            playerView = binding.playerView,
            onContentEnded = { adsLoader.contentComplete() },
            onPlaybackError = { error ->
                showStatus("Playback error: ${error.errorCodeName}")
                recoverContent()
            },
        )
        configureIma()
        videoPlayer.playContent(CONTENT_URL)
        showStatus("Content • IMA request at ${MIDROLL_MS / 1_000}s")
        binding.root.post(midrollCheck)
    }

    private fun configureIma() {
        val factory = ImaSdkFactory.getInstance()
        val settings = factory.createImaSdkSettings().apply {
            language = "en"
            isDebugMode = true
        }
        val displayContainer = ImaSdkFactory.createAdDisplayContainer(binding.adContainer, videoPlayer)
        adsLoader = factory.createAdsLoader(this, settings, displayContainer).also { loader ->
            loader.addAdErrorListener { event ->
                showStatus("IMA error: ${event.error.message}. Resuming content.")
                destroyAdsManager()
                recoverContent()
            }
            loader.addAdsLoadedListener { event ->
                adsManager = event.adsManager.also(::configureAdsManager)
            }
        }
    }

    // [1] The host requests a real client-side pod at its content midroll.
    private fun requestAds() {
        adsRequested = true
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
        manager.addAdErrorListener(
            AdErrorEvent.AdErrorListener { event ->
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
        when (event.type) {
            AdEvent.AdEventType.LOADED -> adsManager?.start()
            AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED -> {
                videoPlayer.pauseContentForAds()
                showStatus("IMA ad break")
            }
            AdEvent.AdEventType.STARTED -> onAdStarted(event.ad)
            AdEvent.AdEventType.PAUSED -> showStatus("IMA ad paused")
            AdEvent.AdEventType.RESUMED -> showStatus("IMA ad resumed")
            AdEvent.AdEventType.CONTENT_RESUME_REQUESTED -> recoverContent()
            AdEvent.AdEventType.ALL_ADS_COMPLETED -> destroyAdsManager()
            else -> Unit
        }
    }

    // [2] AdSystem and trafficking parameters identify the interactive placeholder.
    private fun onAdStarted(ad: Ad?) {
        val type = classifyImaCsaiAd(ad?.adSystem)
        if (type == ImaCsaiAdType.LINEAR) {
            binding.playerView.visibility = View.VISIBLE
            showStatus("IMA linear ad")
            return
        }

        val payload = extractImaCsaiPayload(ad?.traffickingParameters, ad?.description)
        if (payload == null) {
            showStatus("Interactive payload is invalid • continuing IMA fallback")
            videoPlayer.finishCurrentAd()
            adsManager?.resume()
            return
        }

        currentInteractiveType = type
        adsManager?.pause()
        videoPlayer.pauseCurrentAdNearEnd()
        binding.playerView.visibility = View.INVISIBLE
        showStatus("$type interactive ad")
        val newRenderer = ImaCsaiTruexRenderer(this, this)
        renderer = newRenderer
        runCatching { newRenderer.start(binding.adContainer, payload, type) }
            .onFailure { error ->
                showStatus("Renderer setup failed: ${error.message} • continuing IMA")
                finishInteractive(shouldSkipPod = false)
            }
    }

    // [3] AD_FREE_POD credit discards the pod only after AD_COMPLETED.
    override fun onTerminal(shouldSkipPod: Boolean, event: TruexAdEvent) {
        showStatus("Renderer finished: $event")
        finishInteractive(shouldSkipPod)
    }

    private fun finishInteractive(shouldSkipPod: Boolean) {
        val type = currentInteractiveType
        disposeRenderer()
        currentInteractiveType = null
        binding.playerView.visibility = View.VISIBLE
        if (type == ImaCsaiAdType.TRUEX && shouldSkipPod) {
            showStatus("TrueX credit earned • IMA pod discarded")
            adsManager?.discardAdBreak()
            adsManager?.resume()
        } else {
            showStatus("$type complete • continuing IMA pod")
            videoPlayer.finishCurrentAd()
            adsManager?.resume()
        }
    }

    private fun recoverContent() {
        disposeRenderer()
        currentInteractiveType = null
        binding.playerView.visibility = View.VISIBLE
        videoPlayer.resumeContent()
        showStatus("Content resumed")
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

    private fun destroyAdsManager() {
        adsManager?.destroy()
        adsManager = null
    }

    override fun onResume() {
        super.onResume()
        if (renderer != null) renderer?.resume() else videoPlayer.resume()
    }

    override fun onPause() {
        if (renderer != null) renderer?.pause() else videoPlayer.pause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(midrollCheck)
        disposeRenderer()
        destroyAdsManager()
        adsLoader.release()
        videoPlayer.releasePlayer()
        super.onDestroy()
    }

    private companion object {
        const val MIDROLL_MS = 10_000L
        const val CONTENT_URL = "https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4"
        const val AD_TAG_URL =
            "https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/dfp-dai/firetv-vmap/vast-midroll.xml"
    }
}
