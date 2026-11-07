package com.vishwaravi.pocketproj

import com.vishwaravi.pocketproj.data.SaveLinkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards how the outcome of a save is classified.
 *
 * A duplicate used to be filed under `LinkUiState.errorMessage`, which is not a neutral channel: the
 * add-link screens read it to set `isError` on the URL field and to announce
 * `"Validation error: …"` with `stateDescription = "Error"`. So saving a link the library already
 * had painted a perfectly valid URL red and told screen-reader users it was malformed.
 */
class SaveOutcomeTest {

    @Test
    fun aDuplicateIsReportedAsANotice() {
        val state = uiStateForSave(SaveLinkResult.Duplicate(7L))
        assertEquals("This URL is already saved", state.noticeMessage)
    }

    @Test
    fun aDuplicateIsNotAnError() {
        // The regression, asserted directly: errorMessage is what drives the field styling and the
        // "Validation error" announcement, so it has to stay null for a duplicate.
        assertNull(uiStateForSave(SaveLinkResult.Duplicate(7L)).errorMessage)
    }

    @Test
    fun aDuplicateDoesNotLookLikeASuccess() {
        // saveCompleted navigates away from the editor, which would hide the message entirely.
        assertFalse(uiStateForSave(SaveLinkResult.Duplicate(7L)).saveCompleted)
    }

    @Test
    fun aDuplicateIsNotMarkedAsStillSaving() {
        // A stuck spinner next to a "already saved" notice would read as a hang.
        assertFalse(uiStateForSave(SaveLinkResult.Duplicate(7L)).isSaving)
    }

    @Test
    fun aCreatedSaveCompletes() {
        val state = uiStateForSave(SaveLinkResult.Created(3L))
        assertTrue(state.saveCompleted)
        assertNull(state.errorMessage)
        assertNull(state.noticeMessage)
    }

    @Test
    fun aCreatedSaveSaysNothingToTheUser() {
        // Success navigates back, so any message here would flash on the way out.
        val state = uiStateForSave(SaveLinkResult.Created(3L))
        assertTrue(state.errorMessage.isNullOrBlank() && state.noticeMessage.isNullOrBlank())
    }

    @Test
    fun neitherOutcomeClaimsToBeStillSaving() {
        assertFalse(uiStateForSave(SaveLinkResult.Created(3L)).isSaving)
        assertFalse(uiStateForSave(SaveLinkResult.Duplicate(7L)).isSaving)
    }
}
