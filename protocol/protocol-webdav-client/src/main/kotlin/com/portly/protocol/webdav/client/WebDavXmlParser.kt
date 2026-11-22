package com.portly.protocol.webdav.client

import com.portly.core.common.Formatters
import com.portly.core.common.PathValidator
import com.portly.core.model.RemoteFile
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

internal object WebDavXmlParser {

    fun parseMultiStatus(xml: String, requestPath: String, excludeSelf: Boolean): List<RemoteFile> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val responses = doc.getElementsByTagNameNS("*", "response")
        val normalizedReq = PathValidator.normalizeRemotePath(requestPath).trimEnd('/').ifEmpty { "/" }
        val result = mutableListOf<RemoteFile>()

        for (i in 0 until responses.length) {
            val respEl = responses.item(i) as? Element ?: continue
            val hrefRaw = firstElementText(respEl, "href")?.trim() ?: continue
            val decodedHref = decodeHrefPath(hrefRaw)
            val normalizedHref = PathValidator.normalizeRemotePath(decodedHref).trimEnd('/').ifEmpty { "/" }

            if (excludeSelf && normalizedHref == normalizedReq) continue

            val propEl = findFirstSuccessfulProp(respEl) ?: respEl
            val resourceTypeEl = firstElement(propEl, "resourcetype")
            val isDir = resourceTypeEl != null && firstElement(resourceTypeEl, "collection") != null
            val contentLength = firstElementText(propEl, "getcontentlength")?.toLongOrNull() ?: 0L
            val lastModRaw = firstElementText(propEl, "getlastmodified")
            val lastModMillis = parseDateMillis(lastModRaw)
            val contentType = firstElementText(propEl, "getcontenttype")
            val displayName = firstElementText(propEl, "displayname")?.takeIf { it.isNotBlank() }
                ?: normalizedHref.substringAfterLast('/').ifEmpty { "/" }

            result += RemoteFile(
                path = normalizedHref,
                name = displayName,
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else contentLength,
                lastModifiedMillis = lastModMillis,
                mimeType = contentType?.takeIf { !isDir && it.isNotBlank() }
                    ?: Formatters.guessMimeType(displayName, isDir),
            )
        }
        return result
    }

    private fun decodeHrefPath(href: String): String {
        val rawPath = if (href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true)) {
            runCatching { URI(href).rawPath }.getOrDefault(href)
        } else {
            href
        }
        return runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)
    }

    private fun findFirstSuccessfulProp(respEl: Element): Element? {
        val propstats = respEl.getElementsByTagNameNS("*", "propstat")
        for (i in 0 until propstats.length) {
            val ps = propstats.item(i) as? Element ?: continue
            val status = firstElementText(ps, "status").orEmpty()
            if (status.isEmpty() || status.contains("200")) {
                return firstElement(ps, "prop")
            }
        }
        return firstElement(respEl, "prop")
    }

    private fun firstElement(parent: Element, localName: String): Element? {
        val list = parent.getElementsByTagNameNS("*", localName)
        for (i in 0 until list.length) {
            val node = list.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) return node as Element
        }
        return null
    }

    private fun firstElementText(parent: Element, localName: String): String? =
        firstElement(parent, localName)?.textContent

    private fun parseDateMillis(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return runCatching { ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(raw.trim()).toEpochMilli() }
            .getOrDefault(0L)
    }
}
