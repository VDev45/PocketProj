package com.vishwaravi.pocketproj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the version parsing behind the in-app update check.
 *
 * The regression: GitHub's releases API has no `version_code` / `version_name` fields, so the check
 * fell through to deriving a version code from the release tag. Tags are `build-<run_number>`, so
 * the GitHub Actions run number stood in for a version. Against the current build that made every
 * release look newer than the running app forever — users on build 270 (versionCode 3) were always
 * told an update was available, named "build-270".
 */
class UpdateManagerTest {

    /** The body of the live `build-270` release, trimmed to the parts that matter. */
    private val liveReleaseBody = """
        ## What's changed in 0.3.0

        ### Fixed

        - The Hidden Locker's Conceal/Reveal action did nothing.

        ---

        **Version** `0.3.0` (versionCode `3`)
        **Build** [`build-270`](https://github.com/vishwaravi/pocket-proj/actions/runs/36303462172)

        Install `app-release.apk` over an existing install — the signing key is unchanged.
    """.trimIndent()

    @Test
    fun parsesVersionFromTheLiveReleaseBody() {
        assertEquals("0.3.0" to 3L, parseReleaseVersion(liveReleaseBody))
    }

    @Test
    fun runNumberFromTheTagNeverLeaksOutAsTheVersionCode() {
        // The regression itself: the tag is build-270, so the old code produced 270 for a release
        // whose actual versionCode is 3 — permanently "newer" than any app build.
        val parsed = parseReleaseVersion(liveReleaseBody)
        assertEquals(3L, parsed?.second)
        assertTrue("run number must not be mistaken for a version", parsed?.second != 270L)
    }

    @Test
    fun parsesVersionRegardlessOfWhereTheLineSitsInTheBody() {
        assertEquals("1.2.3" to 42L, parseReleaseVersion("**Version** `1.2.3` (versionCode `42`)"))
    }

    @Test
    fun toleratesMissingBackticksAroundTheCode() {
        assertEquals("0.4.0" to 7L, parseReleaseVersion("**Version** `0.4.0` (versionCode 7)"))
    }

    @Test
    fun toleratesExtraWhitespace() {
        assertEquals("0.4.0" to 7L, parseReleaseVersion("**Version**   `0.4.0`  (  versionCode   `7`  )"))
    }

    @Test
    fun handlesMultiDigitAndLargeCodes() {
        assertEquals("10.0.0" to 1000L, parseReleaseVersion("**Version** `10.0.0` (versionCode `1000`)"))
    }

    @Test
    fun returnsNullWhenTheBodyHasNoVersionLine() {
        // An older release, or a hand-edited one. Reporting "no update" beats inventing a version.
        assertNull(parseReleaseVersion("## What's changed\n\n- some fixes\n"))
    }

    @Test
    fun returnsNullForAnEmptyBody() {
        assertNull(parseReleaseVersion(""))
    }

    @Test
    fun returnsNullWhenTheVersionCodeIsMissing() {
        assertNull(parseReleaseVersion("**Version** `0.3.0`"))
    }

    @Test
    fun returnsNullForANonPositiveVersionCode() {
        assertNull(parseReleaseVersion("**Version** `0.3.0` (versionCode `0`)"))
    }

    @Test
    fun doesNotMatchTheProseVersionHeading() {
        // "What's changed in 0.3.0" must not be mistaken for the version line.
        assertNull(parseReleaseVersion("## What's changed in 0.3.0"))
    }
}
