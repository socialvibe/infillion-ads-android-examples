package com.infillion.truex.reference.imassai

import com.google.ads.interactivemedia.v3.api.CompanionAd
import org.json.JSONObject
import java.util.Base64

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

internal fun extractImaSsaiPayload(
    companionAds: List<CompanionAd>?,
    traffickingParameters: String?,
): JSONObject? {
    val companionJson = extractCompanionPayload(companionAds)
    if (companionJson != null) {
        return companionJson
    }
    if (!traffickingParameters.isNullOrBlank()) {
        val parameters = runCatching { JSONObject(traffickingParameters) }.getOrNull()
        if (parameters != null) {
            return parameters
        }
    }
    return null
}

private fun extractCompanionPayload(companionAds: List<CompanionAd>?): JSONObject? {
    if (companionAds == null) {
        return null
    }
    for (companion in companionAds) {
        if (!companion.apiFramework.equals("truex", ignoreCase = true)) {
            continue
        }
        val resource = companion.resourceValue
        if (resource.isBlank()) {
            continue
        }
        val jsonString = if (resource.contains(",")) {
            runCatching { decodeDataUrl(resource) }.getOrNull()
        } else {
            resource
        }
        if (jsonString != null) {
            val json = runCatching { JSONObject(jsonString) }.getOrNull()
            if (json != null) {
                return json
            }
        }
    }
    return null
}

private fun decodeDataUrl(raw: String): String {
    val compact = raw.filterNot { it.isWhitespace() }
    val encoded = compact.substringAfter(',')
    return String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
}

internal fun calculateSsaiSkipTargetMs(timeOffsetSeconds: Double, breakDurationSeconds: Double): Long =
    ((timeOffsetSeconds + breakDurationSeconds + 2.0) * 1_000.0).toLong()
