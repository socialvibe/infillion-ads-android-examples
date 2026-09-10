package com.infillion.truex.reference.imacsai

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
    fun parsesValidTraffickingParametersJson() {
        val payload = extractImaCsaiPayload("""{"channel":"ctv","ad_id":123}""")
        assertNotNull(payload)
        assertEquals("ctv", payload?.getString("channel"))
        assertEquals(123, payload?.getInt("ad_id"))
    }

    @Test
    fun rejectsInvalidOrBlankTraffickingParameters() {
        assertNull(extractImaCsaiPayload(null))
        assertNull(extractImaCsaiPayload(""))
        assertNull(extractImaCsaiPayload("   "))
        assertNull(extractImaCsaiPayload("not valid json"))
    }
}
