package com.vishwaravi.pocketproj

import com.vishwaravi.pocketproj.data.LinkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the library's collection filter.
 *
 * This used to fail *open*: a selected name that matched no collection fell through to the whole
 * library. The reachable case is a collection deleted or renamed while it was the selected one —
 * `linksForLibraryCollection` is called with the selection and the collection list independently, and
 * the list is the one that loses the row first.
 */
class LibraryCollectionFilterTest {

    private fun item(id: Long, name: String) =
        CollectionManagementItem(id = id, name = name, linkCount = 0, isInbox = name == "Inbox")

    private fun link(url: String, collectionId: Long, favorite: Boolean = false, hidden: Boolean = false) =
        LinkEntity(url = url, collectionId = collectionId, isFavorite = favorite, isHidden = hidden)

    private val inbox = item(1L, "Inbox")
    private val work = item(2L, "Work")
    private val reading = item(3L, "Reading")

    private val library = listOf(
        link("https://inbox.example", 1L),
        link("https://work-a.example", 2L),
        link("https://work-b.example", 2L),
        link("https://reading.example", 3L)
    )

    private val items = listOf(inbox, work, reading)

    @Test
    fun selectsOnlyTheNamedCollection() {
        assertEquals(
            listOf("https://work-a.example", "https://work-b.example"),
            linksForLibraryCollection(library, items, "Work").map { it.url }
        )
    }

    @Test
    fun collectionNamesMatchCaseInsensitively() {
        // The app looks names up with equals(ignoreCase = true) everywhere else, and the unique
        // index is COLLATE NOCASE, so a differently-cased selection has to resolve.
        assertEquals(2, linksForLibraryCollection(library, items, "wOrK").size)
        assertEquals(2, linksForLibraryCollection(library, items, "WORK").size)
    }

    @Test
    fun anUnresolvableSelectionShowsNothing() {
        // The regression. "Work" was deleted, so it is no longer in the item list while the
        // selection still names it. Previously this returned every link in the library.
        val withoutWork = listOf(inbox, reading)
        assertTrue(linksForLibraryCollection(library, withoutWork, "Work").isEmpty())
    }

    @Test
    fun anUnresolvableSelectionNeverLeaksLinksFromOtherCollections() {
        val withoutWork = listOf(inbox, reading)
        val shown = linksForLibraryCollection(library, withoutWork, "Work")
        assertTrue("failed open: $shown", shown.none { it.collectionId == 2L })
        assertTrue("leaked other collections: $shown", shown.size < library.size)
    }

    @Test
    fun anEmptyCollectionListShowsNothing() {
        // The initial value of collectionManagementItems is an empty list, so this is the state
        // during the first frame after subscribing, before the database has emitted.
        assertTrue(linksForLibraryCollection(library, emptyList(), "Inbox").isEmpty())
    }

    @Test
    fun favoritesSelectsByFlagAcrossEveryCollection() {
        val withFavorites = library + listOf(
            link("https://fav-work.example", 2L, favorite = true),
            link("https://fav-inbox.example", 1L, favorite = true)
        )
        assertEquals(
            listOf("https://fav-work.example", "https://fav-inbox.example"),
            linksForLibraryCollection(withFavorites, items, "Favorites").map { it.url }
        )
    }

    @Test
    fun favoritesIgnoresCollectionItemsEntirely() {
        // "Favorites" is a pseudo-collection that exists only in the menu, never in the database,
        // so it must not depend on resolving a row the way a named collection does.
        val withFavorite = listOf(
            link("https://inbox.example", 1L),
            link("https://work-a.example", 2L, favorite = true)
        )
        assertEquals(
            listOf("https://work-a.example"),
            linksForLibraryCollection(withFavorite, emptyList(), "Favorites").map { it.url }
        )
    }

    @Test
    fun hiddenLinksAreExcludedFromEveryPath() {
        val withHidden = library + listOf(
            link("https://work-secret.example", 2L, hidden = true),
            link("https://fav-secret.example", 3L, favorite = true, hidden = true)
        )
        assertTrue(linksForLibraryCollection(withHidden, items, "Work").none { it.isHidden })
        assertTrue(linksForLibraryCollection(withHidden, items, "Favorites").none { it.isHidden })
        assertTrue(linksForLibraryCollection(withHidden, emptyList(), "Work").none { it.isHidden })
    }

    @Test
    fun inboxResolvesToTheInboxRow() {
        assertEquals(
            listOf("https://inbox.example"),
            linksForLibraryCollection(library, items, "Inbox").map { it.url }
        )
    }

    @Test
    fun anEmptyLibraryStaysEmpty() {
        assertTrue(linksForLibraryCollection(emptyList(), items, "Work").isEmpty())
        assertTrue(linksForLibraryCollection(emptyList(), items, "Favorites").isEmpty())
        assertTrue(linksForLibraryCollection(emptyList(), items, "Gone").isEmpty())
    }

    /* ── The All view ───────────────────────────────────────────────────────── */

    @Test
    fun allLinksSpansEveryCollection() {
        // All is the absence of a collection, not a collection called "All", so it is answered by
        // its own function rather than by naming it in linksForLibraryCollection.
        assertEquals(
            listOf(
                "https://inbox.example",
                "https://work-a.example",
                "https://work-b.example",
                "https://reading.example"
            ),
            linksForAllCollections(library).map { it.url }
        )
    }

    @Test
    fun allLinksExcludesHiddenLinks() {
        // The one property that has to hold. The All view spans the whole library, so it is the
        // widest possible place for a hidden link to surface: a link hidden by the Hidden Locker,
        // or hidden by carrying a configured hidden tag, which syncHiddenTags reflects into
        // isHidden. Neither rule is consulted here, which is the point -- filtering on isHidden
        // covers both.
        val withHidden = library + listOf(
            link("https://work-secret.example", 2L, hidden = true),
            link("https://inbox-secret.example", 1L, favorite = true, hidden = true)
        )
        val shown = linksForAllCollections(withHidden)
        assertTrue("leaked a hidden link: $shown", shown.none { it.isHidden })
        assertEquals(4, shown.size)
    }

    @Test
    fun allLinksNeedsNoCollectionRowsToResolve() {
        // All resolves no collection rows, so it cannot be affected by the list being empty, stale,
        // or missing a row. collectionManagementItems starts as an empty list before the database
        // has emitted, and a collection can be renamed or deleted while the All view is open.
        val unresolvable = listOf(
            link("https://a.example", 99L),
            link("https://b.example", 100L)
        )
        assertEquals(
            listOf("https://a.example", "https://b.example"),
            linksForAllCollections(unresolvable).map { it.url }
        )
        // The named views cannot see the same links, which is the difference being asserted: they
        // resolve rows, and there are none to resolve.
        assertTrue(linksForLibraryCollection(unresolvable, items, "Work").isEmpty())
        assertTrue(linksForLibraryCollection(unresolvable, emptyList(), "Inbox").isEmpty())
    }

    @Test
    fun allLinksIsNotFavorites() {
        // Favorites is a filter; All is a scope. A favorited link in some other collection is
        // still in the library, so All shows it, and All does not require the star.
        val mixed = listOf(
            link("https://inbox.example", 1L),
            link("https://fav-work.example", 2L, favorite = true),
            link("https://plain-reading.example", 3L)
        )
        assertEquals(3, linksForAllCollections(mixed).size)
        assertEquals(1, linksForLibraryCollection(mixed, items, "Favorites").size)
    }

    @Test
    fun allLinksIsASupersetOfEveryNamedCollection() {
        // Guards against All quietly dropping something a named view shows. The properties that
        // matter are containment and completeness, so assert both rather than a fixed count.
        val withHidden = library + listOf(
            link("https://work-secret.example", 2L, hidden = true),
            link("https://reading-two.example", 3L)
        )
        val all = linksForAllCollections(withHidden)
        items.forEach { collection ->
            val named = linksForLibraryCollection(withHidden, items, collection.name)
            assertTrue(
                "All omitted ${named.size} link(s) from ${collection.name}",
                all.containsAll(named)
            )
        }
        // Every visible link is reachable from exactly one named collection, so All holds nothing
        // extra here -- which is what makes it a scope rather than a leak.
        val namedTotal = items.sumOf { linksForLibraryCollection(withHidden, items, it.name).size }
        assertEquals(namedTotal, all.size)
    }

    @Test
    fun allLinksPreservesTheOrderItWasGiven() {
        // Sorting is applied once, downstream, in sortLinks. Reordering here would make the All
        // view disagree with every other view about what "newest first" means.
        val unsorted = listOf(
            link("https://c.example", 3L),
            link("https://a.example", 1L),
            link("https://b.example", 2L)
        )
        assertEquals(
            listOf("https://c.example", "https://a.example", "https://b.example"),
            linksForAllCollections(unsorted).map { it.url }
        )
    }

    @Test
    fun allLinksOnAnEmptyOrFullyHiddenLibraryIsEmpty() {
        assertTrue(linksForAllCollections(emptyList()).isEmpty())
        val allHidden = listOf(
            link("https://a.example", 1L, hidden = true),
            link("https://b.example", 2L, hidden = true)
        )
        assertTrue(linksForAllCollections(allHidden).isEmpty())
    }

    @Test
    fun allLinksLeavesItsInputAlone() {
        val original = library.toList()
        linksForAllCollections(library)
        assertEquals(original, library)
    }

    @Test
    fun theAllLabelIsNotItselfACollection() {
        // Why All gets its own function rather than a branch inside linksForLibraryCollection.
        // That function's contract is that a name which does not resolve to a collection shows
        // nothing; a branch returning the whole library for one magic name would sit directly
        // beside the branch that fails closed, which is the exact shape of the fail-open bug this
        // suite already guards.
        assertTrue(linksForLibraryCollection(library, items, ALL_LINKS_LABEL).isEmpty())
        assertEquals(4, linksForAllCollections(library).size)
    }

    @Test
    fun aCollectionNamedAfterTheAllViewStaysReachable() {
        // "All links" is a label the app renders, not a name it reserves. Nothing stops someone
        // creating a collection called "All links", and if they do it has to remain a collection
        // that the library can open -- distinct from the view, which reaches links no collection
        // owns. This is why the view is not simply an entry in the collection menu: the menu is
        // deduplicated, so sharing the label there would drop the user's collection and route the
        // surviving entry to the view.
        val alsoCalledAllLinks = item(4L, ALL_LINKS_LABEL)
        val everything = items + alsoCalledAllLinks
        val mixed = library + link("https://real-all.example", 4L)

        assertEquals(
            listOf("https://real-all.example"),
            linksForLibraryCollection(mixed, everything, ALL_LINKS_LABEL).map { it.url }
        )
        // The view still spans the library, including the collection that shares its name.
        assertEquals(5, linksForAllCollections(mixed).size)
    }
}
