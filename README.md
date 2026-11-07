# Pocket Proj

A privacy-first, local-only Android app for saving and organizing links to online content.

## Confirmed Product Requirements

Pocket Proj stores links—not downloaded content. It is designed for articles, blog posts, YouTube videos, Instagram posts, Reddit posts, X posts, and other URLs.

- Save through the Android Share Sheet or by manually pasting a URL.
- Save immediately to **Inbox**; fetch metadata in the background.
- Store the URL, editable title, domain, thumbnail, optional custom cover, collection, optional tags, and favorite status.
- Fetch metadata on-device only, with a 30-second timeout. If fetching fails, keep the URL and show a generic placeholder image.
- One collection per link. Collections support create, rename, delete, reorder, custom icons, and custom colors.
- Deleting a link or collection permanently deletes its contents after confirmation.
- Tags are optional.
- Main library supports both list and grid views with minimal cards: thumbnail, title, and domain.
- Links open in the external/default browser.
- Search supports title, URL, and domain, with filters for collections, tags, favorites, and hidden status.
- Favorites have both a toggle and a dedicated Favorites section.
- Hidden content is controlled by one or more user-selected hidden tags. A link is hidden when it has at least one hidden tag.
- Hidden links are excluded from the library, Favorites, search results, and collection listings.
- The Hidden section is protected by a dedicated PIN. A forgotten PIN can be reset using device authentication.
- Removing a hidden tag does not unhide a link. Unhiding is an explicit action from the Hidden section.
- Export supports JSON and CSV to a user-selected location through Android's file picker.
- Each export asks whether hidden links should be included. Including hidden links requires the Hidden-section PIN.
- No import feature for now, except restoring a previously exported JSON backup after reinstalling.
- No account, backend, or cloud sync for the initial version.
- Supports light, dark, and system themes, Material You dynamic colors, Material 3 Expressive shapes and motion, and custom palettes such as Catppuccin, Kanagawa, and Gruvbox.

## Non-goals for the initial version

- Downloading full articles, videos, or other media.
- Offline content extraction or an in-app reader.
- Cloud accounts and cross-device synchronization.
- Automatic backups.
- Importing arbitrary CSV files or migrating from other apps.

## Proposed technology stack

- Kotlin
- Jetpack Compose + Material 3 Expressive (color, motion, shapes, typography)
- Room for local structured data
- WorkManager for background metadata fetching
- Android Sharesheet intent handling
- Android Storage Access Framework for exports
- Android Keystore and device authentication for PIN recovery/protection
- Kotlin Serialization

## Implementation roadmap

1. Product specification and architecture
2. Android project scaffold and design system
3. Room database, collections, tags, favorites, and CRUD
4. URL save flow and Android Share Sheet integration
5. On-device metadata fetching with timeout, retries, and placeholders
6. Library list/grid views, search, filters, and external-browser opening
7. Hidden-tag rules, Hidden section, PIN, and device-authentication recovery
8. JSON/CSV export and restore-from-JSON flow
9. Theme/palette system, accessibility, testing, and release hardening

See [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) and [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) for details.
