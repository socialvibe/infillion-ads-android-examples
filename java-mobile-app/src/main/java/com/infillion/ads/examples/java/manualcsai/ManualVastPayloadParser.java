package com.infillion.ads.examples.java.manualcsai;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.xml.parsers.DocumentBuilderFactory;

final class ManualVastPayloadParser {
    private ManualVastPayloadParser() {
    }

    static JSONObject load(String url) throws Exception {
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        try (InputStream input = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder xml = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                xml.append(line).append('\n');
            }
            return parse(xml.toString());
        }
    }

    /**
     * Parses the interactive ad JSON payload from either a TrueX companion ad or &lt;AdParameters&gt;.
     *
     * Infillion VAST tags deliver adParameters via one of two formats depending on publisher ad serving setup:
     * 1. Companion tag:
     *    - TrueX: /:placement_hash/vast/companion?&lt;params&gt;
     *    - IDVx:  /:placement_hash/vast/idvx/companion?&lt;params&gt;
     *    Ad parameters are encoded as a base64 JSON data URL in &lt;StaticResource creativeType="application/json"&gt;
     *    inside a &lt;Companion apiFramework="truex"&gt; node.
     * 2. Generic tag:
     *    - TrueX: /:placement_hash/vast/generic?&lt;params&gt;
     *    - IDVx:  /:placement_hash/vast/idvx/generic?&lt;params&gt;
     *    Ad parameters are delivered directly in the &lt;Linear&gt;&lt;AdParameters&gt; node.
     *
     * Fallback resolution order:
     * - Check for an apiFramework="truex" companion first and parse its data URL JSON.
     * - If absent, check &lt;AdParameters&gt; and parse its JSON.
     * - Fail (throw error) if neither source provides valid JSON.
     */
    static JSONObject parse(String xml) throws Exception {
        Element root = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml.trim())))
                .getDocumentElement();

        JSONObject companion = companionJson(root);
        if (companion != null) {
            return companion;
        }
        JSONObject adParameters = adParametersJson(root);
        if (adParameters != null) {
            return adParameters;
        }
        throw new IllegalStateException("VAST has no truex companion JSON or AdParameters");
    }

    @Nullable
    private static JSONObject companionJson(Element root) {
        NodeList companions = root.getElementsByTagName("Companion");
        for (int index = 0; index < companions.getLength(); index++) {
            Element companion = (Element) companions.item(index);
            if (!companion.getAttribute("apiFramework").equalsIgnoreCase("truex")) {
                continue;
            }
            NodeList resources = companion.getElementsByTagName("StaticResource");
            for (int resourceIndex = 0; resourceIndex < resources.getLength(); resourceIndex++) {
                Element resource = (Element) resources.item(resourceIndex);
                if (!resource.getAttribute("creativeType").equalsIgnoreCase("application/json")) {
                    continue;
                }
                String raw = resource.getTextContent();
                if (raw.trim().isEmpty()) {
                    continue;
                }
                try {
                    return new JSONObject(decodeDataUrl(raw));
                } catch (IllegalArgumentException | JSONException error) {
                    // Not a valid base64 JSON data URL; keep looking.
                }
            }
        }
        return null;
    }

    @Nullable
    private static JSONObject adParametersJson(Element root) {
        NodeList nodes = root.getElementsByTagName("AdParameters");
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(text);
        } catch (JSONException error) {
            return null;
        }
    }

    private static String decodeDataUrl(String raw) {
        String compact = raw.replaceAll("\\s", "");
        String encoded = compact.substring(compact.indexOf(',') + 1);
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
