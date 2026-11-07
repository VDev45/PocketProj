package com.vishwaravi.pocketproj.data

import com.vishwaravi.pocketproj.runCatchingCancellable
import kotlinx.coroutines.flow.Flow
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

sealed interface SaveLinkResult {
    data class Created(val id: Long) : SaveLinkResult
    data class Duplicate(val existingId: Long) : SaveLinkResult
}

data class LinkMetadataPreview(
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val domain: String
)

/** A link as carried by a backup file, with optional portable collection identity. */
data class ImportLink(
    val url: String,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val domain: String = "",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val collectionId: Long = 0,
    val collectionName: String? = null,
    val isHidden: Boolean = false
)

enum class DuplicateImportMode {
    SKIP,
    OVERWRITE,
    KEEP_BOTH
}

/** Dispatches a background metadata update (e.g. via WorkManager); tests inject a no-op. */
fun interface MetadataEnqueuer {
    fun enqueue(linkId: Long, url: String)
}

/** Runs the given block inside a single database transaction. */
fun interface TransactionRunner {
    suspend fun run(block: suspend () -> Unit)
}

class LinkRepository(
    private val dao: LinkDao,
    private val collectionDao: CollectionDao? = null,
    private val fetcher: MetadataFetcher = HttpMetadataFetcher(),
    private val transactionRunner: TransactionRunner = TransactionRunner { block -> block() },
    private val enqueuer: MetadataEnqueuer = MetadataEnqueuer { _, _ -> },
    private val hiddenTagsProvider: () -> Set<String> = { emptySet() }
) {
    companion object {
        const val INBOX_COLLECTION = "Inbox"
    }

    fun observeLinks(): Flow<List<LinkEntity>> = dao.observeAll()

    fun observeHidden(): Flow<List<LinkEntity>> = dao.observeHidden()

    fun observeCollections(): Flow<List<CollectionEntity>>? = collectionDao?.observeAll()

    suspend fun findById(id: Long): LinkEntity? = dao.findById(id)

    suspend fun ensureInboxCollection() {
        collectionDao?.let { collectionDao ->
            if (collectionDao.findByName(INBOX_COLLECTION) == null) {
                collectionDao.insert(CollectionEntity(name = INBOX_COLLECTION))
            }
        }
    }

    suspend fun previewMetadata(url: String): Result<LinkMetadataPreview> = runCatchingCancellable {
        val canonical = UrlValidator.canonicalizeOrNull(url)
            ?: error("URL must use http:// or https:// and include a valid host")
        val metadata = fetcher.fetch(canonical)
        LinkMetadataPreview(
            title = metadata.title,
            description = metadata.description,
            imageUrl = metadata.imageUrl,
            domain = URI(canonical).host.orEmpty().removePrefix("www.")
        )
    }

    suspend fun createCollection(name: String): Result<Long> = runCatchingCancellable {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "Collection name cannot be empty" }
        require(normalized.length <= 80) { "Collection name is too long" }
        val collectionDao = collectionDao ?: error("Collection storage is unavailable")
        collectionDao.findByName(normalized)?.let { return@runCatchingCancellable it.id }
        val id = collectionDao.insert(CollectionEntity(name = normalized))
        require(id != -1L) { "Unable to create collection" }
        id
    }

    /**
     * Saves the link immediately (no network on this path) and, when the user left
     * fields blank, enqueues a background metadata fetch (REQUIREMENTS §3).
     */
    suspend fun save(
        url: String,
        collection: String = INBOX_COLLECTION,
        tags: String = "",
        title: String? = null,
        description: String? = null
    ): Result<SaveLinkResult> {
        val validated = UrlValidator.validate(url).getOrElse { return Result.failure(it) }
        val canonical = UrlValidator.canonicalize(validated)
        val duplicate = dao.findByUrl(canonical) ?: dao.findByUrl(validated)
        if (duplicate != null) return Result.success(SaveLinkResult.Duplicate(duplicate.id))
        val safeCollection = collection.trim().ifBlank { INBOX_COLLECTION }
        val normalizedTags = normalizeTags(tags)

        return runCatchingCancellable {
            val host = URI(canonical).host.orEmpty().removePrefix("www.")
            val collectionId = resolveCollectionId(safeCollection)
            val id = dao.insert(
                LinkEntity(
                    url = canonical,
                    title = title?.trim()?.takeIf(String::isNotBlank) ?: canonical,
                    description = description?.trim().orEmpty(),
                    imageUrl = "",
                    domain = host,
                    collectionId = collectionId,
                    tags = normalizedTags,
                    isHidden = isHiddenByTag(normalizedTags)
                )
            )
            if (title.isNullOrBlank() || description.isNullOrBlank()) {
                enqueuer.enqueue(id, canonical)
            }
            SaveLinkResult.Created(id)
        }
    }

    suspend fun update(link: LinkEntity): Result<Unit> = runCatchingCancellable {
        val validatedUrl = UrlValidator.validate(link.url).getOrThrow()
        val canonical = UrlValidator.canonicalize(validatedUrl)
        val currentWithUrl = dao.findByUrl(canonical) ?: dao.findByUrl(validatedUrl)
        require(currentWithUrl == null || currentWithUrl.id == link.id) { "This URL is already saved" }
        val host = URI(canonical).host.orEmpty().removePrefix("www.")
        val updated = link.copy(
            url = canonical,
            title = link.title.trim().ifBlank { canonical },
            domain = host,
            tags = normalizeTags(link.tags),
            isHidden = link.isHidden || isHiddenByTag(link.tags)
        )
        require(dao.update(updated) > 0) { "Link not found" }
    }

    /**
     * Applies the current hidden-tag configuration to existing visible links.
     * Adding a hidden tag can hide matching links immediately; removing a hidden tag
     * never unhides an already-hidden link.
     *
     * Runs in a transaction like [renameTag] and [deleteTag], which are the same shape of sweep. It
     * matters more here than for those two: this is the one that sets `isHidden`, so a failure part
     * way through left some links hidden and others not, with nothing to re-run and correct it —
     * unlike a tag rename, there is no later pass that repairs the state.
     */
    suspend fun syncHiddenTags(hiddenTags: Set<String>): Result<Int> = runCatchingCancellable {
        val normalizedHidden = hiddenTags
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        var changed = 0
        transactionRunner.run {
            dao.getAll().forEach { link ->
                val matches = tagTokens(link.tags).any { token ->
                    normalizedHidden.any { hidden -> hidden.equals(token, ignoreCase = true) }
                }
                if (!link.isHidden && matches && dao.update(link.copy(isHidden = true)) > 0) {
                    changed++
                }
            }
        }
        changed
    }

    /** Explicitly unhide a link. The only way `isHidden` returns to false (REQUIREMENTS §6). */
    suspend fun unhide(id: Long): Result<Unit> = runCatchingCancellable {
        val link = dao.findById(id) ?: error("Link not found")
        require(link.isHidden) { "Link is not hidden" }
        require(dao.update(link.copy(isHidden = false)) > 0) { "Link not found" }
    }

    /** Resolve a collection name to its id; unknown names fall back to Inbox. */
    private suspend fun resolveCollectionId(name: String): Long {
        val collectionDao = collectionDao ?: error("Collection storage is unavailable")
        collectionDao.findByName(name)?.let { return it.id }
        ensureInboxCollection()
        return collectionDao.findByName(INBOX_COLLECTION)?.id
            ?: error("Inbox collection is missing")
    }

    private suspend fun resolveCollectionId(candidate: Long): Long {
        val collectionDao = collectionDao ?: return candidate
        if (candidate > 0 && collectionDao.findById(candidate) != null) return candidate
        ensureInboxCollection()
        return collectionDao.findByName(INBOX_COLLECTION)?.id ?: error("Inbox collection is missing")
    }

    /** Resolve a restore's collection by portable name first, then by legacy numeric id. */
    private suspend fun resolveImportCollection(imported: ImportLink): Long {
        val name = imported.collectionName?.trim().orEmpty()
        if (name.isNotBlank()) {
            val collectionDao = collectionDao ?: error("Collection storage is unavailable")
            collectionDao.findByName(name)?.let { return it.id }
            if (name.equals(INBOX_COLLECTION, ignoreCase = true)) {
                ensureInboxCollection()
                return collectionDao.findByName(INBOX_COLLECTION)?.id ?: error("Inbox collection is missing")
            }
            val created = collectionDao.insert(CollectionEntity(name = name))
            if (created != -1L) return created
        }
        return resolveCollectionId(imported.collectionId)
    }

    suspend fun delete(id: Long): Result<Unit> = runCatchingCancellable {
        require(dao.deleteById(id) > 0) { "Link not found" }
    }

    suspend fun toggleFavorite(id: Long): Result<Unit> = runCatchingCancellable {
        require(dao.toggleFavorite(id) > 0) { "Link not found" }
    }

    suspend fun renameCollection(id: Long, newName: String): Result<Unit> = runCatchingCancellable {
        val collectionDao = collectionDao ?: error("Collection storage is unavailable")
        val collection = collectionDao.findById(id) ?: error("Collection not found")
        require(!collection.name.equals(INBOX_COLLECTION, ignoreCase = true)) { "Inbox cannot be renamed" }

        val normalized = newName.trim()
        require(normalized.isNotBlank()) { "Collection name cannot be empty" }
        require(normalized.length <= 80) { "Collection name is too long" }
        if (collection.name.equals(normalized, ignoreCase = true)) return@runCatchingCancellable
        require(collectionDao.findByName(normalized) == null) { "Collection already exists" }

        require(collectionDao.renameById(id, normalized) > 0) { "Unable to rename collection" }
    }

    /** Delete a collection atomically, moving its links to Inbox or deleting them permanently. */
    suspend fun deleteCollection(id: Long, deleteLinksToo: Boolean = false): Result<Unit> = runCatchingCancellable {
        transactionRunner.run {
            val collectionDao = collectionDao ?: error("Collection storage is unavailable")
            val collection = collectionDao.findById(id) ?: error("Collection not found")
            require(!collection.name.equals(INBOX_COLLECTION, ignoreCase = true)) { "Inbox cannot be deleted" }

            if (deleteLinksToo) {
                dao.deleteByCollectionId(id)
            } else {
                val inbox = collectionDao.findByName(INBOX_COLLECTION)
                    ?: error("Inbox collection is missing")
                dao.reassignCollection(id, inbox.id)
            }
            require(collectionDao.deleteById(id) > 0) { "Unable to delete collection" }
        }
    }

    /** Wipes links and collections and recreates Inbox, all in one transaction. */
    suspend fun clearAllData(): Result<Unit> = runCatchingCancellable {
        transactionRunner.run {
            dao.deleteAll()
            collectionDao?.deleteAll()
            ensureInboxCollection()
        }
    }

    /** Restores a backup atomically while respecting the user's duplicate-handling choice. */
    suspend fun importLinks(imports: List<ImportLink>, mode: DuplicateImportMode): Result<Int> = runCatchingCancellable {
        var changed = 0
        transactionRunner.run {
            imports.forEach { imported ->
                val validated = UrlValidator.validate(imported.url).getOrElse { return@forEach }
                val canonical = UrlValidator.canonicalize(validated)
                val existing = dao.findByUrl(canonical) ?: dao.findByUrl(validated)
                when {
                    existing == null -> {
                        dao.insert(imported.toEntity(canonical, resolveImportCollection(imported)))
                        changed++
                    }
                    mode == DuplicateImportMode.SKIP -> Unit
                    mode == DuplicateImportMode.OVERWRITE -> {
                        dao.update(
                            imported.toEntity(canonical, existing.collectionId)
                                .copy(id = existing.id, createdAt = existing.createdAt)
                        )
                        changed++
                    }
                    mode == DuplicateImportMode.KEEP_BOTH -> {
                        dao.insert(
                            imported.toEntity(canonical, resolveImportCollection(imported))
                                .copy(id = 0)
                        )
                        changed++
                    }
                }
            }
        }
        changed
    }

    private fun ImportLink.toEntity(canonicalUrl: String, collectionId: Long): LinkEntity = LinkEntity(
        url = canonicalUrl,
        title = title,
        description = description,
        imageUrl = imageUrl,
        domain = domain,
        collectionId = collectionId,
        tags = tags,
        createdAt = createdAt,
        isFavorite = isFavorite,
        isHidden = isHidden || isHiddenByTag(tags)
    )

    /**
     * Renames an existing tag everywhere it is assigned.
     * Hidden state is explicit, so renaming/removing a hidden tag never unhides content.
     */
    suspend fun renameTag(oldTag: String, newTag: String): Result<Int> = runCatchingCancellable {
        val old = oldTag.trim()
        val new = newTag.trim()
        require(old.isNotBlank()) { "Tag name cannot be empty" }
        require(new.isNotBlank()) { "Tag name cannot be empty" }
        require(old.length <= 40 && new.length <= 40) { "Tag name is too long" }
        require(!new.contains(",")) { "Tag names cannot contain commas" }
        require(!old.equals(new, ignoreCase = true)) { "Tag name is unchanged" }

        var changed = 0
        transactionRunner.run {
            dao.getAll().forEach { link ->
                val tags = tagTokens(link.tags)
                if (tags.any { it.equals(old, ignoreCase = true) }) {
                    val renamed = tags.map { if (it.equals(old, ignoreCase = true)) new else it }
                        .distinct()
                        .joinToString(", ")
                    if (dao.update(link.copy(tags = renamed)) > 0) changed++
                }
            }
        }
        changed
    }

    /**
     * Removes a tag from every link. This deliberately does not change isHidden;
     * unhide is an explicit action from the Hidden section.
     */
    suspend fun deleteTag(tag: String): Result<Int> = runCatchingCancellable {
        val target = tag.trim()
        require(target.isNotBlank()) { "Tag name cannot be empty" }

        var changed = 0
        transactionRunner.run {
            dao.getAll().forEach { link ->
                val remaining = tagTokens(link.tags).filterNot { it.equals(target, ignoreCase = true) }
                if (remaining.size != tagTokens(link.tags).size) {
                    if (dao.update(link.copy(tags = remaining.joinToString(", "))) > 0) changed++
                }
            }
        }
        changed
    }

    /**
     * Attempts one metadata fetch for a link and applies it (filling only blank fields).
     * Returns true when metadata was applied, false when the fetch failed.
     */
    suspend fun applyMetadata(linkId: Long): Boolean {
        val link = dao.findById(linkId) ?: return false
        return try {
            val metadata = fetcher.fetch(link.url)
            dao.update(
                link.copy(
                    title = if (link.title.isBlank() || link.title == link.url)
                        metadata.title?.trim()?.takeIf(String::isNotBlank) ?: link.title
                    else link.title,
                    description = if (link.description.isBlank()) metadata.description.orEmpty() else link.description,
                    imageUrl = if (link.imageUrl.isBlank()) metadata.imageUrl.orEmpty() else link.imageUrl,
                    metadataState = MetadataState.DONE,
                    metadataAttempts = link.metadataAttempts + 1
                )
            )
            true
        } catch (cancellation: CancellationException) {
            // Not a fetch failure. Rethrown so the cancelled worker does not go on to mark the link
            // FAILED and spend an attempt on work the user has already superseded — which is what
            // happens once the enqueuer replaces in-flight work on an explicit refresh.
            throw cancellation
        } catch (_: Exception) {
            dao.update(link.copy(metadataState = MetadataState.FAILED, metadataAttempts = link.metadataAttempts + 1))
            false
        }
    }

    /**
     * Queues a fresh background metadata refresh for a link (REQUIREMENTS §7).
     *
     * The attempt counter is reset as well as the state. It has to be: [LinkMetadataWorker] treats
     * it as a budget for the *current* request and gives up once it reaches
     * [LinkMetadataWorker.MAX_ATTEMPTS], so a link that had already burned its budget would take a
     * single attempt here and then fail — the manual refresh the worker gives up in order to wait
     * for would be a one-shot rather than a fresh try. Left cumulative it also grew without bound
     * over a link's life, counting every attempt ever made rather than any current request.
     */
    suspend fun refreshMetadata(id: Long): Result<Unit> = runCatchingCancellable {
        val link = dao.findById(id) ?: error("Link not found")
        dao.update(link.copy(metadataState = MetadataState.PENDING, metadataAttempts = 0))
        enqueuer.enqueue(id, link.url)
    }

    private fun isHiddenByTag(tags: String): Boolean =
        tagTokens(tags).any { token ->
            hiddenTagsProvider().any { hidden ->
                hidden.equals(token, ignoreCase = true)
            }
        }

    private fun tagTokens(tags: String): List<String> =
        tags.split(",").map(String::trim).filter(String::isNotBlank)

    private fun normalizeTags(raw: String): String = tagTokens(raw).distinct().joinToString(", ")
}