package com.infillion.truex.reference.manualcsai

import android.content.Context
import androidx.annotation.RawRes
import org.json.JSONObject
import java.util.UUID

internal enum class ManualAdType {
    TRUEX,
    IDVX,
    LINEAR,
}

internal data class ManualAd(
    val id: String,
    val type: ManualAdType,
    val mediaUrl: String,
    val vastUrl: String?,
    val durationMs: Long,
    val adParameters: JSONObject? = null,
)

internal data class ManualAdBreak(
    val id: String,
    val timeOffsetMs: Long,
    val ads: List<ManualAd>,
)

internal object ManualAdBreakParser {
    fun parse(context: Context, @RawRes resourceId: Int): ManualAdBreak {
        val json = context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        return parse(json)
    }

    fun parse(json: String): ManualAdBreak {
        val root = JSONObject(json)
        val adsJson = root.getJSONArray("ads")
        val ads = buildList {
            repeat(adsJson.length()) { index ->
                val item = adsJson.getJSONObject(index)
                val system = item.getString("adSystem")
                add(
                    ManualAd(
                        id = item.getString("id"),
                        type = when {
                            system.equals("trueX", ignoreCase = true) -> ManualAdType.TRUEX
                            system.equals("IDVx", ignoreCase = true) -> ManualAdType.IDVX
                            else -> ManualAdType.LINEAR
                        },
                        mediaUrl = item.getString("mediaUrl"),
                        vastUrl = item.optString("vastUrl").takeIf(String::isNotBlank),
                        durationMs = item.getLong("durationSeconds") * 1_000L,
                    ),
                )
            }
        }
        require(ads.isNotEmpty()) { "The manual ad response contains no ads" }
        return ManualAdBreak(
            id = root.getString("breakId"),
            timeOffsetMs = root.getLong("timeOffsetMs"),
            ads = ads,
        )
    }
}

internal class MidrollGate(private val timeOffsetMs: Long) {
    var triggered: Boolean = false
        private set

    fun shouldTrigger(positionMs: Long): Boolean {
        if (triggered || positionMs < timeOffsetMs) {
            return false
        }
        triggered = true
        return true
    }
}

internal fun shouldSkipRemainingPod(type: ManualAdType, shouldSkipPod: Boolean): Boolean =
    type == ManualAdType.TRUEX && shouldSkipPod

internal fun newReferenceUserId(): String = "ref-app-${UUID.randomUUID()}"

internal fun applyVastUserId(url: String, userId: String): String =
    url.replace("\${user-id}", userId).replace("#{user-id}", userId)
