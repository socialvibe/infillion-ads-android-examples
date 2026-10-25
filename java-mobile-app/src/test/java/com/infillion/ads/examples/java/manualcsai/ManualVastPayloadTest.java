package com.infillion.ads.examples.java.manualcsai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.junit.Test;

public class ManualVastPayloadTest {
    private static final String COMPANION_DATA_URL =
            "data:application/json;base64,eyJ1c2VyX2lkIjoiZnJvbS1jb21wYW5pb24ifQ==";
    private static final String WRAPPED_COMPANION_DATA_URL =
            "\n            data:application/json;base64,eyJ1c2VyX2lkIjoi\n            ZnJvbS1jb21wYW5pb24ifQ==\n            ";

    @Test
    public void readsAdParametersJson() throws Exception {
        JSONObject payload = ManualVastPayloadParser.parse(vastXml(
                "{\"user_id\":\"from-ad-parameters\",\"vast_config_url\":\"https://get.truex.com/example\"}",
                null));

        assertEquals("from-ad-parameters", payload.getString("user_id"));
        assertEquals("https://get.truex.com/example", payload.getString("vast_config_url"));
    }

    @Test
    public void prefersCompanionOverAdParameters() throws Exception {
        JSONObject payload = ManualVastPayloadParser.parse(vastXml(
                "{\"user_id\":\"from-ad-parameters\"}",
                COMPANION_DATA_URL));

        assertEquals("from-companion", payload.getString("user_id"));
    }

    @Test
    public void fallsBackToAdParametersWhenCompanionAbsent() throws Exception {
        JSONObject payload = ManualVastPayloadParser.parse(vastXml(
                "{\"user_id\":\"from-ad-parameters\"}",
                null));

        assertEquals("from-ad-parameters", payload.getString("user_id"));
    }

    @Test
    public void readsWrappedCompanionDataUrl() throws Exception {
        JSONObject payload = ManualVastPayloadParser.parse(vastXml(null, WRAPPED_COMPANION_DATA_URL));

        assertEquals("from-companion", payload.getString("user_id"));
    }

    @Test
    public void parsesVastUrlFromFixture() throws Exception {
        ManualAdBreak adBreak = ManualAdBreak.parse(
                "{\n"
                        + "  \"breakId\": \"reference-midroll\",\n"
                        + "  \"timeOffsetMs\": 10000,\n"
                        + "  \"ads\": [\n"
                        + "    {\n"
                        + "      \"id\": \"truex-interactive\",\n"
                        + "      \"adSystem\": \"trueX\",\n"
                        + "      \"mediaUrl\": \"https://example.com/placeholder.mp4\",\n"
                        + "      \"vastUrl\": \"https://get.truex.com/example/vast/generic\",\n"
                        + "      \"durationSeconds\": 30\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"id\": \"airline-linear\",\n"
                        + "      \"adSystem\": \"Linear\",\n"
                        + "      \"mediaUrl\": \"https://example.com/linear.mp4\",\n"
                        + "      \"durationSeconds\": 30\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}");

        assertEquals("https://get.truex.com/example/vast/generic", adBreak.ads.get(0).vastUrl);
        assertNull(adBreak.ads.get(1).vastUrl);
        assertEquals(ManualAdBreak.AdType.TRUEX, adBreak.ads.get(0).type);
    }

    @Test
    public void appliesUserIdMacroOnLoad() {
        String userId = "ref-app-test-id";
        assertEquals(
                "https://get.truex.com/example/vast/generic?network_user_id=" + userId,
                ManualAdBreak.applyVastUserId(
                        "https://get.truex.com/example/vast/generic?network_user_id=${user-id}",
                        userId));
        assertEquals(
                "https://qa-get.truex.com/example/vast/idvx/generic?network_user_id=" + userId,
                ManualAdBreak.applyVastUserId(
                        "https://qa-get.truex.com/example/vast/idvx/generic?network_user_id=#{user-id}",
                        userId));
    }

    @Test
    public void referenceUserIdHasPrefix() {
        String userId = ManualAdBreak.newReferenceUserId();
        assertTrue(userId.startsWith("ref-app-"));
        assertTrue(userId.length() > "ref-app-".length());
    }

    private static String vastXml(@Nullable String linearAdParameters, @Nullable String companionDataUrl) {
        String companionCreative = "";
        if (companionDataUrl != null) {
            companionCreative =
                    "<Creative id=\"super_tag\">\n"
                            + "  <CompanionAds required=\"all\">\n"
                            + "    <Companion id=\"super_tag\" width=\"960\" height=\"540\" apiFramework=\"truex\">\n"
                            + "      <StaticResource creativeType=\"application/json\">\n"
                            + "        <![CDATA[ " + companionDataUrl + " ]]>\n"
                            + "      </StaticResource>\n"
                            + "    </Companion>\n"
                            + "  </CompanionAds>\n"
                            + "</Creative>";
        }
        String adParameters = "";
        if (linearAdParameters != null) {
            adParameters = "<AdParameters><![CDATA[" + linearAdParameters + "]]></AdParameters>";
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<VAST version=\"4.0\">\n"
                + "  <Ad id=\"super_tag\">\n"
                + "    <InLine>\n"
                + "      <AdSystem>trueX</AdSystem>\n"
                + "      <Creatives>\n"
                + "        " + companionCreative + "\n"
                + "        <Creative id=\"placeholder_video\">\n"
                + "          <Linear>\n"
                + "            <Duration>00:00:30</Duration>\n"
                + "            " + adParameters + "\n"
                + "            <MediaFiles>\n"
                + "              <MediaFile delivery=\"progressive\" type=\"video/mp4\" width=\"1280\" height=\"720\">\n"
                + "                <![CDATA[ https://media.truex.com/m/video/truexloadingplaceholder-30s.mp4 ]]>\n"
                + "              </MediaFile>\n"
                + "            </MediaFiles>\n"
                + "          </Linear>\n"
                + "        </Creative>\n"
                + "      </Creatives>\n"
                + "    </InLine>\n"
                + "  </Ad>\n"
                + "</VAST>";
    }
}
