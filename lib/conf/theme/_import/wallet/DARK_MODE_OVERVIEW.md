# Dark mode — design-system overview & change guide

The design tokens in `tokens.json` define the **light/base** theme only. Dark
mode is a **Tier-2 semantic override** layer: primitives (`palette.*`) and
component refs (`comp.*`) are untouched; only the semantic roles (`color.*`)
re-point to different palette stops. The reference for that layer is the
`[data-theme="dark"]` block in `colors_and_type.css`.

This file is the single place to read and tune the dark theme. When you change a
value here in the design system, mirror it in the two IDK code locations listed
at the bottom (they must stay in lockstep).

---

## The decision on `color.primary` (dark)

The design originally lifted the dark primary to **`brand.300` (`#AE89F1`)** — a
light lavender — for WCAG AA contrast on the near-black surface. In practice that
reads too light / washed out. The dark primary is now **`brand.400`
(`#9564EC`)**: a deeper, clearly-purple accent that also forms the bottom of the
primary button gradient.

| Role | Design (was) | Now | Note |
|---|---|---|---|
| `color.primary` (dark) | `brand.300` `#AE89F1` | **`brand.400` `#9564EC`** | deeper purple |

**To change it in the design system:** in the dark variant (the
`[data-theme="dark"]` override), set
`--color-primary: var(--palette-brand-400)` (was `--palette-brand-300`). Pick any
`brand.*` stop to taste:

| Stop | Hex | Reads as | Contrast on `#0A0D12` |
|---|---|---|---|
| `brand.300` | `#AE89F1` | light lavender | ~6.4:1 — AA for all text |
| **`brand.400`** | **`#9564EC`** | **medium purple (current)** | ~3.8:1 — AA for UI/large text, borderline for small text |
| `brand.500` | `#7C40E8` | vivid brand purple | ~2.7:1 — fails AA for text; OK only as a fill behind white text |

> **Contrast caveat.** Going darker than `brand.400` drops small purple-colored
> *text* (eyebrows, links) below AA on the dark canvas. Buttons are unaffected —
> they put white/`onPrimary` text on the purple fill, which stays readable.

### Buttons use the full brand purple (brand.500), not `color.primary`

The primary **button** gradient ends at **`palette.brand.500` (`#7C40E8`)** in
**both** light and dark — it does *not* track `color.primary`. Rationale: the
button carries white (`onPrimary`) text, so the deeper `brand.500` keeps good
contrast and a strong brand presence, while small *accent text* still needs the
lighter `brand.400` for AA. So:

- `comp.button.primary.background` = `linear-gradient(180deg, #7276F7 0%, {palette.brand.500} 100%)`
- `comp.button.primary.border` = `{palette.brand.500}`
- Accents (eyebrows, active step, focus rings, links) = `color.primary` = `brand.400` in dark.

| Element | Dark color | Stop |
|---|---|---|
| Primary button (gradient end) | `#7C40E8` | `brand.500` |
| Primary button hover | `#5D1AD6` | `brand.600` |
| Accent text / icons | `#9564EC` | `brand.400` |

---

## Full dark-mode semantic map (Tier 2)

Everything the dark variant overrides, with the palette stop each role points to.
Edit a *stop* here to retune dark mode; primitives keep their hex from
`tokens.json`.

### Brand / accent
| Token | Stop | Hex |
|---|---|---|
| `color.primary` | `brand.400` | `#9564EC` |
| `color.onPrimary` | `brand.900` | `#1C0840` |
| `color.primaryContainer` | `brand.800` | `#320E72` |
| `color.onPrimaryContainer` | `brand.100` | `#E0D2FA` |
| `color.secondary` | `blue.300` | `#96A1C8` |
| `color.secondaryContainer` | `blue.800` | `#2C334B` |
| `color.onSecondaryContainer` | `blue.100` | `#CBD1E4` |
| `color.tertiary` | `selenas.300` | `#CA7DE5` |
| `color.onTertiary` | `selenas.900` | `#280A32` |
| `color.tertiaryContainer` | `selenas.800` | `#3A0F4A` |
| `color.onTertiaryContainer` | `selenas.100` | `#E6C1F3` |
| `color.inversePrimary` | `brand.500` | `#7C40E8` |

### Surfaces (neutral charcoal ramp)
| Token | Stop / value | Hex |
|---|---|---|
| `color.surface` | `gray.900` | `#0A0D12` |
| `color.onSurface` | `gray.50` | `#FBFBFB` |
| `color.surfaceVariant` | `gray.800` | `#303030` |
| `color.onSurfaceVariant` | `gray.300` | `#C4C4C4` |
| `color.surfaceContainerLow` | literal | `#131722` |
| `color.surfaceContainer` | literal | `#1A1F2C` |
| `color.surfaceContainerHigh` | literal | `#232838` |
| `color.surfaceContainerHighest` | literal | `#232838` (capped — design defines only 3 elevations; add a 4th step if wanted) |
| `color.background` | `gray.900` | `#0A0D12` |
| `color.inverseSurface` | `gray.50` | `#FBFBFB` |
| `color.inverseOnSurface` | `gray.900` | `#0A0D12` |

### Text & borders
| Token | Stop | Hex |
|---|---|---|
| `color.text.primary` | `gray.50` | `#FBFBFB` |
| `color.text.secondary` | `gray.300` | `#C4C4C4` |
| `color.text.disabled` | `gray.600` | `#727272` |
| `color.border.default` | `gray.700` | `#4E4E4E` |
| `color.border.subtle` | `gray.800` | `#303030` |
| `color.border.disabled` | `gray.800` | `#303030` |
| `color.outline` | `gray.600` | `#727272` |
| `color.outlineVariant` | `gray.700` | `#4E4E4E` |

### Status / feedback (lifted one stop for contrast on dark)
| Token | Stop | Hex |
|---|---|---|
| `color.error` | `error.400` | `#DB7759` |
| `color.onError` | `error.900` | `#320C04` |
| `color.feedback.success` | `success.600` | `#00C249` (was `success.400` `#15FF5D` — neon, too bright on dark) |
| `color.feedback.onSuccess` | `success.900` | `#003516` |
| `color.feedback.warning` | `warning.400` | `#FDB922` |
| `color.feedback.onWarning` | `warning.900` | `#412D05` |
| `color.feedback.info` | `pending.400` | `#3496FF` |
| `color.feedback.onInfo` | `pending.900` | `#002246` |

### Scrim / interactive
| Token | Value |
|---|---|
| `color.scrim` | `rgba(0,0,0,0.7)` |
| `color.shadow` | `rgba(0,0,0,0.5)` |
| `color.interactive.hover` | `rgba(255,255,255,0.06)` |
| `color.interactive.disabled` | `gray.800` `#303030` |

---

## Where this is mirrored in IDK code (keep in lockstep)

When the design-system dark values change, update both:

1. **`lib/conf/theme/core/public/.../defaults/SystemDefaults.kt`** — the
   `baselineDark` block (`ThemeVariant.DARK`). Uses `{palette.*}` refs. This is
   the source of truth; Compose (`DefaultThemeDefaults`) and the web/login page
   (`WebSystemDefaults`) both derive from it.
2. **`lib/conf/theme/react/src/defaults.ts`** — the `darkColors` map. Uses
   **resolved hex** (not refs), so convert the stop to its hex when you edit.
   Run `npm run build` in that package to regenerate `dist/`, then repackage the
   `@sphereon/theme-react` tarball for consumers that pin it (e.g. the
   `platform-onboarding` UI).

Light mode is unaffected by any of the above — its primary stays `brand.500`
`#7C40E8`.
