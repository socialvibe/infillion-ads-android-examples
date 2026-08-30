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

internal fun extractImaCsaiPayload(
    traffickingParameters: String?,
    description: String?,
): ImaCsaiAdPayload? {
    val vastConfigUrl = traffickingParameters
        ?.let(::extractVastConfigUrl)
        ?.takeIf(::isHttpUrl)
    val parameters = traffickingParameters
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching(::JSONObject).getOrNull() }
    return when {
        vastConfigUrl != null -> ImaCsaiAdPayload.Url(vastConfigUrl)
        parameters != null -> ImaCsaiAdPayload.Parameters(parameters)
        isHttpUrl(description) -> ImaCsaiAdPayload.Url(requireNotNull(description))
        else -> null
    }
}

private fun extractVastConfigUrl(parameters: String): String? =
    VAST_CONFIG_URL.find(parameters)?.groupValues?.get(1)

private fun isHttpUrl(value: String?): Boolean =
    value?.startsWith("https://", ignoreCase = true) == true ||
        value?.startsWith("http://", ignoreCase = true) == true

private val VAST_CONFIG_URL = Regex(""""vast_config_url"\s*:\s*"([^"]+)"""")
