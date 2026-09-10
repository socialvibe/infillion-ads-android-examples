package com.infillion.truex.reference.manualcsai

import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URL
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

internal object ManualVastPayloadParser {
    fun load(url: String): JSONObject {
        val connection = URL(url).openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val xml = connection.getInputStream().bufferedReader().use { it.readText() }
        return parse(xml)
    }

    /**
     * Parses the interactive ad JSON payload from either a TrueX companion ad or <AdParameters>.
     *
     * Infillion VAST tags deliver adParameters via one of two formats depending on publisher ad serving setup:
     * 1. Companion tag:
     *    - TrueX: /:placement_hash/vast/companion?<params>
     *    - IDVx:  /:placement_hash/vast/idvx/companion?<params>
     *    Ad parameters are encoded as a base64 JSON data URL in <StaticResource creativeType="application/json">
     *    inside a <Companion apiFramework="truex"> node.
     * 2. Generic tag:
     *    - TrueX: /:placement_hash/vast/generic?<params>
     *    - IDVx:  /:placement_hash/vast/idvx/generic?<params>
     *    Ad parameters are delivered directly in the <Linear><AdParameters> node.
     *
     * Fallback resolution order:
     * - Check for an apiFramework="truex" companion first and parse its data URL JSON.
     * - If absent, check <AdParameters> and parse its JSON.
     * - Fail (throw error) if neither source provides valid JSON.
     */
    fun parse(xml: String): JSONObject {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml.trim())))
        val root = document.documentElement

        val companion = companionJson(root)
        if (companion != null) {
            return companion
        }
        val adParameters = adParametersJson(root)
        if (adParameters != null) {
            return adParameters
        }
        error("VAST has no truex companion JSON or AdParameters")
    }

    private fun companionJson(root: Element): JSONObject? {
        val companions = root.getElementsByTagName("Companion")
        for (index in 0 until companions.length) {
            val companion = companions.item(index) as Element
            if (!companion.getAttribute("apiFramework").equals("truex", ignoreCase = true)) {
                continue
            }
            val resources = companion.getElementsByTagName("StaticResource")
            for (resourceIndex in 0 until resources.length) {
                val resource = resources.item(resourceIndex) as Element
                if (!resource.getAttribute("creativeType").equals("application/json", ignoreCase = true)) {
                    continue
                }
                val raw = resource.textContent
                if (raw.isNotBlank()) {
                    val decoded = runCatching { decodeDataUrl(raw) }.getOrNull()
                    if (decoded != null) {
                        val json = runCatching { JSONObject(decoded) }.getOrNull()
                        if (json != null) {
                            return json
                        }
                    }
                }
            }
        }
        return null
    }

    private fun adParametersJson(root: Element): JSONObject? {
        val nodes = root.getElementsByTagName("AdParameters")
        if (nodes.length == 0) {
            return null
        }
        val text = nodes.item(0).textContent.trim()
        if (text.isBlank()) {
            return null
        }
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun decodeDataUrl(raw: String): String {
        val compact = raw.filterNot { it.isWhitespace() }
        val encoded = compact.substringAfter(',')
        return String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }
}
