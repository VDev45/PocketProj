package com.vishwaravi.pocketproj.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkMetadataFetcherTest {

    @Test
    fun `parses Open Graph metadata`() {
        val html = """
            <html><head>
                <meta charset="utf-8">
                <meta property="og:title" content="A Great Post">
                <meta property="og:description" content="Some description here">
                <meta property="og:image" content="https://cdn.example.com/img.png">
            </head><body></body></html>
        """.trimIndent()

        val metadata = HttpMetadataFetcher.parse(html, "https://example.com/")

        assertEquals("A Great Post", metadata.title)
        assertEquals("Some description here", metadata.description)
        assertEquals("https://cdn.example.com/img.png", metadata.imageUrl)
    }

    @Test
    fun `tolerates attribute order and single-quoted values`() {
        val html = """
            <meta content='Single quoted title' property='og:title'>
            <meta content='https://cdn.example.com/img.png' property='og:image'>
        """.trimIndent()

        val metadata = HttpMetadataFetcher.parse(html, "https://example.com/")

        assertEquals("Single quoted title", metadata.title)
        assertEquals("https://cdn.example.com/img.png", metadata.imageUrl)
    }

    @Test
    fun `falls back to Twitter Card metadata`() {
        val html = """
            <meta name="twitter:title" content="Tweet Title">
            <meta name="twitter:description" content="Tweet description">
            <meta name="twitter:image" content="https://cdn.example.com/tweet.png">
        """.trimIndent()

        val metadata = HttpMetadataFetcher.parse(html, "https://example.com/")

        assertEquals("Tweet Title", metadata.title)
        assertEquals("Tweet description", metadata.description)
        assertEquals("https://cdn.example.com/tweet.png", metadata.imageUrl)
    }

    @Test
    fun `prefers Open Graph over Twitter and classic title`() {
        val html = """
            <title>Classic Title</title>
            <meta name="twitter:title" content="Twitter Title">
            <meta property="og:title" content="OG Title">
        """.trimIndent()

        assertEquals("OG Title", HttpMetadataFetcher.parse(html, "https://example.com/").title)
    }

    @Test
    fun `falls back to the classic title tag`() {
        val html = "<html><head><title>   Just   a   Title   </title></head></html>"

        assertEquals("Just a Title", HttpMetadataFetcher.parse(html, "https://example.com/").title)
    }

    @Test
    fun `resolves relative image urls against the page url`() {
        val html = """<meta property="og:image" content="/assets/cover.png">"""

        val metadata = HttpMetadataFetcher.parse(html, "https://example.com/post/1")

        assertEquals("https://example.com/assets/cover.png", metadata.imageUrl)
    }

    @Test
    fun `malformed html does not crash and yields nothing usable`() {
        val metadata = HttpMetadataFetcher.parse("<html><head>no meta here at all", "https://example.com/")

        assertNull(metadata.title)
        assertNull(metadata.description)
        assertNull(metadata.imageUrl)
    }

    @Test
    fun `empty content values are ignored`() {
        val html = """
            <meta property="og:title" content="">
            <meta property="og:description" content="   ">
        """.trimIndent()

        val metadata = HttpMetadataFetcher.parse(html, "https://example.com/")

        assertNull(metadata.title)
        assertNull(metadata.description)
    }

    @Test
    fun `decodes nested and numeric html entities`() {
        val html = """
            <meta property="og:description" content="&amp;quot;Eren&amp;#x20;&amp;#x266f;&amp;quot;">
        """.trimIndent()

        val metadata = HttpMetadataFetcher.parse(html, "https://example.com/")

        assertEquals("\"Eren ♯\"", metadata.description)
    }
}