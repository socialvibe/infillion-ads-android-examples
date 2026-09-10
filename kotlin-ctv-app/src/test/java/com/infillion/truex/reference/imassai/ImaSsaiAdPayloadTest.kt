package com.infillion.truex.reference.imassai

import com.google.ads.interactivemedia.v3.api.CompanionAd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImaSsaiAdPayloadTest {
    @Test
    fun skipTargetIncludesWholeBreakAndSafetyMargin() {
        assertEquals(72_000L, calculateSsaiSkipTargetMs(10.0, 60.0))
    }

    @Test
    fun classifiesInteractiveAdSystems() {
        assertEquals(ImaSsaiAdType.TRUEX, classifyImaSsaiAd("trueX"))
        assertEquals(ImaSsaiAdType.IDVX, classifyImaSsaiAd("IDVx"))
        assertEquals(ImaSsaiAdType.LINEAR, classifyImaSsaiAd("Google"))
    }

    @Test
    fun truexEligibleOnlyFirstInPod() {
        assertTrue(canPlayTruex(1))
        assertFalse(canPlayTruex(2))
        assertFalse(canPlayTruex(0))
    }

    @Test
    fun extractsJsonFromTruexCompanionDataUrl() {
        val companion = TestCompanionAd(
            apiFramework = "truex",
            resourceValue = COMPANION_DATA_URL,
        )
        val payload = extractImaSsaiPayload(listOf(companion), null)
        assertNotNull(payload)
        assertEquals("from-companion", payload?.getString("user_id"))
    }

    @Test
    fun extractsJsonFromWrappedCompanionDataUrl() {
        val companion = TestCompanionAd(
            apiFramework = "truex",
            resourceValue = WRAPPED_COMPANION_DATA_URL,
        )
        val payload = extractImaSsaiPayload(listOf(companion), null)
        assertNotNull(payload)
        assertEquals("from-companion", payload?.getString("user_id"))
    }

    @Test
    fun ignoresNonTruexCompanionAndFallsBackToTraffickingParameters() {
        val companion = TestCompanionAd(
            apiFramework = "other",
            resourceValue = COMPANION_DATA_URL,
        )
        val payload = extractImaSsaiPayload(
            listOf(companion),
            """{"channel":"ctv","ad_id":123}""",
        )
        assertNotNull(payload)
        assertEquals("ctv", payload?.getString("channel"))
        assertEquals(123, payload?.getInt("ad_id"))
    }

    @Test
    fun parsesValidTraffickingParametersJson() {
        val payload = extractImaSsaiPayload(null, """{"channel":"ctv","ad_id":123}""")
        assertNotNull(payload)
        assertEquals("ctv", payload?.getString("channel"))
        assertEquals(123, payload?.getInt("ad_id"))
    }

    @Test
    fun rejectsInvalidOrBlankParametersAndCompanions() {
        assertNull(extractImaSsaiPayload(null, null))
        assertNull(extractImaSsaiPayload(emptyList(), ""))
        assertNull(extractImaSsaiPayload(emptyList(), "   "))
        assertNull(extractImaSsaiPayload(emptyList(), "not valid json"))

        val invalidCompanion = TestCompanionAd(
            apiFramework = "truex",
            resourceValue = "invalid-data-url",
        )
        assertNull(extractImaSsaiPayload(listOf(invalidCompanion), null))
    }

    private class TestCompanionAd(
        private val apiFramework: String,
        private val resourceValue: String,
    ) : CompanionAd {
        override fun getHeight(): Int = 0
        override fun getWidth(): Int = 0
        override fun getApiFramework(): String = apiFramework
        override fun getResourceValue(): String = resourceValue
    }

    private companion object {
        const val COMPANION_DATA_URL =
            "data:application/json;base64,eyJ1c2VyX2lkIjoiZnJvbS1jb21wYW5pb24ifQ=="
        const val WRAPPED_COMPANION_DATA_URL =
            """
            data:application/json;base64,eyJ1c2VyX2lkIjoi
            ZnJvbS1jb21wYW5pb24ifQ==
            """
    }
}
