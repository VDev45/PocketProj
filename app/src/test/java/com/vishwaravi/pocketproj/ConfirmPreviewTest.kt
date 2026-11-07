package com.vishwaravi.pocketproj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the text quoted back in destructive confirmations (GitHub issue #20).
 *
 * A confirmation quotes the name of the thing being deleted so the user can check they picked the
 * right one. Interpolated raw, a link saved from a page with a 400-character title made the dialog
 * taller than the screen and pushed the Delete button off it — the whole point of a confirmation is
 * that Delete stays reachable.
 */
class ConfirmPreviewTest {

    @Test
    fun shortTextIsLeftAlone() {
        assertEquals("My article", confirmPreview("My article"))
    }

    @Test
    fun textAtTheLimitIsLeftAlone() {
        val exact = "a".repeat(96)
        assertEquals(exact, confirmPreview(exact))
        assertEquals(96, confirmPreview(exact).length)
    }

    @Test
    fun longTextIsTruncatedWithAnEllipsis() {
        val preview = confirmPreview("a".repeat(400))
        assertEquals(96, preview.length)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun theResultNeverExceedsTheLimit() {
        for (length in 0..400) {
            val preview = confirmPreview("a".repeat(length))
            assertTrue("length $length produced ${preview.length}", preview.length <= 96)
        }
    }

    @Test
    fun theTruncationIsAppliedAfterCollapsingWhitespace() {
        // A title made of newlines should not spend the budget on whitespace that renders as nothing.
        val preview = confirmPreview("Deep    space\n\n\nlink   title " + "!".repeat(80))
        assertEquals(96, preview.length)
        assertTrue("collapsed whitespace should be gone: '$preview'", !preview.contains("  "))
        assertTrue(!preview.contains("\n"))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("Trimmed", confirmPreview("   Trimmed   "))
    }

    @Test
    fun blankInputStaysBlank() {
        assertEquals("", confirmPreview(""))
        assertEquals("", confirmPreview("     "))
        assertEquals("", confirmPreview("\n\t "))
    }

    @Test
    fun truncationDoesNotLeaveATrailingSpaceBeforeTheEllipsis() {
        val preview = confirmPreview("word ".repeat(40))
        assertTrue("trailing space before ellipsis: '$preview'", !preview.contains(" …"))
    }

    @Test
    fun aCustomLimitIsHonoured() {
        val preview = confirmPreview("a".repeat(200), maxChars = 20)
        assertEquals(20, preview.length)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun aVeryLongUrlIsAlsoBounded() {
        // The body falls back to the URL when a link has no title, and URLs can be long too.
        val preview = confirmPreview("https://example.com/" + "segment/".repeat(60))
        assertEquals(96, preview.length)
    }

    @Test
    fun aSingleCharacterIsNotMangledByTheEllipsis() {
        // maxChars - 1 is taken, then an ellipsis appended: with maxChars = 1 that leaves an empty
        // string plus the ellipsis, which is acceptable but must not throw or index oddly.
        assertEquals("…", confirmPreview("abcdef", maxChars = 1))
    }

    @Test
    fun truncationNeverSplitsAnEmoji() {
        // Cutting a surrogate pair in half leaves a lone surrogate that renders as U+FFFD, so the
        // result is allowed to be one char shorter than the limit rather than a broken glyph.
        val emojiTitle = "\uD83D\uDE00".repeat(60) // 😀
        for (limit in 1..12) {
            val preview = confirmPreview(emojiTitle, maxChars = limit)
            assertWellFormed("limit $limit: $preview", preview)
            assertTrue("limit $limit produced ${preview.length} units", preview.length <= limit)
        }
    }

    @Test
    fun aTruncationPointInsideAnEmojiShortensRatherThanBreaks() {
        // 95 units of budget lands on the high half of the 48th emoji, so the guard drops it and
        // the preview lands one unit under the limit instead of ending in a broken glyph.
        val preview = confirmPreview("\uD83D\uDE00".repeat(60))
        assertTrue(preview.endsWith("…"))
        assertWellFormed(preview, preview)
        assertEquals(95, preview.length)
    }

    @Test
    fun aTruncationPointOnAPairBoundaryKeepsTheWholeLimit() {
        // The ellipsis takes one unit, so a 97-unit budget leaves 96 for the text — an even number
        // of surrogate halves. The cut lands between pairs, so the guard has nothing to drop and
        // the full budget is usable.
        val preview = confirmPreview("\uD83D\uDE00".repeat(60), maxChars = 97)
        assertWellFormed(preview, preview)
        assertEquals(97, preview.length)
        assertTrue(preview.endsWith("…"))
    }

    /** Fails if any surrogate in [s] is unpaired, which is what renders as a U+FFFD replacement. */
    private fun assertWellFormed(label: String, s: String) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate()) {
                assertTrue("$label: high surrogate at $i has no low surrogate after it", i + 1 < s.length && s[i + 1].isLowSurrogate())
                i += 2
            } else {
                assertTrue("$label: stray low surrogate at $i", !c.isLowSurrogate())
                i += 1
            }
        }
    }
}
