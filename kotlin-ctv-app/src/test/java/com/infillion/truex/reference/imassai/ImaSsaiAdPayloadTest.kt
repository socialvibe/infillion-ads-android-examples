package com.infillion.truex.reference.imassai

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
    fun parsesValidTraffickingParametersJson() {
        val payload = extractImaSsaiPayload("""{"channel":"ctv","ad_id":123}""")
        assertNotNull(payload)
        assertEquals("ctv", payload?.getString("channel"))
        assertEquals(123, payload?.getInt("ad_id"))
    }

    @Test
    fun rejectsInvalidOrBlankTraffickingParameters() {
        assertNull(extractImaSsaiPayload(null))
        assertNull(extractImaSsaiPayload(""))
        assertNull(extractImaSsaiPayload("   "))
        assertNull(extractImaSsaiPayload("not valid json"))
    }
}
