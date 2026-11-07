package com.vishwaravi.pocketproj.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class LinkMetadata(
    val title: String?,
    val description: String?,
    val imageUrl: String?
)

/** Injectable metadata source so tests never depend on the network. */
interface MetadataFetcher {
    suspend fun fetch(url: String): LinkMetadata
}

class HttpMetadataFetcher : MetadataFetcher {
    override suspend fun fetch(url: String): LinkMetadata = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PocketProj/1.0")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }

        try {
            val status = runCatching { connection.responseCode }.getOrNull() ?: -1
            if (status !in 200..299) return@withContext LinkMetadata(null, null, null)
            if (connection.contentType?.contains("text/html", ignoreCase = true) != true) {
                return@withContext LinkMetadata(null, null, null)
            }

            // Revalidate the final redirect destination started from a validated URL.
            val finalUrl = runCatching { connection.url.toString() }.getOrNull()
            if (finalUrl == null || UrlValidator.canonicalizeOrNull(finalUrl) == null) {
                return@withContext LinkMetadata(null, null, null)
            }

            val html = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8_192)
                val result = StringBuilder()
                var total = 0
                while (total < MAX_HTML_BYTES) {
                    val read = reader.read(buffer, 0, minOf(buffer.size, MAX_HTML_BYTES - total))
                    if (read <= 0) break
                    result.append(buffer, 0, read)
                    total += read
                }
                result.toString()
            }

            parse(html, finalUrl)
        } catch (_: Exception) {
            LinkMetadata(null, null, null)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_HTML_BYTES = 512 * 1024
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 7_000

        private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
        private val TITLE_TAG = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        /**
         * Loose HTML metadata extraction: parses each `<meta>` tag into an attribute map
         * (tolerating attribute order, quoting style, extra whitespace and casing) and
         * falls back across Open Graph → Twitter Card → classic `<title>`/`description`.
         */
        internal fun parse(html: String, baseUrl: String): LinkMetadata {
            val metas = META_TAG.findAll(html).mapNotNull { tag ->
                val attrs = linkedMapOf<String, String>()
                ATTRIBUTE.findAll(tag.value).forEach { match ->
                    val name = match.groupValues[1].lowercase()
                    val raw = match.groupValues[2]
                        .ifEmpty { match.groupValues[3] }
                        .ifEmpty { match.groupValues[4] }
                    if (attrs[name] == null && raw.isNotBlank()) attrs[name] = raw
                }
                attrs.takeIf { it.isNotEmpty() }
            }.toList()

            fun pick(vararg keys: String): String? {
                for (key in keys) {
                    val hit = metas.firstOrNull { meta ->
                        (meta["property"] ?: meta["name"])?.equals(key, ignoreCase = true) == true
                        && !meta["content"].isNullOrBlank()
                    }
                    if (hit != null) return hit["content"]?.trim()?.takeIf { it.isNotEmpty() }
                }
                return null
            }

            val title = (pick("og:title", "twitter:title", "title") ?: findTitle(html))?.let(::decodeHtmlEntities)
            val description = pick("og:description", "twitter:description", "description")?.let(::decodeHtmlEntities)
            val imageUrl = pick("og:image", "twitter:image")?.let { resolveUrl(baseUrl, it) }
            return LinkMetadata(title, description, imageUrl)
        }

        private fun findTitle(html: String): String? =
            TITLE_TAG.find(html)?.groupValues?.getOrNull(1)
                ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }

        private fun resolveUrl(base: String, value: String): String = runCatching {
            URI(base).resolve(value).toString()
        }.getOrDefault(value)
    }
}