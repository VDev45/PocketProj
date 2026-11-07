package com.vishwaravi.pocketproj.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class LinkRepositoryTest {
    @Test
    fun save_normalizesInputAndCreatesLink() = runBlocking {
        val dao = FakeLinkDao()
        val collectionDao = FakeCollectionDao(
            listOf(
                CollectionEntity(id = 1, name = "Inbox"),
                CollectionEntity(id = 2, name = "Reading")
            )
        )
        val repository = LinkRepository(dao, collectionDao)
        val result = repository.save("  https://example.com/article  ", "  Reading  ", "kotlin, android, kotlin")

        assertTrue(result.isSuccess)
        assertEquals(SaveLinkResult.Created(1L), result.getOrThrow())
        assertEquals("https://example.com/article", dao.items.value.single().url)
        assertEquals(2L, dao.items.value.single().collectionId)
        assertEquals("kotlin, android", dao.items.value.single().tags)
        assertEquals("example.com", dao.items.value.single().domain)
    }

    @Test
    fun save_fallsBackToInboxWhenCollectionDoesNotExist() = runBlocking {
        val dao = FakeLinkDao()
        val collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        val repository = LinkRepository(dao, collectionDao)

        val result = repository.save("https://example.com/article", "Mystery Collection")

        assertTrue(result.isSuccess)
        assertEquals(1L, dao.items.value.single().collectionId)
        assertEquals(1, collectionDao.collections.value.size)
    }

    @Test
    fun save_returnsDuplicateForExistingUrl() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(dao, FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))))
        repository.save("https://example.com/article")
        val result = repository.save("https://example.com/article")

        assertEquals(SaveLinkResult.Duplicate(1L), result.getOrThrow())
        assertEquals(1, dao.items.value.size)
    }

    @Test
    fun save_detectsCanonicalDuplicatesAcrossSpellingVariants() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(dao, FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))))
        repository.save("https://example.com")

        assertEquals(SaveLinkResult.Duplicate(1L), repository.save("https://example.com/").getOrThrow())
        assertEquals(SaveLinkResult.Duplicate(1L), repository.save("HTTPS://EXAMPLE.COM").getOrThrow())
        assertEquals(1, dao.items.value.size)
        assertEquals("https://example.com", dao.items.value.single().url)
    }

    @Test
    fun save_insertsImmediatelyWithoutNetworkAccess() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))),
            fetcher = FailingFetcher
        )

        val result = repository.save("https://example.com/article")

        assertTrue(result.isSuccess)
        assertEquals(SaveLinkResult.Created(1L), result.getOrThrow())
        assertEquals(1, dao.items.value.size)
    }

    @Test
    fun save_enqueuesMetadataWhenFieldsBlank() = runBlocking {
        val dao = FakeLinkDao()
        val enqueued = mutableListOf<Pair<Long, String>>()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))),
            enqueuer = MetadataEnqueuer { id, url -> enqueued += id to url }
        )

        repository.save("https://example.com/article", title = null, description = null)

        assertEquals(1, enqueued.size)
        assertEquals(1L, enqueued.single().first)
        assertEquals("https://example.com/article", enqueued.single().second)
    }

    @Test
    fun save_doesNotEnqueueWhenUserProvidedTitleAndDescription() = runBlocking {
        val dao = FakeLinkDao()
        val enqueued = mutableListOf<Pair<Long, String>>()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))),
            enqueuer = MetadataEnqueuer { id, url -> enqueued += id to url }
        )

        repository.save("https://example.com/article", title = "My title", description = "My description")

        assertTrue(enqueued.isEmpty())
        assertEquals(MetadataState.PENDING, dao.items.value.single().metadataState)
    }

    @Test
    fun save_setsExplicitHiddenFlagFromConfiguredHiddenTag() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))),
            hiddenTagsProvider = { setOf("private") }
        )

        repository.save("https://example.com/article", tags = "work, private")

        assertTrue(dao.items.value.single().isHidden)
    }

    @Test
    fun save_doesNotUseSystemHiddenTag() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        )

        repository.save("https://example.com/article", tags = "hidden")

        assertFalse(dao.items.value.single().isHidden)
    }

    @Test
    fun renameTag_updatesAllAssignments() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        )
        repository.save("https://example.com/one", tags = "android, dev")
        repository.save("https://example.com/two", tags = "android")

        assertTrue(repository.renameTag("android", "mobile").isSuccess)
        assertEquals("mobile, dev", dao.items.value[0].tags)
        assertEquals("mobile", dao.items.value[1].tags)
    }

    @Test
    fun deleteTag_removesOnlyRequestedTag() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        )
        repository.save("https://example.com/article", tags = "android, dev")

        assertTrue(repository.deleteTag("android").isSuccess)
        assertEquals("dev", dao.items.value.single().tags)
    }

    @Test
    fun deleteTag_rewritesHiddenLinksWithoutUnhidingThem() {
        // REQUIREMENTS §6: hidden state is cleared only by the explicit Unhide action from the
        // Hidden section. A tag sweep is not that action, so it must not smuggle one in. deleteTag
        // rewrites every matching link with link.copy(...), so clearing the flag here would be a
        // one-word slip with no other test in the suite to catch it.
        runBlocking {
            val dao = FakeLinkDao(
                initial = listOf(
                    LinkEntity(id = 1, url = "https://a.example", tags = "android, dev", isHidden = true),
                    LinkEntity(id = 2, url = "https://b.example", tags = "android", isHidden = false)
                )
            )
            val repository = LinkRepository(dao)

            // A hidden link is still in the sweep's scope: its tags get rewritten like any other.
            assertEquals(2, repository.deleteTag("android").getOrThrow())
            assertEquals("dev", dao.items.value[0].tags)
            assertEquals("", dao.items.value[1].tags)
            // And rewriting did not clear the flag on the one that had it.
            assertTrue(dao.items.value[0].isHidden)
            assertFalse(dao.items.value[1].isHidden)
        }
    }

    @Test
    fun renameTag_rewritesHiddenLinksWithoutUnhidingThem() {
        // The same rule as deleteTag, on the other sweep that rewrites tags wholesale. Without
        // this, renaming a tag would double as a bulk unhide for any link that happened to be
        // hidden by tag -- which is exactly the population hidden-tag config creates.
        runBlocking {
            val dao = FakeLinkDao(
                initial = listOf(
                    LinkEntity(id = 1, url = "https://a.example", tags = "private, dev", isHidden = true),
                    LinkEntity(id = 2, url = "https://b.example", tags = "private", isHidden = false)
                )
            )
            val repository = LinkRepository(dao)

            assertEquals(2, repository.renameTag("private", "secret").getOrThrow())
            assertEquals("secret, dev", dao.items.value[0].tags)
            assertEquals("secret", dao.items.value[1].tags)
            assertTrue(dao.items.value[0].isHidden)
            assertFalse(dao.items.value[1].isHidden)
        }
    }

    @Test
    fun aCancelledSweepUnwindsInsteadOfReturningAFailure() = runBlocking {
        // This is the bug runCatchingCancellable exists to fix, tested where it would bite. The
        // repository methods all returned Result, and runCatching catches CancellationException, so
        // a sweep cancelled part way through came back as Result.failure and the caller went on to
        // its failure handling — for work that was merely abandoned, not wrong.
        //
        // Reachable whenever the coroutine is cancelled under a suspending call: the ViewModel
        // cleared, the Activity finished, the CoroutineWorker replaced by an explicit refresh.
        val dao = FakeLinkDao(
            initial = listOf(LinkEntity(id = 1, url = "https://a.example", tags = "work")),
            onGetAll = { awaitCancellation() }
        )
        val repository = LinkRepository(dao)

        var returned: Result<Int>? = null
        var propagated = false
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            try {
                returned = repository.renameTag("work", "job")
            } catch (cancellation: CancellationException) {
                propagated = true
                throw cancellation
            }
        }
        job.cancelAndJoin()

        assertTrue("the cancellation did not propagate out of renameTag", propagated)
        assertNull("cancellation was reported as a Result", returned)
        // And nothing was written on the way out.
        assertEquals("work", dao.items.value.single().tags)
    }

    @Test
    fun theSweepsReadTheTableOnceNotOncePerRow() {
        // The sweeps read the whole table and then write to it. What makes them correct is that the
        // read happens once, up front. Re-reading per row would let a row's own write feed back
        // into the iteration, so renameTag would match a tag it had just created and rewrite rows
        // it had already finished with.
        //
        // Asserted as a call count rather than through the results, because the fake is backed by a
        // StateFlow: its `value` is already current at any moment, so a re-read and a snapshot
        // produce identical rows and identical outcomes. Only the read count tells them apart.
        runBlocking {
            val dao = FakeLinkDao(
                initial = listOf(
                    LinkEntity(id = 1, url = "https://a.example", tags = "work"),
                    LinkEntity(id = 2, url = "https://b.example", tags = "work")
                )
            )
            val repository = LinkRepository(dao)

            assertEquals(2, repository.renameTag("work", "job").getOrThrow())
            assertEquals("renameTag re-read the table", 1, dao.getAllCalls)

            assertEquals(2, repository.deleteTag("job").getOrThrow())
            assertEquals("deleteTag re-read the table", 2, dao.getAllCalls)

            assertEquals(0, repository.syncHiddenTags(setOf("nothing-matches")).getOrThrow())
            assertEquals("syncHiddenTags re-read the table", 3, dao.getAllCalls)
        }
    }

    @Test
    fun save_doesNotHideLinksWithoutHiddenTag() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        )

        repository.save("https://example.com/article", tags = "work")

        assertFalse(dao.items.value.single().isHidden)
    }

    @Test
    fun update_doesNotAutomaticallyUnhideWhenHiddenTagRemoved() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))),
            hiddenTagsProvider = { setOf("private") }
        )
        repository.save("https://example.com/article", tags = "private")

        val stored = dao.items.value.single()
        assertTrue(stored.isHidden)
        assertTrue(repository.update(stored.copy(tags = "now visible")).isSuccess)

        assertTrue(dao.items.value.single().isHidden)
        assertEquals("now visible", dao.items.value.single().tags)
    }

    @Test
    fun syncHiddenTags_hidesExistingMatchesAndReportsTheCount() = runBlocking {
        val dao = FakeLinkDao(
            initial = listOf(
                LinkEntity(id = 1, url = "https://a.example", tags = "private, work"),
                LinkEntity(id = 2, url = "https://b.example", tags = "work"),
                LinkEntity(id = 3, url = "https://c.example", tags = "PRIVATE")
            )
        )
        val repository = LinkRepository(dao)

        val changed = repository.syncHiddenTags(setOf("private")).getOrThrow()

        assertEquals(2, changed)
        assertTrue(dao.items.value.first { it.id == 1L }.isHidden)
        assertFalse(dao.items.value.first { it.id == 2L }.isHidden)
        // Case-insensitive, matching how tags are matched everywhere else.
        assertTrue(dao.items.value.first { it.id == 3L }.isHidden)
    }

    @Test
    fun syncHiddenTags_neverUnhidesAnAlreadyHiddenLink() = runBlocking {
        // REQUIREMENTS §6: removing a hidden tag does not unhide, and re-syncing must not either.
        val dao = FakeLinkDao(
            initial = listOf(LinkEntity(id = 1, url = "https://a.example", tags = "work", isHidden = true))
        )
        val repository = LinkRepository(dao)

        val changed = repository.syncHiddenTags(setOf("private")).getOrThrow()

        assertEquals(0, changed)
        assertTrue(dao.items.value.single().isHidden)
    }

    @Test
    fun syncHiddenTags_doesNotRecountLinksItAlreadyHid() = runBlocking {
        val dao = FakeLinkDao(
            initial = listOf(LinkEntity(id = 1, url = "https://a.example", tags = "private"))
        )
        val repository = LinkRepository(dao)

        assertEquals(1, repository.syncHiddenTags(setOf("private")).getOrThrow())
        assertEquals(0, repository.syncHiddenTags(setOf("private")).getOrThrow())
    }

    @Test
    fun syncHiddenTags_ignoresBlankAndWhitespaceHiddenTags() = runBlocking {
        val dao = FakeLinkDao(
            initial = listOf(LinkEntity(id = 1, url = "https://a.example", tags = "work"))
        )
        val repository = LinkRepository(dao)

        assertEquals(0, repository.syncHiddenTags(setOf("", "   ")).getOrThrow())
        assertFalse(dao.items.value.single().isHidden)
    }

    @Test
    fun syncHiddenTags_runsInsideATransaction() = runBlocking {
        // The other three sweeps (renameTag, deleteTag, and the collection operations) all run
        // inside one. This is the one that sets isHidden, so a failure part way through would
        // otherwise leave the library half hidden with nothing to repair it.
        var entered = 0
        val dao = FakeLinkDao(
            initial = listOf(LinkEntity(id = 1, url = "https://a.example", tags = "private"))
        )
        val repository = LinkRepository(
            dao = dao,
            transactionRunner = TransactionRunner { block -> entered++; block() }
        )

        repository.syncHiddenTags(setOf("private"))

        assertEquals(1, entered)
    }

    @Test
    fun unhide_explicitlyClearsHiddenState() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))),
            hiddenTagsProvider = { setOf("private") }
        )
        repository.save("https://example.com/article", tags = "private, work")

        assertTrue(repository.unhide(1L).isSuccess)
        assertFalse(dao.items.value.single().isHidden)
        assertTrue(dao.items.value.single().tags.contains("private"))
    }

    @Test
    fun unhide_rejectsLinksThatAreNotHidden() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(
            dao = dao,
            collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        )
        repository.save("https://example.com/article")

        val result = repository.unhide(1L)

        assertTrue(result.isFailure)
        assertEquals("Link is not hidden", result.exceptionOrNull()?.message)
    }

    @Test
    fun applyMetadata_fillsBlankFieldsAndMarksDone() = runBlocking {
        val dao = FakeLinkDao(initial = listOf(LinkEntity(id = 1, url = "https://example.com/article")))
        val repository = LinkRepository(dao, fetcher = FakeMetadataFetcher("OG Title", "OG description", "https://example.com/image.png"))

        val applied = repository.applyMetadata(1L)

        assertTrue(applied)
        val stored = dao.items.value.single()
        assertEquals("OG Title", stored.title)
        assertEquals("OG description", stored.description)
        assertEquals("https://example.com/image.png", stored.imageUrl)
        assertEquals(MetadataState.DONE, stored.metadataState)
        assertEquals(1, stored.metadataAttempts)
    }

    @Test
    fun applyMetadata_neverClobbersAUserCuratedTitle() = runBlocking {
        val dao = FakeLinkDao(initial = listOf(LinkEntity(id = 1, url = "https://example.com/article", title = "My curated title")))
        val repository = LinkRepository(dao, fetcher = FakeMetadataFetcher("OG Title", "OG description", null))

        repository.applyMetadata(1L)

        assertEquals("My curated title", dao.items.value.single().title)
    }

    @Test
    fun applyMetadata_recordsFailureAndCountsAttempt() = runBlocking {
        val dao = FakeLinkDao(initial = listOf(LinkEntity(id = 1, url = "https://example.com/article")))
        val repository = LinkRepository(dao, fetcher = FailingFetcher)

        val applied = repository.applyMetadata(1L)

        assertFalse(applied)
        val stored = dao.items.value.single()
        assertEquals(MetadataState.FAILED, stored.metadataState)
        assertEquals(1, stored.metadataAttempts)
    }

    @Test
    fun refreshMetadata_resetsStateAndEnqueues() = runBlocking {
        val dao = FakeLinkDao(
            initial = listOf(
                LinkEntity(id = 1, url = "https://example.com/article", metadataState = MetadataState.FAILED, metadataAttempts = 3)
            )
        )
        val enqueued = mutableListOf<Long>()
        val repository = LinkRepository(
            dao = dao,
            enqueuer = MetadataEnqueuer { id, _ -> enqueued += id }
        )

        assertTrue(repository.refreshMetadata(1L).isSuccess)

        assertEquals(MetadataState.PENDING, dao.items.value.single().metadataState)
        assertEquals(0, dao.items.value.single().metadataAttempts)
        assertEquals(listOf(1L), enqueued)
    }

    @Test
    fun refreshMetadata_restoresTheWorkerRetryBudget() = runBlocking {
        // The fixture is a link that already exhausted its budget, which is exactly the state the
        // worker gives up in and expects a manual refresh to recover from.
        val dao = FakeLinkDao(
            initial = listOf(
                LinkEntity(
                    id = 1,
                    url = "https://example.com/article",
                    metadataState = MetadataState.FAILED,
                    metadataAttempts = LinkMetadataWorker.MAX_ATTEMPTS
                )
            )
        )
        val repository = LinkRepository(
            dao = dao,
            enqueuer = MetadataEnqueuer { _, _ -> }
        )

        // Before: the budget is spent, so the worker's own check would refuse to retry at all.
        assertTrue(dao.items.value.single().metadataAttempts >= LinkMetadataWorker.MAX_ATTEMPTS)

        repository.refreshMetadata(1L)

        // After: the next failure is a retry with backoff, not an immediate give-up.
        val stored = dao.items.value.single()
        assertTrue(
            "manual refresh left the budget exhausted at ${stored.metadataAttempts}",
            stored.metadataAttempts < LinkMetadataWorker.MAX_ATTEMPTS
        )
    }

    @Test
    fun refreshMetadata_onAMissingLinkFails() = runBlocking {
        val dao = FakeLinkDao()
        val enqueued = mutableListOf<Long>()
        val repository = LinkRepository(
            dao = dao,
            enqueuer = MetadataEnqueuer { id, _ -> enqueued += id }
        )

        assertTrue(repository.refreshMetadata(99L).isFailure)
        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun applyMetadata_doesNotSwallowCancellation() = runBlocking {
        val dao = FakeLinkDao(initial = listOf(LinkEntity(id = 1, url = "https://example.com/article")))
        val repository = LinkRepository(dao, fetcher = CancellingFetcher)

        // Rethrown rather than reported as a failed fetch: a cancelled worker must not go on to
        // mark the link FAILED and spend one of its retry attempts on superseded work.
        val thrown = runCatching { repository.applyMetadata(1L) }.exceptionOrNull()
        assertTrue("expected CancellationException, got $thrown", thrown is CancellationException)

        val stored = dao.items.value.single()
        assertEquals(MetadataState.PENDING, stored.metadataState)
        assertEquals(0, stored.metadataAttempts)
    }

    @Test
    fun importLinks_resolvesCollectionByPortableName() = runBlocking {
        val dao = FakeLinkDao()
        val collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        val repository = LinkRepository(dao, collectionDao)

        val result = repository.importLinks(
            listOf(ImportLink(url = "https://example.com/article", collectionName = "Reading")),
            DuplicateImportMode.SKIP
        )

        assertEquals(1, result.getOrThrow())
        val reading = collectionDao.findByName("Reading") ?: error("collection missing")
        assertEquals(reading.id, dao.items.value.single().collectionId)
        assertTrue(dao.items.value.single().title.isBlank())
    }

    @Test
    fun importLinks_overwritesDuplicatesWhenRequested() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(dao, FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))))
        dao.insert(LinkEntity(url = "https://example.com/article", title = "Old title", collectionId = 1))

        val result = repository.importLinks(
            listOf(ImportLink(url = "https://example.com/article", title = "Restored title", collectionId = 1)),
            DuplicateImportMode.OVERWRITE
        )

        assertEquals(1, result.getOrThrow())
        assertEquals("Restored title", dao.items.value.single().title)
        assertEquals(1, dao.items.value.size)
    }

    @Test
    fun importLinks_keepsDuplicatesWhenRequested() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(dao, FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))))
        dao.insert(LinkEntity(url = "https://example.com/article", title = "Existing", collectionId = 1))

        val result = repository.importLinks(
            listOf(ImportLink(url = "https://example.com/article", title = "Restored", collectionId = 1)),
            DuplicateImportMode.KEEP_BOTH
        )

        assertEquals(1, result.getOrThrow())
        assertEquals(2, dao.items.value.size)
    }

    @Test
    fun importLinks_detectsDuplicatesCanonically() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(dao, FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))))
        dao.insert(LinkEntity(url = "https://example.com", collectionId = 1))

        val result = repository.importLinks(
            listOf(ImportLink(url = "https://example.com/")),
            DuplicateImportMode.SKIP
        )

        assertEquals(0, result.getOrThrow())
        assertEquals(1, dao.items.value.size)
    }

    @Test
    fun importLinks_restoresHiddenFlag() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(dao, FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))))

        repository.importLinks(
            listOf(ImportLink(url = "https://example.com/article", isHidden = true, collectionId = 1)),
            DuplicateImportMode.SKIP
        )

        assertTrue(dao.items.value.single().isHidden)
    }

    @Test
    fun clearAllData_runsInsideSingleTransaction() = runBlocking {
        val dao = FakeLinkDao(initial = listOf(LinkEntity(id = 1, url = "https://example.com/article", collectionId = 1)))
        val collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        var transactionCount = 0
        val repository = LinkRepository(
            dao = dao,
            collectionDao = collectionDao,
            transactionRunner = TransactionRunner { block -> transactionCount++; block() }
        )

        assertTrue(repository.clearAllData().isSuccess)

        assertEquals(1, transactionCount)
        assertTrue(dao.items.value.isEmpty())
        assertEquals(listOf("Inbox"), collectionDao.collections.value.map { it.name })
    }

    @Test
    fun deleteCollection_runsInsideSingleTransaction() = runBlocking {
        val linkDao = FakeLinkDao(listOf(LinkEntity(id = 1, url = "https://a.example", collectionId = 2)))
        val collectionDao = FakeCollectionDao(
            listOf(CollectionEntity(id = 1, name = "Inbox"), CollectionEntity(id = 2, name = "Reading"))
        )
        var transactionCount = 0
        val repository = LinkRepository(
            dao = linkDao,
            collectionDao = collectionDao,
            transactionRunner = TransactionRunner { block -> transactionCount++; block() }
        )

        assertTrue(repository.deleteCollection(2).isSuccess)

        assertEquals(1, transactionCount)
        assertEquals(1L, linkDao.items.value.single().collectionId)
    }

    @Test
    fun save_rejectsUnsupportedScheme() = runBlocking {
        val result = LinkRepository(FakeLinkDao()).save("ftp://example.com/file")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("http:// or https://"))
    }

    @Test
    fun delete_removesExistingLink() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(dao, FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))))
        repository.save("https://example.com/article")

        val result = repository.delete(1L)

        assertTrue(result.isSuccess)
        assertTrue(dao.items.value.isEmpty())
    }

    @Test
    fun toggleFavorite_changesFavoriteState() = runBlocking {
        val dao = FakeLinkDao()
        val repository = LinkRepository(dao, FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))))
        repository.save("https://example.com/article")

        assertFalse(dao.items.value.single().isFavorite)
        assertTrue(repository.toggleFavorite(1L).isSuccess)
        assertTrue(dao.items.value.single().isFavorite)
    }

    private object FailingFetcher : MetadataFetcher {
        override suspend fun fetch(url: String): LinkMetadata =
            throw RuntimeException("network must not be touched")
    }

    /** Stands in for a worker cancelled mid-fetch, e.g. replaced by an explicit refresh. */
    private object CancellingFetcher : MetadataFetcher {
        override suspend fun fetch(url: String): LinkMetadata = throw CancellationException("cancelled")
    }

    private class FakeMetadataFetcher(
        private val title: String?,
        private val description: String?,
        private val imageUrl: String?
    ) : MetadataFetcher {
        override suspend fun fetch(url: String): LinkMetadata = LinkMetadata(title, description, imageUrl)
    }

    private class FakeLinkDao(
        initial: List<LinkEntity> = emptyList(),
        /** Suspends inside every whole-table read, so a test can cancel the sweep mid-flight. */
        private val onGetAll: (suspend () -> Unit)? = null
    ) : LinkDao {
        val items = MutableStateFlow(initial)

        /** How many times a whole-table snapshot was read. See the snapshot tests. */
        var getAllCalls = 0

        override fun observeAll(): Flow<List<LinkEntity>> = items

        override fun observeHidden(): Flow<List<LinkEntity>> = items.map { it.filter { it.isHidden } }

        override suspend fun findByUrl(url: String): LinkEntity? = items.value.firstOrNull { it.url == url }

        override suspend fun findById(id: Long): LinkEntity? = items.value.firstOrNull { it.id == id }

        override suspend fun getAll(): List<LinkEntity> {
            getAllCalls++
            onGetAll?.invoke()
            return items.value.toList()
        }

        override suspend fun insert(link: LinkEntity): Long {
            val id = (items.value.maxOfOrNull { it.id } ?: 0L) + 1L
            items.value = items.value + link.copy(id = id)
            return id
        }

        override suspend fun deleteById(id: Long): Int {
            val oldSize = items.value.size
            items.value = items.value.filterNot { it.id == id }
            return oldSize - items.value.size
        }

        override suspend fun toggleFavorite(id: Long): Int {
            var changed = 0
            items.value = items.value.map {
                if (it.id == id) {
                    changed = 1
                    it.copy(isFavorite = !it.isFavorite)
                } else it
            }
            return changed
        }

        override suspend fun reassignCollection(oldCollectionId: Long, newCollectionId: Long): Int {
            var reassigned = 0
            items.value = items.value.map {
                if (it.collectionId == oldCollectionId) {
                    reassigned++
                    it.copy(collectionId = newCollectionId)
                } else it
            }
            return reassigned
        }

        override suspend fun update(link: LinkEntity): Int {
            var updated = 0
            items.value = items.value.map {
                if (it.id == link.id) { updated++; link } else it
            }
            return updated
        }

        override suspend fun deleteByCollectionId(collectionId: Long): Int {
            val before = items.value.size
            items.value = items.value.filterNot { it.collectionId == collectionId }
            return before - items.value.size
        }

        override suspend fun deleteAll(): Int {
            val before = items.value.size
            items.value = emptyList()
            return before
        }
    }

    private class FakeCollectionDao(initial: List<CollectionEntity> = emptyList()) : CollectionDao {
        val collections = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<CollectionEntity>> = collections

        override suspend fun insert(collection: CollectionEntity): Long {
            if (collections.value.any { it.name == collection.name }) return -1L
            val id = (collections.value.maxOfOrNull { it.id } ?: 0L) + 1L
            collections.value = collections.value + collection.copy(id = id)
            return id
        }

        override suspend fun findByName(name: String): CollectionEntity? =
            collections.value.firstOrNull { it.name.equals(name, ignoreCase = true) }

        override suspend fun findById(id: Long): CollectionEntity? =
            collections.value.firstOrNull { it.id == id }

        override suspend fun renameById(id: Long, newName: String): Int {
            var renamed = 0
            collections.value = collections.value.map {
                if (it.id == id) {
                    renamed++
                    it.copy(name = newName)
                } else it
            }
            return renamed
        }

        override suspend fun deleteById(id: Long): Int {
            val before = collections.value.size
            collections.value = collections.value.filterNot { it.id == id }
            return before - collections.value.size
        }

        override suspend fun deleteAll(): Int {
            val before = collections.value.size
            collections.value = emptyList()
            return before
        }
    }
}