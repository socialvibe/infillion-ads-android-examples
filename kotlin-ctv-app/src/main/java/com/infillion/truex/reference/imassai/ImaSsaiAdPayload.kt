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

internal fun extractImaSsaiPayload(
    traffickingParameters: String?,
    description: String?,
): ImaSsaiAdPayload? {
    val vastConfigUrl = traffickingParameters
        ?.let(::extractVastConfigUrl)
        ?.takeIf(::isHttpUrl)
    val parameters = traffickingParameters
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching(::JSONObject).getOrNull() }
    return when {
        vastConfigUrl != null -> ImaSsaiAdPayload.Url(vastConfigUrl)
        parameters != null -> ImaSsaiAdPayload.Parameters(parameters)
        isHttpUrl(description) -> ImaSsaiAdPayload.Url(requireNotNull(description))
        else -> null
    }
}

private fun extractVastConfigUrl(parameters: String): String? =
    VAST_CONFIG_URL.find(parameters)?.groupValues?.get(1)

internal fun calculateSsaiSkipTargetMs(timeOffsetSeconds: Double, breakDurationSeconds: Double): Long =
    ((timeOffsetSeconds + breakDurationSeconds + 2.0) * 1_000.0).toLong()

private fun isHttpUrl(value: String?): Boolean =
    value?.startsWith("https://", ignoreCase = true) == true ||
        value?.startsWith("http://", ignoreCase = true) == true

private val VAST_CONFIG_URL = Regex(""""vast_config_url"\s*:\s*"([^"]+)"""")
