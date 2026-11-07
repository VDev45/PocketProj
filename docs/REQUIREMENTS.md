# Pocket Proj — Requirements Specification

## 1. Product goal

Pocket Proj is a local-only Android app for collecting links to online content and organizing them for later access. It stores links and preview metadata, not the actual online content.

## 2. Supported resources

- Articles and blog posts
- YouTube videos
- Instagram, Reddit, X, and other social-media links
- Any valid URL

## 3. Save flow

### Entry points

1. Android Share Sheet
2. Manual URL paste inside the app

### Save behavior

- Show a save screen for shared links.
- Automatically assign new links to `Inbox` unless another collection is selected.
- Allow editing of title, URL, thumbnail, collection, tags, and favorite status before saving.
- Save immediately, then fetch metadata in the background.
- Metadata fetching is on-device only and has a 30-second timeout.
- If fetching fails, retain the URL, use a generic placeholder image, and allow manual editing.
- When saving a duplicate URL, ask whether to open the existing item, update its metadata, or save another copy.

## 4. Saved-item fields

- URL
- Editable title
- Domain
- Thumbnail/preview image
- Optional custom cover image
- Exactly one collection
- Zero or more tags
- Favorite status
- Created/updated timestamps

## 5. Organization

### Collections

- Exactly one collection per link
- Default collection: `Inbox`
- Create, rename, delete, and reorder collections
- Optional custom icon and color per collection
- Deleting a collection permanently deletes the collection and all its links after confirmation

### Tags

- Tags are optional
- A link can have multiple tags
- Tags can be used for filtering and hidden-content rules

## 6. Hidden-content behavior

- Users can designate multiple custom tags as hidden tags.
- A link is hidden if it contains at least one designated hidden tag.
- Hidden links are excluded from the main library, Favorites, search results, and collection listings.
- Hidden links are accessible only through the PIN-protected Hidden section.
- Removing a hidden tag does not automatically unhide a link.
- Unhiding is an explicit action using an `Unhide` control in the Hidden section.
- The Hidden section uses its own PIN rather than locking the entire app.
- Forgotten PIN recovery uses device authentication.

## 7. Browsing and interaction

- Library supports list and grid layouts.
- Cards are minimal: thumbnail, title, and domain.
- Links open in the external/default browser.
- Favorites have a toggle and a dedicated Favorites section.
- Search covers title, URL, and domain.
- Filters cover collections, tags, favorites, and hidden status.
- Metadata can be manually refreshed.
- Broken-link checking occurs only when opening a link or refreshing metadata.

## 8. Export and restore

- Export formats: JSON and CSV
- Export destination: user-selected location via Android Storage Access Framework
- Every export asks whether hidden links should be included.
- Including hidden links requires the Hidden-section PIN.
- No general import feature in the initial version.
- A JSON export can be restored after reinstalling.
- Automatic backups are out of scope.

## 9. Appearance

- Light, dark, and follow-system modes
- Material You dynamic colors
- Custom palettes, including Catppuccin, Kanagawa, and Gruvbox
- Material 3 Expressive design language: expressive color roles on top of dynamic colors and custom palettes, an expressive shape system (large-radius and pill-shaped surfaces), an expressive motion scheme for prominent transitions, and variable typography

## 10. Explicit non-goals

- Downloading videos, images, PDFs, or full webpages as content archives
- Offline reading or an in-app browser
- Accounts, backend services, and cloud sync
- Automatic backups
- Arbitrary CSV import or third-party migration

## 11. Important implementation note

The hidden feature is an organizational privacy feature. Because the app is local-only, the implementation should protect the PIN and sensitive local state appropriately, but it cannot guarantee protection on a compromised or rooted device.
