package com.vishwaravi.pocketproj.data

import java.net.URI

object UrlValidator {
    private const val MAX_URL_LENGTH = 2_048

    fun validate(rawUrl: String): Result<String> {
        val normalized = rawUrl.trim()

        if (normalized.isEmpty()) {
            return Result.failure(IllegalArgumentException("URL cannot be empty"))
        }

        if (normalized.length > MAX_URL_LENGTH) {
            return Result.failure(IllegalArgumentException("URL is too long"))
        }

        val uri = runCatching { URI(normalized) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host

        if (scheme != "http" && scheme != "https") {
            return Result.failure(IllegalArgumentException("URL must use http:// or https://"))
        }

        if (host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("URL must include a valid host"))
        }

        return Result.success(normalized)
    }

    /**
     * Canonical form of a validated URL, used consistently for duplicate detection.
     * Policy: lowercase scheme and host, drop default ports (:80/:443), drop an empty
     * path's trailing slash, drop the fragment; the query string is preserved verbatim.
     * `https://EXAMPLE.com:443/` and `https://example.com#top` both canonicalize to
     * `https://example.com`.
     */
    fun canonicalize(validatedUrl: String): String {
        val uri = runCatching { URI(validatedUrl) }.getOrNull() ?: return validatedUrl
        val scheme = uri.scheme.lowercase()
        val host = uri.host.lowercase()
        val defaultPort = when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
        val port = if (uri.port > 0 && uri.port != defaultPort) ":${uri.port}" else ""
        var path = uri.rawPath.orEmpty()
        if (path == "/") path = ""
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "$scheme://$host$port$path$query"
    }

    /** Validate then canonicalize; returns null when the URL is invalid. */
    fun canonicalizeOrNull(rawUrl: String): String? {
        val validated = validate(rawUrl).getOrNull() ?: return null
        return runCatching { canonicalize(validated) }.getOrNull()
    }
}