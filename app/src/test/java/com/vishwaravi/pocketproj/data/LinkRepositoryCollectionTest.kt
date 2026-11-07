package com.vishwaravi.pocketproj.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRepositoryCollectionTest {

    // --- createCollection ---

    @Test
    fun createCollection_trimsNameAndCreates() = runBlocking {
        val collectionDao = FakeCollectionDao()
        val repository = LinkRepository(FakeLinkDao(), collectionDao)

        val result = repository.createCollection("  Reading  ")

        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrThrow())
        assertEquals("Reading", collectionDao.findByName("Reading")?.name)
    }

    @Test
    fun createCollection_rejectsBlankName() = runBlocking {
        val repository = LinkRepository(FakeLinkDao(), FakeCollectionDao())

        val result = repository.createCollection("   ")

        assertFalse(result.isSuccess)
        assertEquals("Collection name cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun createCollection_rejectsNameOver80Chars() = runBlocking {
        val repository = LinkRepository(FakeLinkDao(), FakeCollectionDao())

        val result = repository.createCollection("c".repeat(81))

        assertFalse(result.isSuccess)
        assertEquals("Collection name is too long", result.exceptionOrNull()?.message)
    }

    @Test
    fun createCollection_returnsExistingIdForCaseInsensitiveDuplicate() = runBlocking {
        val collectionDao = FakeCollectionDao()
        val repository = LinkRepository(FakeLinkDao(), collectionDao)
        repository.createCollection("Reading")

        val duplicate = repository.createCollection("reading")

        assertTrue(duplicate.isSuccess)
        assertEquals(1L, duplicate.getOrThrow())
        assertEquals(1, collectionDao.collections.value.size)
    }

    @Test
    fun createCollection_failsWhenCollectionStorageMissing() = runBlocking {
        val repository = LinkRepository(FakeLinkDao())

        val result = repository.createCollection("Reading")

        assertFalse(result.isSuccess)
        assertEquals("Collection storage is unavailable", result.exceptionOrNull()?.message)
    }

    // --- renameCollection ---

    @Test
    fun renameCollection_renamesRowWithoutTouchingLinks() = runBlocking {
        val linkDao = FakeLinkDao(listOf(LinkEntity(id = 1, url = "https://a.example", collectionId = 2)))
        val collectionDao = FakeCollectionDao(
            listOf(
                CollectionEntity(id = 1, name = "Inbox"),
                CollectionEntity(id = 2, name = "Reading")
            )
        )
        val repository = LinkRepository(linkDao, collectionDao)

        val result = repository.renameCollection(id = 2, newName = "Books")

        assertTrue(result.isSuccess)
        assertEquals("Books", collectionDao.findByName("Books")?.name)
        assertEquals(2L, linkDao.items.value.single().collectionId)
    }

    @Test
    fun renameCollection_rejectsRenamingInbox() = runBlocking {
        val collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        val repository = LinkRepository(FakeLinkDao(), collectionDao)

        val result = repository.renameCollection(id = 1, newName = "Library")

        assertFalse(result.isSuccess)
        assertEquals("Inbox cannot be renamed", result.exceptionOrNull()?.message)
        assertEquals("Inbox", collectionDao.findByName("Inbox")?.name)
    }

    @Test
    fun renameCollection_rejectsBlankName() = runBlocking {
        val repository = repositoryWithReading()

        val result = repository.renameCollection(id = 2, newName = "   ")

        assertFalse(result.isSuccess)
        assertEquals("Collection name cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun renameCollection_rejectsCaseInsensitiveDuplicate() = runBlocking {
        val collectionDao = FakeCollectionDao(
            listOf(
                CollectionEntity(id = 1, name = "Inbox"),
                CollectionEntity(id = 2, name = "Reading")
            )
        )
        val repository = LinkRepository(FakeLinkDao(), collectionDao)

        val result = repository.renameCollection(id = 2, newName = "inbox")

        assertFalse(result.isSuccess)
        assertEquals("Collection already exists", result.exceptionOrNull()?.message)
        assertEquals("Reading", collectionDao.findById(2)?.name)
    }

    @Test
    fun renameCollection_sameNameDifferentCaseIsNoOp() = runBlocking {
        val collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 2, name = "Reading")))
        val repository = LinkRepository(FakeLinkDao(), collectionDao)

        val result = repository.renameCollection(id = 2, newName = "reading")

        assertTrue(result.isSuccess)
        assertEquals("Reading", collectionDao.findById(2)?.name)
    }

    @Test
    fun renameCollection_failsWhenCollectionStorageMissing() = runBlocking {
        val repository = LinkRepository(FakeLinkDao())

        val result = repository.renameCollection(id = 1, newName = "X")

        assertFalse(result.isSuccess)
        assertEquals("Collection storage is unavailable", result.exceptionOrNull()?.message)
    }

    // --- deleteCollection ---

    @Test
    fun deleteCollection_movesLinksToInboxAndDeletes() = runBlocking {
        val linkDao = FakeLinkDao(listOf(LinkEntity(id = 1, url = "https://a.example", collectionId = 2)))
        val collectionDao = FakeCollectionDao(
            listOf(
                CollectionEntity(id = 1, name = "Inbox"),
                CollectionEntity(id = 2, name = "Reading")
            )
        )
        val repository = LinkRepository(linkDao, collectionDao)

        val result = repository.deleteCollection(2)

        assertTrue(result.isSuccess)
        assertEquals(1L, linkDao.items.value.single().collectionId)
        assertEquals(null, collectionDao.findById(2))
        assertEquals("Inbox", collectionDao.findById(1)?.name)
    }

    @Test
    fun deleteCollection_canDeleteLinksWhenRequested() = runBlocking {
        val linkDao = FakeLinkDao(listOf(LinkEntity(id = 1, url = "https://a.example", collectionId = 2)))
        val collectionDao = FakeCollectionDao(
            listOf(CollectionEntity(id = 1, name = "Inbox"), CollectionEntity(id = 2, name = "Reading"))
        )
        val repository = LinkRepository(linkDao, collectionDao)

        val result = repository.deleteCollection(2, deleteLinksToo = true)

        assertTrue(result.isSuccess)
        assertTrue(linkDao.items.value.isEmpty())
        assertEquals(null, collectionDao.findById(2))
        assertEquals("Inbox", collectionDao.findById(1)?.name)
    }

    @Test
    fun deleteCollection_rejectsDeletingInbox() = runBlocking {
        val collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        val repository = LinkRepository(FakeLinkDao(), collectionDao)

        val result = repository.deleteCollection(1)

        assertFalse(result.isSuccess)
        assertEquals("Inbox cannot be deleted", result.exceptionOrNull()?.message)
        assertTrue(collectionDao.collections.value.size == 1)
    }

    @Test
    fun deleteCollection_failsWhenCollectionMissing() = runBlocking {
        val repository = LinkRepository(FakeLinkDao(), FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox"))))

        val result = repository.deleteCollection(99)

        assertFalse(result.isSuccess)
        assertEquals("Collection not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun deleteCollection_failsWhenInboxMissing() = runBlocking {
        val collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Reading")))
        val repository = LinkRepository(FakeLinkDao(), collectionDao)

        val result = repository.deleteCollection(1)

        assertFalse(result.isSuccess)
        assertEquals("Inbox collection is missing", result.exceptionOrNull()?.message)
        assertTrue(collectionDao.findById(1) != null)
    }

    // --- ensureInboxCollection ---

    @Test
    fun ensureInboxCollection_createsInboxWhenMissing() = runBlocking {
        val collectionDao = FakeCollectionDao()
        val repository = LinkRepository(FakeLinkDao(), collectionDao)

        repository.ensureInboxCollection()

        assertEquals("Inbox", collectionDao.findByName("Inbox")?.name)
        assertEquals(1, collectionDao.collections.value.size)
    }

    @Test
    fun ensureInboxCollection_keepsExistingInbox() = runBlocking {
        val collectionDao = FakeCollectionDao(listOf(CollectionEntity(id = 1, name = "Inbox")))
        val repository = LinkRepository(FakeLinkDao(), collectionDao)

        repository.ensureInboxCollection()

        assertEquals(1, collectionDao.collections.value.size)
        assertEquals(1L, collectionDao.findById(1)?.id)
    }

    private fun repositoryWithReading(): LinkRepository {
        val collectionDao = FakeCollectionDao(
            listOf(
                CollectionEntity(id = 1, name = "Inbox"),
                CollectionEntity(id = 2, name = "Reading")
            )
        )
        return LinkRepository(FakeLinkDao(), collectionDao)
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

    private class FakeLinkDao(initial: List<LinkEntity> = emptyList()) : LinkDao {
        val items = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<LinkEntity>> = items

        override fun observeHidden(): Flow<List<LinkEntity>> = items.map { it.filter { it.isHidden } }

        override suspend fun findByUrl(url: String): LinkEntity? = items.value.firstOrNull { it.url == url }

        override suspend fun findById(id: Long): LinkEntity? = items.value.firstOrNull { it.id == id }

        override suspend fun getAll(): List<LinkEntity> = items.value.toList()

        override suspend fun insert(link: LinkEntity): Long {
            val id = (items.value.maxOfOrNull { it.id } ?: 0L) + 1L
            items.value = items.value + link.copy(id = id)
            return id
        }

        override suspend fun deleteById(id: Long): Int {
            val before = items.value.size
            items.value = items.value.filterNot { it.id == id }
            return before - items.value.size
        }

        override suspend fun toggleFavorite(id: Long): Int {
            var toggled = 0
            items.value = items.value.map {
                if (it.id == id) {
                    toggled++
                    it.copy(isFavorite = !it.isFavorite)
                } else it
            }
            return toggled
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
}