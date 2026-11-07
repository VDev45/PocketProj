package com.vishwaravi.pocketproj

import com.vishwaravi.pocketproj.data.LinkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the payload filter behind the export gating in REQUIREMENTS §8.
 *
 * Hidden links are the ones the Hidden Locker keeps private, so the default has to be "leave them
 * out". Before this was implemented, `toJson` wrote every link with its `isHidden` flag and no prompt
 * at all, so a plain export carried the user's private links into a file handed to a spreadsheet or
 * a cloud drive.
 */
class ExportableLinksTest {

    private fun link(url: String, hidden: Boolean = false) = LinkEntity(url = url, isHidden = hidden)

    private val library = listOf(
        link("https://a.example"),
        link("https://secret.example", hidden = true),
        link("https://b.example"),
        link("https://private.example", hidden = true)
    )

    @Test
    fun hiddenLinksAreExcludedByDefault() {
        val exported = exportableLinks(library, includeHidden = false)
        assertEquals(listOf("https://a.example", "https://b.example"), exported.map { it.url })
    }

    @Test
    fun noHiddenLinkSurvivesTheDefaultPath() {
        // The property that matters, asserted directly rather than via the expected list, so a
        // future edit that leaks a hidden link fails here even if it changes the fixture order.
        assertTrue(exportableLinks(library, includeHidden = false).none { it.isHidden })
    }

    @Test
    fun hiddenLinksAreIncludedOnceAuthorised() {
        val exported = exportableLinks(library, includeHidden = true)
        assertEquals(library.map { it.url }, exported.map { it.url })
    }

    @Test
    fun includingHiddenLinksStillIncludesVisibleOnes() {
        val exported = exportableLinks(library, includeHidden = true)
        assertEquals(4, exported.size)
        assertEquals(2, exported.count { it.isHidden })
    }

    @Test
    fun orderIsPreserved() {
        // Exports are diffed and re-imported by some users; reordering rows would be gratuitous.
        assertEquals(
            listOf("https://a.example", "https://b.example"),
            exportableLinks(library, includeHidden = false).map { it.url }
        )
    }

    @Test
    fun aLibraryWithNoHiddenLinksIsUnchanged() {
        val visible = listOf(link("https://a.example"), link("https://b.example"))
        assertEquals(visible, exportableLinks(visible, includeHidden = false))
    }

    @Test
    fun anEmptyLibraryStaysEmpty() {
        assertTrue(exportableLinks(emptyList(), includeHidden = false).isEmpty())
        assertTrue(exportableLinks(emptyList(), includeHidden = true).isEmpty())
    }

    @Test
    fun aLibraryOfOnlyHiddenLinksExportsNothingByDefault() {
        val allHidden = listOf(link("https://a.example", hidden = true))
        assertTrue(exportableLinks(allHidden, includeHidden = false).isEmpty())
    }
}
