package com.infillion.truex.reference.imassai

import org.json.JSONObject

internal enum class ImaSsaiAdType {
    TRUEX,
    IDVX,
    LINEAR,
}

internal fun classifyImaSsaiAd(adSystem: String?): ImaSsaiAdType = when {
    adSystem?.equals("trueX", ignoreCase = true) == true -> ImaSsaiAdType.TRUEX
    adSystem?.equals("IDVx", ignoreCase = true) == true -> ImaSsaiAdType.IDVX
    else -> ImaSsaiAdType.LINEAR
}

internal fun canPlayTruex(adPosition: Int): Boolean = adPosition == 1

internal fun extractImaSsaiPayload(traffickingParameters: String?): JSONObject? {
    if (traffickingParameters.isNullOrBlank()) {
        return null
    }
    return runCatching { JSONObject(traffickingParameters) }.getOrNull()
}

internal fun calculateSsaiSkipTargetMs(timeOffsetSeconds: Double, breakDurationSeconds: Double): Long =
    ((timeOffsetSeconds + breakDurationSeconds + 2.0) * 1_000.0).toLong()
