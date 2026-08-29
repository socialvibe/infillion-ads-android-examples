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
}
