# Task 1 Report: Shared `IDK_LOCAL_PACKS` settings helper

## Status

**DONE**

## Worktree and branch

- Path: `D:\git\VDX-infra\vdx\edk\idk\.worktrees\artifact-pilot`
- Branch: `pilot/artifact-mode`

## Steps executed (TDD)

### Step 1 — Parser smoke script

Created `gradle/idk-local-packs-parse.test.ps1` per brief (checks helper file exists, inline `Extract-PackModules` mirroring helper comment/regex behavior, asserts `lib-core-api-public` present and `lib-openid-oid4vp-holder-public` absent in core extract).

### Step 2 — Expected FAIL (helper missing)

```text
powershell -NoProfile -File gradle/idk-local-packs-parse.test.ps1
```

Result: **FAIL** with `missing idk-local-packs.gradle.kts` (exit code 1). Matches brief expectation.

### Step 3 — Helper implementation

Created `gradle/idk-local-packs.gradle.kts` with verbatim brief content:

- `IDK_PACK_DIRS` — six pack directory names
- `parseIdkLocalPacks()` — env `IDK_LOCAL_PACKS`, comma-split, trim, validate, distinct
- `extractIdkPackModuleNames(settingsFile)` — regex on non-comment lines for `includeLocal` / `includeMapped`
- `Settings.includeIdkLocalPackBuilds(idkRoot)` — conditional `includeBuild` with `idk-$pack` name and Maven→project substitution

No monolith or consumer `settings.gradle.kts` changes (Task 2+).

### Step 4 — Parser smoke PASS

```text
powershell -NoProfile -File gradle/idk-local-packs-parse.test.ps1
```

Result: **PASS** — `PASS extract core count=13` (exit code 0).

### Step 5 — Commit

```bash
git add gradle/idk-local-packs.gradle.kts gradle/idk-local-packs-parse.test.ps1
git commit -m "Add IDK_LOCAL_PACKS parsing and pack module extraction helper."
```

- **SHA:** `621e5b4ca`
- **Subject:** Add IDK_LOCAL_PACKS parsing and pack module extraction helper.
- **Files:** 2 added, 75 insertions
- Did **not** stage `.gradle-home-solo` or phase1l helper logs.

## Self-review

| Area | Assessment |
|------|------------|
| Brief fidelity | Kotlin helper matches brief verbatim; ps1 matches brief verbatim including unused `$root` assignment. |
| Contract sync | Ps1 uses a slightly looser regex (`include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,`) than Kotlin (requires second string arg). For current `core/settings.gradle.kts` lines, both agree (13 modules, expected assertions pass). |
| Gradle `apply(from)` | Not spike-tested in Task 1; brief allows duplicate-into-consumers if apply cannot expose top-level functions. Left for Task 2+ integration. |
| Scope | No Task 2 thinning; no vdx-build/coordinator used. |

## Concerns

- **Low:** Ps1 vs Kotlin regex divergence could matter if a pack settings file used a non-standard `includeLocal` call shape; smoke test only covers `core`.
- **Low:** Git reported CRLF→LF normalization warnings on add; repo line-ending policy may normalize on next touch.

## Test summary

Parser smoke: FAIL (missing helper) → PASS (`extract core count=13`).

## Artifacts

| File | Role |
|------|------|
| `gradle/idk-local-packs.gradle.kts` | Shared settings helper |
| `gradle/idk-local-packs-parse.test.ps1` | Parser contract smoke test |

## Task 1 review fix — GradleException for validation failures

**Finding:** Program global constraint requires unknown `IDK_LOCAL_PACKS` entries (and related pack validation failures) to surface as `GradleException`, not Kotlin `IllegalArgumentException` from `require { }`.

**Change:** In `gradle/idk-local-packs.gradle.kts`, replaced three `require` checks with explicit `throw org.gradle.api.GradleException("...")`:

- Unknown pack name in `parseIdkLocalPacks()`
- Missing pack `settings.gradle.kts` in `includeIdkLocalPackBuilds`
- Empty module list after `extractIdkPackModuleNames`

**Re-test:**

```text
powershell -NoProfile -File gradle/idk-local-packs-parse.test.ps1
```

Result: **PASS** — `PASS extract core count=13` (exit code 0).

**Commit:** `048dbc952` — Use GradleException for IDK_LOCAL_PACKS validation failures.
