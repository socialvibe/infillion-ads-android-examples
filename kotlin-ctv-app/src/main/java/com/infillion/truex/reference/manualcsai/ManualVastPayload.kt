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

    fun parse(xml: String): JSONObject {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml.trim())))
        val root = document.documentElement
        adParametersJson(root)?.let { return it }
        return companionJson(root)
    }

    private fun adParametersJson(root: Element): JSONObject? {
        val nodes = root.getElementsByTagName("AdParameters")
        if (nodes.length == 0) return null
        val text = nodes.item(0).textContent.trim()
        return JSONObject(text)
    }

    private fun companionJson(root: Element): JSONObject {
        val companions = root.getElementsByTagName("Companion")
        for (index in 0 until companions.length) {
            val companion = companions.item(index) as Element
            if (!companion.getAttribute("apiFramework").equals("truex", ignoreCase = true)) continue
            val resources = companion.getElementsByTagName("StaticResource")
            for (resourceIndex in 0 until resources.length) {
                val resource = resources.item(resourceIndex) as Element
                if (!resource.getAttribute("creativeType").equals("application/json", ignoreCase = true)) continue
                return JSONObject(decodeDataUrl(resource.textContent))
            }
        }
        error("VAST has no AdParameters or truex companion JSON")
    }

    private fun decodeDataUrl(raw: String): String {
        val compact = raw.filterNot { it.isWhitespace() }
        val encoded = compact.substringAfter(',')
        return String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }
}
