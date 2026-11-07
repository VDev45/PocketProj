# AGENTS.md

Pocket Proj: a local-only Android app (Kotlin + Jetpack Compose + Room) for saving and organizing links. Single Gradle module `:app`.

## Build

- `gradlew` is a **stub** that execs a system `gradle` — there is no Gradle wrapper jar in this repo. CI pins Gradle **8.11.1** (`android-build-release.yml`); use that version locally. The Android SDK is at `/home/vishwa/android-sdk/latest` (no `local.properties`).
- Build: `gradle assembleDebug` (or via whatever `gradlew` resolves). Unit tests: `gradle test`. Lint: `gradle lint` (no custom lint config).
- Toolchain: Java 17, Kotlin 2.0.21, AGP 8.7.3, compile/target SDK 35, minSdk 26. Compose BOM 2024.12.01; Compose compiler comes from `org.jetbrains.kotlin.plugin.compose`.
- **No version catalog** (`gradle/libs.versions.toml` absent despite `docs/IMPLEMENTATION_PLAN.md` listing it as a TODO). Dependencies are hardcoded inline in `app/build.gradle.kts` — don't invent a catalog.
- The only CI is `.github/workflows/android-build-release.yml`, in two parallel jobs. **Build Android APK** runs the unit tests (`./gradlew test`), builds the debug APK, runs lint, and (on `main`) publishes; **Locker Notes Instrumentation Tests** boots an API 35 emulator and runs `connectedDebugAndroidTest`. The emulator is a separate job on purpose: it takes minutes, and on a runner where `/dev/kvm` is unusable it can hang, which must never hold up the build/lint/release path. On pushes to `main` it then decides whether to publish a GitHub release (`build-<run_number>`): **only when `versionCode` in `gradle.properties` differs from the previous release's**, so a merge that does not bump the version builds and tests but publishes nothing. The gate fails open — no previous release, an unfetchable tag, or an unreadable `versionCode` all publish. **To cut a release, bump `versionCode`;** `versionName` alone will not do it, and an unchanged `versionCode` means Android refuses the install, so nothing users are missing is reachable until it moves.

## Code layout

- Everything currently lives in `com.vishwaravi.pocketproj`: `MainActivity`, `PocketProjApplication`, `LinkViewModel` in the root package; entities, DAOs, `LinkRepository`, `UrlValidator`, `HttpMetadataFetcher` (+ `MetadataFetcher` interface), `LinkMetadataWorker`, and the encrypted locker-notes entity/DAO/repository in `.data`; `LockerNoteCrypto` in `.security`.
- `docs/IMPLEMENTATION_PLAN.md` calls for `data/domain/ui/metadata/security/export` packages and a version catalog. Only `.security` exists so far (locker-notes crypto); the rest are **not yet implemented**. Match the existing layout; don't restructure without asking.
- Metadata is fetched **in the background after save** (REQUIREMENTS §3: "save immediately, then fetch metadata in the background"). `LinkRepository.save` only inserts the row and enqueues a `LinkMetadataWorker` (WorkManager, `androidx.work:work-runtime-ktx`) when the user left title/description blank. `HttpMetadataFetcher` (an injectable `MetadataFetcher`; 5s connect / 7s read timeouts, 30s total budget) is used by the worker via `repository.applyMetadata` and by the editor's live preview. Unit tests construct `LinkRepository` with a fake fetcher, so `save` never touches the network.

## Room database

- `PocketDatabase` is at **version 7** with `exportSchema = false` and **hand-written `Migration` objects** (`MIGRATION_1_2` … `MIGRATION_6_7`), listed once in `PocketDatabase.MIGRATIONS` and applied with `.addMigrations(*MIGRATIONS)`. `Converters` maps the `MetadataState` enum to TEXT for Room.
- v7 adds `locker_notes`, which stores only ciphertext and key-envelope BLOBs (see `docs/NOTES_ENCRYPTION_DESIGN.md`); no plaintext note column may be added. `LinkRepository.clearAllData` deletes these rows too, so "clear all data" stays a real wipe.
- `links` columns beyond the basics: `isHidden` (explicit hidden flag, separate from hidden-tag config), `metadataState` (PENDING/DONE/FAILED), `metadataAttempts` (drives worker retry limit 3). The v6 migrations also rebuilt the collection-name unique index as `COLLATE NOCASE` (case-insensitive, matching app lookups) after reassigning/dropping historical case-variant duplicates, and added `index_links_url`.
- `links.collectionId` is a foreign key to `collections.id` (NO ACTION on delete — the repository reassigns links to Inbox *before* deleting a collection). The v4→v5 migration used a **table rebuild** (`RENAME` → create → copy with a legacy-`collection`→`collectionId` backfill → `DROP` old) rather than `DROP COLUMN`, which requires SQLite 3.35+ but `minSdk` is 26.
- Any schema change requires: bump the version in `@Database`, add a `Migration(n, n+1)`, and register it in `PocketDatabase.MIGRATIONS`. Since `exportSchema = false`, migrations are written by hand — no auto-generated schema artifacts. Cover the migration in `app/src/androidTest/.../PocketDatabaseMigrationTest.kt`, which builds the previous version with a copy of the old entity set and opens it at the new version so Room validates the migrated schema (a wrong `CREATE TABLE` crashes every upgrading install).
- Room uses **kapt** (`org.jetbrains.kotlin.kapt`), not KSP. Keep it consistent.

## Tests

- Plain JUnit 4 (`testImplementation junit:4.13.2`) in `app/src/test`. No Robolectric or MockK.
- `app/src/androidTest` exists **only** for what a JVM cannot exercise: Android Keystore crypto and real Room migrations/DAOs. It runs on an API 35 emulator in the separate CI job. Do not move ordinary unit tests there.
- Convention: hand-rolled fake DAO classes implementing the DAO interface (see `LinkRepositoryTest.kt`). **When you add a method to a DAO interface, update the fakes in `app/src/test/` too.**
- Repository methods return Kotlin `Result<T>` and validation is via `require(...)` — tests assert on `.isSuccess`/`.getOrThrow()` and exception messages.

## Product context

- `README.md`, `docs/REQUIREMENTS.md`, and `docs/IMPLEMENTATION_PLAN.md` are the authoritative spec. Key enforced rules to preserve: one collection per link; default collection `Inbox` (cannot be renamed/deleted); hidden state is explicit and separate from hidden-tag config (links saved with a built-in `HIDDEN_TAGS` entry start hidden, and only the Hidden section's Unhide action clears it); links open in the external browser; metadata has a 30s budget.
- The manifest registers a `SEND` text/plain intent filter (Share Sheet receiver) and disables automatic backups (`allowBackup="false"` — automatic backups and arbitrary CSV import are out of scope; restore is JSON-only via Import / Export).
- Branch naming convention in git history: `feat/…` and `copilot/…` branches merged via PRs.