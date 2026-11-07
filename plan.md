## Pocket Proj — Next Development Steps

### Phase 1: Collection UI Integration

1. Add collection state to `LinkViewModel`.
2. Load collections reactively from Room.
3. Replace the free-text collection field with a dropdown selector.
4. Select **Inbox** by default.
5. Add a “Create collection” dialog.
6. Refresh the dropdown after creating a collection.
7. Validate duplicate and empty collection names.

### Phase 2: Collection Management

8. Add a dedicated Collections screen.
9. Display collection names and link counts.
10. Allow users to rename collections.
11. Allow users to delete collections safely.
12. Prevent deletion of the default **Inbox** collection.
13. Decide how links should be handled when a collection is deleted.

### Phase 3: Proper Collection Relationships

14. Add `collectionId` to `LinkEntity`.
15. Create a safe Room migration.
16. Migrate existing string-based collections into the new collection table.
17. Add a foreign-key relationship between links and collections.
18. Remove the legacy `collection` string field after migration validation.
19. Update queries to filter by `collectionId`.

### Phase 4: Search and Filtering

20. Add database-level search.
21. Search across titles, URLs, descriptions, domains, and tags.
22. Add collection-based filtering.
23. Add favorites filtering.
24. Add sorting options:

* Newest first
* Oldest first
* Title A–Z
* Domain A–Z

### Phase 5: Link Management

25. Add an edit-link screen.
26. Allow editing titles, descriptions, collections, and tags.
27. Add tag management and normalization.
28. Add swipe-to-delete with undo.
29. Add link-sharing support.
30. Add “Open in browser” and “Copy URL” actions.

### Phase 6: Privacy and Hidden Content

31. Add hidden collections.
32. Add PIN protection.
33. Store the PIN securely using Android Keystore.
34. Add lock-screen timeout behavior.
35. Prevent sensitive content from appearing in previews or screenshots.

### Phase 7: Import and Export

36. Export links to JSON.
37. Export links to CSV.
38. Import JSON backups.
39. Validate imported data.
40. Handle duplicate links during import.
41. Add backup and restore status messages.

### Phase 8: UI and Theming

42. Improve empty states.
43. Add polished loading and error states.
44. Add list/grid view switching.
45. Add theme selection (Material 3 Expressive):

* Expressive color roles and tonal schemes (dynamic color + custom palettes)
* Expressive shape system (large-radius surfaces and pill-shaped components)
* Expressive motion scheme for navigation and hero transitions
* Variable typography on the expressive type scale

46. Support custom palettes such as:

* Catppuccin
* Kanagawa
* Graphite
* AMOLED

47. Persist user interface preferences locally.

### Phase 9: Quality and Release

48. Add repository and ViewModel unit tests.
49. Add Room migration tests.
50. Add Compose UI tests for critical flows.
51. Add lint and formatting checks.
52. Fix accessibility issues.
53. Verify offline behavior.
54. Verify Share Sheet integration.
55. Generate signed release APK.
56. Document installation, backup, and privacy behavior.

### Immediate Next Task

> **Update `LinkViewModel` and `MainActivity` to use Room-backed collections with a dropdown selector and a collection-creation dialog.**

We should complete and verify each milestone before moving to the next one.
