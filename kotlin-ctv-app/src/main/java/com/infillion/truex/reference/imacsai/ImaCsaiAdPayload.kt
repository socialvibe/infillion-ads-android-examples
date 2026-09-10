package com.infillion.truex.reference.imacsai

import org.json.JSONObject

internal enum class ImaCsaiAdType {
    TRUEX,
    IDVX,
    LINEAR,
}

internal fun classifyImaCsaiAd(adSystem: String?): ImaCsaiAdType = when {
    adSystem?.contains("trueX", ignoreCase = true) == true -> ImaCsaiAdType.TRUEX
    adSystem?.contains("IDVx", ignoreCase = true) == true -> ImaCsaiAdType.IDVX
    else -> ImaCsaiAdType.LINEAR
}

internal fun canPlayTruex(adPosition: Int): Boolean = adPosition == 1

internal fun extractImaCsaiPayload(traffickingParameters: String?): JSONObject? {
    if (traffickingParameters.isNullOrBlank()) {
        return null
    }
    return runCatching { JSONObject(traffickingParameters) }.getOrNull()
}
