package com.infillion.truex.reference.imacsai

import com.google.ads.interactivemedia.v3.api.CompanionAd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImaCsaiAdPayloadTest {
    @Test
    fun classifiesInteractiveAdSystems() {
        assertEquals(ImaCsaiAdType.TRUEX, classifyImaCsaiAd("trueX"))
        assertEquals(ImaCsaiAdType.IDVX, classifyImaCsaiAd("IDVx"))
        assertEquals(ImaCsaiAdType.LINEAR, classifyImaCsaiAd("Google"))
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
        val payload = extractImaCsaiPayload(listOf(companion), null)
        assertNotNull(payload)
        assertEquals("from-companion", payload?.getString("user_id"))
    }

    @Test
    fun extractsJsonFromWrappedCompanionDataUrl() {
        val companion = TestCompanionAd(
            apiFramework = "truex",
            resourceValue = WRAPPED_COMPANION_DATA_URL,
        )
        val payload = extractImaCsaiPayload(listOf(companion), null)
        assertNotNull(payload)
        assertEquals("from-companion", payload?.getString("user_id"))
    }

    @Test
    fun ignoresNonTruexCompanionAndFallsBackToTraffickingParameters() {
        val companion = TestCompanionAd(
            apiFramework = "other",
            resourceValue = COMPANION_DATA_URL,
        )
        val payload = extractImaCsaiPayload(
            listOf(companion),
            """{"channel":"ctv","ad_id":123}""",
        )
        assertNotNull(payload)
        assertEquals("ctv", payload?.getString("channel"))
        assertEquals(123, payload?.getInt("ad_id"))
    }

    @Test
    fun parsesValidTraffickingParametersJson() {
        val payload = extractImaCsaiPayload(null, """{"channel":"ctv","ad_id":123}""")
        assertNotNull(payload)
        assertEquals("ctv", payload?.getString("channel"))
        assertEquals(123, payload?.getInt("ad_id"))
    }

    @Test
    fun rejectsInvalidOrBlankParametersAndCompanions() {
        assertNull(extractImaCsaiPayload(null, null))
        assertNull(extractImaCsaiPayload(emptyList(), ""))
        assertNull(extractImaCsaiPayload(emptyList(), "   "))
        assertNull(extractImaCsaiPayload(emptyList(), "not valid json"))

        val invalidCompanion = TestCompanionAd(
            apiFramework = "truex",
            resourceValue = "invalid-data-url",
        )
        assertNull(extractImaCsaiPayload(listOf(invalidCompanion), null))
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
