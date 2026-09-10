package com.infillion.truex.reference.imacsai

import com.google.ads.interactivemedia.v3.api.CompanionAd
import org.json.JSONObject
import java.util.Base64

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

internal fun extractImaCsaiPayload(
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
