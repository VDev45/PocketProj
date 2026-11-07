package com.vishwaravi.pocketproj

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vishwaravi.pocketproj.data.LinkEntity
import com.vishwaravi.pocketproj.data.LinkRepository
import com.vishwaravi.pocketproj.data.SaveLinkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionManagementItem(
    val id: Long,
    val name: String,
    val linkCount: Int,
    val isInbox: Boolean
)

data class LinkUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    /**
     * Something worth telling the user that is not a failure — currently only "you already saved
     * this URL". Kept apart from [errorMessage] because that one drives the URL field's error
     * styling and is announced to screen readers as a validation error, neither of which is true
     * of a duplicate: the URL validated fine, the app simply has it already.
     */
    val noticeMessage: String? = null,
    val saveCompleted: Boolean = false,
    val createCollectionError: String? = null,
    val createdCollectionName: String? = null,
    val renameCollectionError: String? = null,
    val deleteCollectionError: String? = null,
    val renamedCollectionName: String? = null,
    val deletedCollectionId: Long? = null,
    val updatedLinkId: Long? = null,
    val dataCleared: Boolean = false
)

/**
 * The state a finished save should show.
 *
 * A duplicate is a normal outcome, not a failure — the repository reports it as `Result.success`.
 * Filing it under [LinkUiState.errorMessage] painted the URL field red and announced
 * "Validation error" to screen readers, implying a malformed URL when the URL was perfectly valid
 * and the library simply already had it.
 */
internal fun uiStateForSave(result: SaveLinkResult): LinkUiState = when (result) {
    is SaveLinkResult.Created -> LinkUiState(saveCompleted = true)
    is SaveLinkResult.Duplicate -> LinkUiState(noticeMessage = "This URL is already saved")
}

class LinkViewModel(private val repository: LinkRepository) : ViewModel() {
    companion object {
        private const val INBOX = "Inbox"
    }

    val links: StateFlow<List<LinkEntity>> = repository.observeLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _linksLoaded = MutableStateFlow(false)
    val linksLoaded: StateFlow<Boolean> = _linksLoaded.asStateFlow()

    /**
     * Whether [collectionManagementItems] has produced its first real value.
     *
     * Distinct from [linksLoaded] because it is a `combine` over the collections *and* the links, so
     * it stays on its empty placeholder until both have emitted. The library filters links by a
     * collection id it can only get from here, so "links have loaded" is not on its own enough to
     * render: gating the skeleton on that alone produced a frame of "no links yet" on every cold
     * start, because the filter resolves to nothing while this list is still empty.
     */
    private val _collectionsLoaded = MutableStateFlow(false)
    val collectionsLoaded: StateFlow<Boolean> = _collectionsLoaded.asStateFlow()

    private val collectionSource: Flow<List<String>> =
        repository.observeCollections()
            ?.map { collections ->
                val names = collections.map { it.name.trim() }
                    .filter(String::isNotBlank)
                    .distinct()
                    .sortedBy { it.lowercase() }
                if (names.any { it.equals(INBOX, ignoreCase = true) }) names else listOf(INBOX) + names
            }
            ?: flowOf(listOf(INBOX))

    val collections: StateFlow<List<String>> = collectionSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(INBOX))

    val collectionManagementItems: StateFlow<List<CollectionManagementItem>> =
        repository.observeCollections()
            ?.combine(links) { allCollections, allLinks ->
                val countsByCollection = allLinks.filterNot { it.isHidden }.groupingBy { it.collectionId }.eachCount()
                allCollections
                    .map { collection ->
                        CollectionManagementItem(
                            id = collection.id,
                            name = collection.name,
                            linkCount = countsByCollection[collection.id] ?: 0,
                            isInbox = collection.name.equals(INBOX, ignoreCase = true)
                        )
                    }
                    .sortedBy { it.name.lowercase() }
            }
            ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            ?: MutableStateFlow(emptyList<CollectionManagementItem>())

    private val _selectedCollection = MutableStateFlow(INBOX)
    val selectedCollection: StateFlow<String> = _selectedCollection.asStateFlow()

    val selectedCollectionId: StateFlow<Long?> =
        combine(_selectedCollection, collectionManagementItems) { name, items ->
            items.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(LinkUiState())
    val uiState: StateFlow<LinkUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            links.collect { _linksLoaded.value = true }
        }
        viewModelScope.launch {
            collectionManagementItems.collect { _collectionsLoaded.value = true }
        }
        viewModelScope.launch {
            collections.collect { available ->
                if (available.none { it.equals(_selectedCollection.value, ignoreCase = true) }) {
                    _selectedCollection.value = INBOX
                }
            }
        }
    }

    fun selectCollection(collection: String) {
        if (collections.value.any { it.equals(collection, ignoreCase = true) }) {
            _selectedCollection.value = collection
        }
    }

    fun save(
        url: String,
        tags: String,
        collection: String = _selectedCollection.value,
        title: String? = null,
        description: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = LinkUiState(isSaving = true)
            repository.save(url, collection, tags, title, description)
                .onSuccess { result ->
                    _uiState.value = uiStateForSave(result)
                }
                .onFailure { error ->
                    _uiState.value = LinkUiState(errorMessage = error.message ?: "Unable to save link")
                }
        }
    }

    fun updateLink(link: LinkEntity) {
        viewModelScope.launch {
            _uiState.value = LinkUiState(isSaving = true)
            repository.update(link)
                .onSuccess { _uiState.value = LinkUiState(updatedLinkId = link.id) }
                .onFailure { error ->
                    _uiState.value = LinkUiState(errorMessage = error.message ?: "Unable to update link")
                }
        }
    }

    fun clearSaveCompleted() { _uiState.value = _uiState.value.copy(saveCompleted = false) }
    fun clearUpdatedLink() { _uiState.value = _uiState.value.copy(updatedLinkId = null) }

    /** Clears both the error and the notice: they are cleared together on the first field edit. */
    fun clearMessages() { _uiState.value = _uiState.value.copy(errorMessage = null, noticeMessage = null) }

    fun createCollection(name: String) {
        val normalized = name.trim()
        when {
            normalized.isBlank() -> {
                _uiState.value = _uiState.value.copy(createCollectionError = "Collection name cannot be empty")
                return
            }
            collections.value.any { it.equals(normalized, ignoreCase = true) } -> {
                _uiState.value = _uiState.value.copy(createCollectionError = "Collection already exists")
                return
            }
            else -> {
                _uiState.value = _uiState.value.copy(createCollectionError = null)
            }
        }

        viewModelScope.launch {
            repository.createCollection(normalized)
                .onSuccess {
                    _selectedCollection.value = normalized
                    _uiState.value = _uiState.value.copy(createdCollectionName = normalized, createCollectionError = null)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(createCollectionError = error.message ?: "Unable to create collection")
                }
        }
    }

    fun clearCreateCollectionError() {
        _uiState.value = _uiState.value.copy(createCollectionError = null)
    }

    fun clearCreatedCollection() {
        _uiState.value = _uiState.value.copy(createdCollectionName = null)
    }

    fun renameCollection(id: Long, currentName: String, newName: String) {
        val normalized = newName.trim()
        when {
            currentName.equals(INBOX, ignoreCase = true) -> {
                _uiState.value = _uiState.value.copy(renameCollectionError = "Inbox cannot be renamed")
                return
            }
            normalized.isBlank() -> {
                _uiState.value = _uiState.value.copy(renameCollectionError = "Collection name cannot be empty")
                return
            }
            collections.value.any { it.equals(normalized, ignoreCase = true) && !it.equals(currentName, ignoreCase = true) } -> {
                _uiState.value = _uiState.value.copy(renameCollectionError = "Collection already exists")
                return
            }
            else -> _uiState.value = _uiState.value.copy(renameCollectionError = null)
        }

        viewModelScope.launch {
            repository.renameCollection(id, normalized)
                .onSuccess {
                    if (_selectedCollection.value.equals(currentName, ignoreCase = true)) {
                        _selectedCollection.value = normalized
                    }
                    _uiState.value = _uiState.value.copy(renamedCollectionName = normalized, renameCollectionError = null)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(renameCollectionError = error.message ?: "Unable to rename collection")
                }
        }
    }

    fun clearRenameCollectionError() {
        _uiState.value = _uiState.value.copy(renameCollectionError = null)
    }

    fun clearRenamedCollection() {
        _uiState.value = _uiState.value.copy(renamedCollectionName = null)
    }

    fun deleteCollection(id: Long, deleteLinksToo: Boolean = false) {
        viewModelScope.launch {
            repository.deleteCollection(id, deleteLinksToo)
                .onSuccess {
                    _selectedCollection.value = INBOX
                    _uiState.value = _uiState.value.copy(deletedCollectionId = id, deleteCollectionError = null)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(deleteCollectionError = error.message ?: "Unable to delete collection")
                }
        }
    }

    fun clearDeleteCollectionError() {
        _uiState.value = _uiState.value.copy(deleteCollectionError = null)
    }

    fun clearDeletedCollection() {
        _uiState.value = _uiState.value.copy(deletedCollectionId = null)
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
                .onSuccess { _uiState.value = LinkUiState(dataCleared = true) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to clear data") }
        }
    }

    fun clearDataCleared() { _uiState.value = _uiState.value.copy(dataCleared = false) }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to delete link")
                }
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to update favorite")
                }
        }
    }

    /** Explicitly unhide a link from the Hidden section (REQUIREMENTS §6). */
    fun unhide(id: Long) {
        viewModelScope.launch {
            repository.unhide(id)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to unhide link")
                }
        }
    }

    /** Queue a background metadata refresh (REQUIREMENTS §7). */
    fun refreshMetadata(id: Long) {
        viewModelScope.launch {
            repository.refreshMetadata(id)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to refresh metadata")
                }
        }
    }
}

class LinkViewModelFactory(private val repository: LinkRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LinkViewModel::class.java))
        return LinkViewModel(repository) as T
    }
}
