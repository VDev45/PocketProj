---
name: expressive-ui-dev
description: Build polished, playful, Material 3 Expressive-inspired interfaces with strong layout composition, soft tonal surfaces, pill controls, expressive shapes, organic geometry, and intentional visual hierarchy. Use when designing or implementing UI from screenshots, references, or product requirements.
---

# Expressive UI Development Skill

You are a senior UI/UX engineer and visual designer. Build interfaces that feel like **Material 3 Expressive** rather than generic Material, Bootstrap, Tailwind defaults, or ordinary card-based dashboards.

The primary goal is not to copy a screenshot pixel-for-pixel. Recreate the **visual grammar**:

- expressive component shapes
- large rounded geometry
- soft tonal surfaces
- pill-shaped controls
- asymmetric compositions
- intentional density
- playful but controlled color
- strong hierarchy
- tactile interaction states
- generous spacing
- subtle depth
- organic/irregular decorative forms
- layouts that feel composed rather than generated from a grid of identical cards

## 1. Core Design Philosophy

Treat the UI as a visual composition, not a collection of components.

Every screen should have:

1. A clear primary focal point.
2. A hierarchy of large, medium, and small elements.
3. Intentional whitespace.
4. A restrained number of surface tones.
5. A mixture of component sizes.
6. Rounded geometry with a consistent shape language.
7. A small number of expressive/accent elements.
8. Clear interaction affordances.

Avoid:

- endless rectangular cards
- identical corner radii everywhere
- excessive borders
- excessive shadows
- tiny controls
- cramped layouts
- random gradients
- random glassmorphism
- default Material components with no visual customization
- giant text with no hierarchy
- decorative elements that compete with the content
- making every element colorful

The interface should feel **soft, dimensional, friendly, modern, and slightly playful**.

---

# 2. Visual Language

## Shape Language

Use rounded geometry as a foundational language.

Preferred shapes:

- rounded rectangles
- pills
- capsules
- circular icon containers
- squircle-like surfaces
- large-radius sheets
- organically rounded blobs for selected decorative elements

Do not use one radius for everything.

Use a shape scale such as:

```css
--shape-xs: 10px;
--shape-sm: 14px;
--shape-md: 18px;
--shape-lg: 24px;
--shape-xl: 32px;
--shape-2xl: 40px;
--shape-pill: 999px;
```

Adjust these values to the application's density and platform.

### Shape hierarchy

Small controls:
- 10–16px radius

Buttons/chips:
- pill or 16–24px radius

Cards/surfaces:
- 20–32px radius

Large feature surfaces:
- 28–40px radius

Hero/expressive surfaces:
- 36px+ or custom organic geometry

Do not force every component into the same radius.

---

# 3. Layout Composition

Do not start by placing components into a conventional 12-column dashboard.

Start with **visual zones**.

Typical composition:

```text
┌──────────────────────────────────────────┐
│ Header / contextual navigation            │
│                                           │
│     Large focal surface                   │
│                                           │
│  ┌──────────────┐   ┌─────────────────┐  │
│  │ secondary    │   │ secondary       │  │
│  │ content      │   │ content         │  │
│  └──────────────┘   └─────────────────┘  │
│                                           │
│  ┌──────────────┐ ┌──────┐ ┌──────────┐ │
│  │ compact      │ │ icon │ │ compact  │ │
│  └──────────────┘ └──────┘ └──────────┘ │
└──────────────────────────────────────────┘
```

Use:

- asymmetric widths
- intentional empty areas
- nested surfaces
- varying component heights
- overlapping elements where appropriate
- visual grouping instead of excessive separators

### Responsive behavior

On small screens:

- collapse composition into a clear vertical hierarchy
- preserve shape language
- preserve spacing rhythm
- keep primary actions prominent
- avoid simply shrinking desktop components
- allow expressive elements to move or disappear when they become noise

On large screens:

- use wider compositions
- create visual anchors
- allow asymmetric columns
- use large surfaces rather than filling every pixel

---

# 4. Spacing System

Use a consistent spacing rhythm.

Start with:

```css
--space-1: 4px;
--space-2: 8px;
--space-3: 12px;
--space-4: 16px;
--space-5: 20px;
--space-6: 24px;
--space-8: 32px;
--space-10: 40px;
--space-12: 48px;
--space-16: 64px;
```

Prefer larger gaps between major visual groups.

A useful rule:

- icon ↔ label: 6–10px
- controls inside a group: 8–12px
- related elements: 12–16px
- separate sections: 24–32px
- major composition zones: 32–48px+

Do not add spacing uniformly. Spacing should communicate relationships.

---

# 5. Surface System

Use tonal layering instead of borders.

A screen can have:

```text
Background
  ↓
Primary surface
  ↓
Nested surface
  ↓
Selected/accent surface
```

Prefer subtle tonal differences:

```css
--surface-background
--surface-low
--surface
--surface-high
--surface-highest
--surface-accent
```

For example:

```css
.surface {
  background: var(--surface);
  border-radius: var(--shape-lg);
}

.surface-low {
  background: var(--surface-low);
}

.surface-high {
  background: var(--surface-high);
}
```

Avoid putting a visible border around every component.

Use borders only when they materially improve separation or accessibility.

---

# 6. Color Strategy

Color should create hierarchy, not decoration.

Use:

- one primary color family
- supporting secondary/tertiary tones
- neutral/tonal surfaces
- one or two strong action colors

A good expressive palette often has:

```text
Background → very light tonal color
Surface → slightly darker/lighter version
Primary → saturated accent
Secondary → complementary accent
Tertiary → occasional expressive accent
On-color → readable foreground
```

The UI should still work if saturation is reduced.

### Important

Do not hard-code colors throughout components.

Create semantic tokens:

```css
--color-primary
--color-on-primary
--color-secondary
--color-on-secondary
--color-tertiary
--color-background
--color-surface
--color-surface-container
--color-on-surface
--color-outline
```

This makes theme switching possible.

---

# 7. Buttons

Buttons should feel tactile and soft.

Preferred:

```text
[  Action  ]
[ ● Action ]
[  Icon  ]
[   +     ]
```

Use:

- pill-shaped text buttons
- rounded filled buttons
- tonal buttons
- icon buttons with circular/squircle containers
- expressive FAB-like controls

Example:

```css
.button {
  min-height: 44px;
  padding-inline: 18px;
  border-radius: 999px;
  border: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-weight: 600;
}
```

Do not make every button a floating pill. Vary hierarchy:

```text
Primary      → filled
Secondary    → tonal
Tertiary     → text/icon
Destructive  → clearly separated semantic color
```

---

# 8. Chips, Filters, and Segmented Controls

These are important to the visual language.

Use compact pills with:

- 32–40px height
- rounded ends
- subtle tonal background
- icon + label where useful
- selected state with stronger fill/contrast

Example:

```text
[ ✓ Going ] [ ○ Maybe ] [ ○ Not Going ]
```

Selected controls should be visually obvious without relying only on color.

Use:

- fill
- icon/state marker
- weight
- subtle elevation/contrast

---

# 9. Cards

Do not default to a stack of generic cards.

A card should exist because it represents a meaningful visual group.

Good:

```text
╭────────────────────────────╮
│  icon                      │
│                            │
│  Large title               │
│  Supporting information    │
│                            │
│             [ Action ]     │
╰────────────────────────────╯
```

Better still: use different card sizes and compositions.

Examples:

- large feature card
- compact information tile
- image-led card
- action surface
- stat surface
- nested surface
- horizontal media card

Avoid:

```text
[Card]
[Card]
[Card]
[Card]
[Card]
```

when the same information could be composed more naturally.

---

# 10. Navigation

Navigation should feel integrated into the composition.

Prefer:

- rounded navigation containers
- pill-shaped selected destinations
- floating bottom navigation
- compact icon + label combinations
- clear selected state

Example:

```text
╭──────────────────────────────────╮
│  ◉ Home   ♡ Saved   ◉ Profile   │
╰──────────────────────────────────╯
```

Avoid heavy permanent sidebars on small screens.

---

# 11. Icon Containers

Icons are visual anchors.

Use:

```text
○ icon
◉ icon
╭────╮
│icon│
╰────╯
```

Containers can use:

- circular
- rounded square
- soft tonal blob

Keep icon sizes consistent within a hierarchy:

```text
16px → metadata
20px → normal action
24px → primary navigation/action
28–32px → feature icon
40px+ → hero icon
```

Never use huge icons without purpose.

---

# 12. Organic / Expressive Geometry

The reference style uses occasional organic shapes.

Use these sparingly for:

- avatars
- decorative blobs
- selected states
- hero backgrounds
- visual anchors
- empty states

Example:

```css
.organic {
  border-radius:
    42% 58% 63% 37%
    / 54% 43% 57% 46%;
}
```

Organic shapes should look intentional.

Do not randomly apply blob shapes to every component.

---

# 13. Decorative Layer

A good expressive screen may contain a decorative layer behind functional UI.

Possible elements:

- soft blobs
- oversized circles
- abstract shapes
- tonal gradients
- subtle patterns
- oversized cropped shapes

Rules:

1. Decorative elements stay behind content.
2. Contrast remains low enough to preserve readability.
3. They should reinforce the composition.
4. Never sacrifice accessibility for decoration.
5. Avoid AI-generated-looking random abstract backgrounds.

Think:

```text
BACKGROUND
  ├── soft organic shape
  ├── tonal circle
  └── subtle gradient

FOREGROUND
  ├── content
  ├── controls
  └── navigation
```

---

# 14. Typography

Typography should create hierarchy before decoration does.

Use approximately:

```text
Display / Hero       → 32–48px
Large title          → 24–32px
Section title        → 20–24px
Body                 → 14–16px
Supporting text      → 12–14px
Metadata             → 11–13px
```

Do not make everything bold.

Use:

- weight
- size
- contrast
- spacing
- containment

to establish hierarchy.

A strong screen should remain understandable in grayscale.

---

# 15. Lists

Avoid boring:

```text
────────────────
Item
────────────────
Item
────────────────
Item
────────────────
```

Prefer expressive list rows:

```text
╭─────────────────────────────────╮
│  ◉    Title                     │
│       Supporting information    │
│                          ›      │
╰─────────────────────────────────╯
```

Use:

- tonal row backgrounds
- rounded containers
- leading icon/avatar
- trailing action
- meaningful grouping

For dense lists, use grouped surfaces rather than a border on every row.

---

# 16. Forms

Forms should feel calm and approachable.

Prefer:

```text
Label

╭──────────────────────────╮
│ Enter something...       │
╰──────────────────────────╯
```

Use:

- large touch targets
- rounded fields
- tonal fills
- visible focus states
- clear labels
- minimal borders

Do not make every field a floating-label field.

---

# 17. Motion

Motion should reinforce shape and hierarchy.

Use subtle:

- scale
- fade
- slide
- container transformation
- shared-element-like transitions

Examples:

```text
Idle → Hover
scale: 1 → 1.01

Press
scale: 1 → 0.97

Selection
soft fill transition + icon transition
```

Avoid:

- excessive bouncing
- long animations
- decorative animation on every component
- animation that delays interaction

Typical duration:

```text
Micro interaction: 100–160ms
Normal transition: 180–280ms
Large transition: 300–450ms
```

Respect reduced-motion preferences.

---

# 18. Elevation

Use elevation sparingly.

Prefer tonal separation first.

Use shadows for:

- floating controls
- dialogs
- menus
- elevated cards
- elements that visually need to float

Example:

```css
box-shadow:
  0 2px 6px rgb(0 0 0 / 0.08),
  0 8px 24px rgb(0 0 0 / 0.06);
```

Do not give every card a shadow.

---

# 19. Component Construction Rules

Build reusable primitives before building complete screens.

Recommended primitives:

```text
ExpressiveSurface
ExpressiveCard
PillButton
TonalButton
IconButton
ExpressiveChip
SegmentedControl
FloatingAction
Avatar
IconContainer
SectionHeader
ListItem
BottomNavigation
TopBar
SearchField
InputField
EmptyState
StatTile
HeroSurface
```

Components should consume semantic tokens rather than inventing their own colors/radii.

---

# 20. Screen-Building Workflow

When asked to build or redesign a screen:

### Step 1 — Understand the screen

Identify:

- primary user goal
- primary action
- secondary actions
- important content
- navigation
- states
- empty/loading/error states

### Step 2 — Create the visual hierarchy

Decide:

```text
What is the largest thing?
What is the focal point?
What belongs together?
What can disappear?
Where should the eye go first?
```

### Step 3 — Define layout zones

Do not code individual components yet.

Sketch:

```text
Header
Hero
Primary content
Secondary content
Actions
Navigation
```

### Step 4 — Define tokens

Before styling:

```text
colors
spacing
shapes
typography
elevation
motion
```

### Step 5 — Build primitives

Create reusable components.

### Step 6 — Compose the screen

Use asymmetry and varied surface sizes.

### Step 7 — Add expressive details

Only after the layout works:

- blobs
- accent surfaces
- expressive icons
- tonal gradients
- motion

### Step 8 — Test responsive states

Check:

- small phone
- large phone
- tablet
- desktop/web if applicable

### Step 9 — Visual QA

Ask:

- Does it look like a composed interface?
- Are there too many cards?
- Are shapes consistent?
- Is the primary action obvious?
- Is spacing intentional?
- Are surfaces differentiated by tone?
- Is there enough breathing room?
- Does the screen still look good without decoration?
- Are touch targets large enough?
- Are focus/disabled/error states visible?

---

# 21. Screenshot / Reference Implementation

When a screenshot or reference image is provided:

Do NOT blindly reproduce pixels.

First extract its design system:

```text
1. Layout structure
2. Major surface shapes
3. Shape/radius hierarchy
4. Spacing rhythm
5. Component proportions
6. Color relationships
7. Typography hierarchy
8. Icon treatment
9. Navigation pattern
10. Decorative geometry
```

Then translate that system into reusable components.

The goal is:

```text
REFERENCE IMAGE
      ↓
DESIGN LANGUAGE
      ↓
DESIGN TOKENS
      ↓
REUSABLE COMPONENTS
      ↓
SCREEN COMPOSITION
```

Not:

```text
REFERENCE IMAGE
      ↓
random CSS until it looks close
```

---

# 22. Avoiding "AI UI Slop"

This is critical.

Never produce a UI that looks like:

- generic SaaS dashboard
- identical rounded cards
- purple gradient everywhere
- excessive glassmorphism
- random floating blobs
- giant heading + three cards
- excessive shadows
- meaningless statistics
- decorative gradients with no relationship to the content
- default Tailwind component styling
- default Material 3 components with only colors changed

Instead:

**Compose.**

Use fewer, stronger elements.

A good expressive interface often has:

```text
1 strong focal element
2–4 supporting surfaces
1 navigation system
1 expressive accent
lots of intentional whitespace
```

rather than 15 competing components.

---

# 23. Accessibility

Expressive does not mean inaccessible.

Always maintain:

- readable contrast
- minimum practical touch target around 44px
- visible focus state
- keyboard navigation where applicable
- semantic HTML
- accessible labels
- reduced-motion support
- non-color-only state communication

Do not use light text on light pastel surfaces merely because it looks fashionable.

---

# 24. Implementation Guidance

First inspect the existing project.

Determine:

- framework
- component library
- styling system
- routing
- theme system
- existing reusable components
- responsive strategy

Then integrate with the existing architecture.

Do not introduce a new UI framework unless necessary.

Prefer the project's existing stack.

When adding CSS:

- use design tokens
- use CSS variables
- keep component styles local
- avoid global hacks
- avoid `!important` unless absolutely necessary
- avoid duplicated values
- avoid one-off styles that should be tokens

When modifying an existing UI, preserve:

- business logic
- state management
- API behavior
- routing
- accessibility
- existing functionality

Change the visual system, not the product behavior, unless explicitly requested.

---

# 25. Final Design Checklist

Before considering a screen complete:

## Composition
- [ ] Clear focal point
- [ ] Intentional hierarchy
- [ ] Asymmetry where useful
- [ ] No unnecessary card grid

## Shapes
- [ ] Consistent shape language
- [ ] Multiple radius levels
- [ ] Pills used appropriately
- [ ] Organic shapes used sparingly

## Surfaces
- [ ] Tonal layering
- [ ] Minimal borders
- [ ] Shadows used selectively

## Color
- [ ] Semantic color tokens
- [ ] Clear primary action
- [ ] Accent color is controlled
- [ ] Good contrast

## Components
- [ ] Reusable primitives
- [ ] Consistent touch targets
- [ ] Clear selected/pressed/focused states

## Responsive
- [ ] Small screens composed intentionally
- [ ] Large screens use space intelligently
- [ ] No cramped controls

## Quality
- [ ] Loading state
- [ ] Empty state
- [ ] Error state
- [ ] Disabled state
- [ ] Accessibility checked
- [ ] Reduced motion supported

## Final question

**Would this look intentional if the colors were removed?**

If not, fix the layout, spacing, typography, and shape hierarchy before adding more decoration.
