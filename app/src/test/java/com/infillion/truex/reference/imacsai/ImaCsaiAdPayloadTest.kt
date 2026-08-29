package com.infillion.truex.reference.imacsai

import org.junit.Assert.assertEquals
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
    fun prefersVastUrlFromTraffickingParameters() {
        val payload = extractImaCsaiPayload(
            """{"vast_config_url":"https://get.truex.com/example"}""",
            null,
        )

        assertTrue(payload is ImaCsaiAdPayload.Url)
        assertEquals("https://get.truex.com/example", (payload as ImaCsaiAdPayload.Url).value)
    }

    @Test
    fun rejectsNonHttpDescription() {
        assertNull(extractImaCsaiPayload(null, "Interactive ad"))
    }
}
