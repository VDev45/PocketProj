package com.vishwaravi.pocketproj

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] that lets coroutine cancellation through.
 *
 * `runCatching` catches `Throwable`, and `CancellationException` is a `Throwable`. So wrapping a
 * suspending call in it converts "this coroutine was cancelled" into an ordinary
 * `Result.failure`, and the calling coroutine then carries on running the rest of its body instead
 * of unwinding. That breaks the one guarantee structured concurrency exists to provide, and the
 * failure is invisible: the code looks like it handled an error.
 *
 * It is reachable in this app wherever a suspending call sits inside one of these wrappers and the
 * coroutine can be cancelled underneath it — a ViewModel cleared, an Activity finished, a
 * `CoroutineWorker` replaced, a `withTimeout` expiring. `UpdateManager.checkForUpdate` and
 * `downloadAndInstall` are the clearest cases: both do blocking I/O on `Dispatchers.IO`, so
 * cancelling them mid-request throws, and the caller used to continue on to its update-prompt
 * handling for a screen that was no longer there.
 *
 * Intended only for suspending code. Around non-suspending work there is no cancellation to lose —
 * the call cannot throw `CancellationException` on its own — so plain `runCatching` stays correct
 * and is left alone.
 *
 * Note this still catches `Error` and puts it in the `Result`, exactly as `runCatching` does. That
 * is pre-existing behaviour, deliberately preserved here so this is a cancellation fix and not a
 * change to what counts as a recoverable failure.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    // Not a failure to report. Rethrown so the coroutine unwinds normally; swallowing it here is
    // the bug this function exists to prevent.
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(throwable)
}
