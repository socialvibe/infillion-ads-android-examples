package com.infillion.truex.reference.manualcsai

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import com.infillion.truex.reference.BuildConfig
import com.truex.adrenderer.IEventEmitter
import com.truex.adrenderer.TruexAdEvent
import com.truex.adrenderer.TruexAdOptions
import com.truex.adrenderer.TruexAdRenderer
import java.util.UUID

internal class ManualTruexRenderer(
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

    private val eventHandler = IEventEmitter.IEventHandler { event, data ->
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
            -> {
                if (!terminalDelivered) {
                    terminalDelivered = true
                    listener.onTerminal(receivedCredit, event)
                }
            }
            else -> Unit
        }
    }

    init {
        renderer.addEventListener(null, eventHandler)
    }

    fun start(container: ViewGroup, ad: ManualAd) {
        val configUrl = requireNotNull(ad.configUrl) { "Interactive ad ${ad.id} has no config URL" }
        val options = TruexAdOptions().apply {
            supportsUserCancelStream = ad.type == ManualAdType.TRUEX
            fallbackAdvertisingId = UUID.randomUUID().toString()
            enableWebViewDebugging = BuildConfig.DEBUG
        }
        renderer.init(configUrl, options)
        renderer.start(container)
    }

    fun resume() = renderer.resume()

    fun pause() = renderer.pause()

    fun destroy() {
        renderer.removeEventListener(null, eventHandler)
        renderer.stop()
    }
}

