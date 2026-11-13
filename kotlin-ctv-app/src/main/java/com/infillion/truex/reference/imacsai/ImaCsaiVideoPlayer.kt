package com.infillion.truex.reference.imacsai

import android.annotation.SuppressLint
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.ads.interactivemedia.v3.api.AdPodInfo
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate
import kotlin.math.roundToInt

@SuppressLint("UnsafeOptInUsageError")
internal class ImaCsaiVideoPlayer(
    private val playerView: PlayerView,
    private val onContentEnded: () -> Unit,
    private val onPlaybackError: (PlaybackException) -> Unit,
) : VideoAdPlayer {
    private val player = ExoPlayer.Builder(playerView.context).build()
    private val callbacks = mutableListOf<VideoAdPlayer.VideoAdPlayerCallback>()
    private var currentAd: AdMediaInfo? = null
    private var pendingAd: AdMediaInfo? = null
    private var contentUrl: String? = null
    private var savedContentPositionMs = 0L
    private var inAdBreak = false
    private var loadedCallbackSent = false
    private var adHasPlayed = false
    private var adMarkerTimesMs = LongArray(0)

    init {
        playerView.player = player
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val ad = currentAd
                    if (playbackState == Player.STATE_READY && ad != null && !loadedCallbackSent) {
                        loadedCallbackSent = true
                        callbacks.forEach { it.onLoaded(ad) }
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        if (ad != null) {
                            callbacks.forEach { it.onEnded(ad) }
                        } else {
                            onContentEnded()
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val ad = currentAd ?: return
                    if (isPlaying) {
                        if (adHasPlayed) {
                            callbacks.forEach { it.onResume(ad) }
                        } else {
                            adHasPlayed = true
                            callbacks.forEach { it.onPlay(ad) }
                        }
                        updateAdProgress()
                    } else if (player.playbackState == Player.STATE_READY) {
                        callbacks.forEach { it.onPause(ad) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    currentAd?.let { ad -> callbacks.forEach { it.onError(ad) } }
                    onPlaybackError(error)
                }
            },
        )
    }

    val contentPositionMs: Long
        get() = if (!inAdBreak && currentAd == null) player.currentPosition else savedContentPositionMs

    val contentProgressProvider = ContentProgressProvider {
        if (inAdBreak || currentAd != null || player.duration <= 0) {
            VideoProgressUpdate.VIDEO_TIME_NOT_READY
        } else {
            VideoProgressUpdate(player.currentPosition, player.duration)
        }
    }

    fun playContent(url: String, positionMs: Long = 0L) {
        contentUrl = url
        currentAd = null
        pendingAd = null
        inAdBreak = false
        player.setMediaItem(MediaItem.fromUri(url), positionMs)
        player.prepare()
        player.play()
        playerView.useController = true
        playerView.controllerAutoShow = true
    }

    fun pauseContentForAds() {
        if (!inAdBreak) {
            savedContentPositionMs = player.currentPosition
        }
        inAdBreak = true
        player.pause()
        playerView.useController = false
        pendingAd?.let { ad ->
            pendingAd = null
            prepareAd(ad)
        }
    }

    fun resumeContent() {
        val url = contentUrl ?: return
        val wasInAdBreak = inAdBreak
        val resumePositionMs = if (wasInAdBreak || currentAd != null) savedContentPositionMs else player.currentPosition
        playContent(url, resumePositionMs)
        if (wasInAdBreak) {
            updateAdMarkers(markPlayedUpToMs = resumePositionMs)
        }
    }

    fun setAdMarkers(timesMs: LongArray) {
        adMarkerTimesMs = timesMs
        updateAdMarkers(markPlayedUpToMs = -1L)
    }

    fun finishCurrentAd() {
        val duration = player.duration
        if (duration > 200L && duration != C.TIME_UNSET) {
            player.seekTo(duration - 100L)
        }
        player.play()
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun releasePlayer() {
        playerView.player = null
        player.release()
    }

    override fun loadAd(adMediaInfo: AdMediaInfo, adPodInfo: AdPodInfo) {
        if (!inAdBreak) {
            // Defer replacing the active content MediaItem until CONTENT_PAUSE_REQUESTED.
            pendingAd = adMediaInfo
            return
        }
        prepareAd(adMediaInfo)
    }

    override fun playAd(adMediaInfo: AdMediaInfo) {
        if (currentAd != adMediaInfo) {
            pendingAd = null
            prepareAd(adMediaInfo)
        }
        player.play()
    }

    override fun pauseAd(adMediaInfo: AdMediaInfo) = player.pause()

    override fun stopAd(adMediaInfo: AdMediaInfo) {
        currentAd = null
        pendingAd = null
        player.stop()
    }

    override fun release() = Unit

    override fun addCallback(callback: VideoAdPlayer.VideoAdPlayerCallback) {
        callbacks += callback
    }

    override fun removeCallback(callback: VideoAdPlayer.VideoAdPlayerCallback) {
        callbacks -= callback
    }

    override fun getAdProgress(): VideoProgressUpdate {
        val ad = currentAd
        val duration = player.duration
        return if (ad == null || duration <= 0 || duration == C.TIME_UNSET) {
            VideoProgressUpdate.VIDEO_TIME_NOT_READY
        } else {
            VideoProgressUpdate(player.currentPosition, duration)
        }
    }

    override fun getVolume(): Int = (player.volume * 100).roundToInt()

    private fun prepareAd(adMediaInfo: AdMediaInfo) {
        currentAd = adMediaInfo
        loadedCallbackSent = false
        adHasPlayed = false
        player.setMediaItem(MediaItem.fromUri(adMediaInfo.url))
        player.prepare()
    }

    private fun updateAdMarkers(markPlayedUpToMs: Long) {
        if (adMarkerTimesMs.isEmpty()) {
            playerView.setExtraAdGroupMarkers(null, null)
            return
        }
        val played = BooleanArray(adMarkerTimesMs.size) { index ->
            adMarkerTimesMs[index] <= markPlayedUpToMs
        }
        playerView.setExtraAdGroupMarkers(adMarkerTimesMs, played)
    }

    private fun updateAdProgress() {
        val ad = currentAd ?: return
        callbacks.forEach { it.onAdProgress(ad, adProgress) }
        if (player.isPlaying) {
            playerView.postDelayed(::updateAdProgress, 500L)
        }
    }
}
