package com.vishwaravi.pocketproj

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast guards for the Collections screen (GitHub issues #22 and #23).
 *
 * Both issues reported the Collections page as "low contrast" from a screenshot, and the parts
 * named in them turned out to be almost entirely fine — the hero title sits at 13.8:1 and the
 * "Default" pill at 7.4:1. The one genuine failure was the "New collection" CTA, whose roles were
 * inverted: it filled with `onPrimaryContainer`, the hero's own background, and drew `primary` on
 * top. That is 2.4:1 in light theme and 1.3:1 in dark, below even the 3:1 non-text floor, which is
 * exactly why it read as disabled rather than merely soft.
 *
 * These tests pin the measured ratio for every foreground/background pair on that screen, in both
 * themes, so the next palette edit that quietly drops one below target fails here instead of
 * shipping.
 *
 * Two things this cannot do, stated plainly:
 *  - it cannot see the composable. It asserts the *palette* pairs, not that the composable is still
 *    wired to them. A future edit that points the CTA at different tokens would need its own
 *    assertion, and this file's comments name the call sites so the pairing is traceable by hand.
 *  - it cannot see Material You. Dynamic colour is resolved on-device from the user's wallpaper
 *    (Theme.kt), so those pairings are whatever the platform hands us, not what is declared here.
 */
class ColorContrastTest {

    /** WCAG AA for body text. */
    private val textTarget = 4.5f

    /** WCAG AA for large text and for non-text UI: icons, button glyphs, control outlines. */
    private val largeTextAndIconTarget = 3.0f

    @Test
    fun contrastRatioMatchesTheWcAgReferencePoints() {
        // The two anchors the formula is defined against, so a refactor cannot quietly change it.
        assertEquals(21.0f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(21.0f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1.0f, contrastRatio(Color.White, Color.White), 0.01f)
        assertEquals(1.0f, contrastRatio(Color(0xFF6644A6), Color(0xFF6644A6)), 0.01f)
    }

    @Test
    fun compositingATranslucentColourOntoASurfaceFlattensIt() {
        val surface = Color(0xFFEBE0FF)
        val solid = contrastRatio(Color(0xFF23064F), surface)

        // Fully opaque composites back to the original colour, so the ratio is unchanged.
        assertEquals(solid, contrastRatio(Color(0xFF23064F).compositedOver(surface), surface), 0.01f)
        // Fully transparent composites to the surface itself, so the ratio collapses to 1.
        assertEquals(1.0f, contrastRatio(Color.Black.copy(alpha = 0f).compositedOver(surface), surface), 0.01f)
        // And a half-transparent one lands between the two, not below the surface.
        val half = contrastRatio(Color(0xFF23064F).copy(alpha = 0.5f).compositedOver(surface), surface)
        assertTrue("half alpha gave $half", half > 1.0f)
        assertTrue("half alpha gave $half", half < solid)
    }

    @Test
    fun collectionsHeroTitleMeetsTextContrastInBothThemes() {
        assertTextContrast(
            "Collections hero title",
            VoketLight.onPrimaryContainer, VoketLight.primaryContainer
        )
        assertTextContrast(
            "Collections hero title",
            VoketDark.onPrimaryContainer, VoketDark.primaryContainer
        )
    }

    @Test
    fun collectionsHeroDescriptionMeetsTextContrastInBothThemes() {
        // The hero body is drawn at 80% alpha over the hero, so the ratio has to be measured
        // against the composited colour, not the raw token.
        assertTextContrast(
            "Collections hero description",
            VoketLight.onPrimaryContainer.copy(alpha = 0.8f).compositedOver(VoketLight.primaryContainer),
            VoketLight.primaryContainer
        )
        assertTextContrast(
            "Collections hero description",
            VoketDark.onPrimaryContainer.copy(alpha = 0.8f).compositedOver(VoketDark.primaryContainer),
            VoketDark.primaryContainer
        )
    }

    @Test
    fun collectionsNewCollectionCtaMeetsIconAndTextContrastInBothThemes() {
        // The regression from #23. primary on onPrimaryContainer was 2.4:1 light / 1.3:1 dark.
        assertTextContrast("New collection CTA", VoketLight.onPrimary, VoketLight.primary)
        assertTextContrast("New collection CTA", VoketDark.onPrimary, VoketDark.primary)
    }

    @Test
    fun collectionsHeaderAddButtonMeetsIconContrastInBothThemes() {
        // #23 also asked for this to be reviewed. It was already correct at 7.1:1 / 7.8:1 — pinned
        // here so it cannot regress, and so the CTA above has something to match.
        assertIconContrast("Collections header +", VoketLight.onPrimary, VoketLight.primary)
        assertIconContrast("Collections header +", VoketDark.onPrimary, VoketDark.primary)
    }

    @Test
    fun collectionsHeroFolderGlyphMeetsIconContrastInBothThemes() {
        // Drawn in onPrimaryContainer inside a 16%-alpha onPrimaryContainer tile on the hero.
        assertIconContrast("Collections hero folder", VoketLight.onPrimaryContainer, VoketLight.primaryContainer)
        assertIconContrast("Collections hero folder", VoketDark.onPrimaryContainer, VoketDark.primaryContainer)
    }

    @Test
    fun collectionCardTextMeetsTextContrastInBothThemes() {
        assertTextContrast("Collection card title", VoketLight.onSurface, VoketLight.surfaceContainerLow)
        assertTextContrast("Collection card title", VoketDark.onSurface, VoketDark.surfaceContainerLow)
        assertTextContrast("Collection card link count", VoketLight.onSurfaceVariant, VoketLight.surfaceContainerLow)
        assertTextContrast("Collection card link count", VoketDark.onSurfaceVariant, VoketDark.surfaceContainerLow)
    }

    @Test
    fun collectionCardBadgesAndActionsMeetContrastInBothThemes() {
        // The "Default" pill: onSurfaceVariant on surfaceContainerHigh.
        assertTextContrast("Default pill", VoketLight.onSurfaceVariant, VoketLight.surfaceContainerHigh)
        assertTextContrast("Default pill", VoketDark.onSurfaceVariant, VoketDark.surfaceContainerHigh)
        // The rename and delete round actions: primary and error on surfaceContainerHigh.
        assertIconContrast("Collection rename action", VoketLight.primary, VoketLight.surfaceContainerHigh)
        assertIconContrast("Collection rename action", VoketDark.primary, VoketDark.surfaceContainerHigh)
        assertIconContrast("Collection delete action", VoketLight.error, VoketLight.surfaceContainerHigh)
        assertIconContrast("Collection delete action", VoketDark.error, VoketDark.surfaceContainerHigh)
    }

    @Test
    fun collectionSectionLabelMeetsTextContrastInBothThemes() {
        // "All spaces" sits directly on the canvas, with no card behind it.
        assertTextContrast("All spaces label", VoketLight.onSurfaceVariant, VoketLight.background)
        assertTextContrast("All spaces label", VoketDark.onSurfaceVariant, VoketDark.background)
    }

    @Test
    fun noTokenPairUsedOnThisScreenFallsBelowTarget() {
        // A blunt sweep over the whole scheme, so an edit to either palette trips this even if it
        // lands somewhere other than the pairs spelled out above.
        listOf(
            "light" to VoketLight,
            "dark" to VoketDark
        ).forEach { (theme, scheme) ->
            val textPairs = listOf(
                "onSurface/surface" to (scheme.onSurface to scheme.surface),
                "onSurface/surfaceContainerLow" to (scheme.onSurface to scheme.surfaceContainerLow),
                "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
                "onSurfaceVariant/surfaceContainerLow" to (scheme.onSurfaceVariant to scheme.surfaceContainerLow),
                "onSurfaceVariant/surfaceContainerHigh" to (scheme.onSurfaceVariant to scheme.surfaceContainerHigh),
                "onSurfaceVariant/background" to (scheme.onSurfaceVariant to scheme.background),
                "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
                "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "onError/error" to (scheme.onError to scheme.error),
                "onErrorContainer/errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                "onSecondary/secondary" to (scheme.onSecondary to scheme.secondary),
                "onSecondaryContainer/secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "onTertiary/tertiary" to (scheme.onTertiary to scheme.tertiary),
                "onTertiaryContainer/tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer)
            )
            textPairs.forEach { (name, pair) ->
                val (fg, bg) = pair
                val ratio = contrastRatio(fg, bg)
                assertTrue(
                    "$theme $name is $ratio:1, below the $textTarget:1 text target",
                    ratio >= textTarget
                )
            }

            val iconPairs = listOf(
                "primary/surfaceContainerHigh" to (scheme.primary to scheme.surfaceContainerHigh),
                "primary/surface" to (scheme.primary to scheme.surface),
                "error/surfaceContainerHigh" to (scheme.error to scheme.surfaceContainerHigh),
                "outline/surface" to (scheme.outline to scheme.surface)
            )
            iconPairs.forEach { (name, pair) ->
                val (fg, bg) = pair
                val ratio = contrastRatio(fg, bg)
                assertTrue(
                    "$theme $name is $ratio:1, below the $largeTextAndIconTarget:1 icon target",
                    ratio >= largeTextAndIconTarget
                )
            }
        }
    }

    private fun assertTextContrast(label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label is $ratio:1, below the $textTarget:1 text target", ratio >= textTarget)
    }

    private fun assertIconContrast(label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$label is $ratio:1, below the $largeTextAndIconTarget:1 icon target",
            ratio >= largeTextAndIconTarget
        )
    }
}
