package com.infillion.truex.reference.imassai

import org.json.JSONObject

internal enum class ImaSsaiAdType {
    TRUEX,
    IDVX,
    LINEAR,
}

internal sealed interface ImaSsaiAdPayload {
    data class Url(val value: String) : ImaSsaiAdPayload
    data class Parameters(val value: JSONObject) : ImaSsaiAdPayload
}

internal fun classifyImaSsaiAd(adSystem: String?): ImaSsaiAdType = when {
    adSystem?.equals("trueX", ignoreCase = true) == true -> ImaSsaiAdType.TRUEX
    adSystem?.equals("IDVx", ignoreCase = true) == true -> ImaSsaiAdType.IDVX
    else -> ImaSsaiAdType.LINEAR
}

internal fun canPlayTruex(adPosition: Int): Boolean = adPosition == 1

internal fun extractImaSsaiPayload(
    traffickingParameters: String?,
    description: String?,
): ImaSsaiAdPayload? {
    val vastConfigUrl = traffickingParameters
        ?.let(::extractVastConfigUrl)
        ?.let(::normalizeUrl)
    val parameters = traffickingParameters
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching(::JSONObject).getOrNull() }
    val descriptionUrl = normalizeUrl(description)
    return when {
        vastConfigUrl != null -> ImaSsaiAdPayload.Url(vastConfigUrl)
        parameters != null -> ImaSsaiAdPayload.Parameters(parameters)
        descriptionUrl != null -> ImaSsaiAdPayload.Url(descriptionUrl)
        else -> null
    }
}

private fun extractVastConfigUrl(parameters: String): String? =
    VAST_CONFIG_URL.find(parameters)?.groupValues?.get(1)

internal fun calculateSsaiSkipTargetMs(timeOffsetSeconds: Double, breakDurationSeconds: Double): Long =
    ((timeOffsetSeconds + breakDurationSeconds + 2.0) * 1_000.0).toLong()

internal fun normalizeUrl(value: String?): String? {
    val trimmed = value?.trim() ?: return null
    return when {
        trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("get.truex.com", ignoreCase = true) ||
            trimmed.startsWith("qa-get.truex.com", ignoreCase = true) -> "https://$trimmed"
        else -> null
    }
}

private val VAST_CONFIG_URL = Regex(""""vast_config_url"\s*:\s*"([^"]+)"""")
