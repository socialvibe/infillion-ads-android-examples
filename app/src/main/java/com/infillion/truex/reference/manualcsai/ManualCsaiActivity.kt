package com.infillion.truex.reference.manualcsai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.infillion.truex.reference.R
import com.infillion.truex.reference.databinding.ActivityManualCsaiBinding
import com.truex.adrenderer.TruexAdEvent

class ManualCsaiActivity : AppCompatActivity(), ManualTruexRenderer.Listener {
    private lateinit var binding: ActivityManualCsaiBinding
    private lateinit var player: ExoPlayer
    private lateinit var adBreak: ManualAdBreak
    private lateinit var midrollGate: MidrollGate
    private var renderer: ManualTruexRenderer? = null
    private var contentPositionMs = 0L
    private var currentAdIndex = -1
    private var playingAdPod = false
    private var waitingForInteractivePlaceholderEnd = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && playingAdPod) {
                val ad = currentAdOrNull() ?: return
                if (ad.type != ManualAdType.LINEAR && renderer == null) {
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
        playContent(0L)
        binding.root.post(midrollCheck)
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
        binding.playerView.visibility = View.INVISIBLE
        binding.rendererContainer.visibility = View.VISIBLE
        val newRenderer = ManualTruexRenderer(this, this)
        renderer = newRenderer
        runCatching { newRenderer.start(binding.rendererContainer, ad) }
            .onFailure { error ->
                showStatus("Renderer setup failed: ${error.message}. Continuing fallback pod.")
                completeInteractiveAd(receivedCredit = false)
            }
    }

    // [3] Credit is applied only after TAR reports a terminal event.
    override fun onTerminal(receivedCredit: Boolean, event: TruexAdEvent) {
        showStatus("Renderer finished: $event")
        completeInteractiveAd(receivedCredit)
    }

    private fun completeInteractiveAd(receivedCredit: Boolean) {
        val ad = currentAdOrNull() ?: return
        disposeRenderer()
        if (shouldSkipRemainingPod(ad.type, receivedCredit)) {
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
        binding.rendererContainer.removeAllViews()
        binding.rendererContainer.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (renderer != null) renderer?.resume() else player.play()
    }

    override fun onPause() {
        renderer?.pause()
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
        const val CONTENT_URL = "https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4"
    }
}
