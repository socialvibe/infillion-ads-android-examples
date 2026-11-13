package com.infillion.truex.reference.imacsai

import com.google.ads.interactivemedia.v3.api.CompanionAd
import org.json.JSONObject
import java.util.Base64
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

internal fun formatCuePointsSummary(cuePointsSeconds: List<Float>?): String {
    if (cuePointsSeconds.isNullOrEmpty()) {
        return "Content • IMA VAST ad"
    }
    val labels = cuePointsSeconds.map { seconds ->
        when {
            seconds == 0f -> "preroll"
            seconds < 0f -> "postroll"
            else -> "${seconds.roundToInt()}s"
        }
    }
    val noun = if (labels.size == 1) "VMAP break" else "VMAP breaks"
    return if (cuePointsSeconds.all { it > 0f }) {
        "Content • $noun at ${labels.joinToString(", ")}"
    } else {
        "Content • $noun: ${labels.joinToString(", ")}"
    }
}

internal fun cuePointMarkerTimesMs(cuePointsSeconds: List<Float>?): LongArray =
    cuePointsSeconds
        ?.filter { it >= 0f }
        ?.map { (it * 1_000f).roundToLong() }
        ?.sorted()
        ?.toLongArray()
        ?: LongArray(0)

/**
 * Extracts the interactive ad JSON payload from either a TrueX companion ad or trafficking parameters.
 *
 * Infillion VAST tags deliver adParameters via one of two formats depending on publisher ad serving setup:
 * 1. Companion tag:
 *    - TrueX: /:placement_hash/vast/companion?<params>
 *    - IDVx:  /:placement_hash/vast/idvx/companion?<params>
 *    Ad parameters are encoded as a base64 JSON data URL in <StaticResource creativeType="application/json">
 *    inside a <Companion apiFramework="truex"> node.
 * 2. Generic tag:
 *    - TrueX: /:placement_hash/vast/generic?<params>
 *    - IDVx:  /:placement_hash/vast/idvx/generic?<params>
 *    Ad parameters are delivered directly in the <Linear><AdParameters> node, which Google IMA exposes
 *    as traffickingParameters.
 *
 * Fallback resolution order:
 * - Check companionAds first for an apiFramework="truex" companion and parse its data URL JSON.
 * - If absent, check traffickingParameters (<AdParameters>) and parse its JSON.
 * - Fail (return null) if neither source provides valid JSON.
 */
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
