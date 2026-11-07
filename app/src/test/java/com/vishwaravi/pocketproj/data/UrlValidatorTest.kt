package com.vishwaravi.pocketproj.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    @Test
    fun `accepts valid http and https URLs`() {
        assertTrue(UrlValidator.validate("http://example.com").isSuccess)
        assertTrue(UrlValidator.validate("https://example.com/path?q=1").isSuccess)
    }

    @Test
    fun `trims surrounding whitespace`() {
        val result = UrlValidator.validate("  https://example.com  ")

        assertEquals("https://example.com", result.getOrNull())
    }

    @Test
    fun `rejects blank URL`() {
        val result = UrlValidator.validate("   ")

        assertFalse(result.isSuccess)
    }

    @Test
    fun `rejects unsupported schemes`() {
        assertFalse(UrlValidator.validate("javascript:alert(1)").isSuccess)
        assertFalse(UrlValidator.validate("ftp://example.com").isSuccess)
    }

    @Test
    fun `rejects URL without host`() {
        assertFalse(UrlValidator.validate("https://").isSuccess)
        assertFalse(UrlValidator.validate("https:///path").isSuccess)
    }

    // --- Canonicalization policy (duplicate detection) ---

    @Test
    fun `canonicalize lowercases scheme and host`() {
        assertEquals("https://example.com", UrlValidator.canonicalize("HTTPS://EXAMPLE.com"))
        assertEquals("http://example.com", UrlValidator.canonicalize("HTTP://Example.COM"))
    }

    @Test
    fun `canonicalize drops default ports`() {
        assertEquals("https://example.com", UrlValidator.canonicalize("https://example.com:443"))
        assertEquals("http://example.com", UrlValidator.canonicalize("http://example.com:80"))
    }

    @Test
    fun `canonicalize keeps non-default ports`() {
        assertEquals("http://example.com:8080/path", UrlValidator.canonicalize("http://example.com:8080/path"))
    }

    @Test
    fun `canonicalize strips empty-path trailing slash but keeps real paths`() {
        assertEquals("https://example.com", UrlValidator.canonicalize("https://example.com/"))
        assertEquals("https://example.com/path/", UrlValidator.canonicalize("https://example.com/path/"))
    }

    @Test
    fun `canonicalize drops fragments and preserves the query`() {
        assertEquals("https://example.com/path?q=1", UrlValidator.canonicalize("https://example.com/path?q=1#section"))
        assertEquals("https://example.com/path?q=1", UrlValidator.canonicalize("https://example.com/path?q=1"))
    }

    @Test
    fun `canonicalizeOrNull returns null for invalid input`() {
        assertNull(UrlValidator.canonicalizeOrNull(""))
        assertNull(UrlValidator.canonicalizeOrNull("ftp://example.com"))
        assertNull(UrlValidator.canonicalizeOrNull("not a url"))
    }

    @Test
    fun `equivalent spellings canonicalize identically`() {
        val variants = listOf(
            "https://example.com",
            "https://example.com/",
            "HTTPS://EXAMPLE.COM",
            "https://EXAMPLE.com:443",
            "https://example.com/#frag"
        )
        val canonical = variants.map { UrlValidator.canonicalize(it) }
        assertEquals(1, canonical.distinct().size)
        assertEquals("https://example.com", canonical.first())
    }
}