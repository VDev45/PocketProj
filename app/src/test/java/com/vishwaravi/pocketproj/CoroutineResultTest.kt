package com.vishwaravi.pocketproj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * Guards [runCatchingCancellable].
 *
 * The whole reason it exists: `runCatching` catches `Throwable`, and `CancellationException` is one,
 * so wrapping a suspending call in it turns "this coroutine was cancelled" into an ordinary
 * `Result.failure` and the caller carries on. The failure mode is invisible — the code reads as if
 * it handled an error.
 */
class CoroutineResultTest {

    @Test
    fun aValueBecomesASuccess() {
        assertEquals(Result.success(42), runCatchingCancellable { 42 })
    }

    @Test
    fun anOrdinaryExceptionBecomesAFailure() {
        // The whole point of the wrapper: it still does the job runCatching was doing.
        val result = runCatchingCancellable { throw IllegalStateException("boom") }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun aNullResultStaysANullSuccessRatherThanBecomingAFailure() {
        // runCatching's Result.success(null) is a real distinction from failure, and some callers
        // here return null on purpose (checkForUpdate returns null when already up to date).
        val result: Result<String?> = runCatchingCancellable { null }

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun cancellationIsRethrownRatherThanCaptured() {
        var returned = false
        try {
            runCatchingCancellable { throw CancellationException("cancelled") }
            returned = true
        } catch (_: CancellationException) {
            // expected
        }

        assertFalse("cancellation was captured instead of propagated", returned)
    }

    @Test
    fun anErrorIsStillCapturedBecauseThatIsWhatRunCatchingDid() {
        // Deliberate, so this stays a cancellation fix and not a change to what counts as a
        // recoverable failure. Pinned so nobody "fixes" it silently in either direction.
        val result = runCatchingCancellable { throw StackOverflowError("deep") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StackOverflowError)
    }

    @Test
    fun aCancellationRaisedAfterAnEarlierFailureIsNotConfusedForOne() {
        // The failure is captured, and the cancellation that follows it still propagates. These two
        // can both happen in one coroutine — a socket throws, then the scope is cancelled before
        // the handler finishes — and only the second one may be swallowed by accident.
        val result = runCatchingCancellable {
            throw IllegalStateException("first")
        }
        assertTrue(result.isFailure)

        try {
            runCatchingCancellable<Unit> { throw CancellationException("second") }
            throw AssertionError("the second cancellation was swallowed")
        } catch (expected: CancellationException) {
            assertEquals("second", expected.message)
        }
    }
}
