package com.vishwaravi.pocketproj

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/* ═══════════════════════════════════════════════════════════════════════════
   Voket UI primitives.

   Screens compose these instead of raw Material components so the shape
   language, tonal surfaces, spacing rhythm and motion stay consistent.
   ═══════════════════════════════════════════════════════════════════════════ */

/* ── Interaction feedback ─────────────────────────────────────────────────── */

/**
 * Squash-on-press feedback that does not consume the gesture, so it composes
 * with `Surface(onClick = …)` without stealing the tap.
 */
@Composable
fun Modifier.pressScale(pressedScale: Float = 0.96f, enabled: Boolean = true): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = pocketTween(PocketMotion.micro),
        label = "pressScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/* ── Decorative layer ─────────────────────────────────────────────────────── */

/**
 * Low-contrast decorative field that sits behind content: two soft organic
 * blobs and a wide tonal mist. It reinforces the composition without ever
 * competing with text for contrast.
 */
@Composable
fun PocketBackdrop(
    modifier: Modifier = Modifier,
    blobs: List<Color> = listOf(PocketColors.BlobLilac, PocketColors.BlobMint, PocketColors.BlobAmber)
) {
    val mist = PocketColors.Mist
    Box(
        modifier.drawBehind {
            // A whisper of tonal lift at the very top, gone by a third of the
            // way down. The rest of the canvas stays flat so it reads as a
            // quiet zone rather than a painted field.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to mist,
                    0.32f to Color.Transparent
                )
            )
            if (blobs.isNotEmpty()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(blobs[0].copy(alpha = 0.5f), Color.Transparent),
                        center = center.copy(x = size.width * 0.88f, y = size.height * 0.04f),
                        radius = size.minDimension * 0.58f
                    ),
                    radius = size.minDimension * 0.58f,
                    center = center.copy(x = size.width * 0.88f, y = size.height * 0.04f)
                )
            }
            if (blobs.size > 1) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(blobs[1].copy(alpha = 0.4f), Color.Transparent),
                        center = center.copy(x = size.width * -0.08f, y = size.height * 0.42f),
                        radius = size.minDimension * 0.50f
                    ),
                    radius = size.minDimension * 0.50f,
                    center = center.copy(x = size.width * -0.08f, y = size.height * 0.42f)
                )
            }
            if (blobs.size > 2) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(blobs[2].copy(alpha = 0.28f), Color.Transparent),
                        center = center.copy(x = size.width * 0.14f, y = size.height * 0.97f),
                        radius = size.minDimension * 0.46f
                    ),
                    radius = size.minDimension * 0.46f,
                    center = center.copy(x = size.width * 0.14f, y = size.height * 0.97f)
                )
            }
        }
    )
}

/**
 * Full-bleed screen container: tonal canvas plus the decorative field. Each
 * screen passes its own blob triad so the backdrop never looks stamped.
 */
@Composable
fun PocketScreen(
    modifier: Modifier = Modifier,
    blobs: List<Color> = listOf(PocketColors.BlobLilac, PocketColors.BlobMint, PocketColors.BlobAmber),
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxSize().background(PocketColors.Canvas)) {
        PocketBackdrop(Modifier.fillMaxSize(), blobs)
        content()
    }
}

/* ── Headers ──────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PocketPageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onTitleLongPress: (() -> Unit)? = null,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(Modifier.pressScale()) {
                Surface(
                    onClick = onBack,
                    shape = PocketShape.squircle,
                    color = PocketColors.SurfaceRaised,
                    contentColor = PocketColors.Ink,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(PocketSpace.md))
        }
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = PocketType.overline,
                    // Eyebrows are secondary metadata; keep them neutral so
                    // they remain readable against every theme/background.
                    color = PocketColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                title,
                style = if (eyebrow != null) PocketType.headlineSmall else PocketType.headline,
                color = PocketColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onTitleLongPress != null) {
                    Modifier.combinedClickable(
                        onLongClickLabel = "Open hidden vault",
                        onLongClick = onTitleLongPress,
                        onClick = {}
                    )
                } else {
                    Modifier
                }
            )
        }
        Spacer(Modifier.width(PocketSpace.sm))
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Small uppercase eyebrow used above groups of content. */
@Composable
/**
 * Section label. The top padding is the section break; the gap below is left to
 * the caller's `spacedBy`, so the two do not compound into 30dp of dead air
 * between a heading and the thing it introduces.
 */
fun PocketSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = PocketType.overline,
        color = PocketColors.InkFaint,
        modifier = modifier.padding(top = PocketSpace.xs)
    )
}

/** Section title with an optional trailing affordance. */
@Composable
fun PocketSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = PocketSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PocketType.title, color = PocketColors.Ink)
            if (subtitle != null) {
                Text(subtitle, style = PocketType.bodySmall, color = PocketColors.InkMuted)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun PocketFieldLabel(text: String, trailingText: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = PocketType.label, color = PocketColors.InkMuted)
        if (trailingText != null) {
            Spacer(Modifier.weight(1f))
            Text(trailingText, style = PocketType.label, color = PocketColors.Accent)
        }
    }
}

/* ── Inputs ───────────────────────────────────────────────────────────────── */

@Composable
fun PocketFormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = PocketType.body, color = PocketColors.InkFaint) },
        textStyle = PocketType.body.copy(color = PocketColors.Ink, fontWeight = FontWeight.Medium),
        shape = PocketShape.md,
        singleLine = singleLine,
        maxLines = maxLines,
        readOnly = readOnly,
        enabled = enabled,
        isError = isError,
        leadingIcon = leadingContent,
        trailingIcon = trailingContent,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PocketColors.SurfaceLowest,
            unfocusedContainerColor = PocketColors.SurfaceLowest,
            disabledContainerColor = PocketColors.SurfaceLow,
            errorContainerColor = PocketColors.DangerSoft,
            focusedBorderColor = PocketColors.Accent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = PocketColors.Danger,
            focusedTextColor = PocketColors.Ink,
            unfocusedTextColor = PocketColors.Ink,
            disabledTextColor = PocketColors.InkFaint,
            cursorColor = PocketColors.Accent
        )
    )
}

/** Large, calm search field used as the hero of the search screen. */
@Composable
fun PocketSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    onClear: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = PocketType.body, color = PocketColors.InkFaint) },
        leadingIcon = leading,
        trailingIcon = if (onClear != null && value.isNotEmpty()) {
            {
                Box(Modifier.pressScale(0.9f), contentAlignment = Alignment.Center) {
                    Surface(
                        onClick = onClear,
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = PocketColors.InkMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        } else null,
        singleLine = true,
        shape = PocketShape.pill,
        textStyle = PocketType.bodyStrong.copy(color = PocketColors.Ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PocketColors.SurfaceLowest,
            unfocusedContainerColor = PocketColors.SurfaceLowest,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = PocketColors.Ink,
            unfocusedTextColor = PocketColors.Ink,
            cursorColor = PocketColors.Accent
        )
    )
}

/* ── Buttons ──────────────────────────────────────────────────────────────── */

@Composable
fun PocketPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tonal: Boolean = false,
    destructive: Boolean = false,
    /**
     * Stretch to the full width offered by the parent, which is what a dialog's action row and a
     * form's submit button want. Pass false where the button is one item among several on a line —
     * a card footer, say — where stretching turns the action into the card's main content. The
     * 52dp height holds either way, so an unwrapped button still clears the 48dp touch target.
     */
    fillWidth: Boolean = true
) {
    val container = when {
        destructive -> PocketColors.Danger
        tonal -> PocketColors.AccentSoft
        else -> PocketColors.Accent
    }
    val content = when {
        destructive -> PocketColors.OnDanger
        tonal -> PocketColors.OnAccentSoft
        else -> PocketColors.OnAccent
    }
    Box(modifier.pressScale(enabled = enabled)) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = PocketShape.pill,
            color = if (enabled) container else container.copy(alpha = 0.45f),
            contentColor = content,
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(52.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PocketSpace.xl),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(PocketSpace.sm))
                }
                Text(
                    text,
                    style = PocketType.button,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Compact tonal pill button — the secondary action tier. */
@Composable
fun PocketTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Box(modifier.pressScale(enabled = enabled)) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = PocketShape.pill,
            color = PocketColors.SurfaceHigh,
            contentColor = PocketColors.Ink,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PocketSpace.lg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(PocketSpace.xs))
                }
                Text(text, style = PocketType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Wraps its content instead of filling the parent. Use inside a `Row` (headers,
 * card footers) where a full-width button would hijack the layout.
 */
@Composable
fun PocketCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = PocketColors.AccentSoft,
    content: Color = PocketColors.OnAccentSoft
) {
    Box(modifier.pressScale(0.94f, enabled)) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = PocketShape.pill,
            color = if (enabled) container else container.copy(alpha = 0.45f),
            contentColor = content,
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PocketSpace.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(PocketSpace.xs))
                }
                Text(
                    text,
                    style = PocketType.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Text-tier action. */
@Composable
fun PocketTextAction(text: String, modifier: Modifier = Modifier, tint: Color = PocketColors.Accent, onClick: () -> Unit) {
    Text(
        text,
        style = PocketType.label,
        color = tint,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(PocketShape.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = PocketSpace.md, vertical = PocketSpace.sm)
    )
}

/** Icon button in a soft squircle container — 44dp target. */
@Composable
fun PocketIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color = PocketColors.Ink,
    container: Color = PocketColors.SurfaceRaised
) {
    val resolvedTint = if (selected) PocketColors.OnAccentSoft else tint
    val resolvedContainer = if (selected) PocketColors.Accent else container
    Box(modifier.pressScale(0.92f)) {
        Surface(
            onClick = onClick,
            shape = PocketShape.squircle,
            color = resolvedContainer,
            contentColor = resolvedTint,
            modifier = Modifier.size(44.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                    this.selected = selected
                }
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Circular icon button without a container — used inside list rows. */
@Composable
fun PocketRoundAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = PocketColors.InkMuted,
    container: Color = Color.Transparent,
    size: Dp = 42.dp
) {
    Box(modifier.pressScale(0.88f)) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = container,
            contentColor = tint,
            modifier = Modifier.size(size)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.semantics { this.contentDescription = contentDescription }
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            }
        }
    }
}

/* ── Chips, pills and segmented control ───────────────────────────────────── */

/**
 * Filter chip. The semantic leading icon is *always* kept — swapping it for a
 * checkmark when selected made the chip change meaning at the moment the user
 * most needed to recognise it. Selection is carried by the fill, the weight and
 * the label's own semantics instead.
 */
@Composable
fun PocketPill(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    onClick: () -> Unit,
    leading: ImageVector? = null,
    glyph: String? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    val container by animateColorAsState(
        if (selected) PocketColors.Accent else PocketColors.SurfaceRaised,
        animationSpec = pocketTween(PocketMotion.micro),
        label = "pillContainer"
    )
    val content by animateColorAsState(
        if (selected) PocketColors.OnAccent else PocketColors.InkMuted,
        animationSpec = pocketTween(PocketMotion.micro),
        label = "pillContent"
    )
    Box(modifier.pressScale(0.94f)) {
        Surface(
            onClick = onClick,
            shape = PocketShape.pill,
            color = container,
            contentColor = content,
            modifier = Modifier.height(height)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PocketSpace.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (leadingContent != null) {
                    leadingContent()
                } else if (glyph != null) {
                    Text(
                        glyph,
                        style = PocketType.label.copy(fontSize = 13.sp),
                        color = content,
                        maxLines = 1
                    )
                } else if (leading != null) {
                    Icon(leading, contentDescription = null, modifier = Modifier.size(15.dp))
                }
                Text(
                    text,
                    style = if (selected) PocketType.label.copy(fontWeight = FontWeight.Bold) else PocketType.label,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        this.selected = selected
                        stateDescription = if (selected) "Selected" else "Not selected"
                    }
                )
            }
        }
    }
}

/** Horizontally scrolling row of filter pills. */
@Composable
fun PocketChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PocketSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** Pill segmented control with an animated selected fill. */
@Composable
fun <T> PocketSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(PocketShape.pill)
            .background(PocketColors.SurfaceRaised)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            val container by animateColorAsState(
                if (active) PocketColors.Accent else Color.Transparent,
                animationSpec = pocketTween(PocketMotion.normal),
                label = "segmentContainer"
            )
            val content by animateColorAsState(
                if (active) PocketColors.OnAccent else PocketColors.InkMuted,
                animationSpec = pocketTween(PocketMotion.normal),
                label = "segmentContent"
            )
            Box(Modifier.weight(1f).pressScale(0.96f)) {
                Surface(
                    onClick = { onSelect(option) },
                    shape = PocketShape.pill,
                    color = container,
                    contentColor = content,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.semantics {
                            role = Role.RadioButton
                            this.selected = active
                            stateDescription = if (active) "Selected" else "Not selected"
                        }
                    ) {
                        Text(
                            label(option),
                            style = PocketType.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/* ── Surfaces ─────────────────────────────────────────────────────────────── */

@Composable
fun PocketCardSurface(
    tint: Color = PocketColors.Surface,
    modifier: Modifier = Modifier,
    shape: Shape = PocketShape.lg,
    content: @Composable () -> Unit
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = tint, content = content)
}

/** A tonal container that groups a set of rows without bordering each one. */
@Composable
fun PocketGroup(
    modifier: Modifier = Modifier,
    shape: Shape = PocketShape.xl,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = PocketColors.SurfaceLow,
        content = content
    )
}

/** Hairline used *inside* grouped surfaces only — never around every card. */
@Composable
fun PocketHairline(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(PocketColors.Hairline.copy(alpha = 0.7f))
    )
}

/** Grouped list row: leading icon container, text block, trailing affordance. */
@Composable
fun PocketListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    glyph: String? = null,
    accent: Color = PocketColors.Accent,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) {
        modifier.fillMaxWidth().clip(PocketShape.md).clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(horizontal = PocketSpace.md, vertical = PocketSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(PocketSpace.md))
        } else if (icon != null || glyph != null) {
            PocketIconBadge(icon = icon, glyph = glyph, accent = accent, size = 42.dp)
            Spacer(Modifier.width(PocketSpace.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = PocketType.bodyStrong,
                color = PocketColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = PocketType.caption,
                    color = PocketColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(PocketSpace.sm))
            trailing()
        }
    }
}

/** Icon container. Alternates between a squircle and a pill to break repetition. */
@Composable
fun PocketIconBadge(
    icon: ImageVector? = null,
    glyph: String? = null,
    accent: Color = PocketColors.Accent,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    shape: Shape = PocketShape.squircle,
    container: Color = accent.copy(alpha = 0.14f),
    content: (@Composable () -> Unit)? = null
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        when {
            content != null -> content()
            icon != null -> Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(size * 0.5f)
            )
            glyph != null -> Text(
                glyph,
                style = PocketType.glyph.copy(fontSize = (size.value * 0.42f).sp),
                color = accent,
                maxLines = 1
            )
        }
    }
}

/* ── Stat surfaces ────────────────────────────────────────────────────────── */

/**
 * The *dominant* stat. One per screen at most. Laid out horizontally — numeral
 * on the left, label and supporting copy stacked on the right — so it reads as
 * a single dense statement rather than a tall near-empty box.
 */
@Composable
fun PocketDominantStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glyph: String? = null,
    accent: Color = PocketColors.Accent,
    caption: String? = null,
    onClick: (() -> Unit)? = null
) {
    val base = modifier
        .clip(PocketShape.lg)
        .background(PocketColors.SurfaceLow)
    val clickable = if (onClick != null) base.pressScale().clickable(onClick = onClick) else base
    Row(
        modifier = clickable.padding(PocketSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            value,
            style = PocketType.numeric.copy(fontSize = 36.sp, lineHeight = 40.sp),
            color = accent,
            maxLines = 1
        )
        Spacer(Modifier.width(PocketSpace.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                label,
                style = PocketType.bodyStrong,
                color = PocketColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (caption != null) {
                Text(
                    caption,
                    style = PocketType.caption,
                    color = PocketColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (icon != null || glyph != null) {
            Spacer(Modifier.width(PocketSpace.sm))
            PocketIconBadge(icon = icon, glyph = glyph, accent = accent, size = 32.dp, shape = CircleShape)
        }
    }
}

/**
 * A small supporting stat. Reads as a quiet companion to
 * [PocketDominantStat] — icon, value and label on one line, half the type size.
 */
@Composable
fun PocketCompactStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glyph: String? = null,
    accent: Color = PocketColors.Accent,
    onClick: (() -> Unit)? = null
) {
    val base = modifier
        .clip(PocketShape.md)
        .background(PocketColors.SurfaceLow)
    val clickable = if (onClick != null) base.pressScale().clickable(onClick = onClick) else base
    Row(
        modifier = clickable.padding(horizontal = PocketSpace.md, vertical = PocketSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null || glyph != null) {
            PocketIconBadge(icon = icon, glyph = glyph, accent = accent, size = 32.dp, shape = CircleShape)
            Spacer(Modifier.width(PocketSpace.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(
                value,
                style = PocketType.title,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, style = PocketType.caption, color = PocketColors.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * One contextual library summary. Replaces a row of metric cards with a single
 * statement about the pocket, and folds the supporting counts *inside* it so
 * they cannot drift away from the number they qualify.
 *
 * Deliberately a tighter radius than the hero sheet above it — shape contrast,
 * not a stack of identical big rounded boxes.
 */
@Composable
fun PocketSummarySheet(
    value: String,
    headline: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    parts: List<Pair<String, String>> = emptyList(),
    accent: Color = PocketColors.Accent
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PocketShape.md,
        // Keep summary surfaces neutral so dynamic accent colors can never
        // become a low-contrast text background.
        color = PocketColors.SurfaceRaised,
        contentColor = PocketColors.Ink
    ) {
        Column(Modifier.padding(start = PocketSpace.lg, end = PocketSpace.lg, top = PocketSpace.md, bottom = PocketSpace.md)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = PocketType.overline,
                    color = PocketColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(PocketSpace.xs))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = PocketType.numeric.copy(fontSize = 38.sp, lineHeight = 42.sp),
                    color = accent,
                    maxLines = 1
                )
                Spacer(Modifier.width(PocketSpace.sm))
                Text(
                    headline,
                    style = PocketType.titleSmall,
                    color = PocketColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (parts.isNotEmpty()) {
                Spacer(Modifier.height(PocketSpace.xs))
                PocketStatBreakdown(parts = parts, accent = accent)
            }
        }
    }
}

/** A single quiet metadata line, e.g. "Favorites 2 · Collections 3". */
@Composable
fun PocketStatBreakdown(
    parts: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    accent: Color = PocketColors.Accent
) {
    if (parts.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        parts.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Text("·", style = PocketType.caption, color = PocketColors.Hairline)
                Spacer(Modifier.width(PocketSpace.sm))
            }
            Text(label, style = PocketType.caption, color = PocketColors.InkMuted)
            Spacer(Modifier.width(PocketSpace.xxs))
            Text(value, style = PocketType.caption.copy(fontWeight = FontWeight.Bold), color = accent)
        }
    }
}

/* ── Empty / status states ────────────────────────────────────────────────── */

@Composable
fun PocketEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glyph: String? = null,
    accent: Color = PocketColors.Accent,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = PocketSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PocketSpace.sm)
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(PocketShape.blob)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                icon != null -> Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
                glyph != null -> Text(glyph, style = PocketType.display.copy(fontSize = 34.sp), color = accent)
            }
        }
        Spacer(Modifier.height(PocketSpace.xxs))
        Text(
            title,
            style = PocketType.title,
            color = PocketColors.Ink,
            textAlign = TextAlign.Center
        )
        Text(
            body,
            style = PocketType.body,
            color = PocketColors.InkMuted,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(PocketSpace.xs))
            action()
        }
    }
}

/** Inline status/error banner with an icon container instead of a border. */
@Composable
fun PocketBanner(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Close,
    accent: Color = PocketColors.Danger,
    container: Color = PocketColors.DangerSoft,
    onAccent: Color = PocketColors.OnDangerSoft
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PocketShape.md)
            .background(container)
            .padding(horizontal = PocketSpace.md, vertical = PocketSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Text(message, style = PocketType.bodySmall, color = onAccent)
    }
}

/* ── Navigation ───────────────────────────────────────────────────────────── */

data class PocketNavDestination(
    val label: String,
    val icon: ImageVector
)

/**
 * Bottom navigation. Deliberately *one* piece of geometry, not two: a soft
 * rounded container holding a single tonal capsule for the selected
 * destination. There is no outer pill-with-shadow wrapping an inner pill, so the
 * bar cannot compete with the content above it.
 */
@Composable
fun PocketBottomBar(
    destinations: List<PocketNavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PocketShape.xl,
        color = PocketColors.SurfaceLow,
        contentColor = PocketColors.Ink,
        shadowElevation = PocketElevation.whisper,
        tonalElevation = PocketElevation.flat
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex
                val container by animateColorAsState(
                    if (selected) PocketColors.AccentSoft else Color.Transparent,
                    animationSpec = pocketTween(PocketMotion.normal),
                    label = "navContainer"
                )
                val content by animateColorAsState(
                    if (selected) PocketColors.OnAccentSoft else PocketColors.InkMuted,
                    animationSpec = pocketTween(PocketMotion.normal),
                    label = "navContent"
                )
                Box(Modifier.weight(1f).pressScale(0.94f)) {
                    Surface(
                        onClick = { onSelect(index) },
                        shape = PocketShape.pill,
                        color = container,
                        contentColor = content,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics {
                                    role = Role.Tab
                                    this.selected = selected
                                    stateDescription = if (selected) "Selected" else "Not selected"
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                destination.label,
                                style = PocketType.caption,
                                color = content,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Primary action. A flat accent fill — no gradient — so it is clearly the
 * strongest *action* without becoming the strongest *object* on the page.
 *
 * [expanded] controls the form only, never the accessible name: the compact
 * circular variant still announces [label], so switching form does not drop the
 * button's meaning for a screen reader.
 */
@Composable
fun PocketFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    expanded: Boolean = true,
    icon: ImageVector = Icons.Filled.Add
) {
    val pill = expanded && label != null
    Box(modifier.pressScale(0.92f)) {
        Surface(
            onClick = onClick,
            shape = if (pill) PocketShape.pill else CircleShape,
            color = PocketColors.Accent,
            contentColor = PocketColors.OnAccent,
            shadowElevation = PocketElevation.lifted,
            modifier = Modifier.size(if (pill) 54.dp else 56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (pill) {
                    Row(
                        modifier = Modifier.padding(horizontal = PocketSpace.xl),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                        Text(label, style = PocketType.button, maxLines = 1)
                    }
                } else {
                    Icon(
                        icon,
                        contentDescription = label ?: "Add link",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/* ── Dialogs ──────────────────────────────────────────────────────────────── */

@Composable
fun PocketDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String,
    body: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String = "Cancel",
    /**
     * Invoked by the dismiss button, when it needs to mean something other than "cancel" — a
     * two-choice dialog whose secondary action is a decision, not a dismissal. Defaults to
     * [onDismissRequest], which is what every plain Cancel dialog wants.
     */
    onDismissAction: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    // Capped against the viewport so a long body cannot grow the dialog past the screen. The
    // actions sit outside the scrolling region, so they stay reachable however long the content is.
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.fillMaxWidth().heightIn(max = maxDialogHeight),
            shape = PocketShape.xl,
            color = PocketColors.Surface,
            contentColor = PocketColors.Ink,
            shadowElevation = PocketElevation.overlay,
            tonalElevation = PocketElevation.flat
        ) {
            Column(
                modifier = Modifier.padding(PocketSpace.xl),
                verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
            ) {
                // Icon and title stay put: a confirmation has to keep saying what it is about
                // while the content scrolls under it. Titles that carry user text are truncated by
                // confirmPreview(), so pinning them cannot reintroduce the overflow this fixes.
                if (icon != null) {
                    PocketIconBadge(
                        icon = icon,
                        accent = if (destructive) PocketColors.Danger else PocketColors.Accent,
                        size = 52.dp,
                        shape = PocketShape.blob
                    )
                }
                Text(title, style = PocketType.headlineSmall, color = PocketColors.Ink)
                // Only the body and any custom content scroll. fill = false so a short dialog stays
                // compact instead of being stretched out to the cap.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
                ) {
                    if (body != null) {
                        Text(body, style = PocketType.body, color = PocketColors.InkMuted)
                    }
                    content?.invoke()
                }
                if (onConfirm != null && confirmLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = PocketSpace.xs),
                        horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
                    ) {
                        Box(Modifier.weight(1f)) {
                            PocketTonalButton(
                                text = dismissLabel,
                                onClick = onDismissAction ?: onDismissRequest
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            PocketPrimaryButton(
                                text = confirmLabel,
                                onClick = onConfirm,
                                destructive = destructive
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ── Small drawn glyphs ───────────────────────────────────────────────────── */

@Composable
fun PocketFolderIcon(tint: Color = PocketColors.InkMuted, modifier: Modifier = Modifier) {
    Box(modifier.size(20.dp)) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 2.dp)
                .size(width = 9.dp, height = 6.dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomEnd = 1.dp))
                .background(tint)
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(13.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(tint)
        )
    }
}

@Composable
fun PocketGridIcon(tint: Color = PocketColors.Ink, modifier: Modifier = Modifier) {
    Column(modifier.size(20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.5.dp)).background(tint)) }
            }
        }
    }
}

@Composable
fun PocketListIcon(tint: Color = PocketColors.Ink, modifier: Modifier = Modifier) {
    Column(modifier.size(20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { Box(Modifier.fillMaxWidth().height(3.5.dp).clip(CircleShape).background(tint)) }
    }
}

/**
 * Ordering affordance: three rules of decreasing length, reading top to bottom.
 * `material-icons-core` has no Sort glyph, so this stands in for one.
 */
@Composable
fun PocketSortGlyph(tint: Color = PocketColors.InkMuted, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(15.dp)
            .drawBehind {
                val stroke = 1.7.dp.toPx()
                val widths = listOf(1.0f, 0.76f, 0.5f)
                val gap = size.height / 3f
                widths.forEachIndexed { index, fraction ->
                    val y = gap * index + gap / 2f
                    val w = size.width * fraction
                    drawLine(
                        color = tint,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(w, y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
    )
}

/** Diagonal arrow leaving the frame — the "open externally" affordance. */
@Composable
fun PocketOutboundGlyph(tint: Color = PocketColors.InkMuted, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(19.dp)
            .drawBehind {
                val stroke = 1.9.dp.toPx()
                val startX = size.width * 0.20f
                val startY = size.height * 0.80f
                val endX = size.width * 0.82f
                val endY = size.height * 0.18f
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(endX, endY)
                        lineTo(endX - 7.dp.toPx(), endY + 1.5.dp.toPx())
                        moveTo(endX, endY)
                        lineTo(endX - 1.5.dp.toPx(), endY + 7.dp.toPx())
                    },
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
    )
}

/** Two overlapping sheets — the "copy" affordance. */
@Composable
fun PocketCopyGlyph(tint: Color = PocketColors.InkMuted, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(19.dp)
            .drawBehind {
                val stroke = 1.8.dp.toPx()
                val w = size.width * 0.5f
                val h = size.height * 0.58f
                drawRoundRect(
                    color = tint,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.06f, size.height * 0.06f),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx()),
                    style = Stroke(width = stroke)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.36f),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx()),
                    style = Stroke(width = stroke)
                )
            }
    )
}

/** A downward arrow into a tray — the "import" affordance. */
@Composable
fun PocketImportGlyph(tint: Color = PocketColors.InkMuted, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(19.dp)
            .drawBehind {
                val stroke = 1.9.dp.toPx()
                val shaftBottom = size.height * 0.6f
                drawLine(
                    color = tint,
                    start = center.copy(y = size.height * 0.12f),
                    end = center.copy(y = shaftBottom),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.3f, shaftBottom - 5.4.dp.toPx())
                        lineTo(size.width * 0.5f, shaftBottom)
                        lineTo(size.width * 0.7f, shaftBottom - 5.4.dp.toPx())
                    },
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.22f, size.height * 0.76f)
                        lineTo(size.width * 0.22f, size.height * 0.92f)
                        lineTo(size.width * 0.78f, size.height * 0.92f)
                        lineTo(size.width * 0.78f, size.height * 0.76f)
                    },
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
    )
}

/** A key cap with a left arrow — the "delete last digit" affordance. */
@Composable
fun PocketBackspaceGlyph(tint: Color = PocketColors.InkMuted, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .drawBehind {
                val stroke = 1.8.dp.toPx()
                val keyWidth = size.width * 0.62f
                val keyHeight = size.height * 0.68f
                val keyLeft = size.width * 0.34f
                val keyTop = (size.height - keyHeight) / 2f
                drawRoundRect(
                    color = tint,
                    topLeft = androidx.compose.ui.geometry.Offset(keyLeft, keyTop),
                    size = androidx.compose.ui.geometry.Size(keyWidth, keyHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.5.dp.toPx()),
                    style = Stroke(width = stroke)
                )
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(keyLeft, keyTop)
                        lineTo(keyLeft - 8.dp.toPx(), keyTop + keyHeight / 2f)
                        lineTo(keyLeft, keyTop + keyHeight)
                    },
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                val arrowMidY = keyTop + keyHeight / 2f
                val arrowLeft = keyLeft + 7.dp.toPx()
                val arrowRight = keyLeft + keyWidth - 7.dp.toPx()
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(arrowRight, arrowMidY),
                    end = androidx.compose.ui.geometry.Offset(arrowLeft, arrowMidY),
                    strokeWidth = stroke * 0.85f,
                    cap = StrokeCap.Round
                )
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(arrowLeft + 4.6.dp.toPx(), arrowMidY - 4.6.dp.toPx())
                        lineTo(arrowLeft, arrowMidY)
                        lineTo(arrowLeft + 4.6.dp.toPx(), arrowMidY + 4.6.dp.toPx())
                    },
                    color = tint,
                    style = Stroke(width = stroke * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
    )
}

/** Concentric fingerprint arcs — the "biometric unlock" affordance. */
@Composable
fun PocketFingerprintGlyph(tint: Color = PocketColors.InkMuted, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .drawBehind {
                val stroke = 1.7.dp.toPx()
                val cx = size.width / 2f
                val baseY = size.height * 0.88f
                listOf(0.86f, 0.66f, 0.46f, 0.26f).forEachIndexed { index, factor ->
                    val radius = size.width * factor
                    drawArc(
                        color = tint.copy(alpha = if (index == 3) 0.55f else 1f),
                        startAngle = 208f,
                        sweepAngle = 124f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(cx - radius, baseY - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
    )
}

/**
 * Selection indicator for grouped choices. Replaces the stock Material radio so
 * it matches the rest of the tonal system instead of fighting it.
 */
@Composable
fun PocketCheckMark(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    accent: Color = PocketColors.Accent,
    onAccent: Color = PocketColors.OnAccent,
    idle: Color = PocketColors.InkFaint
) {
    val container by animateColorAsState(
        if (checked) accent else Color.Transparent,
        animationSpec = pocketTween(PocketMotion.micro),
        label = "markContainer"
    )
    val border by animateColorAsState(
        if (checked) accent else idle,
        animationSpec = pocketTween(PocketMotion.micro),
        label = "markBorder"
    )
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .border(1.5.dp, border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(size * 0.62f)
            )
        }
    }
}

/* ── Avatars, tags ────────────────────────────────────────────────────────── */

/** The Voket mark, tinted to sit on whatever surface it lands on. */
@Composable
fun PocketLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = PocketColors.Ink
) {
    Icon(
        painter = painterResource(R.drawable.ic_voket_logo),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/** Non-negative modulo so a negative hash code still picks a valid slot. */
private fun wrapIndex(value: Int, size: Int): Int {
    if (size <= 0) return 0
    val remainder = value % size
    return if (remainder < 0) remainder + size else remainder
}

/** Organic avatar seeded from the link's domain, so a row reads at a glance. */
@Composable
fun PocketAvatar(
    letter: String,
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = PocketShape.blob
) {
    val accents = listOf(
        PocketColors.Accent,
        PocketColors.Sage,
        PocketColors.Amber,
        PocketColors.Danger
    )
    val index = wrapIndex(seed.hashCode(), accents.size)
    val accent = accents[index]
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter.take(1).uppercase(),
            style = PocketType.glyph.copy(fontSize = (size.value * 0.42f).sp),
            color = accent
        )
    }
}

@Composable
fun PocketTagPill(tag: String, modifier: Modifier = Modifier) {
    val accents = listOf(
        PocketColors.Accent,
        PocketColors.Sage,
        PocketColors.Amber,
        PocketColors.Danger
    )
    val accent = accents[wrapIndex(tag.hashCode(), accents.size)]
    Surface(
        modifier = modifier,
        shape = PocketShape.pill,
        color = accent.copy(alpha = 0.13f),
        contentColor = accent
    ) {
        Text(
            tag,
            style = PocketType.caption.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = PocketSpace.sm, vertical = 3.dp)
        )
    }
}

/* ── Loading ──────────────────────────────────────────────────────────────── */

@Composable
fun SkeletonBlock(
    width: Dp,
    height: Dp,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(Modifier.size(width, height).clip(shape).background(PocketColors.SurfaceHigh))
}

@Composable
fun PocketSkeletonRow(alpha: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(PocketShape.lg)
            .background(PocketColors.SurfaceLow)
            .padding(PocketSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBlock(46.dp, 46.dp, PocketShape.blob)
        Spacer(Modifier.width(PocketSpace.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)) {
            SkeletonBlock(200.dp, 14.dp)
            SkeletonBlock(110.dp, 11.dp)
        }
        SkeletonBlock(24.dp, 24.dp, CircleShape)
    }
}

/* ── Misc ─────────────────────────────────────────────────────────────────── */

/** Small chevron used as the "drill in" affordance. */
@Composable
fun PocketChevron(modifier: Modifier = Modifier, tint: Color = PocketColors.InkFaint) {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(18.dp)
    )
}

/** Pad for screens whose last element must clear the floating navigation. */
val PocketListBottomPadding = PaddingValues(bottom = 128.dp)

/** Lock glyph used by the vault and keypad surfaces. */
@Composable
fun PocketLockGlyph(modifier: Modifier = Modifier, tint: Color = PocketColors.Accent) {
    Icon(Icons.Filled.Lock, contentDescription = null, tint = tint, modifier = modifier.size(20.dp))
}

/** Warning glyph used by destructive dialogs. */
@Composable
fun PocketWarningGlyph(modifier: Modifier = Modifier, tint: Color = PocketColors.Danger) {
    Icon(Icons.Filled.Warning, contentDescription = null, tint = tint, modifier = modifier.size(20.dp))
}

/** Caret used by dropdown-style pills. */
@Composable
fun PocketCaret(modifier: Modifier = Modifier, tint: Color = PocketColors.InkMuted) {
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(18.dp)
    )
}
