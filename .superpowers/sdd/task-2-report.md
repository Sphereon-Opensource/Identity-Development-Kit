# Task 2 Report: Thin IDK root — remove pack `includeProject` graph

## Status

**DONE** (with known follow-up: thin-root `projects` configures examples that still use type-safe `projects.*` accessors)

## Worktree and branch

- Path: `D:\git\VDX-infra\vdx\edk\idk\.worktrees\artifact-pilot`
- Branch: `pilot/artifact-mode`

## Steps executed

### Step 1 — Snapshot current root project count

```powershell
cd <idk-worktree>
.\gradlew.bat projects --no-configuration-cache -Dkmp.targets=jvm 2>&1 | Tee-Object -FilePath phase1m-root-projects-before.txt
```

**Result:** Configuration ran across the large monolith graph and configured pack modules including `:lib-core-api-public` and `:lib-openid-oid4vp-holder-public` (evidence in `phase1m-root-projects-before.txt`).

**PowerShell caveat:** Unquoted `-Dkmp.targets=jvm` was split by PowerShell; Gradle treated `.targets=jvm` as a task name (`Task '.targets=jvm' not found`), so the before run ended **BUILD FAILED** after configuring projects. Evidence of the pack graph is still present in the before log. Later steps used `"-Dkmp.targets=jvm"`.

### Step 2 — Delete pack-owned `includeProject` calls

In `settings.gradle.kts`:

- Removed every `includeProject(...)` whose path started with `core/`, `platform/`, `infra/`, `identity-security/`, `protocols/`, `wallet-lib/`, or `wallet/` (including `idk-bom` at `platform/versions/idk-bom`).
- Kept examples and integration-test includes exactly as listed in the brief.
- Kept the `BUILD_XCFRAMEWORKS` / `lib-all` conditional block.
- **`lib-core-benchmarks`:** `core/lib/core/benchmarks/build.gradle.kts` does **not** exist (only orphan `lib/core/benchmarks/`). Include **removed** per brief.

### Step 3 — Wire optional selective packs on the thin root

Duplicated Task 1 helpers into `settings.gradle.kts` (with `// keep in sync with gradle/idk-local-packs.gradle.kts`) because `apply(from)` does not export top-level functions into the caller settings script:

- `IDK_PACK_DIRS`
- `parseIdkLocalPacks()`
- `extractIdkPackModuleNames(settingsFile)`
- `Settings.includeIdkLocalPackBuilds(idkRoot)`

Then at end of settings:

```kotlin
// Selective multi-pack source composite when developing from the IDK checkout root.
includeIdkLocalPackBuilds(settings.rootDir)
```

With empty `IDK_LOCAL_PACKS`, settings prints:
`==> IDK pack source builds: none (IDK_LOCAL_PACKS empty; using Maven artifacts for IDK modules)`.

### Step 4 — Verify thin root project list

```powershell
$env:IDK_LOCAL_PACKS = ""
.\gradlew.bat projects --no-configuration-cache "-Dkmp.targets=jvm" 2>&1 | Tee-Object phase1m-root-projects-after.txt
Select-String phase1m-root-projects-after.txt -Pattern "lib-core-api-public|lib-openid-oid4vp-holder-public"
```

**Select-String:** **no matches** for those pack modules (pass).

**Build outcome:** **BUILD FAILED** while configuring `:examples-service-byo-oidc` — unresolved type-safe accessors (`projects.libOauth2JwtValidationApi`, etc.) because pack modules are no longer projects of `Identity-Development-Kit`. Thinning itself is confirmed: after log only configures root + examples (then fails), never `:lib-core-api-public`.

```powershell
.\gradlew.bat -p core projects --no-configuration-cache "-Dkmp.targets=jvm" 2>&1 | Select-String "lib-core-api-public"
```

**Result:** **PASS** — match for `:lib-core-api-public`; `BUILD SUCCESSFUL` (`phase1m-core-projects.txt`).

### Step 5 — Commit

```bash
git add settings.gradle.kts
git commit -m "Thin IDK root: drop pack includeProject graph; honor IDK_LOCAL_PACKS."
```

- **SHA:** `d3398e643` (`d3398e6430841b75766f76d1aeabf69daf037cad`)
- **Subject:** Thin IDK root: drop pack includeProject graph; honor IDK_LOCAL_PACKS.
- **Files:** `settings.gradle.kts` only — 72 insertions, 354 deletions
- Did **not** stage phase1m logs, `.gradle-home-solo`, or `.superpowers/`

## Self-review

| Area | Assessment |
|------|------------|
| Brief fidelity | Pack `includeProject` graph removed; examples/tests/`lib-all` kept; `includeIdkLocalPackBuilds` wired; benchmarks removed (no `core/lib/core/benchmarks`). |
| Helper wiring | Functions duplicated into settings with sync comment (allowed by Task 1/2 briefs). |
| Verification | Pack modules absent from thin-root listing signals; core pack still lists `:lib-core-api-public`. |
| Scope | No VDX-infra / coordinator / vdx-build; no full IDK `compileKotlinJvm`. |

## Concerns

- **Medium:** Thin-root `gradlew projects` (and any configure of kept examples/tests) fails while those modules still depend on type-safe `projects.lib*` / `projects.ktor*` accessors for modules that now live only in packs / Maven. Follow-up should migrate examples/tests to Maven coordinates (optionally substituted via `IDK_LOCAL_PACKS`) or stop registering them on the thin root until that migration lands.
- **Low:** Before snapshot used unquoted `-Dkmp.targets=jvm` under PowerShell; quote the `-D` property on Windows.
- **Low:** `formsBuildProfileProjects` still lists pack module names that are no longer included at the root; harmless filter dead-weight until cleaned.

## Test summary

Thin-root Select-String: no `lib-core-api-public` / `lib-openid-oid4vp-holder-public`; `gradlew -p core projects` SUCCESS with `:lib-core-api-public`; thin-root `projects` configures only examples then fails on `projects.*` accessors.

## Artifacts

| File | Role |
|------|------|
| `settings.gradle.kts` | Thin IDK root + duplicated `IDK_LOCAL_PACKS` helpers |
| `phase1m-root-projects-before.txt` | Pre-thin configure log (pack modules present; PowerShell `-D` quirk) |
| `phase1m-root-projects-after.txt` | Post-thin configure log (no pack modules; examples accessor failure) |
| `phase1m-core-projects.txt` | `-p core projects` SUCCESS evidence |
