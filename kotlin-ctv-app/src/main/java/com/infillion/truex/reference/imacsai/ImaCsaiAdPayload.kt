package com.infillion.truex.reference.imacsai

import org.json.JSONObject

internal enum class ImaCsaiAdType {
    TRUEX,
    IDVX,
    LINEAR,
}

internal sealed interface ImaCsaiAdPayload {
    data class Url(val value: String) : ImaCsaiAdPayload
    data class Parameters(val value: JSONObject) : ImaCsaiAdPayload
}

internal fun classifyImaCsaiAd(adSystem: String?): ImaCsaiAdType = when {
    adSystem?.contains("trueX", ignoreCase = true) == true -> ImaCsaiAdType.TRUEX
    adSystem?.contains("IDVx", ignoreCase = true) == true -> ImaCsaiAdType.IDVX
    else -> ImaCsaiAdType.LINEAR
}

internal fun canPlayTruex(adPosition: Int): Boolean = adPosition == 1

internal fun extractImaCsaiPayload(
    traffickingParameters: String?,
    description: String?,
): ImaCsaiAdPayload? {
    val vastConfigUrl = traffickingParameters
        ?.let(::extractVastConfigUrl)
        ?.let(::normalizeUrl)
    val parameters = traffickingParameters
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching(::JSONObject).getOrNull() }
    val descriptionUrl = normalizeUrl(description)
    return when {
        vastConfigUrl != null -> ImaCsaiAdPayload.Url(vastConfigUrl)
        parameters != null -> ImaCsaiAdPayload.Parameters(parameters)
        descriptionUrl != null -> ImaCsaiAdPayload.Url(descriptionUrl)
        else -> null
    }
}

private fun extractVastConfigUrl(parameters: String): String? =
    VAST_CONFIG_URL.find(parameters)?.groupValues?.get(1)

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
