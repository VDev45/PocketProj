# Pocket Proj — UI Report

> UI/UX review and implementation status for the current Pocket Proj Android application.
>
> Scope: Jetpack Compose UI, navigation, visual hierarchy, interaction patterns, Android system-bar behavior, loading states, and current gaps against docs/IMPLEMENTATION_PLAN.md.
>
> Source of truth: current PocketApp.kt implementation and the project implementation plan.

---

## 1. Current UI Direction

Pocket Proj currently follows a local-first, warm visual design built around:

- Jetpack Compose + Material 3
- Custom Pocket Proj color tokens
- Large rounded surfaces
- Peach/sand warm neutral cards
- Mauve/lavender accent colors
- Compact typography hierarchy
- Pill-shaped buttons and controls
- Lightweight animated skeleton loading
- Bottom-sheet link creation
- Grouped settings sections
- Local-first product messaging

### Design characteristics

| Area | Current direction |
|---|---|
| Primary background | Warm Sun surface |
| Main cards | Peach / Sand |
| Accent | Mauve / Lavender |
| Destructive action | Rose |
| Corners | Large rounded corners |
| Controls | Compact and rounded |
| Navigation | Single-activity Compose routing |
| Add link | Modal bottom sheet |
| Loading | Animated skeleton cards |
| Settings | Grouped sections |
| Data model messaging | Local-first |

## 2. Main Library

The Library is the primary destination.

Current UI supports:
- Collection selection
- List layout
- Grid layout
- Search navigation
- Profile navigation
- Settings navigation
- Add-link action
- Favorite toggling
- Opening an item for editing
- Loading skeletons while the local link stream initializes

### Remaining UI work
- [ ] Refine list card hierarchy
- [ ] Refine grid card density
- [ ] Add clearer empty states
- [ ] Add explicit error states
- [ ] Add collection/tag/favorite/visibility filters
- [ ] Add dedicated Favorites destination
- [ ] Add dedicated Tags destination
- [ ] Add planned Hidden destination
- [ ] Validate large-library scrolling and performance

## 3. Link Cards

The current cards are intentionally compact and content-first.

Recommended hierarchy:
1. Thumbnail / visual identity
2. Title
3. Domain / URL context
4. Favorite/action affordance
5. Collection/tag context where appropriate

A card should answer quickly what the item is and where it came from without becoming a full detail page.

## 4. Add Link Experience

The current Add Link flow uses a Modal Bottom Sheet containing:

1. URL input
2. Metadata preview loading state
3. Metadata preview indication
4. Title input
5. Collection selection
6. Tags input
7. Tag preview pills
8. Validation/error message
9. Save action

Current strengths:
- Keeps the user in the library context
- URL is the primary field
- Preview loading has a skeleton state
- Collection assignment is visible during save
- Tags are editable before saving
- Save state is communicated
- Invalid URLs prevent saving

The UI already communicates that the link can be saved while preview details finish later. The underlying metadata architecture still needs to move fully to the planned WorkManager/background model.

## 5. Search

Current search filtering covers title, URL, domain, description, tags, collection, favorites-only filtering, and sort order.

Remaining UI work:
- [ ] Tag filter
- [ ] Visibility filter
- [ ] Clear-filter state
- [ ] Better no-results state
- [ ] Distinguish empty library from no search results
- [ ] Ensure hidden items never appear in ordinary search

## 6. Collections

Current Collections UI supports:
- Collection list
- Create collection
- Rename collection
- Delete collection
- Move links to Inbox when deleting a collection
- Optionally delete links with the collection

Deletion is handled through an explicit confirmation dialog.

The planned collection model also includes position, iconKey, colorArgb, createdAt, and updatedAt. The eventual UI should therefore support:
- [ ] Reordering
- [ ] Custom icon
- [ ] Custom color
- [ ] Stronger collection identity
- [ ] Empty collection state

## 7. Profile

The current profile screen is a local library overview rather than an online account.

It presents:
- Saved count
- Favorites count
- Collections count
- Hidden count
- Collections shortcut
- Import & Export shortcut
- Privacy & Security shortcut
- Appearance shortcut
- Local-first messaging

The UI should continue avoiding any implication of cloud accounts, remote identity, or synchronization unless those capabilities are actually implemented.

## 8. Settings

Settings has been reorganized into visible sections:

### Appearance summary
- Active palette
- Theme mode
- Saved-link count

### Library
- Collections
- Import & Export

### Privacy & Security
- Hidden content
- Privacy & Security

### Personalize
- Appearance

### About
- Version
- Clear All Data

The settings overflow/dropdown approach was removed in favor of directly visible sections. The current version value is taken from the build configuration instead of being hardcoded.

## 9. Appearance

The implementation plan calls for:
- [ ] Light mode
- [ ] Dark mode
- [ ] System mode
- [ ] Dynamic-color toggle
- [ ] Custom palettes
- [ ] Expressive motion
- [ ] Expressive shapes
- [ ] Variable typography
- [ ] Initial palette collection

The current application already exposes theme mode and palette selection, but the complete design-system checklist is not yet finished.

Future palettes should be represented through centralized semantic theme tokens rather than screen-specific hardcoded colors.

## 10. Android System Bars / Edge-to-Edge

The original UI had status-bar overlap on Android 15: the top header could visually occupy the same area as the system status icons.

The current implementation uses Android edge-to-edge handling and Compose safe-drawing insets. The visual background can continue behind transparent system bars while interactive content is kept inside the safe drawing area.

Verification still required:
- [ ] Android 15 portrait
- [ ] Landscape where supported
- [ ] Gesture navigation
- [ ] Three-button navigation where relevant
- [ ] Modal bottom sheets
- [ ] Dialogs
- [ ] Keyboard/input fields

## 11. Loading States

The UI now includes animated skeleton components for library loading and metadata preview loading.

Use skeletons when the structure of the eventual content is known. Use a progress indicator for operations where the content structure cannot be meaningfully represented.

## 12. Dialogs and Destructive Actions

Current dialogs cover collection creation, collection rename, collection deletion, clear-all data, and other confirmations.

The visual language uses rounded surfaces, explicit titles, supporting text, cancellation, and explicit confirmation. Destructive actions use the Rose semantic color.

Collection deletion explicitly asks whether links should be moved to Inbox or deleted too.

## 13. Hidden Content UI

Hidden-related destinations exist, but the complete security experience is not implemented yet.

The implementation plan requires:
- [ ] Multiple hidden tags
- [ ] Explicit hidden state
- [ ] PIN setup
- [ ] PIN unlock
- [ ] PIN change
- [ ] Lockout
- [ ] Secure PIN storage
- [ ] Device-authentication recovery
- [ ] Protected Hidden section
- [ ] Explicit Unhide action
- [ ] Screenshot/recent-task protection where supported

Important UX distinction:

**Hidden tag** is a configuration that causes matching content to become hidden.

**Hidden state** is the actual persisted state of the saved item.

Removing a hidden tag must not silently expose content that was explicitly hidden.

## 14. Import & Export UI

The application exposes an Import & Export destination.

Final UX requirements include:
- Visible-only export
- Export including hidden content
- Hidden-content confirmation
- Hidden PIN verification before exporting hidden content
- JSON export
- CSV export
- Storage Access Framework
- JSON restore
- Schema validation
- Malformed-backup handling

The UI should make the difference between exporting the visible library and exporting everything, including hidden content, explicit.

## 15. Navigation

Current destinations include:

- Library
- Search
- Add Link
- Edit Link
- Collections
- Profile
- Settings
- Privacy & Security
- Import & Export
- Appearance
- Hidden

The implementation plan ultimately calls for first-class Inbox, Favorites, Collections, Tags, and Hidden destinations.

## 16. Visual Hierarchy

The current redesign establishes a clear hierarchy:

### Level 1 — Page
Large page title and destination identity.

### Level 2 — Section
Small labels such as Library, Privacy & Security, Personalize, and About.

### Level 3 — Action
Rounded rows/cards with one clear purpose.

### Level 4 — Supporting information
Muted metadata such as counts, theme name, version, domain, and status.

This hierarchy should remain consistent as new screens are added.

## 17. UI Consistency Rules

### Cards
Use large rounded corners consistently. Avoid unrelated card shapes.

### Colors
Use the central Pocket color system. Avoid introducing screen-specific colors without a semantic reason.

### Typography
Maintain a predictable page-title, section-label, action-title, body, and metadata hierarchy.

### Actions
Primary actions should be visually distinct. Destructive actions should use the destructive semantic color.

### Spacing
Prefer a small spacing scale instead of arbitrary values throughout individual screens.

### Icons
Meaningful icons need accessibility descriptions. Decorative symbols should not replace accessible labels.

## 18. Accessibility Checklist

- [ ] Content descriptions for meaningful icons
- [ ] Decorative icons handled appropriately
- [ ] Minimum touch targets verified
- [ ] Contrast verified
- [ ] Large-font testing
- [ ] Screen-reader navigation
- [ ] Keyboard navigation where applicable
- [ ] Field errors associated with their fields
- [ ] Clear focus states
- [ ] Do not rely only on color to communicate state

## 19. Performance Checklist

Test the UI with 0, 10, 100, and 1,000+ links.

Verify:
- Library scrolling
- Grid scrolling
- Search filtering
- Collection switching
- Recomposition behavior
- Thumbnail loading
- Skeleton animations
- Bottom-sheet performance

Large libraries should not require unnecessary rebuilding of the entire visible UI.

## 20. UI Technical Debt

Important current technical debt:

1. The UI exposes some concepts before their complete data/security implementation exists.
2. Tags are represented more simply than the planned normalized Tag + SavedItemTag model.
3. Hidden-content UI exists conceptually, but the security model is incomplete.
4. Metadata preview exists in the save UI, but the final WorkManager architecture is still pending.
5. The complete Material 3 Expressive design-system requirements are not implemented yet.
6. Navigation still needs the full Inbox/Favorites/Collections/Tags/Hidden structure.
7. Accessibility and large-library performance need dedicated passes.

## 21. UI Completion Roadmap

### Milestone A — Stabilize current UI
- [ ] Verify current CI/build
- [ ] Verify Android 15 edge-to-edge
- [ ] Verify all navigation paths
- [ ] Verify dialogs
- [ ] Verify Add Link bottom sheet
- [ ] Verify list/grid switching
- [ ] Verify loading states

### Milestone B — Data-aware UI
- [ ] Implement normalized tags
- [ ] Implement explicit hidden state
- [ ] Add visibility filtering
- [ ] Add collection metadata
- [ ] Connect UI states to persisted settings

### Milestone C — Metadata UX
- [ ] Immediate save
- [ ] Background metadata status
- [ ] Loading state
- [ ] Success state
- [ ] Failure state
- [ ] Retry
- [ ] Manual refresh

### Milestone D — Security UX
- [ ] PIN setup
- [ ] PIN unlock
- [ ] PIN change
- [ ] Lockout
- [ ] Device authentication recovery
- [ ] Protected Hidden section
- [ ] Explicit Unhide
- [ ] Screenshot/recent-task protection

### Milestone E — Final polish
- [ ] Material 3 Expressive refinement
- [ ] Accessibility pass
- [ ] Motion pass
- [ ] Large-library performance pass
- [ ] Empty/error state pass
- [ ] Cross-screen consistency pass

## 22. Current UI Status

### Completed / substantially implemented
- [x] Compose application shell
- [x] Library screen
- [x] List/grid rendering
- [x] Search screen
- [x] Collection management UI
- [x] Add-link bottom sheet
- [x] Edit-link screen
- [x] Profile screen
- [x] Settings restructuring
- [x] Appearance entry point
- [x] Import & Export entry point
- [x] Privacy & Security entry point
- [x] Loading skeletons
- [x] Collection delete confirmation
- [x] Clear-all confirmation flow
- [x] Android 15 edge-to-edge direction
- [x] Build-config-driven version display

### Incomplete
- [ ] Full Material 3 Expressive system
- [ ] Complete Inbox/Favorites/Tags/Hidden navigation
- [ ] Normalized tag UI/data model
- [ ] Explicit hidden-state UX/security
- [ ] Complete PIN UX
- [ ] Background metadata status UX
- [ ] Complete export/restore UX
- [ ] Accessibility pass
- [ ] Performance pass
- [ ] Full UI test coverage

## 23. Next UI Priority

The next UI work should not be another broad visual redesign.

The recommended sequence is:

Current UI
  -> Build verification
  -> Data model stabilization
  -> Connect UI to real hidden/tag state
  -> Metadata state UX
  -> Security UX
  -> Accessibility
  -> Performance
  -> Final visual polish

This keeps the UI aligned with actual product behavior instead of creating screens for functionality that does not yet exist underneath.

---

Related project documents:
- docs/IMPLEMENTATION_PLAN.md
- app/src/main/java/com/vishwaravi/pocketproj/PocketApp.kt