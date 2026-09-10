package com.infillion.truex.reference.manualcsai

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    private val testEventListeners = mutableListOf<(TruexAdEvent, Map<*, *>?) -> Unit>()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(TAG, "onPlaybackStateChanged: playbackState=$playbackState, playingAdPod=$playingAdPod, waitingForPlaceholderEnd=$waitingForInteractivePlaceholderEnd")
            if (playbackState == Player.STATE_READY && playingAdPod) {
                val ad = currentAdOrNull()
                if (ad == null) {
                    return
                }
                // When an interactive ad placeholder begins playback, seek near its end and pause.
                // The underlying video player is hidden while TruexAdRenderer takes over the screen.
                if (ad.type != ManualAdType.LINEAR && truexAdRenderer == null) {
                    val end = player.duration.takeIf { it > 200L } ?: ad.durationMs
                    Log.i(TAG, "Interactive placeholder ready; seeking near end (${end - 100L}ms) and displaying TrueX renderer")
                    player.seekTo(end - 100L)
                    player.pause()
                    showInteractiveAd(ad)
                }
            }
            if (playbackState == Player.STATE_ENDED) {
                if (waitingForInteractivePlaceholderEnd) {
                    Log.i(TAG, "Interactive placeholder ended after fallback; advancing ad pod")
                    waitingForInteractivePlaceholderEnd = false
                    advanceAdPod()
                } else if (playingAdPod) {
                    Log.i(TAG, "Ad ended; advancing ad pod")
                    advanceAdPod()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer playback error: ${error.errorCodeName}")
            showStatus("Playback error: ${error.errorCodeName}. Continuing safely.")
            if (playingAdPod) {
                advanceAdPod()
            } else {
                finish()
            }
        }
    }

    private val midrollCheck = object : Runnable {
        override fun run() {
            if (!playingAdPod && midrollGate.shouldTrigger(player.currentPosition)) {
                Log.i(TAG, "Midroll trigger reached at ${player.currentPosition}ms")
                startAdBreak()
            }
            if (!isFinishing) {
                binding.root.postDelayed(this, 250L)
            }
        }
    }

    // Handles lifecycle and user-interaction events emitted by TruexAdRenderer.
    private val truexAdEventHandler = IEventEmitter.IEventHandler { event, data ->
        Log.i(TAG, "TruexAdEvent $event data=$data")
        testEventListeners.forEach { it(event, data) }
        when (event) {
            // Main flow events
            TruexAdEvent.AD_FETCH_COMPLETED -> {
                Log.i(TAG, "TrueX renderer finished fetching ad payload and is ready to present")
            }
            TruexAdEvent.AD_STARTED -> {
                // Interactive unit has started displaying to the viewer
                Log.i(TAG, "TrueX interactive unit started")
                showStatus("Interactive ad • $event")
            }
            TruexAdEvent.AD_DISPLAYED -> {
                Log.i(TAG, "TrueX interactive assets loaded and visible")
            }
            TruexAdEvent.AD_FREE_POD -> {
                // The viewer completed the requirements to earn the ad-free pod reward.
                // Do not skip immediately; wait for a terminal event (AD_COMPLETED) before skipping the pod.
                Log.i(TAG, "TrueX credit earned (AD_FREE_POD); waiting for terminal event to skip remaining pod")
                truexAdCreditReceived = true
                isTruexAdCreditEarnedRecordForTesting = true
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
            TruexAdEvent.VIDEO_EVENT -> {
                // Video progress inside the interactive unit; not required for host timeline management
            }
            else -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: initializing ManualCsaiActivity")
        binding = ActivityManualCsaiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adBreak = ManualAdBreakParser.parse(this, R.raw.manual_ad_break)
        midrollGate = MidrollGate(adBreak.timeOffsetMs)
        player = ExoPlayer.Builder(this).build().also {
            binding.playerView.player = it
            it.addListener(playerListener)
        }
        showStatus("Loading VAST ad parameters")
        Log.i(TAG, "Resolving VAST ad parameters asynchronously")
        Thread(::resolveVastAndStart, "manual-vast-load").start()
    }

    private fun resolveVastAndStart() {
        val userId = newReferenceUserId()
        Log.i(TAG, "VAST user-id generated: $userId")
        val resolved = adBreak.copy(
            ads = adBreak.ads.map { ad ->
                val url = ad.vastUrl?.let { applyVastUserId(it, userId) } ?: return@map ad
                var payload: org.json.JSONObject? = null
                for (attempt in 1..3) {
                    payload = runCatching { ManualVastPayloadParser.load(url) }
                        .onFailure { error ->
                            Log.w(TAG, "Attempt $attempt: Failed to load VAST from $url: ${error.message}")
                        }
                        .getOrNull()
                    if (payload != null) break
                    Thread.sleep(500L)
                }
                payload?.let { ad.copy(vastUrl = url, adParameters = it) } ?: ad.copy(vastUrl = url)
            },
        )
        runOnUiThread {
            if (isFinishing) {
                return@runOnUiThread
            }
            adBreak = resolved
            Log.i(TAG, "VAST resolution complete; starting content playback from 0ms")
            playContent(0L)
            binding.root.post(midrollCheck)
        }
    }

    // [1] The host app owns the content timeline and decides when the ad break starts.
    private fun startAdBreak() {
        contentPositionMs = player.currentPosition
        Log.i(TAG, "startAdBreak: pausing content at ${contentPositionMs}ms and starting ad pod ${adBreak.id}")
        player.pause()
        playingAdPod = true
        currentAdIndex = 0
        showStatus("Simulated ad response loaded • ${adBreak.id}")
        playCurrentAd()
    }

    private fun playCurrentAd() {
        val ad = currentAdOrNull()
        if (ad == null) {
            Log.i(TAG, "playCurrentAd: no more ads in pod; completing ad break")
            finishAdBreak("Ad pod complete")
            return
        }
        Log.i(TAG, "playCurrentAd: index=$currentAdIndex, adId=${ad.id}, type=${ad.type}, mediaUrl=${ad.mediaUrl}")
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
            Log.e(TAG, "showInteractiveAd: ad ${ad.id} has no adParameters; continuing fallback pod")
            showStatus("Renderer setup failed: Interactive ad ${ad.id} has no ad parameters. Continuing fallback pod.")
            completeInteractiveAd(shouldSkipPod = false)
            return
        }
        Log.i(TAG, "showInteractiveAd: hiding player and presenting TrueX renderer container for ${ad.type}")
        binding.playerView.visibility = View.INVISIBLE
        binding.rendererContainer.visibility = View.VISIBLE
        truexAdCreditReceived = false
        truexAdTerminalEvent = false

        // Configure TrueX ad options:
        // - supportsUserCancelStream: enables Back button to trigger USER_CANCEL_STREAM
        // - appId: package name for telemetry attribution
        // - enableWebViewDebugging: allows inspect via chrome://inspect in debug builds
        val newRenderer = TruexAdRenderer(this).also { tar ->
            tar.addEventListener(null, truexAdEventHandler)
            tar.init(
                adParameters,
                TruexAdOptions().apply {
                    supportsUserCancelStream = true
                    appId = packageName
                    enableWebViewDebugging = BuildConfig.DEBUG
                },
            )
        }
        truexAdRenderer = newRenderer
        Log.i(TAG, "Starting TruexAdRenderer inside rendererContainer")
        runCatching { newRenderer.start(binding.rendererContainer) }
            .onFailure { error ->
                Log.e(TAG, "Failed to start TruexAdRenderer: ${error.message}")
                showStatus("Renderer setup failed: ${error.message}. Continuing fallback pod.")
                completeInteractiveAd(shouldSkipPod = false)
            }
    }

    // [3] Terminal event processing: AD_FREE_POD credit skips remaining pod only on AD_COMPLETED.
    private fun finishTruexAd(event: TruexAdEvent) {
        if (truexAdTerminalEvent) {
            return
        }
        truexAdTerminalEvent = true
        if (event == TruexAdEvent.AD_COMPLETED) {
            isTruexAdCompletedRecordForTesting = true
        }
        Log.i(TAG, "finishTruexAd: event=$event, creditReceived=$truexAdCreditReceived")
        showStatus("Renderer finished: $event")
        completeInteractiveAd(shouldSkipPod = truexAdCreditReceived && event == TruexAdEvent.AD_COMPLETED)
    }

    private fun completeInteractiveAd(shouldSkipPod: Boolean) {
        val ad = currentAdOrNull()
        if (ad == null) {
            return
        }
        Log.i(TAG, "completeInteractiveAd: adType=${ad.type}, shouldSkipPod=$shouldSkipPod")
        disposeRenderer()
        if (shouldSkipRemainingPod(ad.type, shouldSkipPod)) {
            Log.i(TAG, "completeInteractiveAd: TrueX credit earned; skipping remaining ads in pod")
            finishAdBreak("TrueX credit earned • remaining ads skipped")
            return
        }
        // Opt-out, error, cancel, or IDVx: let placeholder reach its end, then play fallback linear ads.
        Log.i(TAG, "completeInteractiveAd: ${ad.type} complete without pod skip; playing placeholder to end for fallback ads")
        binding.playerView.visibility = View.VISIBLE
        waitingForInteractivePlaceholderEnd = true
        player.play()
        showStatus("${ad.type} complete • continuing fallback pod")
    }

    private fun advanceAdPod() {
        currentAdIndex += 1
        Log.i(TAG, "advanceAdPod: advancing to ad index $currentAdIndex")
        playCurrentAd()
    }

    private fun finishAdBreak(message: String) {
        Log.i(TAG, "finishAdBreak: $message; resuming content at ${contentPositionMs}ms")
        playingAdPod = false
        currentAdIndex = -1
        waitingForInteractivePlaceholderEnd = false
        disposeRenderer()
        showStatus(message)
        playContent(contentPositionMs)
    }

    private fun playContent(positionMs: Long) {
        Log.i(TAG, "playContent: setting content media item at ${positionMs}ms")
        binding.playerView.visibility = View.VISIBLE
        player.setMediaItem(MediaItem.fromUri(CONTENT_URL), positionMs)
        player.prepare()
        player.play()
        showStatus("Content • midroll at ${adBreak.timeOffsetMs / 1_000}s")
    }

    private fun currentAdOrNull(): ManualAd? = adBreak.ads.getOrNull(currentAdIndex)

    private fun showStatus(message: String) {
        Log.i(TAG, "Status: $message")
        binding.statusText.text = message
    }

    private fun disposeRenderer() {
        Log.i(TAG, "disposeRenderer: releasing TruexAdRenderer and resetting container")
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
        Log.i(TAG, "onResume: truexAdRendererActive=${truexAdRenderer != null}")
        if (truexAdRenderer != null) {
            truexAdRenderer?.resume()
        } else {
            player.play()
        }
    }

    override fun onPause() {
        Log.i(TAG, "onPause: truexAdRendererActive=${truexAdRenderer != null}")
        if (truexAdRenderer != null) {
            truexAdRenderer?.pause()
        }
        player.pause()
        super.onPause()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: releasing resources and player")
        binding.root.removeCallbacks(midrollCheck)
        disposeRenderer()
        player.removeListener(playerListener)
        binding.playerView.player = null
        player.release()
        super.onDestroy()
    }

    @VisibleForTesting
    val isPlayingAdPodForTesting: Boolean get() = playingAdPod

    @VisibleForTesting
    var isTruexAdCreditEarnedRecordForTesting: Boolean = false
        private set

    @VisibleForTesting
    var isTruexAdCompletedRecordForTesting: Boolean = false
        private set

    @VisibleForTesting
    val isTruexAdCreditReceivedForTesting: Boolean get() = truexAdCreditReceived

    @VisibleForTesting
    val isTruexAdTerminalEventForTesting: Boolean get() = truexAdTerminalEvent

    @VisibleForTesting
    val currentContentPositionMsForTesting: Long
        get() = if (::player.isInitialized) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                player.currentPosition
            } else {
                var position = 0L
                val latch = CountDownLatch(1)
                runOnUiThread {
                    position = if (::player.isInitialized) player.currentPosition else 0L
                    latch.countDown()
                }
                latch.await(500L, TimeUnit.MILLISECONDS)
                position
            }
        } else 0L

    @VisibleForTesting
    val statusTextForTesting: String
        get() = if (::binding.isInitialized) binding.statusText.text.toString() else ""

    @VisibleForTesting
    val isRendererContainerVisibleForTesting: Boolean
        get() = if (::binding.isInitialized) binding.rendererContainer.visibility == View.VISIBLE else false

    @VisibleForTesting
    fun addTruexEventListenerForTesting(listener: (TruexAdEvent, Map<*, *>?) -> Unit) {
        testEventListeners.add(listener)
    }

    @VisibleForTesting
    fun removeTruexEventListenerForTesting(listener: (TruexAdEvent, Map<*, *>?) -> Unit) {
        testEventListeners.remove(listener)
    }

    private companion object {
        const val TAG = "ManualCsai"
        const val CONTENT_URL = "https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4"
    }
}
