# Hidden Locker — Private Notes Feature Plan

## 1. Goal

Add a private notes area inside Pocket Proj's existing PIN-protected Hidden Locker. Users should be able to write and manage short personal notes that are separate from saved links.

The feature must feel like a small, focused notes app inside the locker—not a second full note-taking product.

## 2. Product decisions

- Notes live only inside the Hidden Locker.
- Notes are independent of saved links; creating a note must not require a URL, collection, or tag.
- Notes are stored locally on the device. No account, backend, or cloud sync.
- Opening the notes area requires the existing Hidden Locker unlock flow. Do not introduce a second PIN.
- Notes must be protected at rest, not merely hidden from the UI.
- The first release focuses on plain-text notes. Rich text, attachments, checklists, and collaboration are out of scope.
- A note is private locker data and must never appear in the ordinary library, global search, widgets, notifications, or app shortcuts.

## 3. User experience

### Entry point

Add a **Notes** destination or tab within the already-unlocked Hidden Locker. Keep it visually consistent with the locker and the app's Material 3 Expressive design system.

### Notes list

Each note card/list row should show:
- Title (or a generated fallback such as “Untitled note”)
- A short plain-text preview
- Last edited date/time

Provide:
- A prominent **New note** action
- Search within locker notes
- Sort by recently edited (default) and title
- A useful empty state explaining that notes remain private inside the locker

### Create and edit

The editor should include:
- Title field
- Multi-line note body
- Save automatically after a short debounce and when leaving the editor
- Clear saved/saving feedback without interrupting typing
- Back navigation that never discards already-persisted text

Do not require a title. If omitted, show “Untitled note” in the list without forcing that text into the stored title.

### Note actions

For the initial release:
- Open and edit
- Delete with confirmation
- Search
- Sort

Defer pinning, color labels, folders, archive, undo history, and sharing.

## 4. Data model

Add a Room entity such as `LockerNote`:

| Field | Type / purpose |
|---|---|
| `id` | Stable UUID or auto-generated primary key |
| `titleCiphertext` | Encrypted title |
| `bodyCiphertext` | Encrypted note body |
| `createdAt` | Creation timestamp |
| `updatedAt` | Last-edit timestamp |

If the chosen encryption design uses a single encrypted payload, title and body may be stored together. Keep timestamps and other metadata minimal; avoid storing plaintext previews or titles.

Add a DAO and repository with operations to:
- Observe notes ordered by `updatedAt DESC`
- Search notes after decryption (or use a deliberately designed protected search index; do not create a plaintext index)
- Insert/update a note
- Delete a note
- Delete all notes if the user explicitly chooses to clear locker data

Use a Room migration. Do not destructively recreate the existing database.

## 5. Security and privacy requirements

- Reuse the existing Hidden Locker authentication gate; do not duplicate PIN verification in the notes feature.
- Encrypt note title and body before they reach persistent storage.
- Use Android Keystore-backed key material and authenticated encryption (for example, AES-GCM) with a reviewed key lifecycle.
- Never hard-code encryption keys, derive an encryption key directly from the PIN, or store raw PINs.
- Define how the encryption key becomes available after a successful locker unlock and when it is cleared/invalidated.
- Locking the Hidden Locker must remove decrypted note content from active UI state as far as practical.
- Clear sensitive editor/list state when the app backgrounds or the locker relocks, according to the existing lock policy.
- Block screenshots and sensitive recent-task previews while showing notes, consistent with the Hidden Locker.
- Do not put note content in logs, crash breadcrumbs, analytics, clipboard automatically, notifications, or accessibility labels beyond the visible UI text.
- Do not expose notes through Android Sharesheet or external intents in the first release.

**Important:** Keystore-backed encryption protects app data at rest, but cannot guarantee secrecy on a compromised/rooted device or while the device is unlocked and the note is visible.

## 6. Export and backup behavior

The current export flow covers saved links. Before implementing notes, make an explicit product decision for locker-note export.

Recommended initial behavior:
- Do not silently include notes in existing JSON/CSV link exports.
- Keep notes out of CSV.
- In a later version, add a separately named encrypted notes backup/restore flow, gated by Hidden Locker authentication.
- Never export plaintext notes without a clear, explicit user action and warning.

This avoids accidentally changing the meaning or privacy expectations of existing exports.

## 7. Architecture and implementation approach

Keep the feature aligned with the existing project layers:

- **data:** Room entity, DAO, migration, encrypted storage adapter
- **domain:** note model and repository/use cases
- **security:** reuse locker-unlock state and provide a narrowly scoped encryption interface
- **ui:** notes list, editor, search, empty/loading/error states

Avoid making UI code responsible for encryption or direct database access. The editor should work with domain models; encryption belongs below the repository boundary.

## 8. Implementation phases

### Phase A — Design and security spike
- [ ] Inspect the current PIN controller, locker lock lifecycle, Room setup, and export models.
- [ ] Decide the encryption key lifecycle and how an unlocked locker obtains access.
- [ ] Confirm behavior on process death, app restart, device credential changes, and key invalidation.
- [ ] Write tests for encryption/decryption and failure cases before building the UI.

### Phase B — Persistence
- [ ] Add the `LockerNote` entity and Room migration.
- [ ] Add DAO and repository interfaces/implementations.
- [ ] Implement encrypted title/body persistence.
- [ ] Verify that database rows do not contain plaintext note content.
- [ ] Add repository, migration, and encryption tests.

### Phase C — Locker UI
- [ ] Add Notes entry point inside the unlocked Hidden Locker.
- [ ] Build the notes list and empty state.
- [ ] Build the create/edit screen with debounced autosave.
- [ ] Add delete confirmation and sorting.
- [ ] Ensure the existing locker lock action exits notes and clears sensitive UI state.

### Phase D — Search and hardening
- [ ] Add private notes search without a plaintext database index.
- [ ] Test relocking while viewing and editing a note.
- [ ] Test app backgrounding, process recreation, and device rotation.
- [ ] Test rapid edits and autosave ordering to prevent stale writes.
- [ ] Verify screenshots/recent-task protections.
- [ ] Review logs, backup behavior, exports, and all navigation paths for data leaks.
- [ ] Add accessibility labels and UI tests.

## 9. Acceptance criteria

The feature is ready when all of the following are true:

1. A user can enter Notes only after unlocking the Hidden Locker.
2. A user can create, edit, search, sort, and delete plain-text notes.
3. Notes persist across app restarts and device rotations.
4. Autosave does not lose the latest edit or overwrite newer content with a stale write.
5. Locking the Hidden Locker immediately returns the user to a locked state and removes note content from the visible UI.
6. Note titles and bodies are encrypted at rest; database inspection reveals no plaintext content.
7. Notes never appear in ordinary library/search surfaces or external intents.
8. Existing saved-link behavior, PIN setup/unlock, and JSON/CSV exports continue to work.
9. Room migration and security/repository/UI tests pass.

## 10. Explicitly out of scope for v1

- Rich-text formatting or Markdown preview
- Images, files, audio, or other attachments
- Checklists and reminders
- Cloud sync or collaboration
- Sharing or exporting individual notes
- Note folders/tags, pinning, archive, trash, and version history
- A separate notes PIN

## 11. Suggested delivery order

Prioritize the security spike and encrypted persistence before UI polish. A visually complete notes editor is not acceptable if it stores private text in plaintext. Ship the smallest secure version first, then consider richer note-taking features based on actual usage.
