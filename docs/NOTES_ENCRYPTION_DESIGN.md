# Hidden Locker Notes — Encryption Design

## Implemented foundation

`security/LockerNoteCrypto.kt` implements per-note envelope encryption:

1. Generate a fresh random 256-bit DEK for each note.
2. Encrypt the note UTF-8 bytes with AES-256-GCM using the DEK.
3. Bind the ciphertext to the stable note ID with GCM additional authenticated data (AAD).
4. Wrap the DEK with a non-exportable AES-256-GCM key generated and held by Android Keystore.
5. Persist only the ciphertext, content IV, wrapped DEK, and wrapping IV.

The note ID is authenticated as AAD for both encryption layers. Moving a ciphertext or wrapped key to another note ID therefore causes authentication failure. Each encryption operation uses a fresh cipher-generated GCM IV. The GCM authentication tag detects tampering.

The serialized envelope is versioned. Keep the version when adding future formats or migrations.

## PIN and biometric behavior

The existing four-digit Hidden Locker PIN remains an application authentication gate. It is **not** used as an AES key or as a key-derivation password. A four-digit PIN has only 10,000 possible values; even a memory-hard KDF cannot turn that into high-entropy secret material, and a database attacker could test guesses offline if the PIN directly wrapped the DEK.

The Android Keystore wrapping key protects against a database-only copy because the key is non-exportable and bound to the app/device Keystore. It does not protect against a fully compromised device, runtime instrumentation of the unlocked app, or an attacker who can invoke the app's decryption path while its session is open.

Fingerprint authentication must be performed through AndroidX BiometricPrompt. The current implementation does not yet bind the wrapping key's use to a biometric CryptoObject. The biometric UI gate must not be described as hardware-enforced key authorization until that integration is implemented. The PIN fallback remains available through the existing locker flow.

## Storage

- `data/LockerNoteEntity.kt` persists the envelope only: `formatVersion`, `contentIv`, `ciphertext`,
  `wrappedKeyIv`, `wrappedDataKey` (all BLOBs), plus the note ID and its timestamps. Plaintext title
  and body live in memory only and are serialized into the encrypted payload, never into a column.
- `data/LockerNoteRepository.kt` is the only path to the table. It encrypts on write, decrypts on
  read, and runs decryption on `Dispatchers.Default`. It deliberately has no access to the PIN or to
  session preferences: authorization to call it belongs to the locker session layer.
- `data/PocketDatabase.kt` is at version 7 and adds `MIGRATION_6_7`, a plain `CREATE TABLE` — existing
  links and collections are untouched and nothing is backfilled because notes did not exist before.
  `MIGRATION_6_7` is covered by `androidTest/.../PocketDatabaseMigrationTest.kt`, which builds a real
  v6 database and opens it as v7 so Room validates the migrated schema against the entity.

## Lifecycle requirements before shipping notes

- Call crypto operations only after the existing Hidden Locker session is authenticated. **Not yet
  implemented:** the repository is not wired to the session gate.
- Clear decrypted note/editor UI state when the locker locks or the app backgrounds according to the
  lock policy. **Not yet implemented:** no notes UI exists yet.
- Do not persist plaintext title/body, previews, search indexes, logs, notifications, clipboard
  contents, or crash metadata. **Done** for persistence; nothing else touches note text yet.
- Store encrypted envelope fields as Room BLOBs; do not stringify ciphertext as plaintext. **Done.**
- Add a Room migration for the notes table without destructive database recreation. **Done**, with a
  migration test.
- Add Android instrumentation tests on API 26+ for encrypt/decrypt, wrong note ID, ciphertext
  tampering, wrapped-key tampering, and key invalidation. **Partly done:** CI runs the instrumentation
  suite on an API 35 emulator; API 26 coverage and Keystore key invalidation are still missing.
- Define a deliberate recovery/backup story before offering device migration. Keystore-bound keys do
  not automatically move to another device. **Not yet implemented.**
- Handle Keystore key loss/invalidation as an explicit unrecoverable-encryption state; never silently
  replace the wrapping key, because doing so would strand existing notes. **Done:** decryption throws
  when the wrapping key is missing, and key creation is serialized so a first-use race cannot replace
  a key another note already used.

## Not yet implemented

No notes UI, no locker-session wiring, no biometric `CryptoObject` binding, and no export/restore.
`LockerNoteRepository` must not be called outside an authenticated Hidden Locker session, and the app
must not expose notes until the lifecycle requirements above are finished.
