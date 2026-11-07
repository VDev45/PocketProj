package com.vishwaravi.pocketproj

import org.junit.Assert.assertEquals
import org.junit.Test

class PinSetupTest {

    @Test
    fun firstEntry_requiresConfirmation() {
        assertEquals(
            PinSetupResult.NEEDS_CONFIRMATION,
            evaluatePinSetup(firstEntry = null, confirmation = "1234")
        )
    }

    @Test
    fun matchingConfirmation_completesSetup() {
        assertEquals(
            PinSetupResult.MATCH,
            evaluatePinSetup(firstEntry = "1234", confirmation = "1234")
        )
    }

    @Test
    fun mismatchedConfirmation_doesNotCompleteSetup() {
        assertEquals(
            PinSetupResult.MISMATCH,
            evaluatePinSetup(firstEntry = "1234", confirmation = "4321")
        )
    }
}
