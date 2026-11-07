package com.vishwaravi.pocketproj.data

private val NUMERIC_ENTITY = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")

/**
 * Decodes common HTML entities, including values that were encoded more than once.
 * Metadata from social sites is frequently double-encoded, so we make a few passes
 * rather than leaving strings such as &quot; visible to the user.
 */
fun decodeHtmlEntities(value: String): String {
    var decoded = value
    repeat(3) {
        val next = decoded
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&nbsp;", " ", ignoreCase = true)
            .let { text ->
                NUMERIC_ENTITY.replace(text) { match ->
                    val token = match.groupValues[1]
                    val codePoint = runCatching {
                        if (token.startsWith("x", ignoreCase = true)) {
                            token.substring(1).toInt(16)
                        } else {
                            token.toInt()
                        }
                    }.getOrNull()
                    codePoint
                        ?.takeIf { it in 0..Character.MAX_CODE_POINT }
                        ?.let { String(Character.toChars(it)) }
                        ?: match.value
                }
            }

        if (next == decoded) return decoded.replace(Regex("\\s+"), " ").trim()
        decoded = next
    }
    return decoded.replace(Regex("\\s+"), " ").trim()
}
