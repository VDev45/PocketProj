# Changelog

All notable changes to Voket (Pocket Proj) are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The version in each heading is the `versionName` in `gradle.properties`. That file
is the single source of truth: the release workflow reads it to title the GitHub
release, and fails the build if the APK it produced does not match it.

## [0.3.6] - 2026-09-27

### Fixed

- **Biometric unlock crashed on Android 8.0 and 8.1.** The prompt was
  built with `Context.getMainExecutor`, which was added in API 28, and
  the app's `minSdk` is 26 — so tapping the biometric button on those two
  releases threw `NoSuchMethodError` instead of showing a prompt. Since
  that button is the primary way into the Hidden section, the feature was
  unusable rather than merely degraded on them. `ContextCompat`'s
  backport does the same thing on every supported level.

  The first release published by the version-gated workflow, so it is
  also the first where a merge that does *not* bump `versionCode`
  deliberately publishes nothing.

## [0.3.5] - 2026-09-27

Three follow-ups found after 0.3.4 was cut, none of which could be
installed until `versionCode` moved again.

### Fixed

- **Section headings sat flush against the first item of the list below
  them**, in three places: the search screen's "Results" heading, the
  collections screen's "All spaces" label, and the hidden locker's
  "N hidden items" heading. The first two had no gap at all; the locker's
  was 6dp, which is *less* than the 6–10dp those lists space their own
  items by, so the heading sat closer to the first card than the cards sit
  to each other and read as one more of them. All three now use the 14dp
  the library's "Recent saves" heading already used. The tags screen has
  the same shape but already had a 20dp gap, above body text rather than
  a heading, and is left alone.
- **Cancelling a coroutine mid-operation was reported as a failure.**
  Every repository method returning a `Result` was written as
  `runCatching`, which catches `Throwable` — and `CancellationException`
  is one. So a cancelled coroutine did not unwind; it came back as
  `Result.failure` and the caller carried on. The clearest symptom was on
  the Settings screen: the update check runs in a scope tied to the
  screen's lifetime, so navigating away mid-request showed "Could not
  check for updates" for a check the user had themselves cancelled, then
  wrote state from a dead coroutine. The same applied to every save, edit,
  tag rename and import. Non-suspending work still uses plain
  `runCatching`, which is correct there.
- **Renaming or deleting a tag could have silently unhidden links.** Both
  sweeps rewrite every matching row wholesale, and a mistake in the copy
  would have cleared the hidden flag — which REQUIREMENTS §6 says only the
  explicit Unhide action may do. Nothing tested it. It does not happen;
  it now cannot, and two tests pin it.

### Changed

- **The three table sweeps read the links table with a query instead of a
  `Flow`.** `syncHiddenTags`, `renameTag` and `deleteTag` each read every
  row and then wrote to every matching row inside one transaction, and
  each did the read with `observeAll().first()`. That registers an
  invalidation observer and cancels it on the next line, inside the
  transaction, and it made correctness depend on "the first emission
  equals the current table" holding for the whole write phase. One
  `suspend fun getAll()` says what is meant. A test now pins that the read
  happens once, up front, rather than once per row.

## [0.3.4] - 2026-09-27

Nine fixes from the open issue tracker, all of which shipped as `main`
was merged but were **not installable**: `versionCode` had not moved, so
every release published since 0.3.3 carried the same code and Android
refused to upgrade an existing install. This is the first release that
contains them.

### Added

- **An All links view for the library.** The library could only show one
  collection at a time, so seeing everything saved meant stepping through
  the collection menu one name at a time. "All links" is now the first
  entry in that menu and lists every visible link regardless of which
  collection filed it, with each card still showing its own collection.
  It is held as a separate flag beside the selected collection, so
  switching to it and back does not lose which collection you were in,
  and the Add button still has a real collection to file into. Hidden
  links stay hidden — the same filter every other view uses, which covers
  both the Hidden Locker flag and hidden-tag config.

### Fixed

- **The delete confirmation dialog could not be read.** The name being
  deleted sat in the scrollable body, so on a small screen the action
  buttons pushed it off the top with no way to scroll to it. Icon, title
  and actions are now pinned and only the body scrolls, and the dialog is
  capped to 86% of the screen height. Names are also collapsed and
  truncated so a long one cannot push the layout out.
- **The hidden-tags card drew every row on top of the first.** The
  header, the divider and the tag chips were three unwrapped children of
  a `Surface`, which is a `Box` — so they stacked on one another instead
  of forming a column.
- **The "New collection" button was unreadable.** It filled with
  `onPrimaryContainer` and printed `primary` on top, which is the two
  container and content roles swapped: 2.43:1 in light, 1.34:1 in dark,
  against a 4.5:1 requirement. The app's own primary fill is now used.
  Every other pairing in the app was measured and left alone; the ratio
  maths is in `Theme.kt` and is covered by tests.
- **The wordmark's rounded corners were cut off**, describing a shape
  around nothing — the column it was clipping had no background.
- **The open button stretched the full card width** on the feature card,
  competing with the card's own content instead of sitting with it.
- **The feature card's corner radius was the largest shape tier in the
  app** (46dp), which is reserved for the single screen-level hero. The
  card now uses the large tier (24dp), and its header has the vertical
  space the other section headers do.
- **The theme preview cards could not be previewed.** They were inert —
  no click handler, and no radio-button semantics, so a screen reader
  announced three unlabelled pictures. They now select the theme, with
  the Material You card that the app has always supported but never
  showed.

### Not fixed

- Nothing outstanding for this release.

  A note on what this version is, because it explains the version-bump PRs: **every merge to
  `main` used to publish a release**, whether or not the version changed. The trigger was
  `github.ref == 'refs/heads/main'` with nothing checking that `versionCode` moved, so 0.3.3 was
  published three times (builds 311, 313, 315) and 0.3.4 three times as well (319, 321, 323). The
  later artifacts contained fixes the earlier ones did not, under the same version, so "0.3.4" did
  not identify a build, and each batch of PRs needed a version bump purely to become installable at
  all. The workflow now publishes only when `versionCode` actually moves.

## [0.3.3] - 2026-09-27

Five more fixes from the review of `main`, all found after 0.3.2 went
out. Two of them are privacy-shaped: one showed the whole library where a
single collection was asked for, and one announced a valid URL to
screen-reader users as a validation error.

### Fixed

- **Selecting a collection the library could not resolve showed every link
  the user had.** The filter fell back to the whole library when the
  selected name matched no collection, under a heading naming the
  collection that had been asked for. The reachable case was a
  collection deleted or renamed while it was selected. The search filter
  has always failed closed here; the library now matches it, and the
  loading state no longer reports itself finished before the collection
  list has actually loaded, which is what made the window reachable.
- **A manual metadata refresh was a single attempt with its retry budget
  already spent.** Saving a link offline exhausted the three background
  attempts, and the Refresh button that exists to recover from that set
  the state back to PENDING without resetting the counter — so the next
  attempt read `4 >= 3` and gave up. The button now restores the full
  budget, with backoff, and the counter again means attempts for the
  current request rather than every attempt a link has ever made.
- **Repeated taps on Refresh piled up concurrent metadata workers for the
  same link**, each doing its own fetch and each spending an attempt
  from that link's shared retry budget, so the budget was consumed by
  duplicate work. One worker per link now, with a new tap replacing the
  last. This also meant workers were being cancelled for the first time,
  which exposed that a cancelled fetch was being recorded as a failed
  one — marking the link failed and spending an attempt on work the
  user had already superseded. Cancellation is now propagated instead.
- **Hiding links by tag ran outside a transaction**, unlike every other
  sweep over the links in the app. It is the one that sets the hidden
  flag, so a failure part way through left the library half hidden with
  nothing to repair it.
- **Saving a link the library already had was reported as a validation
  error.** The URL was valid; the app simply had it already. The URL
  field was painted as malformed and screen readers announced
  "Validation error", so people retyped URLs that needed no change. It is
  now an ordinary notice, styled neutrally.

## [0.3.2] - 2026-09-27

Five fixes from a full review of `main`, plus the export gating that
`REQUIREMENTS.md` §8 has always specified and `README.md` has always
advertised.

### Fixed

- **The app did not compile.** A stray literal `\n` inside a `//` comment in
  `Theme.kt` swallowed the `val HeroWash` declaration on the same line, so
  `PocketColors.HeroWash` did not exist and its one call site failed to
  resolve. Introduced in `e7dc238`, so CI had been red for two commits.
- **The Hidden Locker could not be opened at all with the lock timeout set to
  "Immediately."** Unlocking stored `now + 0`, a timestamp already in the
  past by the time the next reader looked, so every validity check failed.
  The PIN was accepted, a "PIN verified" message appeared, and the app then
  bounced the user straight back to the Privacy screen with no error. That
  timeout now means what it always should have — valid until the app is
  backgrounded — and the lifecycle observer owns the session's lifetime.
- **The update check always reported an update, named after a CI run number.**
  It read `version_code` and `version_name` off the GitHub release object,
  but the releases API returns neither, so it fell back to deriving a version
  from the tag. Tags are `build-<run_number>`, so the run number stood in for
  a version: against `build-270` at `versionCode 3` the check computed
  `270 <= 3` and offered an "update" on every check, to users already running
  that build, labelled "Voket build-270 is ready." The version is now read
  from the release body, where the workflow deliberately publishes it.
- **Exports reported success when no file was written.** `writeText`
  discarded its result and safe-called through a stream that
  `openOutputStream` may return `null`, so a failed write still produced
  "JSON export created." A user told they had a backup would not think to
  check, and would find out when they came to restore.
- **Changing the lock timeout mid-session was ignored by one of the two
  session writers.** The PIN controller captured the preference at
  construction while the settings screen kept a live copy, so the two could
  disagree about the timeout in force.

### Added

- **Exports now ask about hidden links, and require the PIN to include them.**
  Previously a plain export wrote every link — including the ones the Hidden
  Locker exists to keep private — with no prompt. Both JSON and CSV now offer
  the choice, and including hidden links clears the Hidden-section PIN first.
  The PIN is used purely as an authorisation gate here: it does not also open
  a hidden-content session, so authorising an export never unlocks the vault.
  CSV also records each row's `isHidden` flag, which it previously dropped.

## [0.3.1] - 2026-09-27

### Fixed

- **The bottom navigation bar vanished on two of its own four tabs.** It was
  rendered inside the library screen only, so Library and Favorites had it —
  those are the same screen with a different collection filter — while Search
  and Profile, which are separate screens, showed nothing at all. The bar now
  belongs to the three top-level destinations and is drawn above whichever is
  active, with the selected item tracking the route. Sub-screens (Settings,
  Collections, Hidden Locker, the link editors) still have no bar and navigate
  by their header back arrow, and the header back arrows on Search and Profile
  are unchanged.
- Search and Profile now reserve the height the bar occupies, so the last item
  in their scroll containers is no longer hidden underneath it.

## [0.3.0] - 2026-09-27

A visual recomposition of the app, plus one functional fix in the Hidden Locker.

### Fixed

- **The Hidden Locker's Conceal/Reveal action did nothing.** Tapping "Reveal"
  swapped the section subtitle and left every card concealed. The toggle's state
  was never passed to the card, so the locker could only ever render one of its
  two states. The cause was a duplicate `VaultLinkCard` that was a copy of
  `LibraryLinkCard` with `revealContent` hardcoded to `false` — it even computed
  the link's title and description and then never used them. Reveal now renders
  the real title, domain, description and tag pills; the locker still starts
  concealed, and the Unhide action and collection name stay put in both states.
  The duplicate card has been deleted (−145 lines).

### Changed

- **Composition over uniform components.** Screens are recomposed around
  content, scale and whitespace rather than restyled in place. The recurring
  link card in the library list is deliberately unchanged.
- **Inbox hero compressed** from roughly 200dp to a single 56dp row. The
  collection name is now the dropdown trigger, and the 150dp list/grid segmented
  control is a single icon button.
- **Bottom navigation de-nested** from a pill inside a pill to one container with
  one tonal capsule, with a softer shadow. All four destination labels are
  always visible.
- **Floating action button** is a 56dp circle anchored to the bottom end, and no
  longer carries a gradient.
- **Stat surfaces laid out horizontally** — numeral on the left, label and
  supporting copy stacked on the right — instead of tall boxes holding a single
  number. The Profile breakdown counts moved inside the summary surface so they
  stay attached to the number they qualify, and Settings now pairs one dominant
  stat with two compact ones instead of three equal cards.
- **Section rhythm tightened** on Profile and Settings. Section gaps and the
  section label's own padding were compounding into roughly 110dp of dead air
  around each heading; the label now keeps only its top padding and leaves the
  gap below to the layout.
- **Backdrop blobs and mist gradients toned down**, with tighter corner radii
  across the shape scale and a new `sheet` radius for single hero surfaces.
- **Grid tiles reduced in size** so they contrast in scale against the list rows.

### Accessibility

- Filter chips keep their semantic icon when selected. Previously the icon was
  replaced with a checkmark, which changed what the chip meant at the moment the
  user most needed to recognise it.
- The save FAB's accessible name is unchanged after its shape changed, via a new
  `expanded` parameter.

### Release

- App version moved to **0.3.0 (versionCode 3)**. See below — previously every
  published release shipped `0.2.1` / code 2.
- Release titles now carry the app version, and the release notes are this
  changelog rather than GitHub's auto-generated commit list.

## [0.2.1] - 2026-09-26

- Material 3 Expressive redesign of the Voket UI.

## [0.2.0]

- Not published under this name. Releases before 0.3.0 are tagged `build-<run>`
  and all shipped `versionName` 0.2.1 / `versionCode` 2, because the build never
  received a version and fell back to a hardcoded default.
