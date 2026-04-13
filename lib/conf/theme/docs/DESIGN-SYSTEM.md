# Sphereon Design System Specification

> Authoritative reference for the Sphereon 3-tier design token architecture.
> Synthesizes Material Design 3 (M3), Figma definitions, and the IDK/EDK/VDX
> token pipeline into one coherent, cross-platform system.

---

## Table of Contents

1. [Philosophy — 3-Tier Token Architecture](#1-philosophy)
2. [Color System](#2-color-system)
3. [Typography](#3-typography)
4. [Spacing](#4-spacing)
5. [Shape / Border Radius](#5-shape--border-radius)
6. [Border Width](#6-border-width)
7. [Shadows](#7-shadows)
8. [Motion](#8-motion)
9. [Component Token Namespace](#9-component-token-namespace)
10. [IDK / EDK / VDX Split](#10-idk--edk--vdx-split)
11. [Legacy Compatibility](#11-legacy-compatibility)
12. [Figma-to-Token Mapping](#12-figma-to-token-mapping)

---

## 1. Philosophy

### 3-Tier Token Architecture

The design system is organized into three tiers that progressively increase in
specificity and decrease in reusability:

```
Tier 1 — Primitive Tokens          (IDK, open-source)
    Raw values with no semantic meaning.
    Examples: palette.primary.40, fontFamily.roboto, spacing.4

Tier 2 — Semantic Tokens           (IDK, open-source)
    Purpose-driven aliases that reference primitives.
    Examples: color.primary, typography.bodyLarge.fontSize, shadow.raised

Tier 3 — Component Tokens          (EDK/VDX, commercial)
    Per-component overrides that reference semantic tokens.
    Examples: comp.button.primary.background, comp.input.border
```

**Why three tiers?**

- Tier 1 is the stable palette of raw values generated algorithmically (HCT for
  color, a 4px grid for spacing). It rarely changes.
- Tier 2 maps those values to design intent. A theme switch (light to dark,
  brand A to brand B) only rewires Tier 2 references; Tier 1 stays the same.
- Tier 3 lets product teams fine-tune individual components without polluting the
  global semantic layer. It is the primary extension point for EDK presets and
  customer white-labelling.

### Design Principles

| Principle | Rationale |
|-----------|-----------|
| M3 as foundation | Industry-standard, well-documented, broad tooling support. Custom extensions layer on top rather than replacing M3. |
| Cross-platform parity | Every token must resolve on web (CSS custom properties), Android (Compose), and iOS (SwiftUI). Platform SDKs translate units where needed. |
| Single source of truth | Token definitions live in code (IDK `TokenKeyConstants.kt` + theme JSON). Figma variables are generated from this source, not the other way around. |
| Convention over configuration | Sensible defaults ship with IDK. EDK presets layer brand personality. VDX deployments can override any token via tenant configuration. |

---

## 2. Color System

### 2.1 M3 HCT Palette Generation

Colors are generated using the M3 **Hue-Chroma-Tone (HCT)** color space.
Given a single seed color (typically the brand primary), the system produces a
full tonal palette across five key colors:

- **Primary** — Brand identity
- **Secondary** — Supporting accents
- **Tertiary** — Complementary accents
- **Error** — Destructive/error states
- **Neutral** — Surfaces, backgrounds, text

Each key color fans out into a tonal palette with the following stops:

| Stop | Tone | Typical Use |
|------|------|-------------|
| 0 | 0 | Pure black reference |
| 10 | 10 | Darkest usable shade |
| 20 | 20 | Dark theme container fills |
| 30 | 30 | Dark theme on-container text |
| 40 | 40 | Light theme primary / accent |
| 50 | 50 | Mid-tone reference |
| 60 | 60 | Dark theme primary / accent |
| 70 | 70 | Lighter accent |
| 80 | 80 | Dark theme container fills |
| 90 | 90 | Light theme container fills |
| 95 | 95 | Light theme surface tints |
| 99 | 99 | Near-white surface |
| 100 | 100 | Pure white reference |

**Extended stops** (50-step granularity for utility palettes):

| Stop | Tone |
|------|------|
| 50 | 50 |
| 150 | 15 |
| 250 | 25 |
| 350 | 35 |
| 450 | 45 |
| 550 | 55 |
| 650 | 65 |
| 750 | 75 |
| 850 | 85 |
| 950 | 95 |

Token key pattern: `palette.{keyColor}.{stop}` (Tier 1)

### 2.2 Semantic Color Roles (Tier 2)

#### M3 Core Roles

| Token Key | Light Source | Dark Source | Purpose |
|-----------|-------------|------------|---------|
| `color.primary` | palette.primary.40 | palette.primary.80 | Primary brand actions |
| `color.onPrimary` | palette.primary.100 | palette.primary.20 | Content on primary |
| `color.primaryContainer` | palette.primary.90 | palette.primary.30 | Primary container fills |
| `color.onPrimaryContainer` | palette.primary.10 | palette.primary.90 | Content on primary container |
| `color.secondary` | palette.secondary.40 | palette.secondary.80 | Secondary actions |
| `color.onSecondary` | palette.secondary.100 | palette.secondary.20 | Content on secondary |
| `color.secondaryContainer` | palette.secondary.90 | palette.secondary.30 | Secondary container fills |
| `color.onSecondaryContainer` | palette.secondary.10 | palette.secondary.90 | Content on secondary container |
| `color.tertiary` | palette.tertiary.40 | palette.tertiary.80 | Tertiary accents |
| `color.onTertiary` | palette.tertiary.100 | palette.tertiary.20 | Content on tertiary |
| `color.tertiaryContainer` | palette.tertiary.90 | palette.tertiary.30 | Tertiary container fills |
| `color.onTertiaryContainer` | palette.tertiary.10 | palette.tertiary.90 | Content on tertiary container |
| `color.error` | palette.error.40 | palette.error.80 | Error states |
| `color.onError` | palette.error.100 | palette.error.20 | Content on error |
| `color.errorContainer` | palette.error.90 | palette.error.30 | Error container fills |
| `color.onErrorContainer` | palette.error.10 | palette.error.90 | Content on error container |
| `color.surface` | palette.neutral.98 | palette.neutral.6 | Default background |
| `color.onSurface` | palette.neutral.10 | palette.neutral.90 | Default foreground text |
| `color.surfaceVariant` | palette.neutralVariant.90 | palette.neutralVariant.30 | Alternative surface |
| `color.onSurfaceVariant` | palette.neutralVariant.30 | palette.neutralVariant.80 | Content on surface variant |
| `color.surfaceContainer` | palette.neutral.94 | palette.neutral.12 | Card / sheet fill |
| `color.surfaceContainerLow` | palette.neutral.96 | palette.neutral.10 | Recessed container |
| `color.surfaceContainerHigh` | palette.neutral.92 | palette.neutral.17 | Elevated container |
| `color.surfaceContainerHighest` | palette.neutral.90 | palette.neutral.22 | Most elevated container |
| `color.surfaceContainerLowest` | palette.neutral.100 | palette.neutral.4 | Deepest recessed container |
| `color.outline` | palette.neutralVariant.50 | palette.neutralVariant.60 | Borders, dividers |
| `color.outlineVariant` | palette.neutralVariant.80 | palette.neutralVariant.30 | Subtle borders |

#### Extended Interactive Roles

These extend M3 to cover common UI interaction states not addressed by the
core specification:

| Token Key | Description | Light Default | Dark Default |
|-----------|-------------|---------------|--------------|
| `color.interactive.hover` | Hover overlay on interactive elements | `{color.primary}` at 8% opacity | `{color.primary}` at 12% opacity |
| `color.interactive.pressed` | Press/active overlay | `{color.primary}` at 12% opacity | `{color.primary}` at 16% opacity |
| `color.interactive.disabled` | Disabled element fill | `{color.onSurface}` at 12% opacity | `{color.onSurface}` at 12% opacity |
| `color.interactive.focus` | Focus ring color | `{color.primary}` | `{color.primary}` |

#### Extended Feedback Roles

Utility colors for status communication. These are independent palettes
generated from fixed seed hues:

| Token Key | Description | Light Default | Dark Default |
|-----------|-------------|---------------|--------------|
| `color.feedback.success` | Positive outcome | palette.success.40 | palette.success.80 |
| `color.feedback.successContainer` | Success container fill | palette.success.90 | palette.success.30 |
| `color.feedback.onSuccess` | Content on success | palette.success.100 | palette.success.20 |
| `color.feedback.onSuccessContainer` | Content on success container | palette.success.10 | palette.success.90 |
| `color.feedback.warning` | Caution / attention | palette.warning.40 | palette.warning.80 |
| `color.feedback.warningContainer` | Warning container fill | palette.warning.90 | palette.warning.30 |
| `color.feedback.onWarning` | Content on warning | palette.warning.100 | palette.warning.20 |
| `color.feedback.onWarningContainer` | Content on warning container | palette.warning.10 | palette.warning.90 |
| `color.feedback.info` | Informational / neutral status | palette.info.40 | palette.info.80 |
| `color.feedback.infoContainer` | Info container fill | palette.info.90 | palette.info.30 |
| `color.feedback.onInfo` | Content on info | palette.info.100 | palette.info.20 |
| `color.feedback.onInfoContainer` | Content on info container | palette.info.10 | palette.info.90 |

Utility palette seed hues (HCT):

| Palette | Hue | Chroma |
|---------|-----|--------|
| Success | 145 | 50 |
| Warning | 85 | 70 |
| Info | 260 | 40 |

#### Extended Text Roles

| Token Key | Description | Light Default | Dark Default |
|-----------|-------------|---------------|--------------|
| `color.text.primary` | High-emphasis text | `{color.onSurface}` | `{color.onSurface}` |
| `color.text.secondary` | Medium-emphasis text | `{color.onSurfaceVariant}` | `{color.onSurfaceVariant}` |
| `color.text.disabled` | Disabled text | `{color.onSurface}` at 38% opacity | `{color.onSurface}` at 38% opacity |
| `color.text.inverse` | Text on inverse surfaces | `{color.surface}` | `{color.surface}` |

#### Extended Border Roles

| Token Key | Description | Light Default | Dark Default |
|-----------|-------------|---------------|--------------|
| `color.border.default` | Standard border | `{color.outline}` | `{color.outline}` |
| `color.border.strong` | Emphasized border | `{color.onSurface}` | `{color.onSurface}` |
| `color.border.subtle` | De-emphasized border | `{color.outlineVariant}` | `{color.outlineVariant}` |
| `color.border.disabled` | Disabled border | `{color.onSurface}` at 12% opacity | `{color.onSurface}` at 12% opacity |

---

## 3. Typography

### 3.1 Font Primitives (Tier 1)

| Token Key | IDK Default | EDK Preset (Poppins) | Notes |
|-----------|-------------|----------------------|-------|
| `fontFamily.primary` | Roboto | Poppins | Body and UI text |
| `fontFamily.display` | Roboto | Poppins | Display and headline text |
| `fontFamily.monospace` | Roboto Mono | JetBrains Mono | Code and technical content |

**Rationale — Roboto as IDK default:** M3 specifies Roboto as the neutral
reference typeface. IDK ships Roboto so that open-source consumers get a
complete, license-free experience out of the box. EDK presets override this
with brand typefaces (Poppins for Sphereon).

### 3.2 Type Scale

Each style defines five properties:

- `fontFamily` — Reference to a Tier 1 font primitive
- `fontSize` — In sp (Android) / rem (web) / pt (iOS)
- `fontWeight` — Single canonical weight (see 3.3)
- `lineHeight` — Multiplier or absolute value
- `letterSpacing` — In em (web) / sp (Android)

#### Full Type Scale

| Style | Token Prefix | Size (sp) | Weight | Line Height | Letter Spacing | Origin |
|-------|-------------|-----------|--------|-------------|---------------|--------|
| Display Extra Large | `typography.displayExtraLarge` | 72 | 400 | 80 | -0.25 | Extended |
| Display Large | `typography.displayLarge` | 57 | 400 | 64 | -0.25 | M3 |
| Display Medium | `typography.displayMedium` | 45 | 400 | 52 | 0 | M3 |
| Display Small | `typography.displaySmall` | 36 | 400 | 44 | 0 | M3 |
| Headline Large | `typography.headlineLarge` | 32 | 400 | 40 | 0 | M3 |
| Headline Medium | `typography.headlineMedium` | 28 | 400 | 36 | 0 | M3 |
| Headline Small | `typography.headlineSmall` | 24 | 400 | 32 | 0 | M3 |
| Title Large | `typography.titleLarge` | 22 | 400 | 28 | 0 | M3 |
| Title Medium | `typography.titleMedium` | 16 | 500 | 24 | 0.15 | M3 |
| Title Small | `typography.titleSmall` | 14 | 500 | 20 | 0.1 | M3 |
| Label Large | `typography.labelLarge` | 14 | 500 | 20 | 0.1 | M3 |
| Label Medium | `typography.labelMedium` | 12 | 500 | 16 | 0.5 | M3 |
| Label Small | `typography.labelSmall` | 11 | 500 | 16 | 0.5 | M3 |
| Body Large | `typography.bodyLarge` | 16 | 400 | 24 | 0.5 | M3 |
| Body Medium | `typography.bodyMedium` | 14 | 400 | 20 | 0.25 | M3 |
| Body Small | `typography.bodySmall` | 12 | 400 | 16 | 0.4 | M3 |
| Body Extra Small | `typography.bodyExtraSmall` | 10 | 400 | 14 | 0.4 | Extended |

Each style produces token keys following the pattern:
`typography.{style}.fontSize`, `typography.{style}.fontWeight`,
`typography.{style}.lineHeight`, `typography.{style}.letterSpacing`,
`typography.{style}.fontFamily`

### 3.3 Canonical Weight-Per-Style Rationale

Figma exports often define 4 weights per style (Regular, Medium, SemiBold,
Bold), producing a combinatorial explosion of tokens. This is unnecessary and
harmful:

- It creates ambiguity about which weight to use for a given style.
- It inflates the token set without adding design value.
- It complicates cross-platform parity (native platforms bind one weight per
  text style).

**Decision:** Each typography style carries exactly **one** canonical weight.
Display and Body styles use 400 (Regular). Title and Label styles use 500
(Medium). If a product needs bold emphasis within body text, it applies an
inline `fontWeight` override at the component level, not a separate token.

### 3.4 Headline Retention

M3's Figma kit dropped the Headline category in some versions, leaving a gap
between Display (marketing hero) and Title (section headings). The IDK retains
all three Headline sizes (Large/Medium/Small) because:

- They fill the 24-32sp range that Title does not cover.
- Enterprise dashboards and multi-level navigation require more heading
  granularity than consumer apps.
- Removing them would be a breaking change for existing consumers.

---

## 4. Spacing

### 4.1 Grid Foundation

All spacing derives from a **4px base grid**. Every spacing token is a multiple
of 4px except `spacing.0` (0px) and `spacing.3` (12px, which is 3x4).

### 4.2 Primitive Scale (17 Stops)

The scale is curated to 17 stops. M3 and Figma define 30+ stops; most are
never used in practice and create decision fatigue.

| Token Key | Value | Multiplier | Common Use |
|-----------|-------|------------|------------|
| `spacing.0` | 0px | 0x | Reset, collapse |
| `spacing.1` | 4px | 1x | Tight internal padding |
| `spacing.2` | 8px | 2x | Icon-to-label gap |
| `spacing.3` | 12px | 3x | Compact card padding |
| `spacing.4` | 16px | 4x | Standard content padding |
| `spacing.5` | 20px | 5x | Medium gap |
| `spacing.6` | 24px | 6x | Section spacing |
| `spacing.8` | 32px | 8x | Component group spacing |
| `spacing.10` | 40px | 10x | Large section breaks |
| `spacing.12` | 48px | 12x | Header/footer padding |
| `spacing.14` | 56px | 14x | Toolbar height |
| `spacing.16` | 64px | 16x | Hero section spacing |
| `spacing.20` | 80px | 20x | Page margin (tablet) |
| `spacing.24` | 96px | 24x | Page margin (desktop) |
| `spacing.32` | 128px | 32x | Large layout gaps |
| `spacing.40` | 160px | 40x | Section divider spacing |
| `spacing.48` | 192px | 48x | Maximum layout spacing |

**Rationale for 17 stops:** Analysis of Sphereon's component library showed that
only 17 distinct spacing values were used across all production components. The
remaining values from the full 4px scale (spacing.7, spacing.9, spacing.11, etc.)
had zero usage. Removing them simplifies decision-making without reducing
expressiveness.

### 4.3 Semantic Spacing (Tier 2)

Semantic spacing tokens encode *intent* rather than *magnitude*:

#### Inline (horizontal between siblings)

| Token Key | Value | Use |
|-----------|-------|-----|
| `spacing.inline.xs` | 4px | Icon-label gap in compact controls |
| `spacing.inline.sm` | 8px | Button icon-label gap |
| `spacing.inline.md` | 16px | Standard sibling spacing |
| `spacing.inline.lg` | 24px | Card grid gap |
| `spacing.inline.xl` | 32px | Wide layout gap |

#### Stack (vertical between siblings)

| Token Key | Value | Use |
|-----------|-------|-----|
| `spacing.stack.xs` | 4px | Tight list item spacing |
| `spacing.stack.sm` | 8px | Form field spacing |
| `spacing.stack.md` | 16px | Section content spacing |
| `spacing.stack.lg` | 24px | Section break |
| `spacing.stack.xl` | 32px | Major section divider |

#### Inset (padding within containers)

| Token Key | Value | Use |
|-----------|-------|-----|
| `spacing.inset.xs` | 4px | Compact chip padding |
| `spacing.inset.sm` | 8px | Small button padding |
| `spacing.inset.md` | 16px | Card/dialog padding |
| `spacing.inset.lg` | 24px | Page content padding |
| `spacing.inset.xl` | 32px | Hero section padding |

---

## 5. Shape / Border Radius

### 5.1 Platform-Agnostic Scale

Shape tokens use `dp` as the canonical unit. Platform SDKs convert:
- Web: `dp` maps to `px` at 1:1 (CSS pixels are density-independent)
- Android Compose: `dp` is native
- iOS: `dp` maps to `pt` at 1:1

| Token Key | Value | M3 Equivalent | Figma Name | Use |
|-----------|-------|---------------|------------|-----|
| `shape.radius.none` | 0dp | — | None | Sharp corners (images, dividers) |
| `shape.radius.xs` | 2dp | — | Extra Small | Subtle rounding (badges) |
| `shape.radius.sm` | 4dp | Extra Small | Small | Chips, compact controls |
| `shape.radius.md` | 8dp | Small | Medium | Buttons, text fields |
| `shape.radius.lg` | 12dp | Medium | Large | Cards, dialogs |
| `shape.radius.xl` | 16dp | Large | Extra Large | Sheets, expanded panels |
| `shape.radius.xxl` | 24dp | — | 2XL | Modal containers |
| `shape.radius.xxxl` | 28dp | Extra Large | 3XL | Large modal/bottom sheet |
| `shape.radius.full` | 9999dp | Full | Full | Pills, avatars, FABs |

### 5.2 Mapping from M3 Shape Names

M3 defines shape using cornerSize names. The mapping is:

| M3 Name | New Key | Value |
|---------|---------|-------|
| Extra Small | `shape.radius.sm` | 4dp |
| Small | `shape.radius.md` | 8dp |
| Medium | `shape.radius.lg` | 12dp |
| Large | `shape.radius.xl` | 16dp |
| Extra Large | `shape.radius.xxxl` | 28dp |
| Full | `shape.radius.full` | 9999dp |

The `xs` (2dp), `xxl` (24dp) stops are extensions that fill gaps in the M3
scale needed for fine-grained UI control.

---

## 6. Border Width

A minimal 4-stop scale covering all production use cases:

| Token Key | Value | Use |
|-----------|-------|-----|
| `borderWidth.none` | 0px | No border |
| `borderWidth.thin` | 1px | Default border (inputs, cards, dividers) |
| `borderWidth.medium` | 2px | Emphasized border (active tabs, selected items) |
| `borderWidth.thick` | 4px | Heavy border (focus indicators, drag handles) |

**Rationale for 4 stops:** Border widths beyond 4px are exceedingly rare in UI
design. The 3px value is intentionally omitted to keep the scale minimal and to
avoid sub-pixel rendering issues on 1x displays.

---

## 7. Shadows

### 7.1 Design Decisions

**Light theme gets real shadows.** Figma's Sphereon light theme shipped with all
shadow values set to 0, which is incorrect. Light themes rely on shadows for
depth hierarchy because the background is already bright and color-based
elevation (tinting) is insufficient alone.

**Dark theme gets higher-opacity shadows.** On dark backgrounds, shadows must be
more opaque to remain visible. The dark theme uses 2-3x the opacity of the
light theme.

### 7.2 Elevation Shadow Scale (Tier 2)

Each elevation level defines a **composite** box-shadow (multiple layers for
realistic depth). Values are specified in CSS syntax; platform SDKs translate.

| Token Key | Light Theme Value | Dark Theme Value |
|-----------|-------------------|------------------|
| `shadow.elevation.none` | `none` | `none` |
| `shadow.elevation.xs` | `0 1px 2px 0 rgba(0,0,0,0.05)` | `0 1px 2px 0 rgba(0,0,0,0.3)` |
| `shadow.elevation.sm` | `0 1px 3px 0 rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)` | `0 1px 3px 0 rgba(0,0,0,0.4), 0 1px 2px -1px rgba(0,0,0,0.35)` |
| `shadow.elevation.md` | `0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -2px rgba(0,0,0,0.1)` | `0 4px 6px -1px rgba(0,0,0,0.4), 0 2px 4px -2px rgba(0,0,0,0.35)` |
| `shadow.elevation.lg` | `0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -4px rgba(0,0,0,0.1)` | `0 10px 15px -3px rgba(0,0,0,0.45), 0 4px 6px -4px rgba(0,0,0,0.4)` |
| `shadow.elevation.xl` | `0 20px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1)` | `0 20px 25px -5px rgba(0,0,0,0.5), 0 8px 10px -6px rgba(0,0,0,0.45)` |
| `shadow.elevation.xxl` | `0 25px 50px -12px rgba(0,0,0,0.25)` | `0 25px 50px -12px rgba(0,0,0,0.65)` |

### 7.3 Semantic Shadow Aliases

Semantic names map to elevation levels for intent-driven usage:

| Token Key | References | Typical Use |
|-----------|------------|-------------|
| `shadow.subtle` | `{shadow.elevation.xs}` | Hover hints, subtle depth |
| `shadow.raised` | `{shadow.elevation.sm}` | Cards, list items |
| `shadow.floating` | `{shadow.elevation.md}` | Dropdowns, popovers |
| `shadow.overlay` | `{shadow.elevation.lg}` | Modals, dialogs |
| `shadow.dramatic` | `{shadow.elevation.xxl}` | Full-screen overlays, spotlights |

### 7.4 State Shadows

State shadows use **brand color references** instead of neutral black, creating
adaptive focus rings and selection indicators that automatically match the
active theme's brand palette.

| Token Key | Light Theme Value | Dark Theme Value | Notes |
|-----------|-------------------|------------------|-------|
| `shadow.state.focus` | `0 0 0 3px rgba({color.primary}, 0.4)` | `0 0 0 3px rgba({color.primary}, 0.6)` | Focus ring; resolves `{color.primary}` at runtime to the theme's primary color RGB channels |
| `shadow.state.error` | `0 0 0 3px rgba({color.error}, 0.4)` | `0 0 0 3px rgba({color.error}, 0.6)` | Validation error ring |
| `shadow.state.active` | `0 0 0 1px rgba({color.primary}, 0.3), {shadow.elevation.xs}` | `0 0 0 1px rgba({color.primary}, 0.5), {shadow.elevation.xs}` | Active/pressed state |
| `shadow.state.selected` | `0 0 0 2px {color.primary}` | `0 0 0 2px {color.primary}` | Selected item indicator |

**Implementation note:** The `{color.primary}` and `{color.error}` references
in shadow values require the runtime to decompose the resolved color into RGB
channels before constructing the `rgba()` expression. Platform SDKs handle this
transparently.

---

## 8. Motion

### 8.1 Design Decisions

**Semantic duration names over numeric suffixes.** M3 uses `short1`, `short2`,
`medium1`, etc. While the IDK retains these for backward compatibility, the
preferred semantic names (`fast`, `normal`, `slow`) communicate intent more
clearly and are easier to remember.

### 8.2 Duration Scale

#### M3 Base Durations (retained for compatibility)

| Token Key | Value |
|-----------|-------|
| `motion.duration.short1` | 50ms |
| `motion.duration.short2` | 100ms |
| `motion.duration.short3` | 150ms |
| `motion.duration.short4` | 200ms |
| `motion.duration.medium1` | 250ms |
| `motion.duration.medium2` | 300ms |
| `motion.duration.medium3` | 350ms |
| `motion.duration.medium4` | 400ms |
| `motion.duration.long1` | 450ms |
| `motion.duration.long2` | 500ms |
| `motion.duration.long3` | 550ms |
| `motion.duration.long4` | 600ms |

#### Semantic Durations (preferred)

| Token Key | Value | References | Use |
|-----------|-------|------------|-----|
| `motion.duration.instant` | 0ms | — | Immediate transitions (no animation) |
| `motion.duration.fast` | 100ms | `{motion.duration.short2}` | Micro-interactions: toggles, checkboxes |
| `motion.duration.normal` | 250ms | `{motion.duration.medium1}` | Standard transitions: fade, slide |
| `motion.duration.slow` | 400ms | `{motion.duration.medium4}` | Complex transitions: expand, morph |
| `motion.duration.slower` | 600ms | `{motion.duration.long4}` | Dramatic transitions: page, route |

### 8.3 Easing Curves

| Token Key | Value | Use |
|-----------|-------|-----|
| `motion.easing.standard` | `cubic-bezier(0.2, 0.0, 0, 1.0)` | Default easing for most transitions |
| `motion.easing.emphasized` | `cubic-bezier(0.2, 0.0, 0, 1.0)` | High-priority transitions (M3 emphasized) |
| `motion.easing.linear` | `linear` | Progress indicators, continuous animations |
| `motion.easing.bounce` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | Playful overshoot for attention-drawing elements |

### 8.4 Delay

| Token Key | Value | Use |
|-----------|-------|-----|
| `motion.delay.none` | 0ms | No delay (default) |
| `motion.delay.short` | 100ms | Staggered list item animations |

### 8.5 Velocity (Extended)

Velocity tokens combine duration and easing into a single shorthand for common
transition patterns:

| Token Key | Expands To |
|-----------|------------|
| `motion.velocity.fast` | `{motion.duration.fast} {motion.easing.standard}` |
| `motion.velocity.normal` | `{motion.duration.normal} {motion.easing.standard}` |
| `motion.velocity.slow` | `{motion.duration.slow} {motion.easing.emphasized}` |
| `motion.velocity.bounce` | `{motion.duration.normal} {motion.easing.bounce}` |

---

## 9. Component Token Namespace

### 9.1 Structure

Component tokens (Tier 3) follow the namespace pattern:

```
comp.{component}.{variant}.{property}
```

Where:
- `component` — The UI component name (button, tab, modal, input, etc.)
- `variant` — The visual variant (primary, secondary, ghost, outline, etc.)
- `property` — The design property (background, foreground, border,
  borderRadius, padding, fontSize, etc.)

### 9.2 Reference Pattern

Component tokens always reference Tier 2 semantic tokens, never Tier 1
primitives directly. This ensures theme switches propagate correctly.

```
comp.button.primary.background    -> {color.primary}
comp.button.primary.foreground    -> {color.onPrimary}
comp.button.primary.border        -> {borderWidth.none}
comp.button.primary.borderRadius  -> {shape.radius.md}
comp.button.primary.paddingX      -> {spacing.inline.md}
comp.button.primary.paddingY      -> {spacing.inset.sm}
comp.button.primary.fontSize      -> {typography.labelLarge.fontSize}
comp.button.primary.fontWeight    -> {typography.labelLarge.fontWeight}
comp.button.primary.shadow        -> {shadow.subtle}
comp.button.primary.hoverBg       -> {color.primaryContainer}
comp.button.primary.disabledBg    -> {color.interactive.disabled}
```

### 9.3 Key Components

#### Button

| Token Key | Default Reference |
|-----------|-------------------|
| `comp.button.primary.background` | `{color.primary}` |
| `comp.button.primary.foreground` | `{color.onPrimary}` |
| `comp.button.primary.border` | `{borderWidth.none}` |
| `comp.button.primary.borderRadius` | `{shape.radius.md}` |
| `comp.button.secondary.background` | `{color.secondaryContainer}` |
| `comp.button.secondary.foreground` | `{color.onSecondaryContainer}` |
| `comp.button.ghost.background` | `transparent` |
| `comp.button.ghost.foreground` | `{color.primary}` |
| `comp.button.outline.background` | `transparent` |
| `comp.button.outline.foreground` | `{color.primary}` |
| `comp.button.outline.border` | `{borderWidth.thin}` |
| `comp.button.outline.borderColor` | `{color.border.default}` |

#### Tab

| Token Key | Default Reference |
|-----------|-------------------|
| `comp.tab.active.foreground` | `{color.primary}` |
| `comp.tab.active.border` | `{borderWidth.medium}` |
| `comp.tab.active.borderColor` | `{color.primary}` |
| `comp.tab.inactive.foreground` | `{color.text.secondary}` |
| `comp.tab.inactive.border` | `{borderWidth.none}` |
| `comp.tab.hover.foreground` | `{color.primary}` |
| `comp.tab.hover.background` | `{color.interactive.hover}` |

#### Modal

| Token Key | Default Reference |
|-----------|-------------------|
| `comp.modal.background` | `{color.surfaceContainerHigh}` |
| `comp.modal.foreground` | `{color.onSurface}` |
| `comp.modal.borderRadius` | `{shape.radius.xxl}` |
| `comp.modal.shadow` | `{shadow.overlay}` |
| `comp.modal.overlayBackground` | `rgba(0, 0, 0, 0.5)` |
| `comp.modal.padding` | `{spacing.inset.lg}` |

#### Input

| Token Key | Default Reference |
|-----------|-------------------|
| `comp.input.background` | `{color.surfaceContainerLowest}` |
| `comp.input.foreground` | `{color.onSurface}` |
| `comp.input.border` | `{borderWidth.thin}` |
| `comp.input.borderColor` | `{color.border.default}` |
| `comp.input.borderRadius` | `{shape.radius.md}` |
| `comp.input.focusBorderColor` | `{color.primary}` |
| `comp.input.focusShadow` | `{shadow.state.focus}` |
| `comp.input.errorBorderColor` | `{color.error}` |
| `comp.input.errorShadow` | `{shadow.state.error}` |
| `comp.input.disabledBackground` | `{color.interactive.disabled}` |
| `comp.input.placeholderColor` | `{color.text.secondary}` |

---

## 10. IDK / EDK / VDX Split

### What Lives Where

| Layer | Tier | Contents | License |
|-------|------|----------|---------|
| **IDK** (open-source) | 1 + 2 | M3 HCT palette generator, primitive tokens, semantic tokens, platform SDKs (Compose, React, CSS), responsive scale, font primitives (Roboto), theme resolution engine | Apache 2.0 |
| **EDK** (commercial SDK) | 3 | Component tokens, brand presets (Sphereon/Poppins, others), Figma sync plugin, advanced theming (multi-brand, tenant override), preset gallery | Commercial |
| **VDX** (deployment) | 3 | Tenant-specific theme overrides via admin API, runtime theme injection, white-label configuration, theme persistence (database) | Commercial |

### Token Resolution Order

When resolving a token value, the system checks sources in this order
(first match wins):

1. **VDX tenant override** — Runtime configuration from database/admin API
2. **EDK preset** — Brand-specific defaults (e.g., Sphereon Poppins preset)
3. **IDK semantic default** — M3-derived semantic value
4. **IDK primitive fallback** — Raw palette/scale value

### Upgrade Path

Projects start with IDK (free, M3 defaults). Adding EDK unlocks:

- Component tokens for fine-grained control
- Brand presets with curated typography, color, and spacing
- Figma variable synchronization
- Multi-brand support within a single deployment

Adding VDX unlocks:

- Per-tenant theme customization via admin UI
- Runtime theme switching without redeployment
- Theme persistence and versioning
- White-label onboarding workflows

---

## 11. Legacy Compatibility

### Alias Mappings

The following legacy token keys are retained as aliases. They resolve to the
new canonical keys at runtime. New code should use the canonical keys; legacy
aliases will be deprecated in a future major version.

#### Elevation to Shadow

| Legacy Key | Canonical Key | Value |
|------------|---------------|-------|
| `elevation.none` | `shadow.elevation.none` | `none` |
| `elevation.xs` | `shadow.elevation.xs` | See section 7.2 |
| `elevation.sm` | `shadow.elevation.sm` | See section 7.2 |
| `elevation.md` | `shadow.elevation.md` | See section 7.2 |
| `elevation.lg` | `shadow.elevation.lg` | See section 7.2 |
| `elevation.xl` | `shadow.elevation.xl` | See section 7.2 |

#### Shape Corner to Shape Radius

| Legacy Key | Canonical Key | Value |
|------------|---------------|-------|
| `shape.cornerExtraSmall` | `shape.radius.sm` | 4dp |
| `shape.cornerSmall` | `shape.radius.md` | 8dp |
| `shape.cornerMedium` | `shape.radius.lg` | 12dp |
| `shape.cornerLarge` | `shape.radius.xl` | 16dp |
| `shape.cornerExtraLarge` | `shape.radius.xxxl` | 28dp |

#### M3 Duration to Semantic Duration

| Legacy Key | Canonical Key | Notes |
|------------|---------------|-------|
| `motion.duration.short2` | `motion.duration.fast` | Both remain valid; semantic name is preferred |
| `motion.duration.medium1` | `motion.duration.normal` | Both remain valid |
| `motion.duration.medium4` | `motion.duration.slow` | Both remain valid |
| `motion.duration.long4` | `motion.duration.slower` | Both remain valid |

### Deprecation Strategy

1. **Phase 1 (current):** Both legacy and canonical keys work. Resolution
   engine logs a deprecation warning when a legacy key is accessed.
2. **Phase 2 (next minor):** Figma export and documentation use only canonical
   keys. Legacy keys still resolve but are hidden from autocomplete.
3. **Phase 3 (next major):** Legacy keys removed. Migration tooling provided
   to bulk-rename keys in theme JSON files and code.

---

## 12. Figma-to-Token Mapping

### Variable Collection Structure

Figma variables are organized into collections that mirror the token tiers:

| Figma Collection | Token Tier | Example Variables |
|------------------|------------|-------------------|
| `Primitives/Color` | 1 | `primary/40`, `neutral/90` |
| `Primitives/Spacing` | 1 | `spacing/4`, `spacing/16` |
| `Primitives/Typography` | 1 | `font-size/body-large`, `font-weight/medium` |
| `Semantic/Color` | 2 | `color/primary`, `color/on-primary` |
| `Semantic/Elevation` | 2 | `shadow/elevation/md` |
| `Semantic/Shape` | 2 | `shape/radius/md` |
| `Components/Button` | 3 | `button/primary/background` |
| `Components/Input` | 3 | `input/border-color` |

### Name Mapping Rules

Figma variable names use `/` as separator; token keys use `.`:

| Figma Variable | Token Key |
|----------------|-----------|
| `color/primary` | `color.primary` |
| `color/on-primary` | `color.onPrimary` |
| `color/primary-container` | `color.primaryContainer` |
| `color/surface-container-high` | `color.surfaceContainerHigh` |
| `color/feedback/success` | `color.feedback.success` |
| `color/feedback/success-container` | `color.feedback.successContainer` |
| `color/text/primary` | `color.text.primary` |
| `color/border/default` | `color.border.default` |
| `color/interactive/hover` | `color.interactive.hover` |
| `typography/display-large/font-size` | `typography.displayLarge.fontSize` |
| `typography/body-extra-small/font-size` | `typography.bodyExtraSmall.fontSize` |
| `spacing/inline/md` | `spacing.inline.md` |
| `spacing/stack/lg` | `spacing.stack.lg` |
| `spacing/inset/sm` | `spacing.inset.sm` |
| `shape/radius/md` | `shape.radius.md` |
| `shadow/elevation/lg` | `shadow.elevation.lg` |
| `shadow/state/focus` | `shadow.state.focus` |
| `border-width/thin` | `borderWidth.thin` |
| `motion/duration/fast` | `motion.duration.fast` |
| `motion/easing/bounce` | `motion.easing.bounce` |
| `comp/button/primary/background` | `comp.button.primary.background` |

### Naming Convention Summary

| Pattern | Figma | Token Key |
|---------|-------|-----------|
| Separator | `/` | `.` |
| Casing | `kebab-case` | `camelCase` |
| Multi-word segments | `on-primary` | `onPrimary` |
| Component prefix | `comp/` | `comp.` |

### Figma Mode Mapping

Figma variable modes map to theme variants:

| Figma Mode | Theme | Notes |
|------------|-------|-------|
| `Light` | Light theme | Default mode |
| `Dark` | Dark theme | Alternate mode |

Only Tier 2 (semantic) and Tier 3 (component) collections define multiple
modes. Tier 1 (primitive) collections have a single mode because raw values
are theme-independent.

### Sync Direction

```
Code (TokenKeyConstants.kt + theme JSON)
  |
  v  [generate]
Figma Variables
  |
  v  [design iteration]
Figma Design Files
  |
  v  [export diff]
Code (review + merge)
```

The canonical source is always code. Figma is a design tool that consumes
and iterates on the token set. Changes made in Figma are exported as diffs
and reviewed before merging back into the code source.

---

## Appendix A: Complete Token Key Reference

For the definitive list of token keys and their constant names, see
`TokenKeyConstants.kt` in the IDK theme core module. This document describes
the design rationale and values; the Kotlin source is the single source of
truth for key strings.

## Appendix B: Platform SDK Unit Mapping

| Token Unit | Web (CSS) | Android (Compose) | iOS (SwiftUI) |
|------------|-----------|-------------------|---------------|
| `px` (spacing, border) | `px` | `dp` | `pt` |
| `sp` (font size) | `rem` (base 16) | `sp` | `pt` with Dynamic Type |
| `dp` (shape radius) | `px` | `dp` | `pt` |
| `ms` (duration) | `ms` | `ms` (Long) | `TimeInterval` (seconds) |
| CSS shadow | `box-shadow` | `Shadow` composable | `.shadow()` modifier |
| CSS easing | `transition-timing-function` | `AnimationSpec` | `Animation` |

## Appendix C: M3 Compliance Notes

This design system is M3-compatible with the following intentional deviations:

1. **Extended type scale** — `displayExtraLarge` and `bodyExtraSmall` are
   additions not present in M3.
2. **Headline retention** — M3 Figma kit may omit Headline; we retain it.
3. **Feedback colors** — M3 defines only Error. We add Success, Warning, Info
   as first-class utility palettes with container/onContainer pairs.
4. **Shadow values** — M3 uses tonal elevation (surface tint) rather than
   box-shadow. We provide both: M3 tonal elevation via `color.surfaceContainer*`
   tokens and explicit shadow values for platforms that benefit from them.
5. **Extended shape scale** — `xs` (2dp) and `xxl` (24dp) fill gaps in the
   M3 shape scale.
6. **Semantic motion names** — M3 uses `short1`/`medium2` etc.; we add
   `fast`/`normal`/`slow` as preferred aliases.
7. **Spacing system** — M3 does not define a spacing token system. Our 17-stop
   scale with semantic aliases is a pure extension.
8. **Border width** — Not part of M3. Added as a practical necessity.
