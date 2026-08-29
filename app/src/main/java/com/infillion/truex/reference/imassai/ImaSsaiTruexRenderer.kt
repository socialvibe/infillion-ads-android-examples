package com.infillion.truex.reference.imassai

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import com.infillion.truex.reference.BuildConfig
import com.truex.adrenderer.IEventEmitter
import com.truex.adrenderer.TruexAdEvent
import com.truex.adrenderer.TruexAdOptions
import com.truex.adrenderer.TruexAdRenderer

internal class ImaSsaiTruexRenderer(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onTerminal(receivedCredit: Boolean, event: TruexAdEvent)
        fun onPopup(uri: Uri)
        fun onCancelStream()
        fun onEvent(event: TruexAdEvent)
    }

    private val renderer = TruexAdRenderer(context)
    private var receivedCredit = false
    private var terminalDelivered = false
    private val handler = IEventEmitter.IEventHandler { event, data ->
        listener.onEvent(event)
        when (event) {
            TruexAdEvent.AD_FREE_POD -> receivedCredit = true
            TruexAdEvent.POPUP_WEBSITE -> {
                val uri = (data["url"] as? String)?.let(Uri::parse)
                if (uri?.scheme == "https" || uri?.scheme == "http") listener.onPopup(uri)
            }
            TruexAdEvent.USER_CANCEL_STREAM -> listener.onCancelStream()
            TruexAdEvent.AD_COMPLETED,
            TruexAdEvent.AD_ERROR,
            TruexAdEvent.NO_ADS_AVAILABLE,
            -> if (!terminalDelivered) {
                terminalDelivered = true
                listener.onTerminal(receivedCredit, event)
            }
            else -> Unit
        }
    }

    init {
        renderer.addEventListener(null, handler)
    }

    fun start(container: ViewGroup, payload: ImaSsaiAdPayload, type: ImaSsaiAdType) {
        val options = TruexAdOptions().apply {
            supportsUserCancelStream = type == ImaSsaiAdType.TRUEX
            enableWebViewDebugging = BuildConfig.DEBUG
        }
        when (payload) {
            is ImaSsaiAdPayload.Url -> renderer.init(payload.value, options)
            is ImaSsaiAdPayload.Parameters -> renderer.init(payload.value, options)
        }
        renderer.start(container)
    }

    fun resume() = renderer.resume()
    fun pause() = renderer.pause()

    fun destroy() {
        renderer.removeEventListener(null, handler)
        renderer.stop()
    }
}

