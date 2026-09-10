package com.infillion.truex.reference.manualcsai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualVastPayloadTest {
    @Test
    fun readsAdParametersJson() {
        val payload = ManualVastPayloadParser.parse(
            vastXml(
                linearAdParameters = """{"user_id":"from-ad-parameters","vast_config_url":"https://get.truex.com/example"}""",
            ),
        )

        assertEquals("from-ad-parameters", payload.getString("user_id"))
        assertEquals("https://get.truex.com/example", payload.getString("vast_config_url"))
    }

    @Test
    fun prefersCompanionOverAdParameters() {
        val payload = ManualVastPayloadParser.parse(
            vastXml(
                linearAdParameters = """{"user_id":"from-ad-parameters"}""",
                companionDataUrl = COMPANION_DATA_URL,
            ),
        )

        assertEquals("from-companion", payload.getString("user_id"))
    }

    @Test
    fun fallsBackToAdParametersWhenCompanionAbsent() {
        val payload = ManualVastPayloadParser.parse(
            vastXml(
                linearAdParameters = """{"user_id":"from-ad-parameters"}""",
                companionDataUrl = null,
            ),
        )

        assertEquals("from-ad-parameters", payload.getString("user_id"))
    }

    @Test
    fun readsWrappedCompanionDataUrl() {
        val payload = ManualVastPayloadParser.parse(
            vastXml(companionDataUrl = WRAPPED_COMPANION_DATA_URL),
        )

        assertEquals("from-companion", payload.getString("user_id"))
    }

    @Test
    fun parsesVastUrlFromFixture() {
        val adBreak = ManualAdBreakParser.parse(
            """
            {
              "breakId": "reference-midroll",
              "timeOffsetMs": 10000,
              "ads": [
                {
                  "id": "truex-interactive",
                  "adSystem": "trueX",
                  "mediaUrl": "https://example.com/placeholder.mp4",
                  "vastUrl": "https://get.truex.com/example/vast/generic",
                  "durationSeconds": 30
                },
                {
                  "id": "airline-linear",
                  "adSystem": "Linear",
                  "mediaUrl": "https://example.com/linear.mp4",
                  "durationSeconds": 30
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("https://get.truex.com/example/vast/generic", adBreak.ads[0].vastUrl)
        assertNull(adBreak.ads[1].vastUrl)
        assertEquals(ManualAdType.TRUEX, adBreak.ads[0].type)
    }

    @Test
    fun appliesUserIdMacroOnLoad() {
        val userId = "ref-app-test-id"
        assertEquals(
            "https://get.truex.com/example/vast/generic?network_user_id=$userId",
            applyVastUserId(
                "https://get.truex.com/example/vast/generic?network_user_id=\${user-id}",
                userId,
            ),
        )
        assertEquals(
            "https://qa-get.truex.com/example/vast/idvx/generic?network_user_id=$userId",
            applyVastUserId(
                "https://qa-get.truex.com/example/vast/idvx/generic?network_user_id=#{user-id}",
                userId,
            ),
        )
    }

    @Test
    fun referenceUserIdHasPrefix() {
        val userId = newReferenceUserId()
        assertTrue(userId.startsWith("ref-app-"))
        assertTrue(userId.length > "ref-app-".length)
    }

    private fun vastXml(
        linearAdParameters: String? = null,
        companionDataUrl: String? = null,
    ): String {
        val companionCreative = if (companionDataUrl == null) {
            ""
        } else {
            """
            <Creative id="super_tag">
              <CompanionAds required="all">
                <Companion id="super_tag" width="960" height="540" apiFramework="truex">
                  <StaticResource creativeType="application/json">
                    <![CDATA[ $companionDataUrl ]]>
                  </StaticResource>
                </Companion>
              </CompanionAds>
            </Creative>
            """.trimIndent()
        }
        val adParameters = if (linearAdParameters == null) {
            ""
        } else {
            "<AdParameters><![CDATA[$linearAdParameters]]></AdParameters>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <VAST version="4.0">
              <Ad id="super_tag">
                <InLine>
                  <AdSystem>trueX</AdSystem>
                  <Creatives>
                    $companionCreative
                    <Creative id="placeholder_video">
                      <Linear>
                        <Duration>00:00:30</Duration>
                        $adParameters
                        <MediaFiles>
                          <MediaFile delivery="progressive" type="video/mp4" width="1280" height="720">
                            <![CDATA[ https://media.truex.com/m/video/truexloadingplaceholder-30s.mp4 ]]>
                          </MediaFile>
                        </MediaFiles>
                      </Linear>
                    </Creative>
                  </Creatives>
                </InLine>
              </Ad>
            </VAST>
        """.trimIndent()
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
