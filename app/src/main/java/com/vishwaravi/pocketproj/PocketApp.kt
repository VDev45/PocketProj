package com.vishwaravi.pocketproj

import com.vishwaravi.pocketproj.BuildConfig

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vishwaravi.pocketproj.data.decodeHtmlEntities
import com.vishwaravi.pocketproj.data.LinkEntity
import com.vishwaravi.pocketproj.data.LinkRepository
import com.vishwaravi.pocketproj.data.UrlValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AppDestination {
    LIBRARY,
    SEARCH,
    ADD_LINK,
    EDIT_LINK,
    COLLECTIONS,
    SETTINGS,
    PRIVACY,
    IMPORT_EXPORT,
    APPEARANCE,
    HIDDEN,
    PROFILE,
    TAGS
}

private const val APP_PREFERENCES = "pocket-proj-preferences"

/**
 * How much bottom padding a scrollable screen needs to clear the bottom bar.
 * The bar is 44dp of item height inside 6dp of row padding, floating PocketSpace.md
 * above the edge -- 72dp, plus a little air so the last item is not sitting flush
 * against it. The library needs more than this because its FAB is on the same side.
 */
private val BottomBarClearance = 84.dp
private val LibraryBottomClearance = 148.dp

/**
 * The links the library shows for [selectedCollection], before sorting.
 *
 * Fails closed. If the selected name cannot be resolved to a collection this returns nothing, not
 * everything: the name is stale when the collection it referred to has been renamed or deleted, and
 * answering that with the whole library would show the user every link they have while labelling it
 * as the collection they asked for. The search filter has always resolved an unknown collection to
 * "matches nothing"; this brings the library in line with it.
 *
 * `collectionItems` is not the same list as `vm.collections`, which is what guards the selection:
 * `collections` synthesises an Inbox entry if the database somehow lacks one, and both start from
 * different placeholder values (`listOf(INBOX)` versus an empty list), so a name validated against
 * one is not guaranteed to resolve against the other.
 */
internal fun linksForLibraryCollection(
    links: List<LinkEntity>,
    collectionItems: List<CollectionManagementItem>,
    selectedCollection: String
): List<LinkEntity> {
    val visible = links.filterNot { it.isHidden }
    if (selectedCollection == "Favorites") return visible.filter { it.isFavorite }
    val selectedId = collectionItems.firstOrNull { it.name.equals(selectedCollection, true) }?.id
    return if (selectedId == null) emptyList() else visible.filter { it.collectionId == selectedId }
}

/** Label for the library view that spans every collection rather than sitting inside one. */
internal const val ALL_LINKS_LABEL = "All links"

/**
 * The links the library shows for the [ALL_LINKS_LABEL] view: every visible link, whichever
 * collection it was filed under.
 *
 * Kept separate from [linksForLibraryCollection] rather than added to it as a magic name, because
 * that function is a filter over one named collection and its whole contract is what happens when
 * a name does not resolve. All is the absence of a collection, not a collection called "All", and
 * folding the two together would put a branch that returns everything next to a branch that
 * deliberately fails closed — the two opposite answers to "this name is not a collection I know".
 *
 * Hidden links are excluded for the same reason they are in every other view: `isHidden` is the
 * flag the Hidden Locker sets, and `syncHiddenTags` keeps it in step with the hidden-tag config, so
 * filtering on it here covers both rules without either being consulted.
 */
internal fun linksForAllCollections(links: List<LinkEntity>): List<LinkEntity> =
    links.filterNot { it.isHidden }

@Composable
fun PocketAppRoot(repository: LinkRepository, sharedText: String?) {
    val vm: LinkViewModel = viewModel(factory = LinkViewModelFactory(repository))
    val links by vm.links.collectAsStateWithLifecycle()
    val collectionNames by vm.collections.collectAsStateWithLifecycle()
    val collectionItems by vm.collectionManagementItems.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val linksLoaded by vm.linksLoaded.collectAsStateWithLifecycle()
    val collectionsLoaded by vm.collectionsLoaded.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE) }
    val securityPreferences = remember { context.getSharedPreferences("pocket-proj-security", Context.MODE_PRIVATE) }
    val updateManager = remember(context) { UpdateManager(context) }
    val updateScope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun securityTimeoutMillis(): Long = when (securityPreferences.getString("lock-timeout", "1 minute")) {
        "Immediately" -> 0L
        "30 seconds" -> 30_000L
        "1 minute" -> 60_000L
        "5 minutes" -> 300_000L
        "15 minutes" -> 900_000L
        else -> 60_000L
    }

    var themeMode by rememberSaveable { mutableStateOf(preferences.getString("theme-mode", "Default") ?: "Default") }
    var materialYou by rememberSaveable { mutableStateOf(preferences.getBoolean("material-you", false)) }
    var route by rememberSaveable { mutableStateOf(AppDestination.LIBRARY) }
    val routeStack = remember { mutableStateListOf<AppDestination>() }
    var libraryCollection by rememberSaveable { mutableStateOf("Inbox") }
    // The All view is a lens over the library rather than a collection, so it is held beside the
    // selected collection instead of overwriting it. Overwriting would mean that tapping a
    // collection while in All had nothing to come back to, and that the Add button's save target
    // -- which reads libraryCollection directly -- would have no valid collection to name.
    var showAllLinks by rememberSaveable { mutableStateOf(false) }
    var libraryGrid by rememberSaveable { mutableStateOf(false) }
    var librarySort by rememberSaveable { mutableStateOf(SortOrder.NEWEST_FIRST) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchCollection by rememberSaveable { mutableStateOf("All collections") }
    var searchFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var searchSort by rememberSaveable { mutableStateOf(SortOrder.NEWEST_FIRST) }
    var searchTag by rememberSaveable { mutableStateOf("") }
    var editingLinkId by rememberSaveable { mutableLongStateOf(-1L) }
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    var initialAddUrl by rememberSaveable { mutableStateOf("") }
    var initialAddCollection by rememberSaveable { mutableStateOf("Inbox") }
    var showAddLinkSheet by rememberSaveable { mutableStateOf(false) }

    var showCreateCollection by rememberSaveable { mutableStateOf(false) }
    var newCollectionName by rememberSaveable { mutableStateOf("") }
    var pendingRenameId by rememberSaveable { mutableLongStateOf(-1L) }
    var pendingRenameCurrent by rememberSaveable { mutableStateOf("") }
    var pendingRenameValue by rememberSaveable { mutableStateOf("") }
    var pendingDeleteCollectionId by rememberSaveable { mutableLongStateOf(-1L) }
    var pendingDeleteCollectionName by rememberSaveable { mutableStateOf("") }
    var deleteLinksToo by rememberSaveable { mutableStateOf(false) }
    var showClearAllDialog by rememberSaveable { mutableStateOf(false) }
    var showVaultUnlock by rememberSaveable { mutableStateOf(false) }

    fun navigate(destination: AppDestination) {
        if (destination != route) {
            routeStack.add(route)
            route = destination
        }
    }

    fun navigateRoot(destination: AppDestination) {
        routeStack.clear()
        route = destination
    }

    fun goBack() {
        route = if (routeStack.isNotEmpty()) {
            routeStack.removeAt(routeStack.lastIndex)
        } else {
            AppDestination.LIBRARY
        }
    }

    fun openAdd(prefill: String = "") {
        initialAddUrl = prefill
        initialAddCollection = libraryCollection.takeUnless { it == "Favorites" } ?: "Inbox"
        editorSession++
        showAddLinkSheet = true
        vm.clearMessages()
    }

    fun openEdit(id: Long) {
        editingLinkId = id
        editorSession++
        navigate(AppDestination.EDIT_LINK)
        vm.clearMessages()
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val timeout = securityTimeoutMillis()
                    if (timeout == 0L) {
                        securityPreferences.edit().remove("hidden-session-until").apply()
                    } else {
                        val current = securityPreferences.getLong("hidden-session-until", 0L)
                        if (current > 0L) {
                            securityPreferences.edit().putLong(
                                "hidden-session-until",
                                System.currentTimeMillis() + timeout
                            ).apply()
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    val expiry = securityPreferences.getLong("hidden-session-until", 0L)
                    // Only act on a session that actually exists, and treat "does not survive a
                    // restart" as expired: a foreground-scoped session must never carry over.
                    if (expiry != 0L && !sessionSurvivesRestart(expiry, System.currentTimeMillis())) {
                        securityPreferences.edit().remove("hidden-session-until").apply()
                        if (route == AppDestination.HIDDEN) {
                            route = AppDestination.PRIVACY
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) openAdd(sharedText)
    }

    LaunchedEffect(route) {
        if (route == AppDestination.SETTINGS) {
            updateChecking = true
            updateError = null
            updateManager.checkForUpdate()
                .onSuccess { availableUpdate = it }
                .onFailure { updateError = "Could not check for updates" }
            updateChecking = false
        }
    }
    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) {
            if (showAddLinkSheet) showAddLinkSheet = false else goBack()
            initialAddUrl = ""
            vm.clearSaveCompleted()
        }
    }
    LaunchedEffect(uiState.updatedLinkId) {
        if (uiState.updatedLinkId != null) {
            goBack()
            vm.clearUpdatedLink()
        }
    }
    LaunchedEffect(uiState.createdCollectionName) {
        uiState.createdCollectionName?.let { createdName ->
            showCreateCollection = false
            newCollectionName = ""
            libraryCollection = createdName
            vm.clearCreatedCollection()
        }
    }
    LaunchedEffect(uiState.renamedCollectionName) {
        uiState.renamedCollectionName?.let { renamedName ->
            if (libraryCollection.equals(pendingRenameCurrent, ignoreCase = true)) libraryCollection = renamedName
            pendingRenameId = -1L
            pendingRenameCurrent = ""
            pendingRenameValue = ""
            vm.clearRenamedCollection()
        }
    }
    LaunchedEffect(uiState.deletedCollectionId) {
        if (uiState.deletedCollectionId != null) {
            pendingDeleteCollectionId = -1L
            pendingDeleteCollectionName = ""
            vm.clearDeletedCollection()
        }
    }
    LaunchedEffect(uiState.dataCleared) {
        if (uiState.dataCleared) {
            showClearAllDialog = false
            libraryCollection = "Inbox"
            vm.clearDataCleared()
        }
    }
    LaunchedEffect(collectionNames) {
        if (libraryCollection != "Favorites" && collectionNames.none { it.equals(libraryCollection, true) }) {
            libraryCollection = "Inbox"
        }
        if (searchCollection != "All collections" && collectionNames.none { it.equals(searchCollection, true) }) {
            searchCollection = "All collections"
        }
    }

    BackHandler(enabled = route != AppDestination.LIBRARY) { goBack() }

    val darkTheme = themeMode == "Dark" || (themeMode == "Material You" && androidx.compose.foundation.isSystemInDarkTheme())

    PocketProjTheme(darkTheme = darkTheme, materialYou = materialYou) {
        // Keep the palette/background behind transparent system bars, while
        // keeping every interactive screen inside the safe drawing area.
        PocketScreen {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                val namesById = remember(collectionItems) { collectionItems.associate { it.id to it.name } }
                // All is deliberately not in this list. The list is deduplicated, so mixing a
                // reserved label in with real collection names means a collection a user actually
                // called "All links" loses its entry and can never be opened -- and the surviving
                // entry routes to the All view, not to their collection. The All view is rendered
                // as its own item in the dropdown instead. ("Favorites" has the same collision and
                // still has it; that predates this work.)
                val fullCollectionMenu = remember(collectionNames) {
                    listOf("Inbox", "Favorites") + collectionNames.filterNot { it.equals("Inbox", true) }
                }.distinct()
                val homeLinks = remember(links, libraryCollection, collectionItems, librarySort, showAllLinks) {
                    val scoped = if (showAllLinks) {
                        linksForAllCollections(links)
                    } else {
                        linksForLibraryCollection(links, collectionItems, libraryCollection)
                    }
                    sortLinks(scoped, librarySort)
                }
                val searchLinks = remember(links, searchQuery, searchCollection, searchFavoritesOnly, searchSort, searchTag, collectionItems) {
                    val query = searchQuery.trim().lowercase()
                    val selectedCollectionId = collectionItems.firstOrNull { it.name.equals(searchCollection, true) }?.id
                    val filtered = links.filter { link ->
                        if (link.isHidden) return@filter false
                        val matchesQuery = query.isBlank() || listOf(link.title, link.url, link.domain, link.description, link.tags).any { it.lowercase().contains(query) }
                        val matchesCollection = searchCollection == "All collections" || selectedCollectionId == link.collectionId
                        val matchesTag = searchTag.isBlank() || parseTags(link.tags).any { it.equals(searchTag, ignoreCase = true) }
                        matchesQuery && matchesCollection && matchesTag && (!searchFavoritesOnly || link.isFavorite)
                    }
                    sortLinks(filtered, searchSort)
                }

                when (route) {
                    AppDestination.LIBRARY -> LibraryScreen(
                        links = homeLinks,
                        isLoading = !linksLoaded || !collectionsLoaded,
                        collectionName = if (showAllLinks) ALL_LINKS_LABEL else libraryCollection,
                        collectionMenu = fullCollectionMenu,
                        collectionNameById = namesById,
                        isGrid = libraryGrid,
                        onCollectionSelected = { selected ->
                            if (selected == ALL_LINKS_LABEL) {
                                showAllLinks = true
                            } else {
                                showAllLinks = false
                                libraryCollection = selected
                            }
                        },
                        onCreateCollection = {
                            showCreateCollection = true
                            newCollectionName = ""
                            vm.clearCreateCollectionError()
                        },
                        onManageCollections = { navigate(AppDestination.COLLECTIONS) },
                        onSearch = { navigate(AppDestination.SEARCH) },
                        onProfile = { navigate(AppDestination.PROFILE) },
                        onSettings = { navigate(AppDestination.SETTINGS) },
                        onToggleGrid = { libraryGrid = !libraryGrid },
                        onAddLink = { openAdd() },
                        onOpenLink = { id -> openEdit(id) },
                        onToggleFavorite = vm::toggleFavorite,
                        onOpenVault = {
                            if (isHiddenSessionValid(
                                    securityPreferences.getLong("hidden-session-until", 0L),
                                    System.currentTimeMillis()
                                )
                            ) {
                                routeStack.clear()
                                route = AppDestination.HIDDEN
                            } else {
                                showVaultUnlock = true
                            }
                        }
                    )
                    AppDestination.SEARCH -> SearchScreen(
                        query = searchQuery,
                        isLoading = !linksLoaded || !collectionsLoaded,
                        onQueryChange = { searchQuery = it },
                        links = searchLinks,
                        collectionMenu = listOf("All collections") + collectionNames,
                        selectedCollection = searchCollection,
                        favoritesOnly = searchFavoritesOnly,
                        sortOrder = searchSort,
                        collectionNameById = namesById,
                        onBack = { goBack() },
                        onSelectCollection = { searchCollection = it },
                        selectedTag = searchTag,
                        onSelectTag = { searchTag = it },
                        availableTags = remember(links) { links.filterNot { it.isHidden }.flatMap { parseTags(it.tags) }.distinct().sortedBy { it.lowercase() } },
                        onToggleFavorites = { searchFavoritesOnly = !searchFavoritesOnly },
                        onClearFilters = {
                            searchCollection = "All collections"
                            searchFavoritesOnly = false
                            searchSort = SortOrder.NEWEST_FIRST
                            searchTag = ""
                        },
                        onSortSelected = { searchSort = it },
                        onOpenLink = { id -> openEdit(id) },
                        onToggleFavorite = vm::toggleFavorite
                    )
                    AppDestination.ADD_LINK -> LinkEditorScreen(
                        screenKey = "add-$editorSession",
                        repository = repository,
                        isEditing = false,
                        initialUrl = initialAddUrl,
                        initialCollection = initialAddCollection,
                        initialLink = null,
                        collections = collectionNames,
                        collectionItems = collectionItems,
                        isSaving = uiState.isSaving,
                        errorMessage = uiState.errorMessage,
                        noticeMessage = uiState.noticeMessage,
                        onBack = { goBack() },
                        onClearMessages = vm::clearMessages,
                        onSave = { url, title, description, tags, selectedCollection ->
                            vm.save(url, tags, selectedCollection, title, description)
                        },
                        onRefresh = {},
                        onUpdate = {},
                        onDelete = {}
                    )
                    AppDestination.EDIT_LINK -> {
                        val link = links.firstOrNull { it.id == editingLinkId }
                        if (link == null) {
                            LaunchedEffect(editingLinkId) { goBack() }
                        } else {
                            LinkEditorScreen(
                                screenKey = "edit-$editorSession-${link.id}",
                                repository = repository,
                                isEditing = true,
                                initialUrl = link.url,
                                initialCollection = collectionItems.firstOrNull { it.id == link.collectionId }?.name ?: "Inbox",
                                initialLink = link,
                                collections = collectionNames,
                                collectionItems = collectionItems,
                                isSaving = uiState.isSaving,
                                errorMessage = uiState.errorMessage,
                                noticeMessage = uiState.noticeMessage,
                                onBack = { goBack() },
                                onClearMessages = vm::clearMessages,
                                onSave = { _, _, _, _, _ -> },
                                onRefresh = vm::refreshMetadata,
                                onUpdate = { updated -> vm.updateLink(updated) },
                                onDelete = vm::delete
                            )
                        }
                    }
                    AppDestination.COLLECTIONS -> CollectionsScreen(
                        collections = collectionItems,
                        onBack = { goBack() },
                        onNewCollection = {
                            showCreateCollection = true
                            newCollectionName = ""
                            vm.clearCreateCollectionError()
                        },
                        onSelectCollection = {
                            // Picking a collection on the Collections screen is an explicit choice
                            // of where to go, so it also lifts the All lens.
                            showAllLinks = false
                            libraryCollection = it
                            navigateRoot(AppDestination.LIBRARY)
                        },
                        onRename = { collection ->
                            pendingRenameId = collection.id
                            pendingRenameCurrent = collection.name
                            pendingRenameValue = collection.name
                            vm.clearRenameCollectionError()
                        },
                        onDelete = { collection ->
                            pendingDeleteCollectionId = collection.id
                            pendingDeleteCollectionName = collection.name
                            deleteLinksToo = false
                            vm.clearDeleteCollectionError()
                        }
                    )
                    AppDestination.TAGS -> TagManagerScreen(
                        repository = repository,
                        preferences = preferences,
                        tags = remember(links) { links.filterNot { it.isHidden }.flatMap { parseTags(it.tags) }.distinct().sortedBy { it.lowercase() } },
                        links = links,
                        onBack = { goBack() },
                        onSelectTag = {
                            searchTag = it
                            searchQuery = ""
                            searchCollection = "All collections"
                            searchFavoritesOnly = false
                            navigate(AppDestination.SEARCH)
                        }
                    )
                    AppDestination.PROFILE -> ProfileScreen(
                        totalLinks = links.size,
                        favorites = links.count { it.isFavorite },
                        collections = collectionItems.size,
                        themeMode = themeMode,
                        materialYou = materialYou,
                        onBack = { goBack() },
                        onSettings = { navigate(AppDestination.SETTINGS) },
                        onFavorites = {
                            showAllLinks = false
                            libraryCollection = "Favorites"
                            navigateRoot(AppDestination.LIBRARY)
                        },
                        onCollections = { navigate(AppDestination.COLLECTIONS) },
                        onTags = { navigateRoot(AppDestination.TAGS) },
                        onImportExport = { navigate(AppDestination.IMPORT_EXPORT) },
                        onAppearance = { navigate(AppDestination.APPEARANCE) }
                    )
                    AppDestination.SETTINGS -> SettingsScreen(
                        totalLinks = links.size,
                        favorites = links.count { it.isFavorite },
                        themeMode = themeMode,
                        materialYou = materialYou,
                        onBack = { goBack() },
                        onAppearance = { navigate(AppDestination.APPEARANCE) },
                        onImportExport = { navigate(AppDestination.IMPORT_EXPORT) },
                        onCollections = { navigate(AppDestination.COLLECTIONS) },
                        onClearAll = { showClearAllDialog = true },
                        availableUpdate = availableUpdate,
                        updateChecking = updateChecking,
                        updateDownloading = updateDownloading,
                        updateError = updateError,
                        canInstallUpdates = updateManager.canInstallUnknownSources(),
                        onCheckForUpdate = {
                            updateChecking = true
                            updateError = null
                            updateScope.launch {
                                updateManager.checkForUpdate()
                                    .onSuccess { availableUpdate = it }
                                    .onFailure { updateError = "Could not check for updates" }
                                updateChecking = false
                            }
                        },
                        onUpdate = {
                            val update = availableUpdate
                            if (update != null) {
                                if (!updateManager.canInstallUnknownSources()) {
                                    updateManager.openInstallPermissionSettings()
                                } else {
                                    updateDownloading = true
                                    updateError = null
                                    updateScope.launch {
                                        updateManager.downloadAndInstall(update)
                                            .onFailure { updateError = it.message ?: "Update failed" }
                                        updateDownloading = false
                                    }
                                }
                            }
                        },
                        onAllowInstall = updateManager::openInstallPermissionSettings
                    )
                    AppDestination.PRIVACY -> PrivacySecurityScreen(
                        onBack = { goBack() },
                        onOpenHidden = { navigate(AppDestination.HIDDEN) }
                    )
                    AppDestination.IMPORT_EXPORT -> ImportExportScreen(
                        repository = repository,
                        links = links,
                        collectionNameById = namesById,
                        securityPreferences = securityPreferences,
                        onBack = { goBack() }
                    )
                    AppDestination.APPEARANCE -> AppearanceScreen(
                        mode = themeMode,
                        materialYou = materialYou,
                        onBack = { goBack() },
                        onModeChange = { selected ->
                            themeMode = selected
                            materialYou = selected == "Material You"
                            preferences.edit()
                                .putString("theme-mode", selected)
                                .putBoolean("material-you", materialYou)
                                .apply()
                        },
                        onMaterialYouChange = { enabled ->
                            materialYou = enabled
                            themeMode = if (enabled) "Material You" else "Default"
                            preferences.edit()
                                .putBoolean("material-you", enabled)
                                .putString("theme-mode", themeMode)
                                .apply()
                        }
                    )
                    AppDestination.HIDDEN -> {
                        if (isHiddenSessionValid(
                                securityPreferences.getLong("hidden-session-until", 0L),
                                System.currentTimeMillis()
                            )
                        ) {
                            HiddenContentScreen(
                                repository = repository,
                                securityPreferences = securityPreferences,
                                collectionNameById = namesById,
                                onBack = { goBack() },
                                onOpenLink = { id -> openEdit(id) },
                                onToggleFavorite = vm::toggleFavorite,
                                onUnhide = vm::unhide
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                route = AppDestination.PRIVACY
                            }
                        }
                    }
                }

                // The bottom bar belongs to the three top-level destinations, not
                // to the library screen -- otherwise it vanishes on 2 of its own 4
                // tabs. Sub-screens (Settings, Collections, Locker, editors) have
                // no bar and navigate by their header back arrow.
                if (route == AppDestination.LIBRARY ||
                    route == AppDestination.SEARCH ||
                    route == AppDestination.PROFILE
                ) {
                    PocketBottomBar(
                        destinations = listOf(
                            PocketNavDestination("Library", Icons.Filled.Home),
                            PocketNavDestination("Search", Icons.Filled.Search),
                            PocketNavDestination("Favorites", Icons.Filled.Star),
                            PocketNavDestination("Profile", Icons.Filled.Person)
                        ),
                        selectedIndex = when (route) {
                            AppDestination.SEARCH -> 1
                            AppDestination.PROFILE -> 3
                            // All is reachable from inside Favorites, and entering it there leaves
                            // libraryCollection alone so the selection is remembered. Without the
                            // showAllLinks guard the bar would keep highlighting Favorites while
                            // the library was showing every collection.
                            else -> if (!showAllLinks && libraryCollection == "Favorites") 2 else 0
                        },
                        onSelect = { index ->
                            when (index) {
                                0 -> {
                                    // Landing on Library from the bar means Inbox, as it always
                                    // has, so the All lens is lifted along with the collection.
                                    if (showAllLinks || libraryCollection != "Inbox") {
                                        showAllLinks = false
                                        libraryCollection = "Inbox"
                                    }
                                    navigateRoot(AppDestination.LIBRARY)
                                }
                                1 -> navigateRoot(AppDestination.SEARCH)
                                2 -> {
                                    showAllLinks = false
                                    libraryCollection = "Favorites"
                                    navigateRoot(AppDestination.LIBRARY)
                                }
                                else -> navigateRoot(AppDestination.PROFILE)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = PocketSpace.md)
                            .padding(bottom = PocketSpace.md)
                    )
                }

                if (showAddLinkSheet) {
                    AddLinkSheet(
                        initialUrl = initialAddUrl,
                        initialCollection = initialAddCollection,
                        collections = collectionNames,
                        isSaving = uiState.isSaving,
                        errorMessage = uiState.errorMessage,
                        noticeMessage = uiState.noticeMessage,
                        repository = repository,
                        onDismiss = {
                            if (!uiState.isSaving) {
                                showAddLinkSheet = false
                                initialAddUrl = ""
                                vm.clearMessages()
                            }
                        },
                        onClearMessages = vm::clearMessages,
                        onSave = { url, title, description, tags, collection ->
                            vm.save(url, tags, collection, title, description)
                        }
                    )
                }

                if (showCreateCollection) {
                    CollectionNameDialog(
                        name = newCollectionName,
                        error = uiState.createCollectionError,
                        onNameChange = {
                            newCollectionName = it
                            vm.clearCreateCollectionError()
                        },
                        onDismiss = {
                            showCreateCollection = false
                            newCollectionName = ""
                            vm.clearCreateCollectionError()
                        },
                        onCreate = { vm.createCollection(newCollectionName) }
                    )
                }

                if (pendingRenameId >= 0L) {
                    CollectionNameDialog(
                        title = "Rename collection",
                        confirmLabel = "Save",
                        name = pendingRenameValue,
                        error = uiState.renameCollectionError,
                        onNameChange = {
                            pendingRenameValue = it
                            vm.clearRenameCollectionError()
                        },
                        onDismiss = {
                            pendingRenameId = -1L
                            pendingRenameCurrent = ""
                            pendingRenameValue = ""
                            vm.clearRenameCollectionError()
                        },
                        onCreate = { vm.renameCollection(pendingRenameId, pendingRenameCurrent, pendingRenameValue) }
                    )
                }

                if (pendingDeleteCollectionId >= 0L) {
                    DeleteCollectionDialog(
                        collectionName = pendingDeleteCollectionName,
                        deleteLinksToo = deleteLinksToo,
                        error = uiState.deleteCollectionError,
                        onDeleteLinksTooChange = { deleteLinksToo = it },
                        onDismiss = {
                            pendingDeleteCollectionId = -1L
                            pendingDeleteCollectionName = ""
                            vm.clearDeleteCollectionError()
                        },
                        onConfirm = { vm.deleteCollection(pendingDeleteCollectionId, deleteLinksToo) }
                    )
                }

                if (showClearAllDialog) {
                    ConfirmActionDialog(
                        title = "Clear all data?",
                        body = "All saved links and custom collections will be permanently removed. Inbox will remain available.",
                        confirmLabel = "Clear all data",
                        destructive = true,
                        error = uiState.errorMessage,
                        onDismiss = { showClearAllDialog = false },
                        onConfirm = vm::clearAllData
                    )
                }

                if (showVaultUnlock) {
                    PinGateDialog(
                        preferences = securityPreferences,
                        onDismiss = { showVaultUnlock = false },
                        onUnlocked = {
                            showVaultUnlock = false
                            routeStack.clear()
                            route = AppDestination.HIDDEN
                        }
                    )
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Library
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun LibraryScreen(
    links: List<LinkEntity>,
    isLoading: Boolean,
    collectionName: String,
    collectionMenu: List<String>,
    collectionNameById: Map<Long, String>,
    isGrid: Boolean,
    onCollectionSelected: (String) -> Unit,
    onCreateCollection: () -> Unit,
    onManageCollections: () -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onToggleGrid: () -> Unit,
    onAddLink: () -> Unit,
    onOpenLink: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenVault: () -> Unit
) {
    var collectionExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = PocketSpace.lg),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            LibraryTopBar(
                onSearch = onSearch,
                onProfile = onProfile,
                onOpenVault = onOpenVault
            )

            Spacer(Modifier.height(PocketSpace.sm))

            LibraryHero(
                collectionName = collectionName,
                linkCount = links.size,
                isGrid = isGrid,
                collectionExpanded = collectionExpanded,
                onToggleCollectionMenu = { collectionExpanded = true },
                onDismissCollectionMenu = { collectionExpanded = false },
                onCollectionSelected = {
                    onCollectionSelected(it)
                    collectionExpanded = false
                },
                onCreateCollection = {
                    collectionExpanded = false
                    onCreateCollection()
                },
                onManageCollections = {
                    collectionExpanded = false
                    onManageCollections()
                },
                collectionMenu = collectionMenu,
                onToggleLayout = onToggleGrid
            )

            Spacer(Modifier.height(PocketSpace.xl))

            if (links.isNotEmpty() || isLoading) {
                PocketSectionHeader(
                    title = "Recent saves",
                    subtitle = if (isLoading) "Loading your library…" else null,
                    trailing = {
                        if (links.isNotEmpty()) {
                            Text(
                                "${links.size} ${if (links.size == 1) "link" else "links"}",
                                style = PocketType.label,
                                color = PocketColors.InkFaint
                            )
                        }
                    }
                )
                // PocketSectionHeader pads its own top only, so without this the title's descenders
                // sit flush against the first card. md, not sm: the list spaces its own cards by xs
                // (6dp), so 10dp would still have read as attached. 14dp is comfortably more than
                // double the inter-card gap while staying under the xl that separates this section
                // from the hero above it — a heading wants less space above it than below.
                // Inside the same branch as the header, so the empty state — which has no header
                // above it — is not nudged off centre.
                Spacer(Modifier.height(PocketSpace.md))
            }

            if (isLoading) {
                LibrarySkeleton(isGrid = isGrid, modifier = Modifier.weight(1f).fillMaxWidth())
            } else if (links.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyLibraryState(collectionName)
                }
            } else if (isGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = LibraryBottomClearance),
                    verticalArrangement = Arrangement.spacedBy(PocketSpace.sm),
                    horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
                ) {
                    // The newest save gets a full-width feature card so the grid
                    // reads as a composed layout rather than a uniform matrix.
                    if (links.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryFeatureCard(
                                link = links.first(),
                                collectionName = collectionNameById[links.first().collectionId] ?: "Inbox",
                                onClick = { onOpenLink(links.first().id) },
                                onOpenInBrowser = { openInBrowser(context, links.first().url) },
                                onToggleFavorite = { onToggleFavorite(links.first().id) }
                            )
                        }
                    }
                    gridItemsIndexed(links.drop(1), key = { _, link -> link.id }) { _, link ->
                        LibraryLinkTile(
                            link = link,
                            collectionName = collectionNameById[link.collectionId] ?: "Inbox",
                            onClick = { onOpenLink(link.id) },
                            onOpenInBrowser = { openInBrowser(context, link.url) },
                            onToggleFavorite = { onToggleFavorite(link.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = LibraryBottomClearance),
                    verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)
                ) {
                    itemsIndexed(links, key = { _, link -> link.id }) { _, link ->
                        LibraryLinkCard(
                            link = link,
                            collectionName = collectionNameById[link.collectionId] ?: "Inbox",
                            onClick = { onOpenLink(link.id) },
                            onOpenInBrowser = { openInBrowser(context, link.url) },
                            onToggleFavorite = { onToggleFavorite(link.id) }
                        )
                    }
                }
            }
        }

        // Compact and anchored bottom-end: the strongest action, but never the
        // loudest object, and clear of both the bar and the content beneath it.
        PocketFab(
            onClick = onAddLink,
            label = "Save link",
            expanded = false,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PocketSpace.lg)
                .padding(bottom = 82.dp)
        )
    }
}

@Composable
private fun LibraryTopBar(
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onOpenVault: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = PocketSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.pressScale(0.94f)) {
            Surface(
                shape = PocketShape.squircle,
                color = PocketColors.AccentSoft,
                contentColor = PocketColors.OnAccentSoft,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PocketLogoMark(size = 22.dp)
                }
            }
        }
        Spacer(Modifier.width(PocketSpace.sm))
        Column(
            Modifier
                .weight(1f)
                // Deliberately not clipped. A 14dp rounded clip here shaved the top-left corner of
                // the "V", because the wordmark sits flush against this box's left edge with no
                // padding to keep the glyph out of the corner arc. Every other clipped element in
                // the app pairs the clip with a background and pads its content inward; there is
                // no background here for the corners to describe, and a permanently nibbled
                // wordmark is a worse trade than a rectangular long-press ripple.
                .combinedClickableVault(onOpenVault)
        ) {
            Text(
                stringResource(R.string.app_name),
                style = PocketType.title,
                color = PocketColors.Ink
            )
            Text("Your local pocket", style = PocketType.caption, color = PocketColors.InkFaint)
        }
        Spacer(Modifier.width(PocketSpace.sm))
        PocketIconButton(
            icon = Icons.Filled.Search,
            contentDescription = "Search links",
            onClick = onSearch
        )
        Spacer(Modifier.width(PocketSpace.xs))
        Box(Modifier.pressScale(0.92f)) {
            Surface(
                onClick = onProfile,
                shape = CircleShape,
                color = PocketColors.Accent,
                contentColor = PocketColors.OnAccent,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

/** Long-pressing the wordmark is the hidden gesture that opens the vault. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableVault(onOpenVault: () -> Unit): Modifier =
    this.combinedClickable(
        onLongClickLabel = "Open hidden vault",
        onLongClick = onOpenVault,
        onClick = {}
    )

/**
 * Contextual header. Compressed to a single row: it answers "which collection,
 * how much" and gets out of the way. The collection name *is* the menu trigger,
 * and the layout toggle is one icon rather than a 150dp segmented control, so
 * the saved links below get the screen instead.
 */
@Composable
private fun LibraryHero(
    collectionName: String,
    linkCount: Int,
    isGrid: Boolean,
    collectionMenu: List<String>,
    collectionExpanded: Boolean,
    onToggleCollectionMenu: () -> Unit,
    onDismissCollectionMenu: () -> Unit,
    onCollectionSelected: (String) -> Unit,
    onCreateCollection: () -> Unit,
    onManageCollections: () -> Unit,
    onToggleLayout: () -> Unit
) {
    val isFavorites = collectionName == "Favorites"
    val isAllLinks = collectionName == ALL_LINKS_LABEL
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.lg,
        color = PocketColors.SurfaceLow,
        contentColor = PocketColors.Ink,
        tonalElevation = PocketElevation.flat
    ) {
        Row(
            Modifier.padding(
                start = PocketSpace.md,
                end = PocketSpace.sm,
                top = PocketSpace.sm,
                bottom = PocketSpace.sm
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PocketColors.HeroWashStrong),
                contentAlignment = Alignment.Center
            ) {
                if (isFavorites) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = PocketColors.Amber,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isAllLinks) {
                    // A folder icon would claim a single collection this view does not have.
                    // AccountBox is already in the icon set and reads as "everything in the box".
                    Icon(
                        Icons.Filled.AccountBox,
                        contentDescription = null,
                        tint = PocketColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    PocketFolderIcon(tint = PocketColors.Accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(PocketSpace.md))
            Column(Modifier.weight(1f)) {
                Box {
                    Surface(
                        onClick = onToggleCollectionMenu,
                        shape = PocketShape.xs,
                        color = Color.Transparent,
                        contentColor = PocketColors.Ink
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .semantics {
                                    // Not "Collection" for All, because it is not one. The control
                                    // switches between a named collection and a whole-library
                                    // view, so the label has to be able to say so.
                                    contentDescription = if (isAllLinks) {
                                        "View: $collectionName"
                                    } else {
                                        "Collection: $collectionName"
                                    }
                                    role = Role.Button
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                collectionName,
                                style = PocketType.title,
                                color = PocketColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(PocketSpace.xxs))
                            PocketCaret(tint = PocketColors.InkFaint)
                        }
                    }
                    DropdownMenu(expanded = collectionExpanded, onDismissRequest = onDismissCollectionMenu) {
                        // Its own item rather than an entry in collectionMenu, so a user collection
                        // that happens to share the label keeps its own entry and stays reachable.
                        DropdownMenuItem(
                            text = { Text(ALL_LINKS_LABEL, style = PocketType.body) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.AccountBox,
                                    contentDescription = null,
                                    tint = if (isAllLinks) PocketColors.Accent else PocketColors.InkFaint,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = if (isAllLinks) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = PocketColors.Accent) }
                            } else null,
                            onClick = { onCollectionSelected(ALL_LINKS_LABEL) }
                        )
                        collectionMenu.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name, style = PocketType.body) },
                                leadingIcon = {
                                    if (name == "Favorites") {
                                        Icon(Icons.Filled.Star, contentDescription = null, tint = PocketColors.Amber)
                                    } else {
                                        PocketFolderIcon(
                                            tint = if (name == collectionName) PocketColors.Accent else PocketColors.InkFaint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                trailingIcon = if (name == collectionName) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, tint = PocketColors.Accent) }
                                } else null,
                                onClick = { onCollectionSelected(name) }
                            )
                        }
                        PocketHairline()
                        DropdownMenuItem(
                            text = { Text("Create collection", style = PocketType.label) },
                            onClick = onCreateCollection
                        )
                        DropdownMenuItem(
                            text = { Text("Manage collections", style = PocketType.label, color = PocketColors.InkMuted) },
                            onClick = onManageCollections
                        )
                    }
                }
                Text(
                    if (linkCount == 1) "1 saved link" else "$linkCount saved links",
                    style = PocketType.caption,
                    color = PocketColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(PocketSpace.xs))
            Box(Modifier.pressScale(0.9f)) {
                Surface(
                    onClick = onToggleLayout,
                    shape = CircleShape,
                    color = Color.Transparent,
                    contentColor = PocketColors.Ink,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.semantics {
                            contentDescription = if (isGrid) "Switch to list view" else "Switch to grid view"
                            role = Role.Button
                        }
                    ) {
                        if (isGrid) {
                            PocketListIcon(tint = PocketColors.Ink, modifier = Modifier.size(18.dp))
                        } else {
                            PocketGridIcon(tint = PocketColors.Ink, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(collectionName: String) {
    PocketEmptyState(
        // All is not a space, so it cannot be "ready" for one. If it is empty then every
        // collection is empty too, so the invite to save a first link is still the right one.
        title = when {
            collectionName == "Favorites" -> "No favorites yet"
            collectionName == ALL_LINKS_LABEL -> "Nothing saved yet"
            else -> "This space is ready"
        },
        body = if (collectionName == "Favorites") {
            "Star any saved link and it will collect here."
        } else {
            "Tap Save link to keep your first URL. Everything stays on this device."
        },
        icon = if (collectionName == "Favorites") Icons.Filled.Star else null,
        glyph = if (collectionName == "Favorites") null else "#",
        accent = if (collectionName == "Favorites") PocketColors.Amber else PocketColors.Accent
    )
}

/* ── Link surfaces ────────────────────────────────────────────────────────── */

@Composable
private fun LibraryFeatureCard(
    link: LinkEntity,
    collectionName: String,
    onClick: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val displayTitle = decodeHtmlEntities(link.title.ifBlank { link.url })
    val displayDescription = decodeHtmlEntities(link.description)

    // The title has to be the loudest thing in here, so the container is a
    // near-neutral wash and carries no shadow to compete with it.
    //
    // lg, not sheet. sheet (46dp) is the largest shape tier and is reserved for the one hero
    // surface on a screen; this is a list item that can be one of dozens, and at xl padding inside
    // it a 46dp radius ate nearly half the content inset and read as a giant pill. lg is what
    // LibraryLinkCard already uses, so both cards under "Recent saves" now agree.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.lg,
        color = PocketColors.HeroWashStrong,
        contentColor = PocketColors.Ink
    ) {
        Column(
            Modifier
                .pressScale(0.985f)
                .clickable(onClick = onClick)
                .padding(PocketSpace.xl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NEWEST",
                    style = PocketType.overline,
                    color = PocketColors.Accent
                )
                Spacer(Modifier.weight(1f))
                PocketRoundAction(
                    icon = Icons.Filled.Star,
                    contentDescription = if (link.isFavorite) "Remove from favorites" else "Add to favorites",
                    onClick = onToggleFavorite,
                    tint = if (link.isFavorite) PocketColors.Amber else PocketColors.InkMuted,
                    container = PocketColors.Surface
                )
            }
            Spacer(Modifier.height(PocketSpace.sm))
            Text(
                displayTitle,
                style = PocketType.headline,
                color = PocketColors.Ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(PocketSpace.xs))
            Text(
                link.domain.ifBlank { link.url },
                style = PocketType.mono,
                color = PocketColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (displayDescription.isNotBlank()) {
                Spacer(Modifier.height(PocketSpace.sm))
                Text(
                    displayDescription,
                    style = PocketType.body,
                    color = PocketColors.InkMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(PocketSpace.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PocketTagPill(collectionName)
                if (link.tags.isNotBlank()) {
                    Spacer(Modifier.width(PocketSpace.xs))
                    Text(
                        parseTags(link.tags).take(2).joinToString("  ·  "),
                        style = PocketType.caption,
                        color = PocketColors.InkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                // Not stretched: this is one item on a footer line, not the card's main content.
                // The weight spacer above pushes it to the trailing edge now that the button
                // leaves room for it — while the button was filling the row, that spacer was
                // always measuring to zero.
                PocketPrimaryButton(
                    text = "Open",
                    onClick = onOpenInBrowser,
                    fillWidth = false
                )
            }
        }
    }
}

@Composable
private fun LibraryLinkTile(
    link: LinkEntity,
    collectionName: String,
    onClick: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val displayTitle = decodeHtmlEntities(link.title.ifBlank { link.url })
    val displayDescription = decodeHtmlEntities(link.description)

    // Deliberately small: this is supporting content, so its job is to make the
    // feature card above look big by contrast, not to compete with it.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.md,
        color = PocketColors.SurfaceLow
    ) {
        Column(
            Modifier
                .pressScale(0.97f)
                .clickable(onClick = onClick)
                .padding(PocketSpace.sm),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.xxs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PocketAvatar(
                    letter = link.title.trim().firstOrNull()?.uppercase() ?: "?",
                    seed = link.domain.ifBlank { link.url },
                    size = 28.dp
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.pressScale(0.85f)) {
                        Surface(
                            onClick = onToggleFavorite,
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.semantics {
                                    contentDescription =
                                        if (link.isFavorite) "Remove from favorites" else "Add to favorites"
                                    role = Role.Button
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = if (link.isFavorite) PocketColors.Amber else PocketColors.InkFaint,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
            Text(
                displayTitle,
                style = PocketType.bodyStrong,
                color = PocketColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                link.domain.ifBlank { link.url },
                style = PocketType.mono.copy(fontSize = 10.sp),
                color = PocketColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (displayDescription.isNotBlank()) {
                Text(
                    displayDescription,
                    style = PocketType.caption,
                    color = PocketColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(PocketSpace.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PocketTagPill(collectionName, Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(PocketSpace.xs))
                Box(Modifier.pressScale(0.85f)) {
                    Surface(
                        onClick = onOpenInBrowser,
                        shape = CircleShape,
                        color = PocketColors.SurfaceHigh,
                        contentColor = PocketColors.Accent,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.semantics { contentDescription = "Open in browser" }
                        ) {
                            PocketOutboundGlyph(tint = PocketColors.Accent, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The tonal list row shared by the library, search and the hidden locker.
 */
@Composable
internal fun LibraryLinkCard(
    link: LinkEntity,
    collectionName: String,
    onClick: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onToggleFavorite: () -> Unit,
    revealContent: Boolean = true,
    onUnhide: (() -> Unit)? = null
) {
    val displayTitle = decodeHtmlEntities(link.title.ifBlank { link.url })
    val displayDescription = decodeHtmlEntities(link.description)
    val tags = remember(link.tags) { parseTags(link.tags) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.lg,
        color = PocketColors.SurfaceLow
    ) {
        Column(
            Modifier
                .pressScale(0.99f)
                .clickable(onClick = onClick)
                .padding(start = PocketSpace.md, end = PocketSpace.xs, top = PocketSpace.md, bottom = PocketSpace.md),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (revealContent) {
                    PocketAvatar(
                        letter = link.title.trim().firstOrNull()?.uppercase() ?: "?",
                        seed = link.domain.ifBlank { link.url },
                        size = 44.dp
                    )
                } else {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(PocketShape.blob)
                            .background(PocketColors.SurfaceHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Hidden link",
                            tint = PocketColors.InkFaint,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Spacer(Modifier.width(PocketSpace.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (revealContent) displayTitle else "Hidden content",
                        style = PocketType.bodyStrong,
                        color = PocketColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (revealContent) link.domain.ifBlank { link.url } else "••••••••••",
                            style = PocketType.mono,
                            color = PocketColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(PocketSpace.xs))
                        Text("·", style = PocketType.caption, color = PocketColors.Hairline)
                        Spacer(Modifier.width(PocketSpace.xs))
                        Text(
                            collectionName,
                            style = PocketType.caption.copy(fontWeight = FontWeight.Bold),
                            color = PocketColors.Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (onUnhide != null) {
                    PocketTextAction(text = "Unhide", onClick = onUnhide, tint = PocketColors.Sage)
                }
                Box(Modifier.pressScale(0.85f)) {
                    Surface(
                        onClick = onOpenInBrowser,
                        shape = CircleShape,
                        color = PocketColors.SurfaceHigh,
                        contentColor = PocketColors.Accent,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.semantics { contentDescription = "Open in browser" }
                        ) {
                            PocketOutboundGlyph(tint = PocketColors.Accent, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                Box(Modifier.pressScale(0.85f)) {
                    Surface(
                        onClick = onToggleFavorite,
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.semantics {
                                contentDescription =
                                    if (link.isFavorite) "Remove from favorites" else "Add to favorites"
                                role = Role.Button
                                this.selected = link.isFavorite
                            }
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (link.isFavorite) PocketColors.Amber else PocketColors.InkFaint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (revealContent && displayDescription.isNotBlank()) {
                Text(
                    displayDescription,
                    style = PocketType.bodySmall,
                    color = PocketColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 56.dp, end = PocketSpace.sm)
                )
            }

            if (revealContent && tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(start = 56.dp, end = PocketSpace.sm)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(PocketSpace.xxs)
                ) {
                    tags.take(3).forEach { PocketTagPill(it) }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════════
   Search
   ═════════════════════════════════════════════════════════════════════════════ */
@Composable
private fun SearchScreen(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    links: List<LinkEntity>,
    collectionMenu: List<String>,
    selectedCollection: String,
    favoritesOnly: Boolean,
    sortOrder: SortOrder,
    selectedTag: String,
    availableTags: List<String>,
    collectionNameById: Map<Long, String>,
    onBack: () -> Unit,
    onSelectCollection: (String) -> Unit,
    onSelectTag: (String) -> Unit,
    onToggleFavorites: () -> Unit,
    onClearFilters: () -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onOpenLink: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit
) {
    var collectionExpanded by remember { mutableStateOf(false) }
    var tagExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showSortDialog by remember { mutableStateOf(false) }
    val hasFilters = selectedCollection != "All collections" ||
        favoritesOnly ||
        sortOrder != SortOrder.NEWEST_FIRST ||
        selectedTag.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.pressScale(0.92f)) {
                    Surface(
                        onClick = onBack,
                        shape = PocketShape.squircle,
                        color = PocketColors.SurfaceRaised,
                        contentColor = PocketColors.Ink,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.semantics {
                                contentDescription = "Close search"
                                role = Role.Button
                            }
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(PocketSpace.sm))
                PocketSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search titles, domains, tags…",
                    leading = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = PocketColors.InkMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    },
                    onClear = { onQueryChange("") }
                )
            }

            // Filters read as a related group: one compact row, each chip
            // carrying the icon that names what it filters by.
            PocketChipRow {
                Box {
                    Surface(
                        onClick = { collectionExpanded = true },
                        shape = PocketShape.pill,
                        color = PocketColors.SurfaceRaised,
                        contentColor = PocketColors.InkMuted,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = PocketSpace.md)
                                .semantics {
                                    role = Role.Button
                                    this.selected = selectedCollection != "All collections"
                                    stateDescription =
                                        if (selectedCollection != "All collections") "Selected" else "Not selected"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PocketFolderIcon(tint = PocketColors.InkMuted, modifier = Modifier.size(15.dp))
                            Text(
                                if (selectedCollection == "All collections") "Collection" else selectedCollection,
                                style = PocketType.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    DropdownMenu(expanded = collectionExpanded, onDismissRequest = { collectionExpanded = false }) {
                        collectionMenu.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name, style = PocketType.body) },
                                trailingIcon = if (name == selectedCollection) {
                                    { Icon(Icons.Filled.Check, null, tint = PocketColors.Accent) }
                                } else null,
                                onClick = { onSelectCollection(name); collectionExpanded = false }
                            )
                        }
                    }
                }
                if (availableTags.isNotEmpty()) {
                    Box {
                        PocketPill(
                            text = if (selectedTag.isBlank()) "Tag" else "#$selectedTag",
                            selected = selectedTag.isNotBlank(),
                            height = 36.dp,
                            glyph = "#",
                            onClick = { tagExpanded = true }
                        )
                        DropdownMenu(expanded = tagExpanded, onDismissRequest = { tagExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("All tags", style = PocketType.body) },
                                trailingIcon = if (selectedTag.isBlank()) {
                                    { Icon(Icons.Filled.Check, null, tint = PocketColors.Accent) }
                                } else null,
                                onClick = { onSelectTag(""); tagExpanded = false }
                            )
                            availableTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text("#$tag", style = PocketType.body) },
                                    trailingIcon = if (tag.equals(selectedTag, true)) {
                                        { Icon(Icons.Filled.Check, null, tint = PocketColors.Accent) }
                                    } else null,
                                    onClick = { onSelectTag(tag); tagExpanded = false }
                                )
                            }
                        }
                    }
                }
                PocketPill(
                    text = "Favorites",
                    selected = favoritesOnly,
                    height = 36.dp,
                    leading = Icons.Filled.Star,
                    onClick = onToggleFavorites
                )
                PocketPill(
                    text = sortOrder.label,
                    selected = sortOrder != SortOrder.NEWEST_FIRST,
                    height = 36.dp,
                    leadingContent = { PocketSortGlyph(tint = PocketColors.InkMuted, modifier = Modifier.size(15.dp)) },
                    onClick = { showSortDialog = true }
                )
                if (hasFilters) {
                    PocketPill(
                        text = "Clear",
                        selected = false,
                        height = 36.dp,
                        onClick = onClearFilters
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = PocketSpace.lg)) {
            PocketSectionHeader(
                title = "Results",
                subtitle = when {
                    isLoading -> "Searching…"
                    links.isEmpty() -> null
                    links.size == 1 -> "1 matching link"
                    else -> "${links.size} matching links"
                }
            )
        }

        if (isLoading) {
            LibrarySkeleton(isGrid = false, modifier = Modifier.weight(1f).padding(horizontal = PocketSpace.lg))
        } else if (links.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SearchEmptyState(query = query, hasFilters = hasFilters)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = PocketSpace.lg,
                    end = PocketSpace.lg,
                    // Same reasoning as the library's "Recent saves" header. This list had no top
                    // padding at all, so the heading's descenders sat flush against the first card
                    // -- a 0dp gap against a 6dp inter-card gap. md (14dp) is comfortably more than
                    // double the gap between the cards themselves, which is what makes a heading
                    // read as a heading rather than as another item.
                    top = PocketSpace.md,
                    bottom = BottomBarClearance
                ),
                verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)
            ) {
                itemsIndexed(links, key = { _, link -> link.id }) { _, link ->
                    LibraryLinkCard(
                        link = link,
                        collectionName = collectionNameById[link.collectionId] ?: "Inbox",
                        onClick = { onOpenLink(link.id) },
                        onOpenInBrowser = { openInBrowser(context, link.url) },
                        onToggleFavorite = { onToggleFavorite(link.id) }
                    )
                }
            }
        }
    }

    if (showSortDialog) {
        SortOrderDialog(
            selectedOrder = sortOrder,
            onSelect = { onSortSelected(it); showSortDialog = false },
            onDismiss = { showSortDialog = false }
        )
    }
}

@Composable
private fun SearchEmptyState(query: String, hasFilters: Boolean) {
    PocketEmptyState(
        title = when {
            query.isNotBlank() -> "No matching links"
            hasFilters -> "Nothing matches these filters"
            else -> "No saved links yet"
        },
        body = when {
            query.isNotBlank() -> "Try a different title, domain, URL, or tag."
            hasFilters -> "Adjust the collection, favorites, or sort filters."
            else -> "Save a link from the library to start building your pocket."
        },
        icon = Icons.Filled.Search
    )
}

/* ═══════════════════════════════════════════════════════════════════════════
   Tags
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun TagManagerScreen(
    repository: LinkRepository,
    preferences: android.content.SharedPreferences,
    tags: List<String>,
    links: List<LinkEntity>,
    onBack: () -> Unit,
    onSelectTag: (String) -> Unit
) {
    var managedTags by remember(tags) {
        mutableStateOf(
            (tags + preferences.getStringSet("known-tags", emptySet()).orEmpty())
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { tag ->
                    preferences.getStringSet("hidden-tags", emptySet()).orEmpty()
                        .any { hidden -> hidden.equals(tag, ignoreCase = true) }
                }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        )
    }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var editingTag by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingTag by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val visibleLinks = remember(links) { links.filterNot { it.isHidden } }
    val counts = remember(managedTags, visibleLinks) {
        managedTags.associateWith { tag ->
            visibleLinks.count { link -> parseTags(link.tags).any { it.equals(tag, ignoreCase = true) } }
        }
    }

    fun persistCatalog(updated: List<String>) {
        managedTags = updated.map(String::trim).filter(String::isNotBlank)
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        preferences.edit().putStringSet("known-tags", managedTags.toSet()).apply()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm)) {
        PocketPageHeader(
            title = "Tags",
            onBack = onBack,
            eyebrow = "Organize",
            actions = {
                PocketCompactButton(
                    text = "New tag",
                    onClick = {
                        error = null
                        showCreate = true
                    },
                    icon = Icons.Filled.Add
                )
            }
        )

        Spacer(Modifier.height(PocketSpace.md))

        Text(
            "Labels shared across your library. Tap a tag to see every link that uses it.",
            style = PocketType.body,
            color = PocketColors.InkMuted
        )

        Spacer(Modifier.height(PocketSpace.lg))

        if (managedTags.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PocketEmptyState(
                    title = "No tags yet",
                    body = "Create a tag, then assign it while editing a saved link.",
                    glyph = "#"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = PocketSpace.xxl),
                verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)
            ) {
                itemsIndexed(managedTags, key = { _, tag -> tag.lowercase() }) { _, tag ->
                    TagRowCard(
                        tag = tag,
                        count = counts[tag] ?: 0,
                        onSelect = { onSelectTag(tag) },
                        onRename = { error = null; editingTag = tag },
                        onDelete = { error = null; deletingTag = tag }
                    )
                }
            }
        }
    }

    if (showCreate) {
        TagNameDialog(
            title = "Create tag",
            confirmLabel = "Create",
            initialValue = "",
            error = error,
            onDismiss = { showCreate = false; error = null },
            onConfirm = { value ->
                val normalized = value.trim()
                when {
                    normalized.isBlank() -> error = "Enter a tag name"
                    normalized.length > 40 -> error = "Tag name is too long"
                    normalized.contains(",") -> error = "Use one tag at a time"
                    managedTags.any { it.equals(normalized, true) } ||
                        preferences.getStringSet("hidden-tags", emptySet()).orEmpty().any { it.equals(normalized, true) } ->
                        error = "That tag is managed by the Hidden Locker"
                    else -> {
                        persistCatalog(managedTags + normalized)
                        showCreate = false
                        error = null
                    }
                }
            }
        )
    }

    editingTag?.let { current ->
        TagNameDialog(
            title = "Rename tag",
            confirmLabel = "Rename",
            initialValue = current,
            error = error,
            onDismiss = { editingTag = null; error = null },
            onConfirm = { value ->
                val normalized = value.trim()
                when {
                    normalized.isBlank() -> error = "Enter a tag name"
                    normalized.length > 40 -> error = "Tag name is too long"
                    normalized.contains(",") -> error = "Use one tag at a time"
                    managedTags.any { it.equals(normalized, true) && !it.equals(current, true) } -> error = "That tag already exists"
                    else -> scope.launch {
                        repository.renameTag(current, normalized)
                            .onSuccess {
                                persistCatalog(managedTags.map { if (it.equals(current, true)) normalized else it })
                                editingTag = null
                                error = null
                            }
                            .onFailure { error = it.message ?: "Unable to rename tag" }
                    }
                }
            }
        )
    }

    deletingTag?.let { current ->
        ConfirmTagDeleteDialog(
            tag = current,
            onDismiss = { deletingTag = null },
            onConfirm = {
                scope.launch {
                    repository.deleteTag(current)
                        .onSuccess {
                            persistCatalog(managedTags.filterNot { it.equals(current, true) })
                            deletingTag = null
                        }
                        .onFailure { error = it.message ?: "Unable to delete tag" }
                }
            }
        )
    }
}

@Composable
private fun TagRowCard(
    tag: String,
    count: Int,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.lg,
        color = PocketColors.SurfaceLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .pressScale(0.99f)
                .clickable(onClick = onSelect)
                .padding(start = PocketSpace.md, end = PocketSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PocketIconBadge(glyph = "#", accent = PocketColors.Accent, size = 40.dp, shape = CircleShape)
            Spacer(Modifier.width(PocketSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    tag,
                    style = PocketType.bodyStrong,
                    color = PocketColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (count == 1) "1 link" else "$count links",
                    style = PocketType.caption,
                    color = PocketColors.InkMuted
                )
            }
            PocketRoundAction(
                icon = Icons.Filled.Edit,
                contentDescription = "Rename $tag",
                onClick = onRename,
                tint = PocketColors.InkMuted,
                container = PocketColors.SurfaceHigh,
                size = 40.dp
            )
            PocketRoundAction(
                icon = Icons.Filled.Delete,
                contentDescription = "Delete $tag",
                onClick = onDelete,
                tint = PocketColors.Danger,
                container = PocketColors.SurfaceHigh,
                size = 40.dp
            )
        }
    }
}

@Composable
private fun TagNameDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable(title, initialValue) { mutableStateOf(initialValue) }
    PocketDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Filled.Edit
    ) {
        PocketFormField(value, { value = it }, "e.g. Android", isError = error != null)
        error?.let { PocketBanner(message = it) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = PocketSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
        ) {
            Box(Modifier.weight(1f)) { PocketTonalButton(text = "Cancel", onClick = onDismiss) }
            Box(Modifier.weight(1f)) { PocketPrimaryButton(text = confirmLabel, onClick = { onConfirm(value) }) }
        }
    }
}

@Composable
private fun ConfirmTagDeleteDialog(
    tag: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    PocketDialog(
        onDismissRequest = onDismiss,
        title = "Delete #${confirmPreview(tag, 48)}?",
        body = "This removes the tag from every saved link. It does not delete the links.",
        icon = Icons.Filled.Delete,
        destructive = true,
        confirmLabel = "Delete",
        onConfirm = onConfirm
    )
}

@Composable
private fun SortOrderDialog(selectedOrder: SortOrder, onSelect: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    PocketDialog(onDismissRequest = onDismiss, title = "Sort links") {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(PocketShape.lg)
                .background(PocketColors.SurfaceLow)
        ) {
            SortOrder.entries.forEach { order ->
                val active = order == selectedOrder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .pressScale(0.99f)
                        .clickable { onSelect(order) }
                        .padding(horizontal = PocketSpace.md)
                        .semantics {
                            role = Role.RadioButton
                            this.selected = active
                            stateDescription = if (active) "Selected" else "Not selected"
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        order.label,
                        style = if (active) PocketType.bodyStrong else PocketType.body,
                        color = if (active) PocketColors.Ink else PocketColors.InkMuted,
                        modifier = Modifier.weight(1f)
                    )
                    if (active) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = PocketColors.Accent)
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Collections
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun CollectionsScreen(
    collections: List<CollectionManagementItem>,
    onBack: () -> Unit,
    onNewCollection: () -> Unit,
    onSelectCollection: (String) -> Unit,
    onRename: (CollectionManagementItem) -> Unit,
    onDelete: (CollectionManagementItem) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm)) {
        PocketPageHeader(
            title = "Collections",
            onBack = onBack,
            eyebrow = "Spaces",
            actions = {
                PocketIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "New collection",
                    onClick = onNewCollection,
                    container = PocketColors.Accent,
                    tint = PocketColors.OnAccent,
                    selected = true
                )
            }
        )

        Spacer(Modifier.height(PocketSpace.md))

        CollectionsHero(collectionCount = collections.size, onNewCollection = onNewCollection)

        Spacer(Modifier.height(PocketSpace.xl))

        if (collections.isNotEmpty()) {
            PocketSectionLabel("All spaces")
        }

        if (collections.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PocketEmptyState(
                    title = "Keep links organized",
                    body = "Create a collection when you want a separate space for a group of saved links.",
                    icon = Icons.Filled.AccountBox
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                // "All spaces" is a heading, and this had no top padding, so it sat flush against
                // the first row -- a 0dp gap against a 6dp gap between the rows themselves. Same
                // fix as the library and search headings. This list only renders when there is at
                // least one collection, which is exactly when the label above it renders too, so
                // the centred empty state is untouched.
                contentPadding = PaddingValues(top = PocketSpace.md, bottom = PocketSpace.xxl),
                verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)
            ) {
                itemsIndexed(collections, key = { _, item -> item.id }) { _, collection ->
                    CollectionItemRow(
                        collection = collection,
                        onSelect = { onSelectCollection(collection.name) },
                        onRename = { onRename(collection) },
                        onDelete = { onDelete(collection) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionsHero(collectionCount: Int, onNewCollection: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.xxl,
        color = PocketColors.AccentSoft,
        contentColor = PocketColors.OnAccentSoft
    ) {
        Row(
            Modifier.padding(PocketSpace.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(PocketShape.pebble)
                    .background(PocketColors.OnAccentSoft.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                PocketFolderIcon(tint = PocketColors.OnAccentSoft, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(PocketSpace.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    if (collectionCount == 1) "1 space" else "$collectionCount spaces",
                    style = PocketType.title,
                    color = PocketColors.OnAccentSoft
                )
                Text(
                    "Group links into folders of their own.",
                    style = PocketType.bodySmall,
                    color = PocketColors.OnAccentSoft.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(PocketSpace.md))
                Surface(
                    onClick = onNewCollection,
                    shape = PocketShape.pill,
                    // primary/onPrimary, not onPrimaryContainer/primary. The container colour is
                    // already the hero's background, so using it as this button's fill put Accent
                    // on top of its own light container: 2.4:1 in light, 1.3:1 in dark, which is
                    // what made the primary action on this screen look disabled. This also matches
                    // the + in the page header, so the two add affordances now read as one idea.
                    color = PocketColors.Accent,
                    contentColor = PocketColors.OnAccent,
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = PocketSpace.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New collection", style = PocketType.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionItemRow(
    collection: CollectionManagementItem,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = if (collection.isInbox) PocketColors.Accent else PocketColors.Sage
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.lg,
        color = PocketColors.SurfaceLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .pressScale(0.99f)
                .clickable(onClick = onSelect)
                .padding(start = PocketSpace.md, end = PocketSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PocketIconBadge(
                accent = accent,
                size = 42.dp
            ) {
                PocketFolderIcon(tint = accent)
            }
            Spacer(Modifier.width(PocketSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    collection.name,
                    style = PocketType.bodyStrong,
                    color = PocketColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (collection.linkCount == 1) "1 saved link" else "${collection.linkCount} saved links",
                    style = PocketType.caption,
                    color = PocketColors.InkMuted
                )
            }
            if (collection.isInbox) {
                Surface(
                    shape = PocketShape.pill,
                    color = PocketColors.SurfaceHigh,
                    contentColor = PocketColors.InkMuted
                ) {
                    Text(
                        "Default",
                        style = PocketType.caption,
                        modifier = Modifier.padding(horizontal = PocketSpace.sm, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(PocketSpace.xs))
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Inbox cannot be renamed or deleted",
                    tint = PocketColors.InkFaint,
                    modifier = Modifier.size(18.dp).padding(end = 2.dp)
                )
                Spacer(Modifier.width(PocketSpace.sm))
            } else {
                PocketRoundAction(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Rename ${collection.name}",
                    onClick = onRename,
                    container = PocketColors.SurfaceHigh,
                    size = 40.dp
                )
                PocketRoundAction(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Delete ${collection.name}",
                    onClick = onDelete,
                    tint = PocketColors.Danger,
                    container = PocketColors.SurfaceHigh,
                    size = 40.dp
                )
                Spacer(Modifier.width(PocketSpace.xxs))
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Link editor
   ═══════════════════════════════════════════════════════════════════════════ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkEditorScreen(
    screenKey: String,
    repository: LinkRepository,
    isEditing: Boolean,
    initialUrl: String,
    initialCollection: String,
    initialLink: LinkEntity?,
    collections: List<String>,
    collectionItems: List<CollectionManagementItem>,
    isSaving: Boolean,
    errorMessage: String?,
    noticeMessage: String?,
    onBack: () -> Unit,
    onClearMessages: () -> Unit,
    onSave: (url: String, title: String, description: String, tags: String, collection: String) -> Unit,
    onRefresh: (Long) -> Unit,
    onUpdate: (LinkEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    key(screenKey) {
        val context = LocalContext.current
        var url by remember { mutableStateOf(initialUrl) }
        var title by remember { mutableStateOf(initialLink?.title.orEmpty()) }
        var description by remember { mutableStateOf(initialLink?.description.orEmpty()) }
        var tags by remember { mutableStateOf(initialLink?.tags.orEmpty()) }
        var selectedCollection by remember {
            mutableStateOf(collectionItems.firstOrNull { it.id == initialLink?.collectionId }?.name ?: initialCollection)
        }
        var collectionExpanded by remember { mutableStateOf(false) }
        var autoFetched by remember { mutableStateOf(false) }
        var titleWasEdited by remember { mutableStateOf(isEditing) }
        var domainPreview by remember { mutableStateOf(initialLink?.domain.orEmpty()) }
        var refreshQueued by rememberSaveable { mutableStateOf(false) }
        var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(url, isEditing) {
            if (isEditing) return@LaunchedEffect
            val normalized = url.trim()
            if (UrlValidator.validate(normalized).isFailure) return@LaunchedEffect
            delay(650)
            val metadata = repository.previewMetadata(normalized).getOrNull() ?: return@LaunchedEffect
            domainPreview = metadata.domain
            if (!titleWasEdited && !metadata.title.isNullOrBlank()) {
                title = metadata.title
                autoFetched = true
            }
            if (description.isBlank() && !metadata.description.isNullOrBlank()) description = metadata.description
        }

        Column(Modifier.fillMaxSize().padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm)) {
            PocketPageHeader(
                title = if (isEditing) "Edit link" else "Add link",
                onBack = onBack,
                eyebrow = if (isEditing) "Details" else "New save",
                actions = {
                    if (isEditing && initialLink != null) {
                        PocketIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Delete link",
                            onClick = { showDeleteConfirm = true },
                            container = PocketColors.DangerSoft,
                            tint = PocketColors.Danger
                        )
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = PocketSpace.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
            ) {
                if (isEditing) {
                    PocketFieldLabel("URL")
                    PocketFormField(url, {}, "https://example.com", readOnly = true)
                } else {
                    PocketFieldLabel("URL")
                    PocketFormField(
                        value = url,
                        onValueChange = {
                            url = it
                            title = ""
                            description = ""
                            titleWasEdited = false
                            autoFetched = false
                            domainPreview = ""
                            onClearMessages()
                        },
                        placeholder = "Paste a link here",
                        isError = errorMessage != null,
                        leadingContent = {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = PocketColors.InkFaint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                if (!isEditing && autoFetched && domainPreview.isNotBlank()) {
                    MetadataPreview(
                        domain = domainPreview,
                        title = title,
                        description = description
                    )
                }

                PocketFieldLabel("Title", if (autoFetched) "Auto-fetched" else null)
                PocketFormField(
                    value = title,
                    onValueChange = { title = it; titleWasEdited = true; autoFetched = false; onClearMessages() },
                    placeholder = "Add a title"
                )

                PocketFieldLabel("Description")
                PocketFormField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "Add a description",
                    singleLine = false,
                    maxLines = 3
                )

                PocketFieldLabel("Collection")
                CollectionField(
                    selected = selectedCollection,
                    expanded = collectionExpanded,
                    collections = collections,
                    onClick = { collectionExpanded = true },
                    onDismiss = { collectionExpanded = false },
                    onSelect = {
                        selectedCollection = it
                        collectionExpanded = false
                    }
                )

                PocketFieldLabel("Tags")
                PocketFormField(value = tags, onValueChange = { tags = it }, placeholder = "Comma separated, e.g. design, read")
                if (parseTags(tags).isNotEmpty()) {
                    PocketChipRow {
                        parseTags(tags).take(5).forEach { PocketTagPill(it) }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    PocketBanner(
                        message = errorMessage,
                        modifier = Modifier.semantics {
                            contentDescription = "Validation error: $errorMessage"
                            stateDescription = "Error"
                        }
                    )
                } else if (!noticeMessage.isNullOrBlank()) {
                    // Neutral styling, and deliberately no stateDescription = "Error": a duplicate
                    // is not something that went wrong. The URL field above is likewise left
                    // unstyled, since the URL was valid and it is the app's library that already
                    // has it.
                    PocketBanner(
                        message = noticeMessage,
                        icon = Icons.Filled.Info,
                        accent = PocketColors.Sage,
                        container = PocketColors.SageSoft,
                        onAccent = PocketColors.OnSageSoft,
                        modifier = Modifier.semantics { contentDescription = noticeMessage }
                    )
                }
            }

            if (isEditing && initialLink != null) {
                Spacer(Modifier.height(PocketSpace.md))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PocketSpace.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EditorAction(Icons.Filled.Share, "Share", Modifier.weight(1f)) { shareLink(context, url) }
                    EditorAction(null, "Copy", Modifier.weight(1f), glyph = { tint -> PocketCopyGlyph(tint) }) { copyLink(context, url) }
                    EditorAction(null, "Open", Modifier.weight(1f), glyph = { tint -> PocketOutboundGlyph(tint) }) { openInBrowser(context, url) }
                    EditorAction(Icons.Filled.Refresh, "Refresh", Modifier.weight(1f)) {
                        onRefresh(initialLink.id)
                        refreshQueued = true
                    }
                }
                if (refreshQueued) {
                    LaunchedEffect(refreshQueued) { delay(2_500); refreshQueued = false }
                    Text(
                        "Metadata refresh queued",
                        style = PocketType.caption,
                        color = PocketColors.InkMuted,
                        modifier = Modifier.padding(top = PocketSpace.xs)
                    )
                }
            }

            Spacer(Modifier.height(PocketSpace.md))

            PocketPrimaryButton(
                text = if (isSaving) "Saving…" else if (isEditing) "Save changes" else "Save link",
                onClick = {
                    if (isEditing && initialLink != null) {
                        val collectionId = collectionItems.firstOrNull { it.name.equals(selectedCollection, true) }?.id
                            ?: initialLink.collectionId
                        onUpdate(initialLink.copy(title = title, description = description, tags = tags, collectionId = collectionId))
                    } else {
                        onSave(url, title, description, tags, selectedCollection)
                    }
                },
                enabled = !isSaving
            )
            Spacer(Modifier.height(PocketSpace.sm))
        }

        if (showDeleteConfirm && initialLink != null) {
            ConfirmActionDialog(
                title = "Delete link?",
                body = "“${confirmPreview(initialLink.title.ifBlank { initialLink.url })}” will be permanently removed. This can't be undone.",
                confirmLabel = "Delete",
                destructive = true,
                error = null,
                onDismiss = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    onDelete(initialLink.id)
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun MetadataPreview(domain: String, title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.lg,
        color = PocketColors.SageSoft
    ) {
        Row(
            Modifier.padding(PocketSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(PocketShape.pebble)
                    .background(PocketColors.Sage.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PocketColors.Sage, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(PocketSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "Preview from $domain",
                    style = PocketType.label,
                    color = PocketColors.OnSageSoft
                )
                Text(
                    description.ifBlank { title.ifBlank { "Fetching title and description…" } },
                    style = PocketType.caption,
                    color = PocketColors.OnSageSoft.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CollectionField(
    selected: String,
    expanded: Boolean,
    collections: List<String>,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Box {
        Box(Modifier.pressScale(0.99f)) {
            Surface(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .semantics {
                        contentDescription = "Collection: $selected"
                        role = Role.Button
                    },
                shape = PocketShape.md,
                color = PocketColors.SurfaceLowest
            ) {
                Row(
                    Modifier.padding(horizontal = PocketSpace.md, vertical = PocketSpace.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PocketFolderIcon(tint = PocketColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(PocketSpace.sm))
                    Text(
                        selected,
                        style = PocketType.body,
                        color = PocketColors.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    PocketCaret()
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            collections.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, style = PocketType.body) },
                    trailingIcon = if (name == selected) {
                        { Icon(Icons.Filled.Check, null, tint = PocketColors.Accent) }
                    } else null,
                    onClick = { onSelect(name) }
                )
            }
        }
    }
}

@Composable
private fun EditorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    modifier: Modifier = Modifier,
    glyph: @Composable ((Color) -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(modifier.pressScale(0.94f)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.height(46.dp),
            shape = PocketShape.pill,
            color = PocketColors.SurfaceRaised,
            contentColor = PocketColors.Ink
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PocketSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = PocketColors.Accent, modifier = Modifier.size(17.dp))
                } else if (glyph != null) {
                    glyph(PocketColors.Accent)
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = PocketType.caption, maxLines = 1)
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Profile
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun ProfileScreen(
    totalLinks: Int,
    favorites: Int,
    collections: Int,
    themeMode: String,
    materialYou: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onFavorites: () -> Unit,
    onCollections: () -> Unit,
    onTags: () -> Unit,
    onImportExport: () -> Unit,
    onAppearance: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm)) {
        PocketPageHeader(
            title = "Profile",
            onBack = onBack,
            eyebrow = "You",
            actions = {
                PocketIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    onClick = onSettings
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = PocketSpace.lg)
                .padding(bottom = BottomBarClearance)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
        ) {
            ProfileHero()

            PocketSectionLabel("Library pulse")

            // One statement about the pocket rather than a row of metric cards.
            // The supporting counts live inside it so they cannot drift off and
            // leave a hole in the page.
            PocketSummarySheet(
                value = totalLinks.toString(),
                headline = if (totalLinks == 1) "Saved link" else "Saved links",
                eyebrow = "Your pocket",
                parts = listOf(
                    "Favorites" to favorites.toString(),
                    "Collections" to collections.toString()
                ),
                accent = PocketColors.Accent
            )

            PocketSectionLabel("Quick access")

            PocketGroup {
                Column {
                    PocketListRow(
                        title = "Favorites",
                        subtitle = "Jump into starred links",
                        icon = Icons.Filled.Star,
                        accent = PocketColors.Amber,
                        onClick = onFavorites,
                        trailing = { PocketChevron() }
                    )
                    PocketHairline(inset = 68.dp)
                    PocketListRow(
                        title = "Collections",
                        subtitle = "Browse your saved spaces",
                        icon = Icons.Filled.AccountBox,
                        accent = PocketColors.Sage,
                        onClick = onCollections,
                        trailing = { PocketChevron() }
                    )
                    PocketHairline(inset = 68.dp)
                    PocketListRow(
                        title = "Tags",
                        subtitle = "Organize links with labels",
                        glyph = "#",
                        accent = PocketColors.Accent,
                        onClick = onTags,
                        trailing = { PocketChevron() }
                    )
                    PocketHairline(inset = 68.dp)
                    PocketListRow(
                        title = "Import & export",
                        subtitle = "Move or back up your library",
                        icon = Icons.Filled.Info,
                        accent = PocketColors.Accent,
                        onClick = onImportExport,
                        trailing = { PocketChevron() }
                    )
                }
            }

            PocketSectionLabel("Personalize")

            PocketGroup {
                PocketListRow(
                    title = "Appearance",
                    subtitle = if (materialYou) "Material You" else themeMode,
                    glyph = "✦",
                    accent = PocketColors.Accent,
                    onClick = onAppearance,
                    trailing = { PocketChevron() }
                )
            }

            Text(
                "Everything stays on this device.",
                style = PocketType.caption,
                color = PocketColors.InkFaint,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = PocketSpace.xs)
            )
        }
    }
}

@Composable
private fun ProfileHero() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.xxl,
        color = PocketColors.AccentSoft,
        contentColor = PocketColors.OnAccentSoft
    ) {
        Row(
            Modifier.padding(PocketSpace.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(66.dp)
                    .clip(PocketShape.blob)
                    .background(PocketColors.OnAccentSoft.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                PocketLogoMark(size = 34.dp)
            }
            Spacer(Modifier.width(PocketSpace.lg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PocketSpace.xxs)) {
                Text(
                    "Your pocket",
                    style = PocketType.headlineSmall,
                    color = PocketColors.OnAccentSoft
                )
                Text(
                    "Local-first. No account, no cloud.",
                    style = PocketType.bodySmall,
                    color = PocketColors.OnAccentSoft.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(PocketSpace.xs))
                Surface(
                    shape = PocketShape.pill,
                    color = PocketColors.Sage,
                    contentColor = PocketColors.OnSage
                ) {
                    Text(
                        "PRIVATE",
                        style = PocketType.caption.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.padding(horizontal = PocketSpace.sm, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Loading
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun LibrarySkeleton(isGrid: Boolean, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "librarySkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = pocketTween(1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "librarySkeletonAlpha"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)
    ) {
        repeat(if (isGrid) 5 else 6) {
            PocketSkeletonRow(alpha)
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Add link sheet
   ═══════════════════════════════════════════════════════════════════════════ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLinkSheet(
    initialUrl: String,
    initialCollection: String,
    collections: List<String>,
    isSaving: Boolean,
    errorMessage: String?,
    noticeMessage: String?,
    repository: LinkRepository,
    onDismiss: () -> Unit,
    onClearMessages: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var title by rememberSaveable(initialUrl) { mutableStateOf("") }
    var description by rememberSaveable(initialUrl) { mutableStateOf("") }
    var tags by rememberSaveable(initialUrl) { mutableStateOf("") }
    var selectedCollection by rememberSaveable(initialUrl) { mutableStateOf(initialCollection) }
    var collectionExpanded by remember { mutableStateOf(false) }
    var previewLoading by remember(initialUrl) { mutableStateOf(false) }
    var previewDomain by remember(initialUrl) { mutableStateOf("") }
    var previewLoaded by remember(initialUrl) { mutableStateOf(false) }

    LaunchedEffect(url) {
        val normalized = url.trim()
        if (UrlValidator.validate(normalized).isFailure) {
            previewLoading = false
            previewLoaded = false
            return@LaunchedEffect
        }
        delay(450)
        previewLoading = true
        val metadata = repository.previewMetadata(normalized).getOrNull()
        previewLoading = false
        if (metadata != null) {
            previewLoaded = true
            previewDomain = metadata.domain
            if (title.isBlank()) title = metadata.title.orEmpty()
            if (description.isBlank()) description = metadata.description.orEmpty()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PocketColors.Canvas,
        contentColor = PocketColors.Ink,
        shape = PocketShape.xxl,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = PocketSpace.md)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(PocketColors.Hairline)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Let the IME reduce the sheet's usable height so the whole
                // bottom sheet moves above the keyboard instead of being covered.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PocketSpace.lg)
                .padding(top = PocketSpace.md, bottom = PocketSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add a link", style = PocketType.headlineSmall, color = PocketColors.Ink)
                    Text(
                        "Save it now — details can finish in the background.",
                        style = PocketType.bodySmall,
                        color = PocketColors.InkMuted
                    )
                }
                PocketRoundAction(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    container = PocketColors.SurfaceRaised,
                    tint = PocketColors.InkMuted
                )
            }

            PocketFieldLabel("URL")
            PocketFormField(
                value = url,
                onValueChange = {
                    url = it
                    title = ""
                    description = ""
                    previewLoaded = false
                    previewDomain = ""
                    onClearMessages()
                },
                placeholder = "Paste a link here",
                isError = errorMessage != null
            )

            if (previewLoading) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(PocketShape.lg)
                        .background(PocketColors.SurfaceLow)
                        .padding(PocketSpace.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBlock(42.dp, 42.dp, PocketShape.pebble)
                    Spacer(Modifier.width(PocketSpace.md))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)) {
                        SkeletonBlock(160.dp, 13.dp)
                        SkeletonBlock(90.dp, 11.dp)
                    }
                }
            } else if (previewLoaded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PocketShape.lg,
                    color = PocketColors.SageSoft
                ) {
                    Row(
                        Modifier.padding(PocketSpace.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = PocketColors.Sage,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(PocketSpace.sm))
                        Text(
                            if (previewDomain.isBlank()) "Preview ready" else "Preview from $previewDomain",
                            style = PocketType.label,
                            color = PocketColors.OnSageSoft
                        )
                    }
                }
            }

            PocketFieldLabel("Title")
            PocketFormField(
                value = title,
                onValueChange = { title = it; onClearMessages() },
                placeholder = "Optional title"
            )

            PocketFieldLabel("Collection")
            CollectionField(
                selected = selectedCollection,
                expanded = collectionExpanded,
                collections = collections,
                onClick = { collectionExpanded = true },
                onDismiss = { collectionExpanded = false },
                onSelect = {
                    selectedCollection = it
                    collectionExpanded = false
                }
            )

            PocketFieldLabel("Tags")
            PocketFormField(
                value = tags,
                onValueChange = { tags = it },
                placeholder = "Optional · comma separated"
            )
            if (parseTags(tags).isNotEmpty()) {
                PocketChipRow {
                    parseTags(tags).take(5).forEach { PocketTagPill(it) }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                PocketBanner(message = errorMessage)
            } else if (!noticeMessage.isNullOrBlank()) {
                // Neutral styling for the same reason as the editor screen above: a duplicate is a
                // normal outcome, not a failure, and the URL field is left unstyled.
                PocketBanner(
                    message = noticeMessage,
                    icon = Icons.Filled.Info,
                    accent = PocketColors.Sage,
                    container = PocketColors.SageSoft,
                    onAccent = PocketColors.OnSageSoft
                )
            }

            PocketPrimaryButton(
                text = if (isSaving) "Saving…" else "Save link",
                onClick = { onSave(url, title, description, tags, selectedCollection) },
                enabled = !isSaving && UrlValidator.validate(url.trim()).isSuccess
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Settings
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun SettingsScreen(
    totalLinks: Int,
    favorites: Int,
    themeMode: String,
    materialYou: Boolean,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onImportExport: () -> Unit,
    onCollections: () -> Unit,
    onClearAll: () -> Unit,
    availableUpdate: AppUpdate?,
    updateChecking: Boolean,
    updateDownloading: Boolean,
    updateError: String?,
    canInstallUpdates: Boolean,
    onCheckForUpdate: () -> Unit,
    onUpdate: () -> Unit,
    onAllowInstall: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
        ) {
            PocketPageHeader(title = "Settings", onBack = onBack, eyebrow = "Preferences")

            SettingsHero(themeMode = themeMode, materialYou = materialYou)

            PocketSectionLabel("At a glance")

            // One dominant tile carrying two smaller ones, instead of three
            // equal cards that all shout equally.
            PocketDominantStat(
                value = totalLinks.toString(),
                label = "Links",
                icon = Icons.Filled.Home,
                accent = PocketColors.Accent
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)) {
                PocketCompactStat(
                    value = favorites.toString(),
                    label = "Favorites",
                    icon = Icons.Filled.Star,
                    accent = PocketColors.Amber,
                    modifier = Modifier.weight(1f)
                )
                PocketCompactStat(
                    value = if (materialYou) "Auto" else themeMode,
                    label = "Theme",
                    icon = Icons.Filled.Settings,
                    accent = PocketColors.Sage,
                    modifier = Modifier.weight(1f)
                )
            }

            PocketSectionLabel("Library")

            PocketGroup {
                Column {
                    PocketListRow(
                        title = "Collections",
                        subtitle = "Rename, delete, or create a space",
                        icon = Icons.Filled.AccountBox,
                        accent = PocketColors.Accent,
                        onClick = onCollections,
                        trailing = { PocketChevron() }
                    )
                    PocketHairline(inset = 68.dp)
                    PocketListRow(
                        title = "Import & export",
                        subtitle = "JSON backup, CSV export, restore",
                        icon = Icons.Filled.Info,
                        accent = PocketColors.Sage,
                        onClick = onImportExport,
                        trailing = { PocketChevron() }
                    )
                }
            }

            PocketSectionLabel("Personalize")

            PocketGroup {
                PocketListRow(
                    title = "Appearance",
                    subtitle = if (materialYou) "Material You" else themeMode,
                    glyph = "✦",
                    accent = PocketColors.Accent,
                    onClick = onAppearance,
                    trailing = { PocketChevron() }
                )
            }

            PocketSectionLabel("About")

            UpdateSurface(
                availableUpdate = availableUpdate,
                updateChecking = updateChecking,
                updateDownloading = updateDownloading,
                updateError = updateError,
                canInstallUpdates = canInstallUpdates,
                onCheckForUpdate = onCheckForUpdate,
                onUpdate = onUpdate,
                onAllowInstall = onAllowInstall
            )

            PocketGroup {
                PocketListRow(
                    title = "Version",
                    subtitle = "Voket build",
                    icon = Icons.Filled.Info,
                    accent = PocketColors.InkMuted,
                    trailing = {
                        Text(
                            "v" + BuildConfig.VERSION_NAME,
                            style = PocketType.mono,
                            color = PocketColors.InkFaint
                        )
                    }
                )
            }

            Surface(
                onClick = onClearAll,
                modifier = Modifier.fillMaxWidth(),
                shape = PocketShape.lg,
                color = PocketColors.DangerSoft
            ) {
                Row(
                    Modifier.padding(horizontal = PocketSpace.md, vertical = PocketSpace.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PocketIconBadge(
                        icon = Icons.Filled.Delete,
                        accent = PocketColors.Danger,
                        size = 42.dp,
                        container = PocketColors.Danger.copy(alpha = 0.14f)
                    )
                    Spacer(Modifier.width(PocketSpace.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Clear all data",
                            style = PocketType.bodyStrong,
                            color = PocketColors.Danger
                        )
                        Text(
                            "Remove every saved link and custom collection",
                            style = PocketType.caption,
                            color = PocketColors.OnDangerSoft.copy(alpha = 0.8f)
                        )
                    }
                    PocketChevron(tint = PocketColors.Danger)
                }
            }

            Text(
                "Local-first · stored on this device",
                style = PocketType.caption,
                color = PocketColors.InkFaint,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(PocketSpace.sm))
        }
    }
}

/**
 * The app's one true accent block, and deliberately the only one. Everything
 * else in the app earns colour from tonal surfaces, so the primary family
 * stays reserved for real emphasis rather than becoming the page background.
 */
@Composable
private fun SettingsHero(themeMode: String, materialYou: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.sheet,
        color = PocketColors.HeroWash,
        contentColor = PocketColors.Ink
    ) {
        Row(
            Modifier.padding(PocketSpace.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(PocketShape.blob)
                    .background(PocketColors.Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                PocketLogoMark(size = 30.dp)
            }
            Spacer(Modifier.width(PocketSpace.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = PocketType.display.copy(fontSize = PocketType.display.fontSize * 0.72f),
                    color = PocketColors.Ink
                )
                Text(
                    "Your saved space, your way.",
                    style = PocketType.bodySmall,
                    color = PocketColors.InkMuted
                )
                Spacer(Modifier.height(PocketSpace.sm))
                Surface(
                    shape = PocketShape.pill,
                    color = PocketColors.Accent,
                    contentColor = PocketColors.OnAccent
                ) {
                    Text(
                        if (materialYou) "Material You" else themeMode,
                        style = PocketType.caption,
                        modifier = Modifier.padding(horizontal = PocketSpace.sm, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateSurface(
    availableUpdate: AppUpdate?,
    updateChecking: Boolean,
    updateDownloading: Boolean,
    updateError: String?,
    canInstallUpdates: Boolean,
    onCheckForUpdate: () -> Unit,
    onUpdate: () -> Unit,
    onAllowInstall: () -> Unit
) {
    val ready = availableUpdate != null
    val accent = if (ready) PocketColors.Accent else PocketColors.Sage
    val container = if (ready) PocketColors.AccentSoft else PocketColors.SageSoft
    val onContainer = if (ready) PocketColors.OnAccentSoft else PocketColors.OnSageSoft

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PocketShape.xl,
        color = container
    ) {
        Column(
            Modifier.padding(PocketSpace.lg),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PocketIconBadge(
                    icon = if (ready) Icons.Filled.Warning else Icons.Filled.Check,
                    accent = accent,
                    size = 44.dp,
                    container = accent.copy(alpha = 0.16f)
                )
                Spacer(Modifier.width(PocketSpace.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (ready) "Update available" else "Updates",
                        style = PocketType.titleSmall,
                        color = onContainer
                    )
                    Text(
                        when {
                            updateDownloading -> "Downloading " + (availableUpdate?.versionName ?: "") + "…"
                            updateChecking -> "Checking for updates…"
                            ready -> "Voket " + availableUpdate.versionName + " is ready"
                            updateError != null -> updateError
                            else -> "You're up to date"
                        },
                        style = PocketType.bodySmall,
                        color = onContainer.copy(alpha = 0.82f)
                    )
                }
            }
            if (ready && !canInstallUpdates) {
                Text(
                    "Android needs permission to install updates from Voket.",
                    style = PocketType.caption,
                    color = onContainer.copy(alpha = 0.8f)
                )
            }
            if (availableUpdate?.notes?.isNotBlank() == true) {
                Text(
                    availableUpdate.notes.lineSequence().firstOrNull().orEmpty().take(120),
                    style = PocketType.caption,
                    color = onContainer.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)) {
                if (ready && !canInstallUpdates) {
                    Box(Modifier.weight(1f)) {
                        PocketTonalButton(
                            text = "Allow installs",
                            onClick = onAllowInstall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    PocketPrimaryButton(
                        text = when {
                            updateDownloading -> "Downloading"
                            ready && !canInstallUpdates -> "Allow"
                            ready -> "Update"
                            else -> "Check for updates"
                        },
                        onClick = if (ready) onUpdate else onCheckForUpdate,
                        enabled = !updateChecking && !updateDownloading
                    )
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Dialogs
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun CollectionNameDialog(
    title: String = "Create collection",
    confirmLabel: String = "Create",
    name: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    PocketDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Filled.Add
    ) {
        PocketFieldLabel("Collection name")
        PocketFormField(name, onNameChange, "e.g. Dev Resources", isError = error != null)
        if (error != null) PocketBanner(message = error)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = PocketSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
        ) {
            Box(Modifier.weight(1f)) { PocketTonalButton(text = "Cancel", onClick = onDismiss) }
            Box(Modifier.weight(1f)) { PocketPrimaryButton(text = confirmLabel, onClick = onCreate) }
        }
    }
}

@Composable
private fun DeleteCollectionDialog(
    collectionName: String,
    deleteLinksToo: Boolean,
    error: String?,
    onDeleteLinksTooChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    PocketDialog(
        onDismissRequest = onDismiss,
        title = "Delete ${confirmPreview(collectionName, 48)}?",
        body = "Choose what happens to the links saved inside this collection.",
        icon = Icons.Filled.Delete,
        destructive = true,
        confirmLabel = "Delete",
        onConfirm = onConfirm
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PocketSpace.xs),
            modifier = Modifier.semantics { stateDescription = "Choose how to handle saved links" }
        ) {
            DeleteChoice("Move links to Inbox", selected = !deleteLinksToo) { onDeleteLinksTooChange(false) }
            DeleteChoice("Delete links too", selected = deleteLinksToo) { onDeleteLinksTooChange(true) }
        }
        if (error != null) {
            PocketBanner(
                message = error,
                modifier = Modifier.semantics {
                    contentDescription = "Error: $error"
                    stateDescription = "Error"
                }
            )
        }
    }
}

@Composable
private fun DeleteChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) PocketColors.AccentSoft else PocketColors.SurfaceLow,
        animationSpec = pocketTween(PocketMotion.normal),
        label = "deleteChoiceContainer"
    )
    Box(Modifier.fillMaxWidth().pressScale(0.985f)) {
        Surface(
            onClick = onClick,
            shape = PocketShape.md,
            color = container,
            contentColor = PocketColors.Ink,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    role = Role.RadioButton
                    this.selected = selected
                    stateDescription = if (selected) "Selected" else "Not selected"
                }
        ) {
            Row(
                Modifier.padding(horizontal = PocketSpace.md, vertical = PocketSpace.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
            ) {
                PocketCheckMark(checked = selected)
                Text(
                    label,
                    style = PocketType.bodyStrong,
                    color = if (selected) PocketColors.OnAccentSoft else PocketColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * User-supplied text quoted back in a confirmation, shortened so it cannot dominate the dialog.
 *
 * Whoever opened the confirmation has just seen the full name on the thing they tapped delete on,
 * so it only needs enough of it to confirm they picked the right one. Whitespace is collapsed
 * first, so a title full of newlines does not spend the whole budget on blank space.
 */
internal fun confirmPreview(text: String, maxChars: Int = 96): String {
    val collapsed = text.trim().replace(Regex("\\s+"), " ")
    if (collapsed.length <= maxChars) return collapsed
    val head = collapsed.take(maxChars - 1).trimEnd()
    // take() counts UTF-16 units, so it can slice an emoji down the middle and leave a lone
    // surrogate that renders as a replacement glyph. Titles here come off the open web, so
    // emoji-heavy ones are routine, not hypothetical.
    val safe = if (head.isNotEmpty() && head.last().isHighSurrogate()) head.dropLast(1) else head
    return safe + "…"
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    PocketDialog(
        onDismissRequest = onDismiss,
        title = title,
        body = body,
        icon = if (destructive) Icons.Filled.Warning else Icons.Filled.Info,
        destructive = destructive,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm
    ) {
        if (error != null) PocketBanner(message = error)
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Link actions
   ═══════════════════════════════════════════════════════════════════════════ */

private fun shareLink(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, "Share link"))
}

private fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Voket link", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}
