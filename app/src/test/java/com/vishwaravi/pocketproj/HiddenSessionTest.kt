package com.vishwaravi.pocketproj

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two rules that decide whether a stored "hidden-session-until" value still lets the
 * user into the Hidden Locker.
 *
 * The regression these exist for: with the lock timeout set to "Immediately", unlocking stored
 * `now + 0` — a timestamp that was already in the past by the time the next reader looked — so the
 * session the user had just created was rejected and the app bounced them straight back to the
 * Privacy screen. The vault was unreachable, with no error shown.
 */
class HiddenSessionTest {

    private val now = 1_700_000_000_000L

    // ── isHiddenSessionValid: "may I show hidden content right now?" ──────────────

    @Test
    fun noSession_isNotValid() {
        assertFalse(isHiddenSessionValid(expiry = 0L, now = now))
    }

    @Test
    fun foregroundSession_isValid() {
        assertTrue(isHiddenSessionValid(expiry = FOREGROUND_SESSION, now = now))
    }

    @Test
    fun foregroundSession_staysValidAsClockAdvances() {
        // The whole point of the sentinel: unlike `now + 0`, it does not go stale one frame later.
        assertTrue(isHiddenSessionValid(expiry = FOREGROUND_SESSION, now = now))
        assertTrue(isHiddenSessionValid(expiry = FOREGROUND_SESSION, now = now + 60_000L))
        assertTrue(isHiddenSessionValid(expiry = FOREGROUND_SESSION, now = now + 86_400_000L))
    }

    @Test
    fun timedSession_isValidBeforeExpiry() {
        assertTrue(isHiddenSessionValid(expiry = now + 60_000L, now = now))
    }

    @Test
    fun timedSession_isNotValidAtOrAfterExpiry() {
        assertFalse(isHiddenSessionValid(expiry = now, now = now))
        assertFalse(isHiddenSessionValid(expiry = now - 1L, now = now))
    }

    @Test
    fun sentinel_isNegativeSoItCannotCollideWithAnEpochExpiry() {
        // Guards the encoding itself: an expiry is wall-clock millis, so a negative marker is
        // unreachable by accident.
        assertTrue(FOREGROUND_SESSION < 0L)
    }

    // ── sessionSurvivesRestart: "may this session be trusted after the process restarts?" ──

    @Test
    fun foregroundSession_doesNotSurviveRestart() {
        assertFalse(sessionSurvivesRestart(expiry = FOREGROUND_SESSION, now = now))
    }

    @Test
    fun foregroundSession_doesNotSurviveRestart_evenAfterALongDelay() {
        // If this ever passed, cold-launching the app would expose hidden content with no PIN.
        assertFalse(sessionSurvivesRestart(expiry = FOREGROUND_SESSION, now = now + 86_400_000L))
    }

    @Test
    fun unexpiredTimedSession_survivesRestart() {
        assertTrue(sessionSurvivesRestart(expiry = now + 60_000L, now = now))
    }

    @Test
    fun expiredTimedSession_doesNotSurviveRestart() {
        assertFalse(sessionSurvivesRestart(expiry = now, now = now))
        assertFalse(sessionSurvivesRestart(expiry = now - 1L, now = now))
    }

    @Test
    fun absentSession_doesNotSurviveRestart_soCallersCanGuardOnIt() {
        // 0L is also "false" here, which is why ON_START pairs this with an `expiry != 0L` guard
        // before treating the result as "there was a session to clear".
        assertFalse(sessionSurvivesRestart(expiry = 0L, now = now))
    }

    @Test
    fun theTwoRulesDisagree_onlyForTheForegroundSentinel() {
        // The distinction the implementation depends on: a foreground session is valid *now* but
        // must not be trusted *after a restart*. Every other value answers both questions alike.
        assertTrue(isHiddenSessionValid(FOREGROUND_SESSION, now))
        assertFalse(sessionSurvivesRestart(FOREGROUND_SESSION, now))

        for (expiry in listOf(0L, now - 1L, now, now + 60_000L)) {
            assertTrue(
                "rules should agree for expiry=$expiry",
                isHiddenSessionValid(expiry, now) == sessionSurvivesRestart(expiry, now)
            )
        }
    }
}
