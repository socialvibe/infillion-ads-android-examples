package com.infillion.truex.reference.imassai

import android.annotation.SuppressLint
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.ads.interactivemedia.v3.api.CuePoint
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer
import java.util.HashMap
import kotlin.math.roundToInt

@SuppressLint("UnsafeOptInUsageError")
internal class ImaSsaiVideoStreamPlayer(
    private val playerView: PlayerView,
    private val listener: Listener,
) : VideoStreamPlayer {
    interface Listener {
        fun onStreamLoaded()
        fun onAdBreakStarted()
        fun onAdBreakEnded()
        fun onUserSeek(positionMs: Long)
        fun onPlaybackError(error: PlaybackException)
    }

    private val player = ExoPlayer.Builder(playerView.context).build()
    private val callbacks = mutableListOf<VideoStreamPlayer.VideoStreamPlayerCallback>()
    private var suppressSeekCallback = false
    private var streamLoadedSent = false

    init {
        playerView.player = player
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && !streamLoadedSent) {
                        streamLoadedSent = true
                        listener.onStreamLoaded()
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        callbacks.forEach { it.onContentComplete() }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        callbacks.forEach { it.onResume() }
                    } else if (player.playbackState == Player.STATE_READY) {
                        callbacks.forEach { it.onPause() }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK && !suppressSeekCallback) {
                        listener.onUserSeek(newPosition.positionMs)
                    }
                    suppressSeekCallback = false
                }

                override fun onPlayerError(error: PlaybackException) {
                    listener.onPlaybackError(error)
                }
            },
        )
    }

    val currentPositionMs: Long
        get() = player.currentPosition

    override fun loadUrl(url: String, subtitles: MutableList<HashMap<String, String>>) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    override fun pause() = player.pause()

    override fun resume() = player.play()

    override fun seek(milliseconds: Long) = seekTo(milliseconds)

    fun seekTo(milliseconds: Long) {
        suppressSeekCallback = true
        player.seekTo(milliseconds)
    }

    fun hide() {
        playerView.visibility = android.view.View.INVISIBLE
    }

    fun show() {
        playerView.visibility = android.view.View.VISIBLE
    }

    fun setControlsEnabled(enabled: Boolean) {
        playerView.useController = enabled
        playerView.controllerAutoShow = enabled
        if (enabled) {
            playerView.showController()
        } else {
            playerView.hideController()
        }
    }

    fun setAdMarkers(cuePoints: List<CuePoint>) {
        val times = cuePoints.map(CuePoint::getStartTimeMs).toLongArray()
        val played = cuePoints.map(CuePoint::isPlayed).toBooleanArray()
        playerView.setExtraAdGroupMarkers(times, played)
    }

    fun releasePlayer() {
        playerView.player = null
        player.release()
    }

    override fun onAdBreakStarted() {
        setControlsEnabled(false)
        listener.onAdBreakStarted()
    }

    override fun onAdBreakEnded() {
        setControlsEnabled(true)
        listener.onAdBreakEnded()
    }

    override fun onAdPeriodStarted() = Unit

    override fun onAdPeriodEnded() = Unit

    override fun addCallback(callback: VideoStreamPlayer.VideoStreamPlayerCallback) {
        callbacks += callback
    }

    override fun removeCallback(callback: VideoStreamPlayer.VideoStreamPlayerCallback) {
        callbacks -= callback
    }

    override fun getContentProgress(): VideoProgressUpdate {
        val duration = player.duration
        return if (duration <= 0 || duration == C.TIME_UNSET) {
            VideoProgressUpdate.VIDEO_TIME_NOT_READY
        } else {
            VideoProgressUpdate(player.currentPosition, duration)
        }
    }

    override fun getVolume(): Int = (player.volume * 100).roundToInt()
}
