# Theme SDK

The IDK theme stack provides a complete design-token system built on Material Design 3 (M3). It covers palette generation, token resolution, CSS variable mapping, and framework-specific providers for React and Compose. All theme data flows through a single token model — the same tokens that drive CSS custom properties on the web also drive Compose `CompositionLocal` values on mobile.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Core Module](#core-module)
- [Web Module](#web-module)
- [React SDK](#react-sdk)
- [Compose SDK](#compose-sdk)
- [App Integration Guide](#app-integration-guide)

---

## Architecture Overview

The theme stack is split into four layers, each adding platform-specific capabilities on top of a shared core:

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
│  (Portal, Web Wallet, Mobile Wallet, Admin Dashboard)       │
├──────────────────────┬──────────────────────────────────────┤
│  @sphereon/theme-    │  IDK Compose SDK                     │
│  react               │  (lib-conf-theme-compose)            │
│  React Provider,     │  DefaultTheme {},                    │
│  hooks, SSR          │  CompositionLocals                   │
├──────────────────────┤──────────────────────────────────────┤
│  Web Module          │                                      │
│  (lib-conf-theme-web)│         (not needed for              │
│  CSS vars, FOUC,     │          native Compose)             │
│  legacy aliases      │                                      │
├──────────────────────┴──────────────────────────────────────┤
│                    Core Module                               │
│                (lib-conf-theme-core-public)                  │
│  Token model, M3 palette, resolution, validation            │
└─────────────────────────────────────────────────────────────┘
```

The EDK extends this with backend-connected variants:

```
IDK (open-source)                  EDK (enterprise)
─────────────────                  ─────────────────
@sphereon/theme-react        ──►   @sphereon/theme-react-vdx
                                   (re-exports IDK + adds ThemeClient)

lib-conf-theme-compose       ──►   edk-theme-compose
                                   (re-exports IDK + adds ThemeClient, rememberThemeState)
```

### Token Resolution Chain

Tokens resolve through a layered scope hierarchy. Each scope can override tokens from the scope below it:

```
  PRINCIPAL   ◄── Per-user overrides (highest priority)
      │
   TENANT     ◄── Tenant-specific branding
      │
     APP      ◄── Application-wide theme
      │
   SYSTEM     ◄── M3 baseline defaults (lowest priority)
```

`TokenFlattener.merge()` applies definitions in this order — later layers override earlier ones. After merging, `TokenReferenceResolver.resolve()` expands any `{key}` references to their final values.

---

## Core Module

**Module**: `lib-conf-theme-core-public`
**Package**: `com.sphereon.conf.theme.core`

The core module is pure Kotlin Multiplatform (JVM, JS, wasmJs) with no platform dependencies. It defines the token data model, M3 palette generation, and token processing pipeline.

### Token Model

Every theme is defined as a `ThemeDefinition` containing a list of `ThemeToken` values:

```kotlin
@Serializable
data class ThemeDefinition(
    val id: String,
    val name: String,
    val variant: ThemeVariant? = null,
    val parentId: String? = null,
    val scope: ThemeScope = ThemeScope.APP,
    val appId: String? = null,
    val tokens: List<ThemeToken> = emptyList(),
    val version: Long = 1,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)

@Serializable
data class ThemeToken(
    val key: String,                                    // e.g. "color.primary"
    val value: String,                                  // literal or reference "{color.secondary}"
    val type: ThemeTokenType = ThemeTokenType.STRING
)
```

`ThemeVariant` controls which color scheme a definition targets:

| Variant | Description |
|---------|-------------|
| `LIGHT` | Standard light theme |
| `DARK` | Dark theme |
| `HIGH_CONTRAST` | Accessibility high-contrast theme |

`ThemeScope` determines the layer a definition belongs to in the resolution chain:

| Scope | Priority | Typical use |
|-------|----------|-------------|
| `SYSTEM` | Lowest | Built-in M3 baseline |
| `APP` | Low | Application-wide branding |
| `TENANT` | High | Tenant white-labeling |
| `PRINCIPAL` | Highest | Per-user preferences |

`ThemeTokenType` constrains what kind of value a token holds:

| Type | Example value | Description |
|------|---------------|-------------|
| `COLOR` | `#6750A4` | CSS hex color or M3 color role |
| `DIMENSION` | `16dp`, `14sp` | Dimension in dp/sp/px |
| `FONT_FAMILY` | `Roboto` | Font family name |
| `FONT_WEIGHT` | `500` | CSS font weight |
| `OPACITY` | `0.38` | Opacity 0.0–1.0 |
| `DURATION` | `300ms` | Animation duration |
| `EASING` | `cubic-bezier(...)` | Cubic bezier easing function |
| `STRING` | any | Arbitrary string value |

After resolution, tokens are flattened into a `ResolvedTheme`:

```kotlin
@Serializable
data class ResolvedTheme(
    val tokens: Map<String, String>,         // flat key → value map
    val resolvedAt: Instant,
    val variant: ThemeVariant? = null,
    val layerCount: Int = 0,
    val etag: String? = null,                // content hash for caching
    val fallback: Boolean = false,
    val tenantId: String? = null,
    val appId: String? = null,
    val branding: BrandingMetadata? = null,
    val appliedLayers: List<String>? = null,
    val web: WebBrandingMetadata? = null
)
```

### Token Key Constants

`TokenKeyConstants` defines all well-known token keys following M3 naming. Keys use dot-separated identifiers organized by category:

**Colors** — M3 color roles:
- `color.primary`, `color.onPrimary`, `color.primaryContainer`, `color.onPrimaryContainer`
- `color.secondary`, `color.onSecondary`, `color.secondaryContainer`, `color.onSecondaryContainer`
- `color.tertiary`, `color.onTertiary`, `color.tertiaryContainer`, `color.onTertiaryContainer`
- `color.error`, `color.onError`, `color.errorContainer`, `color.onErrorContainer`
- `color.surface`, `color.onSurface`, `color.surfaceVariant`, `color.onSurfaceVariant`
- `color.surfaceDim`, `color.surfaceBright`, `color.surfaceContainerLowest` through `color.surfaceContainerHighest`
- `color.outline`, `color.outlineVariant`, `color.inverseSurface`, `color.inverseOnSurface`, `color.inversePrimary`
- `color.background`, `color.onBackground`, `color.scrim`, `color.shadow`

**Typography** — M3 type scale (each with `fontFamily`, `fontSize`, `fontWeight`, `lineHeight`, `letterSpacing`):
- `typography.display.large.*`, `typography.display.medium.*`, `typography.display.small.*`
- `typography.headline.large.*`, `typography.headline.medium.*`, `typography.headline.small.*`
- `typography.title.large.*`, `typography.title.medium.*`, `typography.title.small.*`
- `typography.body.large.*`, `typography.body.medium.*`, `typography.body.small.*`
- `typography.label.large.*`, `typography.label.medium.*`, `typography.label.small.*`

**Elevation**: `elevation.none`, `elevation.xs`, `elevation.sm`, `elevation.md`, `elevation.lg`, `elevation.xl`

**Shape**: `shape.corner.extraSmall`, `shape.corner.small`, `shape.corner.medium`, `shape.corner.large`, `shape.corner.extraLarge`

**Motion**: `motion.duration.short`, `motion.duration.medium`, `motion.duration.long`, `motion.easing.standard`, `motion.easing.decelerate`, `motion.easing.accelerate`, `motion.themeTransition.duration`, `motion.themeTransition.easing`

**Responsive**: `responsive.scale.compact`, `responsive.scale.medium`, `responsive.scale.expanded`

**Branding**: `branding.appName`, `branding.primaryColor`, `branding.logoUrl`, `branding.logoDarkUrl`, `branding.faviconUrl`, `branding.fontResourceId`, `branding.logoResourceId`, `branding.logoDarkResourceId`

### M3 Palette Generation

`M3PaletteGenerator` produces a full Material Design 3 color palette from a single seed color using the HCT (Hue-Chroma-Tone) color space:

```kotlin
val palette: ThemePalette = M3PaletteGenerator.generate("#1a73e8")
```

This generates five tonal palettes, each with 13 tone stops (0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100):

```kotlin
@Serializable
data class ThemePalette(
    val seedColor: String,
    val primary: TonalPaletteResult,      // max chroma from seed
    val secondary: TonalPaletteResult,    // chroma 16
    val tertiary: TonalPaletteResult,     // hue + 60°
    val neutral: TonalPaletteResult,      // chroma 4
    val error: TonalPaletteResult         // fixed red hue
)

@Serializable
data class TonalPaletteResult(
    val tone0: String,     // "#000000"
    val tone10: String,
    // ... tone20 through tone95 ...
    val tone99: String,
    val tone100: String    // "#FFFFFF"
)
```

The algorithm:

1. Convert seed hex → HCT color space via `HctColor.fromHex()`
2. Build a `TonalPalette` for each M3 palette role (primary uses the seed's chroma, secondary uses 16, tertiary rotates hue by 60°, neutral uses 4, error uses a fixed hue)
3. Generate all 13 tone stops for each palette

The HCT color space is a perceptually uniform color model from Material Design that ensures generated colors meet WCAG contrast requirements at predictable tone combinations (e.g., tone 40 on tone 100 reliably produces 4.5:1 contrast).

### Token Processing

**TokenBuilder** — DSL for constructing token lists:

```kotlin
val tokens = buildTokens {
    color("color.primary", "#6750A4")
    dimension("typography.body.large.fontSize", "16sp")
    fontFamily("typography.body.large.fontFamily", "Roboto")
    string("branding.appName", "My App")
}
```

**TokenFlattener** — Merges multiple `ThemeDefinition` layers into a flat map. Definitions are processed in precedence order (first = lowest priority):

```kotlin
val merged: Map<String, String> = TokenFlattener.merge(
    listOf(systemDefaults, appTheme, tenantOverrides)
)
// tenantOverrides tokens win over appTheme, which wins over systemDefaults
```

**TokenReferenceResolver** — Expands `{key}` references in token values. Includes cycle detection (max depth 10):

```kotlin
// Given tokens: { "color.link": "{color.primary}", "color.primary": "#6750A4" }
val resolved = TokenReferenceResolver.resolve(tokens)
// Result: { "color.link": "#6750A4", "color.primary": "#6750A4" }
```

### System Defaults

`SystemDefaults` provides complete M3 baseline theme definitions for all three variants:

```kotlin
val light: ThemeDefinition = SystemDefaults.baseline              // M3 light (seed #6750A4)
val dark: ThemeDefinition = SystemDefaults.baselineDark           // M3 dark
val hc: ThemeDefinition = SystemDefaults.baselineHighContrast     // M3 high contrast
```

These contain the full set of M3 color tokens, typography, elevation, shape, and motion tokens. They serve as the `SYSTEM` scope layer — every theme starts from these defaults.

### Validation

`ThemeValidator` validates theme definitions, individual tokens, and custom CSS:

```kotlin
// Validate a full definition
val result = ThemeValidator.validate(definition)
if (!result.valid) {
    println(result.errors)  // e.g. ["Invalid hex color: xyz", "Invalid token key: ..."]
}

// Validate individual values
ThemeValidator.isValidHexColor("#6750A4")    // true
ThemeValidator.isValidHexColor("red")       // false
ThemeValidator.isValidTokenKey("color.primary")  // true

// Validate custom CSS against security policy
val errors = ThemeValidator.validateCustomCss(css, CssPolicyConfig(maxSizeBytes = 50_000))
// Blocks: expression(), javascript:, @import, url(), -moz-binding, behavior
```

### JavaScript Interop

`JsThemeInterop` (JS target only) provides JSON serialization bridges for use from TypeScript:

```kotlin
@JsExport
fun resolvedThemeFromJson(jsonStr: String): ResolvedTheme
fun resolvedThemeToJson(theme: ResolvedTheme): String
fun systemDefaultsLightJson(): String
fun systemDefaultsDarkJson(): String
fun systemDefaultsTokens(variant: String): Map<String, String>  // "light", "dark", "highContrast"
```

These functions enable the React SDK to consume Kotlin-generated defaults and serialize theme data across the JS/Kotlin boundary.

---

## Web Module

**Module**: `lib-conf-theme-web`
**Package**: `com.sphereon.conf.theme.web`

The web module adds CSS-specific functionality on top of core. It maps tokens to CSS custom properties, generates legacy aliases for backward compatibility, and provides FOUC prevention for server-rendered pages.

### CSS Token Mapping

`CssTokenMapper` converts token keys and values to CSS-compatible formats:

```kotlin
CssTokenMapper.tokenKeyToCssVar("color.primary")     // "--color-primary"
CssTokenMapper.convertUnit("16dp")                    // "16px"
CssTokenMapper.convertUnit("14sp")                    // "14px"
CssTokenMapper.hexToRgba("#6750A4", 0.5)              // "rgba(103, 80, 164, 0.5)"

// Convert all tokens at once
val cssVars = CssTokenMapper.tokensToCssVars(resolvedTokens)
// { "--color-primary": "#6750A4", "--color-on-primary": "#FFFFFF", ... }
```

Unit conversion rules: `dp` (density-independent pixels) and `sp` (scale-independent pixels) both convert 1:1 to `px` for web. Values already in `px`, `rem`, `em`, `%`, or `vh`/`vw` are passed through unchanged.

### Legacy CSS Aliases

`CssLegacyAliases` generates backward-compatible CSS variable names for existing portal and web-wallet code:

```kotlin
val aliases = CssLegacyAliases.generateAliases(resolvedTokens)
```

This produces mappings like:

| Legacy alias | Maps to token |
|-------------|---------------|
| `--color-background` | `color.background` |
| `--color-foreground` | `color.onBackground` |
| `--color-text-primary` | `color.onSurface` |
| `--color-text-secondary` | `color.onSurfaceVariant` |
| `--color-bg-primary` | `color.surface` |
| `--color-bg-secondary` | `color.surfaceContainerLow` |
| `--color-bg-card` | `color.surfaceContainer` |
| `--color-bg-hover` | `color.surfaceContainerHigh` |
| `--color-border-primary` | `color.outline` |
| `--color-border-error` | `color.error` |
| `--radius-sm` | `shape.corner.small` |
| `--radius-md` | `shape.corner.medium` |
| `--radius-lg` | `shape.corner.large` |

Both the new `--color-primary` and legacy `--color-text-link` variables are emitted, so existing CSS continues to work during migration.

### Branding Extraction

`WebBrandingTokens` extracts structured branding metadata from a flat token map:

```kotlin
val branding: BrandingMetadata = WebBrandingTokens.extract(resolvedTokens)
// BrandingMetadata(
//     appName = "My App",
//     primaryColor = "#1a73e8",
//     logoUrl = "https://...",
//     logoDarkUrl = "https://...",
//     faviconUrl = "https://..."
// )
```

### FOUC Prevention

Flash of Unstyled Content (FOUC) occurs when a server-rendered page briefly displays in the wrong theme before JavaScript hydrates. `FoucPreventionScript` generates a minimal inline script that runs before any rendering:

```kotlin
val script = FoucPreventionScript.generate(
    defaultMode = "system",
    cookieName = "sphereon-theme-mode",
    storageKey = "theme-mode"
)
// Returns a self-contained <script> body
```

The generated script:
1. Reads the user's preference from `localStorage` (key: `theme-mode`)
2. Falls back to reading the cookie (`sphereon-theme-mode`)
3. If mode is `system`, checks `window.matchMedia('(prefers-color-scheme: dark)')`
4. Sets `data-theme="light"` or `data-theme="dark"` on `<html>`
5. Sets `document.documentElement.style.colorScheme`

This runs synchronously in `<head>`, before the browser paints.

### Pre-Resolved Web Defaults

`WebSystemDefaults` provides pre-resolved, CSS-ready token maps so web consumers don't need to run the full resolution pipeline:

```kotlin
val lightTokens: Map<String, String> = WebSystemDefaults.light
val darkTokens: Map<String, String> = WebSystemDefaults.dark
val hcTokens: Map<String, String> = WebSystemDefaults.highContrast

// Or by variant
val tokens = WebSystemDefaults.forVariant(ThemeVariant.DARK)
```

These are lazily computed on first access and cached.

### DOM CSS Injection (JS-only)

`DomCssInjector` applies tokens directly to the DOM (only available on the JS target):

```kotlin
// Apply raw CSS variables
DomCssInjector.applyCssVars(mapOf("--color-primary" to "#6750A4"))

// Full pipeline: convert tokens → CSS vars + legacy aliases → apply to document.documentElement
DomCssInjector.applyTokens(resolvedTokens)
```

---

## React SDK

**Package**: `@sphereon/theme-react`

The React SDK provides a complete theming solution for React applications with zero-config defaults, M3 palette generation, SSR support, and FOUC prevention.

### Installation

```bash
npm install @sphereon/theme-react
# or
yarn add @sphereon/theme-react
```

Peer dependencies: `react >= 18`, `react-dom >= 18`

### ThemeProvider

The `ThemeProvider` component manages all theme state and applies CSS custom properties to the DOM:

```tsx
import { ThemeProvider } from '@sphereon/theme-react'

function App() {
  return (
    <ThemeProvider primaryColor="#1a73e8" appName="My App">
      <YourApp />
    </ThemeProvider>
  )
}
```

**Props**:

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `children` | `ReactNode` | required | Child components |
| `primaryColor` | `string` | `'#7276F7'` | Seed color for M3 palette generation |
| `appName` | `string` | `'Portal'` | Application name for branding tokens |
| `logoUrl` | `string` | — | Light-mode logo URL |
| `logoDarkUrl` | `string` | — | Dark-mode logo URL |
| `defaultMode` | `ThemeMode` | `'system'` | Initial theme mode |
| `tokenOverrides` | see below | — | Custom token overrides |

**Token resolution order** (inside the provider):

```
System defaults (M3 baseline for current variant)
        │
        ▼
M3 palette tokens (generated from primaryColor)
        │
        ▼
tokenOverrides (your custom overrides)
        │
        ▼
Final token map → applied as CSS custom properties
```

**tokenOverrides** accepts three forms:

```tsx
// 1. Flat map — same overrides for both variants
<ThemeProvider tokenOverrides={{ 'color.surface': '#FAFAFA' }}>

// 2. Function — variant-specific overrides
<ThemeProvider tokenOverrides={(variant) => ({
  'color.surface': variant === 'light' ? '#FAFAFA' : '#1A1A1A'
})}>

// 3. Object with light/dark keys
<ThemeProvider tokenOverrides={{
  light: { 'color.surface': '#FAFAFA' },
  dark: { 'color.surface': '#1A1A1A' }
}}>
```

**Mode persistence**: The provider stores the selected mode in both `localStorage` (key: `theme-mode`) and a cookie (`sphereon-theme-mode`, 1-year expiry, `SameSite=Lax`). The cookie enables server-side mode detection for SSR.

### Split Contexts

The provider exposes two React contexts to minimize unnecessary re-renders:

```
ThemeContext              ThemeModeContext
(full state)              (mode only)
├── mode                  ├── mode
├── resolvedMode          ├── resolvedMode
├── setMode               └── setMode
├── tokens
├── branding
├── primaryColor
└── appName
```

Components that only need to toggle light/dark mode can use `useThemeMode()` and won't re-render when tokens change. Components that read tokens use `useTheme()`.

### Hooks

**`useTheme()`** — returns the full theme context:

```tsx
import { useTheme } from '@sphereon/theme-react'

function BrandedHeader() {
  const { branding, tokens, resolvedMode, setMode } = useTheme()

  return (
    <header>
      <img src={resolvedMode === 'dark' ? branding.logoDarkUrl : branding.logoUrl} />
      <h1>{branding.appName}</h1>
      <button onClick={() => setMode(resolvedMode === 'light' ? 'dark' : 'light')}>
        Toggle theme
      </button>
    </header>
  )
}
```

**`useThemeMode()`** — lightweight hook for mode-only access:

```tsx
import { useThemeMode } from '@sphereon/theme-react'

function ThemeToggle() {
  const { mode, resolvedMode, setMode } = useThemeMode()

  return (
    <select value={mode} onChange={(e) => setMode(e.target.value as ThemeMode)}>
      <option value="system">System</option>
      <option value="light">Light</option>
      <option value="dark">Dark</option>
    </select>
  )
}
```

### Types

```typescript
type ThemeMode = 'light' | 'dark' | 'system'
type ThemeVariant = 'light' | 'dark'
type ThemeTokenMap = Record<string, string>

interface ThemeBranding {
  appName?: string
  logoUrl?: string
  logoDarkUrl?: string
  faviconUrl?: string
}

interface ThemeModeContextValue {
  mode: ThemeMode
  resolvedMode: ThemeVariant
  setMode: (mode: ThemeMode) => void
}

interface ThemeContextValue extends ThemeModeContextValue {
  tokens: ThemeTokenMap
  branding: ThemeBranding
  primaryColor: string
  appName: string
}
```

### FOUC Prevention

For server-rendered applications (Next.js, Remix), use `<ThemeScript />` in the document `<head>` to prevent a flash of the wrong theme:

```tsx
import { ThemeScript } from '@sphereon/theme-react'

// Next.js app/layout.tsx
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <ThemeScript defaultMode="system" />
      </head>
      <body>
        <ThemeProvider>
          {children}
        </ThemeProvider>
      </body>
    </html>
  )
}
```

The script renders as a blocking `<script>` tag that reads the user's saved preference (localStorage → cookie → system) and sets `data-theme` and `colorScheme` on `<html>` before any paint occurs.

For server-side mode resolution (e.g., to set initial styles or meta tags):

```tsx
import { resolveInitialMode } from '@sphereon/theme-react/server'
import { cookies } from 'next/headers'

// In a server component or middleware
const mode = resolveInitialMode(cookies())
```

### CSS Injection

The `ThemeProvider` automatically injects CSS custom properties. For manual control:

```tsx
import { tokenKeyToCssVar, applyTokens } from '@sphereon/theme-react'

// Convert a single key
tokenKeyToCssVar('color.primary')  // "--color-primary"

// Apply tokens to the DOM manually
applyTokens({ 'color.primary': '#1a73e8', 'color.surface': '#FFFFFF' })
```

### M3 Palette Generation

Generate palettes directly without a provider:

```tsx
import { generateM3Palette, paletteToTokens } from '@sphereon/theme-react'

const palette = generateM3Palette('#1a73e8')
const lightTokens = paletteToTokens(palette, 'light')
const darkTokens = paletteToTokens(palette, 'dark')
```

### System Defaults

Access the built-in M3 baseline tokens:

```tsx
import { getSystemDefaults } from '@sphereon/theme-react/defaults'

const lightDefaults = getSystemDefaults('light')
const darkDefaults = getSystemDefaults('dark')
```

### Sub-Path Exports

The package exposes three entry points for tree-shaking:

| Import path | Contents |
|-------------|----------|
| `@sphereon/theme-react` | Provider, hooks, CSS injection, palette, types |
| `@sphereon/theme-react/defaults` | `getSystemDefaults()` |
| `@sphereon/theme-react/server` | `resolveInitialMode()` for SSR |

---

## Compose SDK

**Module**: `lib-conf-theme-compose`
**Package**: `com.sphereon.conf.theme.compose`

The Compose SDK provides theming for Kotlin Multiplatform Compose applications with the same token model as the React SDK.

### DefaultTheme

The main composable entry point with two overloads:

```kotlin
// Simple branding
@Composable
fun DefaultTheme(
    primaryColor: String? = null,
    appName: String? = null,
    logoUrl: String? = null,
    mode: ThemeMode = ThemeMode.SYSTEM,
    animateTransition: Boolean = false,
    adaptToWindowSize: Boolean = false,
    accessibilityState: AccessibilityState? = null,
    content: @Composable () -> Unit
)

// Pre-resolved theme
@Composable
fun DefaultTheme(
    resolvedTheme: ResolvedTheme,
    animateTransition: Boolean = false,
    adaptToWindowSize: Boolean = false,
    accessibilityState: AccessibilityState? = null,
    content: @Composable () -> Unit
)
```

Usage patterns:

```kotlin
// Zero-config (M3 baseline)
DefaultTheme {
    MyApp()
}

// Custom branding
DefaultTheme(primaryColor = "#1a73e8", appName = "My App") {
    MyApp()
}

// Pre-resolved from backend
DefaultTheme(resolvedTheme = theme) {
    MyApp()
}
```

### ThemeMode

```kotlin
enum class ThemeMode {
    SYSTEM,   // Follow OS dark mode setting
    LIGHT,    // Always light
    DARK      // Always dark
}
```

### CompositionLocals

Access theme data from any composable:

```kotlin
val tokens = LocalThemeTokens.current          // Map<String, String>
val theme = LocalResolvedTheme.current         // ResolvedTheme?
val variant = LocalThemeVariant.current        // ThemeVariant?
val branding = LocalBrandingTokens.current     // BrandingTokens
val motion = LocalMotionTokens.current         // MotionTokens
val a11y = LocalAccessibilityState.current     // AccessibilityState
val windowClass = LocalWindowWidthSizeClass.current  // WindowWidthSizeClass
```

### Client-Side Palette Resolution

`ClientPaletteResolver` generates a `ResolvedTheme` locally without a backend:

```kotlin
val theme = ClientPaletteResolver.resolve(
    primaryColor = "#1a73e8",
    appName = "My App",
    logoUrl = "https://example.com/logo.png",
    variant = ThemeVariant.LIGHT
)
```

### Accessibility

```kotlin
@Immutable
data class AccessibilityState(
    val isHighContrast: Boolean = false,     // boost contrast
    val prefersReducedMotion: Boolean = false  // disable animations
)
```

### Responsive Breakpoints

```kotlin
enum class WindowWidthSizeClass {
    Compact,      // < 600dp (phones)
    Medium,       // 600dp–840dp (tablets)
    Expanded      // >= 840dp (desktops)
}
```

When `adaptToWindowSize = true`, `DefaultTheme` applies responsive typography scaling factors from the `responsive.scale.*` tokens.

---

## App Integration Guide

### Zero-Config (React)

The simplest integration — M3 defaults with the standard purple seed color:

```tsx
import { ThemeProvider } from '@sphereon/theme-react'

export default function App() {
  return (
    <ThemeProvider>
      <YourApp />
    </ThemeProvider>
  )
}
```

This gives you all M3 tokens as CSS custom properties (e.g., `var(--color-primary)`, `var(--color-surface)`) plus legacy aliases.

### Custom Branding

Set your brand color and app name. The provider generates a full M3 palette from the seed:

```tsx
<ThemeProvider primaryColor="#1a73e8" appName="Acme Portal" logoUrl="/logo.svg">
  <YourApp />
</ThemeProvider>
```

### App-Specific Overrides

For apps that need to deviate from the generated palette (e.g., portal uses pure grey surfaces instead of M3 tinted surfaces):

```tsx
<ThemeProvider
  primaryColor="#1a73e8"
  tokenOverrides={{
    light: {
      'color.surface': '#FFFFFF',
      'color.surfaceContainer': '#F5F5F5',
      'color.surfaceContainerLow': '#FAFAFA',
      'color.surfaceContainerHigh': '#EEEEEE',
    },
    dark: {
      'color.surface': '#121212',
      'color.surfaceContainer': '#1E1E1E',
      'color.surfaceContainerLow': '#1A1A1A',
      'color.surfaceContainerHigh': '#2C2C2C',
    }
  }}
>
  <YourApp />
</ThemeProvider>
```

### Next.js Integration

Full SSR setup with FOUC prevention:

```tsx
// app/layout.tsx
import { ThemeProvider, ThemeScript } from '@sphereon/theme-react'

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <ThemeScript />
      </head>
      <body>
        <ThemeProvider primaryColor="#1a73e8" appName="My App">
          {children}
        </ThemeProvider>
      </body>
    </html>
  )
}
```

To read the theme mode on the server (e.g., for conditional rendering):

```tsx
// app/page.tsx (server component)
import { resolveInitialMode } from '@sphereon/theme-react/server'
import { cookies } from 'next/headers'

export default function Page() {
  const mode = resolveInitialMode(cookies())
  // mode is 'light', 'dark', or 'system'
}
```

### Using CSS Custom Properties

All tokens are available as CSS custom properties. Use them in your stylesheets:

```css
.card {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
  border: 1px solid var(--color-outline-variant);
  border-radius: var(--shape-corner-medium);
  box-shadow: var(--elevation-sm);
}

.button-primary {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-radius: var(--shape-corner-small);
  transition: background var(--motion-duration-short) var(--motion-easing-standard);
}

/* Legacy aliases also work */
.legacy-component {
  color: var(--color-text-primary);
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
}
```

### Docker Builds

For cross-repo dependencies (when `@sphereon/theme-react` isn't published to npm), use the tarball approach:

```dockerfile
# Pack the dependency
COPY vdx/edk/idk/lib/conf/theme/react /theme-react
RUN cd /theme-react && npm pack

# Install in your app
COPY apps/portal /app
RUN cd /app && npm install /theme-react/sphereon-theme-react-0.1.0.tgz
```

---

## See Also

- [THEME-VDX.md](../../../../theme/docs/THEME-VDX.md) — EDK/VDX theme extension with backend integration
- [CORE-CONCEPTS.md](../../../core/api/public/docs/CORE-CONCEPTS.md) — IDK configuration hierarchy (App → Tenant → Principal)
- [CONFIGURATION.md](../../../core/api/public/docs/CONFIGURATION.md) — Property sources and config binding
