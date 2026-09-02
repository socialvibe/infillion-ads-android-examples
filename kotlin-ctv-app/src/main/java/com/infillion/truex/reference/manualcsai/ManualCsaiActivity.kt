package com.infillion.truex.reference.manualcsai

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.infillion.truex.reference.BuildConfig
import com.infillion.truex.reference.R
import com.infillion.truex.reference.databinding.ActivityManualCsaiBinding
import com.truex.adrenderer.IEventEmitter
import com.truex.adrenderer.TruexAdEvent
import com.truex.adrenderer.TruexAdOptions
import com.truex.adrenderer.TruexAdRenderer

class ManualCsaiActivity : AppCompatActivity() {
    private lateinit var binding: ActivityManualCsaiBinding
    private lateinit var player: ExoPlayer
    private lateinit var adBreak: ManualAdBreak
    private lateinit var midrollGate: MidrollGate
    private var truexAdRenderer: TruexAdRenderer? = null
    private var truexAdCreditReceived = false
    private var truexAdTerminalEvent = false
    private var contentPositionMs = 0L
    private var currentAdIndex = -1
    private var playingAdPod = false
    private var waitingForInteractivePlaceholderEnd = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && playingAdPod) {
                val ad = currentAdOrNull() ?: return
                if (ad.type != ManualAdType.LINEAR && truexAdRenderer == null) {
                    val end = player.duration.takeIf { it > 200L } ?: ad.durationMs
                    player.seekTo(end - 100L)
                    player.pause()
                    showInteractiveAd(ad)
                }
            }
            if (playbackState == Player.STATE_ENDED) {
                when {
                    waitingForInteractivePlaceholderEnd -> {
                        waitingForInteractivePlaceholderEnd = false
                        advanceAdPod()
                    }
                    playingAdPod -> advanceAdPod()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            showStatus("Playback error: ${error.errorCodeName}. Continuing safely.")
            if (playingAdPod) advanceAdPod() else finish()
        }
    }

    private val midrollCheck = object : Runnable {
        override fun run() {
            if (!playingAdPod && midrollGate.shouldTrigger(player.currentPosition)) {
                startAdBreak()
            }
            if (!isFinishing) binding.root.postDelayed(this, 250L)
        }
    }

    private val truexAdEventHandler = IEventEmitter.IEventHandler { event, data ->
        Log.i(TAG, "TruexAdEvent $event data=$data")
        when (event) {
            // Main flow
            TruexAdEvent.AD_FETCH_COMPLETED -> Unit // init ad request finished; truex renderer is ready to present
            TruexAdEvent.AD_STARTED -> { // truex renderer starts showing
                showStatus("Interactive ad • $event")
            }
            TruexAdEvent.AD_DISPLAYED -> Unit // truex renderer UX assets are loaded and visible
            TruexAdEvent.AD_COMPLETED -> { // terminal: truex renderer finished; resume playback
                finishTruexAd(event)
            }
            TruexAdEvent.AD_ERROR -> { // terminal: unrecoverable truex renderer error
                finishTruexAd(event)
            }
            TruexAdEvent.NO_ADS_AVAILABLE -> { // terminal: no ads available
                finishTruexAd(event)
            }
            TruexAdEvent.AD_FREE_POD -> { // credit earned; wait for a terminal event before skipping the pod
                truexAdCreditReceived = true
            }
            TruexAdEvent.USER_CANCEL_STREAM -> { // terminal: viewer wants to leave the stream
                showStatus("Viewer cancelled the stream")
                finish()
            }
            // Informative
            TruexAdEvent.OPT_IN -> { // viewer chose the interactive ad
                showStatus("Interactive ad • $event")
            }
            TruexAdEvent.OPT_OUT -> { // viewer chose linear ads, or the choice-card timer expired
                showStatus("Interactive ad • $event")
            }
            TruexAdEvent.USER_CANCEL -> Unit // backed out of the interactive unit after opt-in
            TruexAdEvent.VIDEO_EVENT -> Unit // video progress inside the unit; not required for this app
            else -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManualCsaiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adBreak = ManualAdBreakParser.parse(this, R.raw.manual_ad_break)
        midrollGate = MidrollGate(adBreak.timeOffsetMs)
        player = ExoPlayer.Builder(this).build().also {
            binding.playerView.player = it
            it.addListener(playerListener)
        }
        showStatus("Loading VAST ad parameters")
        Thread(::resolveVastAndStart, "manual-vast-load").start()
    }

    private fun resolveVastAndStart() {
        val userId = newReferenceUserId()
        Log.i(TAG, "VAST user-id $userId")
        val resolved = adBreak.copy(
            ads = adBreak.ads.map { ad ->
                val url = ad.vastUrl?.let { applyVastUserId(it, userId) } ?: return@map ad
                runCatching { ManualVastPayloadParser.load(url) }
                    .getOrNull()
                    ?.let { ad.copy(vastUrl = url, adParameters = it) }
                    ?: ad.copy(vastUrl = url)
            },
        )
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            adBreak = resolved
            playContent(0L)
            binding.root.post(midrollCheck)
        }
    }

    // [1] The host app owns the content timeline and decides when the ad break starts.
    private fun startAdBreak() {
        contentPositionMs = player.currentPosition
        player.pause()
        playingAdPod = true
        currentAdIndex = 0
        showStatus("Simulated ad response loaded • ${adBreak.id}")
        playCurrentAd()
    }

    private fun playCurrentAd() {
        val ad = currentAdOrNull()
        if (ad == null) {
            finishAdBreak("Ad pod complete")
            return
        }
        showStatus(
            when (ad.type) {
                ManualAdType.LINEAR -> "Linear fallback • ${ad.id}"
                ManualAdType.TRUEX -> "TrueX placeholder • waiting for renderer"
                ManualAdType.IDVX -> "IDVx placeholder • waiting for renderer"
            },
        )
        binding.playerView.visibility = View.VISIBLE
        player.setMediaItem(MediaItem.fromUri(ad.mediaUrl))
        player.prepare()
        player.playWhenReady = ad.type == ManualAdType.LINEAR
    }

    // [2] Interactive placeholders pause at their end while TAR owns the overlay.
    private fun showInteractiveAd(ad: ManualAd) {
        val adParameters = ad.adParameters
        if (adParameters == null) {
            showStatus("Renderer setup failed: Interactive ad ${ad.id} has no ad parameters. Continuing fallback pod.")
            completeInteractiveAd(shouldSkipPod = false)
            return
        }
        binding.playerView.visibility = View.INVISIBLE
        binding.rendererContainer.visibility = View.VISIBLE
        truexAdCreditReceived = false
        truexAdTerminalEvent = false
        val newRenderer = TruexAdRenderer(this).also { tar ->
            tar.addEventListener(null, truexAdEventHandler)
            tar.init(
                adParameters,
                TruexAdOptions().apply {
                    // IDVx: true → Back fires USER_CANCEL_STREAM. false → Back does nothing.
                    // TrueX: true → Back on the choice card fires USER_CANCEL_STREAM.
                    //        false → Back on the choice card fires OPT_OUT.
                    supportsUserCancelStream = true
                    // Internal tracking. TAR uses the host package name when unset.
                    appId = packageName
                    // Debug only. Chrome inspect via chrome://inspect. Do not enable in production.
                    enableWebViewDebugging = BuildConfig.DEBUG
                    // Do not set userAdvertisingId / fallbackAdvertisingId.
                    // The ad-server advertising-id macro should already be in AdParameters.
                    // Confirm that during integration certification.
                },
            )
        }
        truexAdRenderer = newRenderer
        runCatching { newRenderer.start(binding.rendererContainer) }
            .onFailure { error ->
                showStatus("Renderer setup failed: ${error.message}. Continuing fallback pod.")
                completeInteractiveAd(shouldSkipPod = false)
            }
    }

    private fun finishTruexAd(event: TruexAdEvent) {
        if (truexAdTerminalEvent) return
        truexAdTerminalEvent = true
        showStatus("Renderer finished: $event")
        completeInteractiveAd(shouldSkipPod = truexAdCreditReceived && event == TruexAdEvent.AD_COMPLETED)
    }

    // [3] AD_FREE_POD credit is applied only when TAR later reports AD_COMPLETED.
    private fun completeInteractiveAd(shouldSkipPod: Boolean) {
        val ad = currentAdOrNull() ?: return
        disposeRenderer()
        if (shouldSkipRemainingPod(ad.type, shouldSkipPod)) {
            finishAdBreak("TrueX credit earned • remaining ads skipped")
            return
        }
        binding.playerView.visibility = View.VISIBLE
        waitingForInteractivePlaceholderEnd = true
        player.play()
        showStatus("${ad.type} complete • continuing fallback pod")
    }

    private fun advanceAdPod() {
        currentAdIndex += 1
        playCurrentAd()
    }

    private fun finishAdBreak(message: String) {
        playingAdPod = false
        currentAdIndex = -1
        waitingForInteractivePlaceholderEnd = false
        disposeRenderer()
        showStatus(message)
        playContent(contentPositionMs)
    }

    private fun playContent(positionMs: Long) {
        binding.playerView.visibility = View.VISIBLE
        player.setMediaItem(MediaItem.fromUri(CONTENT_URL), positionMs)
        player.prepare()
        player.play()
        showStatus("Content • midroll at ${adBreak.timeOffsetMs / 1_000}s")
    }

    private fun currentAdOrNull(): ManualAd? = adBreak.ads.getOrNull(currentAdIndex)

    private fun showStatus(message: String) {
        binding.statusText.text = message
    }

    private fun disposeRenderer() {
        truexAdRenderer?.removeEventListener(null, truexAdEventHandler)
        truexAdRenderer?.stop()
        truexAdRenderer = null
        truexAdCreditReceived = false
        truexAdTerminalEvent = false
        binding.rendererContainer.removeAllViews()
        binding.rendererContainer.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (truexAdRenderer != null) truexAdRenderer?.resume() else player.play()
    }

    override fun onPause() {
        truexAdRenderer?.pause()
        player.pause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(midrollCheck)
        disposeRenderer()
        player.removeListener(playerListener)
        binding.playerView.player = null
        player.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ManualCsai"
        const val CONTENT_URL = "https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4"
    }
}
