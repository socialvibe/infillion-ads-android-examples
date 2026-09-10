package com.infillion.truex.reference.imassai

import org.junit.Assert.assertEquals
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
        org.junit.Assert.assertTrue(canPlayTruex(1))
        org.junit.Assert.assertFalse(canPlayTruex(2))
        org.junit.Assert.assertFalse(canPlayTruex(0))
    }

    @Test
    fun normalizesDescriptionWithoutScheme() {
        val payload = extractImaSsaiPayload(null, "get.truex.com/example/config")
        org.junit.Assert.assertTrue(payload is ImaSsaiAdPayload.Url)
        assertEquals("https://get.truex.com/example/config", (payload as ImaSsaiAdPayload.Url).value)
    }
}
