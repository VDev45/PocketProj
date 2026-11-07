# Pocket Proj — Implementation Plan

## Guiding decisions

- Native Android application
- Kotlin + Jetpack Compose + Material 3 (Expressive)
- Local-first architecture with no backend
- Single-collection model for saved links
- Explicit hidden-state model in addition to hidden-tag configuration
- Small, reviewable commits
- Tests added alongside each feature

## Phase 0 — Foundation

- [ ] Confirm application ID, minimum Android SDK, and supported devices
- [ ] Create a single-activity Compose project
- [ ] Establish package structure: `data`, `domain`, `ui`, `metadata`, `security`, `export`
- [ ] Add dependency management through a version catalog
- [ ] Configure formatting, static analysis, and basic CI

## Phase 1 — Design system and navigation

- [ ] Build Material 3 Expressive theme layer (color scheme, motion scheme, shapes, typography via `MaterialExpressiveTheme`)
- [ ] Add light, dark, and system modes
- [ ] Add dynamic-color toggle
- [ ] Apply the expressive motion scheme to prominent transitions and hero interactions
- [ ] Define an expressive shape system with large-radius and pill-shaped surfaces
- [ ] Configure variable typography (weight/width axes) on the expressive type scale
- [ ] Design custom palette abstraction and initial palettes
- [ ] Create app navigation shell
- [ ] Add Inbox, Favorites, Collections, Tags, and Hidden destinations
- [ ] Add list/grid layout preference

## Phase 2 — Local data layer

- [ ] Define Room entities and migrations
- [ ] Implement `SavedItem`, `Collection`, `Tag`, and item-tag relation
- [ ] Store explicit `isHidden` state separately from hidden-tag configuration
- [ ] Add settings tables for theme, layout, and security preferences
- [ ] Create DAOs and repositories
- [ ] Seed the Inbox collection on first launch
- [ ] Add CRUD and repository tests

## Phase 3 — Save and edit flow

- [ ] Build manual URL paste flow
- [ ] Implement Android Share Sheet receiver
- [ ] Build save/edit screen
- [ ] Validate URLs and normalize domains
- [ ] Implement duplicate URL decision dialog
- [ ] Support collection selection, optional tags, favorite toggle, and image selection
- [ ] Add confirmation dialogs for destructive actions

## Phase 4 — Metadata fetching

- [ ] Implement on-device HTTP metadata client
- [ ] Parse title, Open Graph image, Twitter card image, and domain
- [ ] Apply a 30-second timeout
- [ ] Save immediately before fetching metadata
- [ ] Run metadata work through WorkManager
- [ ] Add placeholder image fallback
- [ ] Add retry policy and visible failed status
- [ ] Implement manual metadata refresh
- [ ] Avoid downloading arbitrary page content beyond required metadata/images

## Phase 5 — Library experience

- [ ] Implement minimal list cards
- [ ] Implement grid cards
- [ ] Add layout switcher
- [ ] Add title/URL/domain search
- [ ] Add collection, tag, favorite, and visibility filters
- [ ] Add Favorites section
- [ ] Open URLs using the external/default browser
- [ ] Add empty, loading, and error states
- [ ] Ensure hidden items are excluded from all ordinary queries

## Phase 6 — Hidden content and security

- [ ] Add configuration for multiple hidden tags
- [ ] Recompute hidden status when tags change
- [ ] Ensure any matching hidden tag hides the item
- [ ] Build PIN setup, unlock, change, and lockout flows
- [ ] Store PIN material securely; never store the raw PIN
- [ ] Add device-authentication recovery flow
- [ ] Build PIN-protected Hidden section
- [ ] Add explicit Unhide action
- [ ] Preserve hidden state when a hidden tag is removed
- [ ] Block screenshots and recent-task previews in the Hidden section where supported
- [ ] Add security-focused tests

## Phase 7 — Export and restore

- [ ] Build export format models
- [x] Export visible-only or all content based on user choice
- [x] Require the Hidden PIN before exporting hidden content
- [x] Implement JSON export through the Storage Access Framework
- [x] Implement CSV export through the Storage Access Framework
- [x] Implement JSON restore after reinstall
- [ ] Validate schema versions and handle malformed backups
- [ ] Add export/restore tests — partially: the payload filter is covered by `ExportableLinksTest`; the JSON/CSV codecs and `writeText` still are not (they need `Context`/`ContentResolver`, i.e. Robolectric)

## Phase 8 — Hardening and release

- [ ] Accessibility pass: labels, contrast, touch targets, screen readers
- [ ] Performance pass for large libraries
- [ ] Database migration tests
- [ ] UI tests for save, duplicate, hide, unhide, delete, export, and restore
- [ ] Offline/error behavior tests
- [ ] Privacy review and permission minimization
- [ ] Generate signed release build
- [ ] Document limitations and backup behavior

## Suggested initial data model

### `Collection`

- `id`
- `name`
- `position`
- `iconKey`
- `colorArgb`
- `createdAt`
- `updatedAt`

### `SavedItem`

- `id`
- `url`
- `canonicalUrl`
- `title`
- `domain`
- `thumbnailUri`
- `customCoverUri`
- `collectionId`
- `isFavorite`
- `isHidden`
- `createdAt`
- `updatedAt`
- `metadataStatus`
- `metadataError`

### `Tag`

- `id`
- `name`
- `isHiddenTag`

### `SavedItemTag`

- `itemId`
- `tagId`

### `AppSettings`

- `themeMode`
- `useDynamicColor`
- `selectedPalette`
- `libraryLayout`
- `hiddenPinHash`
- `hiddenPinSalt`

## First implementation milestone

Build a runnable shell with:

1. Inbox collection seeded automatically
2. Empty library screen with list/grid toggle
3. Manual URL save screen
4. Local Room persistence
5. Basic saved-item cards
6. Collection and tag assignment
7. Delete-with-confirmation flow

Do not implement metadata fetching or PIN protection until the basic local CRUD flow is tested and stable.
