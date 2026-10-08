# IDK Thin Root + Hybrid Pack Composite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire the IDK monolith module `includeProject` graph so default and enterprise builds resolve IDK as Maven artifacts, while `IDK_LOCAL_PACKS` selectively `includeBuild`s only the pack roots under edit.

**Architecture:** Pack directories remain standalone Gradle roots with `consume*AsArtifacts=true`. The IDK checkout root becomes a thin settings entry (shared pluginManagement / GBS / optional examples-tests). VDX-infra, VDX, and EDK stop `includeBuild`ing the whole IDK monolith; they either use artifacts (`USE_LOCAL_IDK=false` or empty pack list) or substitute modules from only the packs named in `IDK_LOCAL_PACKS`.

**Tech Stack:** Gradle 9 settings composites, existing pack `settings.gradle.kts` (`includeLocal` / `includeMapped`), Maven (`.worktree-m2` / Nexus), env `IDK_LOCAL_PACKS` + existing `USE_LOCAL_IDK`.

## Global Constraints

- Work in IDK worktree `vdx/edk/idk/.worktrees/artifact-pilot` on `pilot/artifact-mode` for IDK files; edit `VDX-infra/settings.gradle.kts`, `vdx/settings.gradle.kts`, and `vdx/edk/settings.gradle.kts` in the main infra checkout when flipping composites (same machine, paths relative to that checkout’s `vdx/edk/idk` → use worktree path or merge pilot first — prefer implementing composite helpers in the worktree, then point infra at the worktree only if that is how the pilot is mounted; otherwise land IDK commits then update infra against the worktree submodule path the user uses).
- Do **not** use the build coordinator for this pilot (user directive); use direct `gradlew` / `gradlew -p <pack>`.
- Do not `includeBuild` all six packs unless `IDK_LOCAL_PACKS` lists them.
- Preserve Gradle project and Maven names (`:lib-crypto-core`, `com.sphereon.idk:lib-crypto-core`).
- Known pack directory tokens (exact): `core`, `platform`, `infra`, `identity-security`, `protocols`, `wallet-lib`.
- Env `IDK_LOCAL_PACKS`: comma-separated pack tokens; trim whitespace; case-sensitive lowercase as above; unknown token → `GradleException`; empty/unset → no IDK pack source `includeBuild`.
- `USE_LOCAL_IDK=false` → never source-couple IDK packs (artifacts).
- `USE_LOCAL_IDK` true/default with empty `IDK_LOCAL_PACKS` → **no** IDK pack `includeBuild` (artifacts), print one clear `println` that selective packs are required for source coupling (avoids accidental full-tree configure).
- No `IDK_LOCAL_PACKS=*` in v1.
- Spec: `docs/superpowers/specs/2026-09-19-idk-thin-root-hybrid-packs-design.md`.

---

## File map

| File | Responsibility |
| --- | --- |
| `gradle/idk-local-packs.gradle.kts` (new, under IDK root) | Shared parsers + `includeIdkLocalPackBuilds(idkRoot)` for Settings |
| `settings.gradle.kts` (IDK thin root) | Drop pack `includeProject`s; keep pluginManagement/GBS; keep examples/tests/lib-all gating; apply local-packs helper only when `IDK_LOCAL_PACKS` set |
| `core|platform|infra|identity-security|protocols|wallet-lib/settings.gradle.kts` | Unchanged dual-mode flags (already artifact-default) |
| `VDX-infra/settings.gradle.kts` | Replace monolith IDK `includeBuild` + `includeProject` extractor with pack allow-list composite |
| `vdx/settings.gradle.kts` | Same |
| `vdx/edk/settings.gradle.kts` | Same |
| `phase1m-gates.md` / `phase1-progress.md` | Gates + Next pointer |
| `docs/superpowers/plans/2026-09-19-idk-thin-root-hybrid-packs.md` | This plan |

---

### Task 1: Shared `IDK_LOCAL_PACKS` settings helper

**Files:**
- Create: `gradle/idk-local-packs.gradle.kts` (IDK worktree root)
- Create: `gradle/idk-local-packs-parse.test.ps1` (parser smoke; run with PowerShell)
- Test: run the ps1 script

**Interfaces:**
- Consumes: env `IDK_LOCAL_PACKS`, pack `settings.gradle.kts` files under `<idkRoot>/<pack>/`
- Produces: Settings-script API used by later tasks:
  - `val IDK_PACK_DIRS: Set<String>`
  - `fun parseIdkLocalPacks(): List<String>`
  - `fun extractIdkPackModuleNames(settingsFile: File): List<String>`
  - `fun Settings.includeIdkLocalPackBuilds(idkRoot: File)` — for each parsed pack, `includeBuild(idkRoot.resolve(pack)) { name = "idk-$pack"; dependencySubstitution { modules.forEach { substitute(module("com.sphereon.idk:$it")).using(project(":$it")) } } }`

- [ ] **Step 1: Write the parser smoke script (fail first if helper missing)**

Create `gradle/idk-local-packs-parse.test.ps1`:

```powershell
$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not (Test-Path "$PSScriptRoot/idk-local-packs.gradle.kts")) {
  throw "missing idk-local-packs.gradle.kts"
}
# Inline expected regex behavior matching the helper (keep in sync):
function Extract-PackModules([string]$settingsPath) {
  Get-Content $settingsPath | Where-Object {
    $t = $_.Trim()
    -not ($t.StartsWith("//") -or $t.StartsWith("/*") -or $t.StartsWith("*"))
  } | ForEach-Object {
    if ($_ -match 'include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,') { $Matches[1] }
  }
}
$core = Extract-PackModules "$PSScriptRoot/../core/settings.gradle.kts"
if ($core -notcontains "lib-core-api-public") { throw "expected lib-core-api-public in core extract" }
if ($core -contains "lib-openid-oid4vp-holder-public") { throw "core must not list protocols modules" }
Write-Output "PASS extract core count=$($core.Count)"
```

Fix `$root` / paths so `$PSScriptRoot` is `…/idk/gradle` and core settings is `…/idk/core/settings.gradle.kts` (`"$PSScriptRoot/../core/settings.gradle.kts"`).

- [ ] **Step 2: Run script — expect FAIL (helper file missing) or FAIL extract if you create script before helper**

Run (from IDK worktree):

```powershell
powershell -NoProfile -File gradle/idk-local-packs-parse.test.ps1
```

Expected: FAIL with `missing idk-local-packs.gradle.kts` if Step 1 ran before Step 3.

- [ ] **Step 3: Implement `gradle/idk-local-packs.gradle.kts`**

```kotlin
import java.io.File

val IDK_PACK_DIRS: Set<String> =
    setOf("core", "platform", "infra", "identity-security", "protocols", "wallet-lib")

fun parseIdkLocalPacks(): List<String> {
    val raw = System.getenv("IDK_LOCAL_PACKS")?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()
    return raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .also { packs ->
            packs.forEach { pack ->
                require(pack in IDK_PACK_DIRS) {
                    "Unknown IDK_LOCAL_PACKS entry '$pack'. Allowed: ${IDK_PACK_DIRS.joinToString()}"
                }
            }
        }
        .distinct()
}

fun extractIdkPackModuleNames(settingsFile: File): List<String> {
    if (!settingsFile.isFile) return emptyList()
    val localOrMapped =
        Regex("""include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,\s*"[^"]+"\s*\)""")
    return settingsFile.readLines()
        .filter { line ->
            val t = line.trim()
            !t.startsWith("//") && !t.startsWith("/*") && !t.startsWith("*")
        }
        .mapNotNull { line -> localOrMapped.find(line)?.groupValues?.get(1) }
        .distinct()
}

fun Settings.includeIdkLocalPackBuilds(idkRoot: File) {
    val packs = parseIdkLocalPacks()
    if (packs.isEmpty()) {
        println("==> IDK pack source builds: none (IDK_LOCAL_PACKS empty; using Maven artifacts for IDK modules)")
        return
    }
    packs.forEach { pack ->
        val packRoot = idkRoot.resolve(pack)
        val packSettings = packRoot.resolve("settings.gradle.kts")
        require(packSettings.isFile) { "Missing pack settings: ${packSettings.absolutePath}" }
        val modules = extractIdkPackModuleNames(packSettings)
        require(modules.isNotEmpty()) { "No includeLocal/includeMapped modules in ${packSettings.absolutePath}" }
        includeBuild(packRoot) {
            name = "idk-$pack"
            dependencySubstitution {
                modules.forEach { moduleName ->
                    substitute(module("com.sphereon.idk:$moduleName")).using(project(":$moduleName"))
                }
            }
        }
        println("==> IDK pack source build: $pack (${modules.size} modules)")
    }
}
```

Note: in a `settings.gradle.kts` applied via `apply(from = …)`, prefer inlining these functions into each consumer settings file **or** use `apply(from = rootDir.resolve("gradle/idk-local-packs.gradle.kts"))` only where `rootDir` is the IDK root. If `apply(from)` cannot define top-level functions for the caller in this Gradle version, **copy the three functions verbatim** into each of the four settings files (IDK thin root, VDX-infra, vdx, edk) and keep the ps1 extract test as the contract. Prefer one shared apply if it works in a 5-minute spike; otherwise duplicate and add a comment `// keep in sync with gradle/idk-local-packs.gradle.kts`.

- [ ] **Step 4: Re-run parser smoke**

```powershell
powershell -NoProfile -File gradle/idk-local-packs-parse.test.ps1
```

Expected: `PASS extract core count=…`

- [ ] **Step 5: Commit**

```bash
git add gradle/idk-local-packs.gradle.kts gradle/idk-local-packs-parse.test.ps1
git commit -m "Add IDK_LOCAL_PACKS parsing and pack module extraction helper."
```

---

### Task 2: Thin IDK root — remove pack `includeProject` graph

**Files:**
- Modify: `settings.gradle.kts` (IDK worktree)
- Modify: move or fix `lib/core/benchmarks` include if present (`includeProject("lib-core-benchmarks", "lib/core/benchmarks")` → `core/lib/core/benchmarks` if that tree exists, else delete the include)
- Keep: `examples/*`, `tests/*`, conditional `lib/all`
- Test: `gradlew projects` at IDK root; `gradlew -p core projects`

**Interfaces:**
- Consumes: Task 1 helper / duplicated functions
- Produces: Thin root that does not register pack modules as projects of `Identity-Development-Kit`

- [ ] **Step 1: Snapshot current root project count**

```powershell
cd <idk-worktree>
.\gradlew.bat projects --no-configuration-cache -Dkmp.targets=jvm 2>&1 | Tee-Object -FilePath phase1m-root-projects-before.txt
```

Expected: large list including `:lib-core-api-public`, `:lib-openid-oid4vp-holder-public`, etc.

- [ ] **Step 2: Delete pack-owned `includeProject` calls**

In `settings.gradle.kts`, remove every `includeProject(...)` whose path starts with `core/`, `platform/`, `infra/`, `identity-security/`, `protocols/`, `wallet-lib/`, or `wallet/` (wallet submodule modules are owned by `wallet-lib` via `includeMapped` when that pack builds).

Keep:

```kotlin
includeProject("examples-oid4vc-webapp-server", "examples/oid4vc/webapp/server")
includeProject("examples-service-byo-oidc", "examples/service-byo-oidc")
includeProject("tests-oid4vc-integration", "tests/oid4vc-integration")
includeProject("tests-oauth2-integration", "tests/oauth2-integration")
includeProject("tests-oidf-conformance-oidc-op", "tests/oidf/conformance/oidc/op")
includeProject("tests-oidf-conformance-oid4vc", "tests/oidf/conformance/oid4vc")
includeProject("tests-oidf-conformance-oid4vc-services", "tests/oidf/conformance/oid4vc-services")
includeProject("tests-oidf-conformance-oid4vc-wallet", "tests/oidf/conformance/oid4vc-wallet")
```

and the existing `BUILD_XCFRAMEWORKS` / `lib-all` block.

For `lib-core-benchmarks`: if `core/lib/core/benchmarks/build.gradle.kts` exists, change path to `core/lib/core/benchmarks`; else remove the include.

- [ ] **Step 3: Wire optional selective packs on the thin root**

At end of `settings.gradle.kts` (after pluginManagement / dependencyResolutionManagement as appropriate — `includeBuild` for packs must be outside `pluginManagement`):

```kotlin
// Selective multi-pack source composite when developing from the IDK checkout root.
includeIdkLocalPackBuilds(settings.rootDir)
```

If functions were not applied globally, paste the Task 1 functions above this call.

- [ ] **Step 4: Verify thin root project list**

```powershell
$env:IDK_LOCAL_PACKS = ""
.\gradlew.bat projects --no-configuration-cache -Dkmp.targets=jvm 2>&1 | Tee-Object phase1m-root-projects-after.txt
Select-String phase1m-root-projects-after.txt -Pattern "lib-core-api-public|lib-openid-oid4vp-holder-public"
```

Expected: **no** matches for those pack modules. Examples/tests may remain.

```powershell
.\gradlew.bat -p core projects --no-configuration-cache -Dkmp.targets=jvm 2>&1 | Select-String "lib-core-api-public"
```

Expected: match for `:lib-core-api-public`.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts
git commit -m "Thin IDK root: drop pack includeProject graph; honor IDK_LOCAL_PACKS."
```

---

### Task 3: Pack smoke after thin root

**Files:**
- Create: `phase1m-gates.md` (partial)
- Test: compile smokes

**Interfaces:**
- Consumes: thin root + existing pack settings
- Produces: gate evidence that packs still build standalone

- [ ] **Step 1: Core compile**

```powershell
cd <idk-worktree>
$env:GRADLE_USER_HOME = "$pwd\.gradle-home-solo"
.\gradlew.bat -p core compileKotlinJvm --no-configuration-cache --console=plain -Dkmp.targets=jvm --max-workers=4
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Protocols compile (artifacts for lower packs)**

```powershell
.\gradlew.bat -p protocols compileKotlinJvm --no-configuration-cache --console=plain -Dkmp.targets=jvm --max-workers=4
```

Expected: `BUILD SUCCESSFUL` (uses `.worktree-m2` / Nexus for lower layers). If resolve fails, publish missing lower packs to `.worktree-m2` using existing Phase 1 publish scripts, then retry — do not re-expand monolith includes.

- [ ] **Step 3: Write gates rows**

Create/update `phase1m-gates.md` with PASS rows for thin root project list, core smoke, protocols smoke.

- [ ] **Step 4: Commit**

```bash
git add phase1m-gates.md
git commit -m "Record phase 1m gates for thin IDK root pack smokes."
```

---

### Task 4: VDX-infra selective IDK composite

**Files:**
- Modify: `D:/git/VDX-infra/settings.gradle.kts` (functions ~250–360 and IDK `includeBuild` block)
- Test: `USE_LOCAL_IDK=false` configure of a small task; `IDK_LOCAL_PACKS=core` includeBuild smoke

**Interfaces:**
- Consumes: Task 1 parsers (duplicated or applied from IDK path `vdx/edk/idk/gradle/idk-local-packs.gradle.kts` — if pilot worktree is not the submodule path, either merge pilot to the submodule checkout used by infra **or** temporarily set path to the worktree; prefer merging/cherry-picking Tasks 1–3 into the IDK revision infra points at before this task)
- Produces: infra no longer substitutes every IDK module from monolith settings

- [ ] **Step 1: Replace `extractModuleNamesFromSettings` usage for IDK**

Remove reliance on parsing IDK root `includeProject` for IDK composites.

Replace the block:

```kotlin
includeBuild("vdx/edk/idk") {
    name = "Identity-Development-Kit"
    dependencySubstitution {
        idkModules.forEach { … }
    }
}
```

with logic:

```kotlin
if (useIdkComposite) {
    val idkRoot = file("vdx/edk/idk")
    // paste or apply parseIdkLocalPacks / extractIdkPackModuleNames / includeIdkLocalPackBuilds
    val packs = parseIdkLocalPacks()
    if (packs.isEmpty()) {
        println("==> IDK Composite Build: DISABLED (set IDK_LOCAL_PACKS=core,protocols,… for selective source; else Maven artifacts)")
    } else {
        includeIdkLocalPackBuilds(idkRoot)
    }
} else {
    println("==> IDK Composite Build: DISABLED (USE_LOCAL_IDK=false, using Maven artifacts)")
}
```

Keep GBS `includeBuild` behavior unchanged.

- [ ] **Step 2: Artifact-default configure smoke**

```powershell
cd D:\git\VDX-infra
$env:USE_LOCAL_IDK = "false"
.\gradlew.bat :enterprise-platform:tasks --all --no-configuration-cache 2>&1 | Tee-Object phase1m-infra-idk-artifacts.txt
```

Expected: completes without configuring `vdx/edk/idk/core/...` projects (no `Configure project :lib-core-api-public` from an included IDK pack). Exact task may be adjusted to a lighter existing project if `enterprise-platform` is too heavy — use any infra project that depends on `com.sphereon.idk` coordinates.

- [ ] **Step 3: Selective pack smoke**

```powershell
$env:USE_LOCAL_IDK = "true"
$env:IDK_LOCAL_PACKS = "core"
.\gradlew.bat :enterprise-platform:tasks --all --no-configuration-cache 2>&1 | Tee-Object phase1m-infra-idk-core-pack.txt
Select-String phase1m-infra-idk-core-pack.txt -Pattern "IDK pack source build: core"
```

Expected: println present; protocols pack not included.

- [ ] **Step 4: Commit in VDX-infra repo**

```bash
cd D:/git/VDX-infra
git add settings.gradle.kts
git commit -m "Use IDK_LOCAL_PACKS for selective IDK source composites."
```

(If infra must not commit until IDK is merged, leave the infra change in the worktree and note it in `phase1m-gates.md` as blocked on IDK gitlink — still implement and verify locally.)

---

### Task 5: VDX + EDK settings parity

**Files:**
- Modify: `vdx/settings.gradle.kts` (IDK `includeBuild("edk/idk")` block)
- Modify: `vdx/edk/settings.gradle.kts` (IDK `includeBuild("idk")` block)
- Test: same env semantics as Task 4 from those roots if routinely used

**Interfaces:**
- Consumes: same helper contract as Task 4
- Produces: no remaining monolith-wide IDK module substitute lists

- [ ] **Step 1: Mirror Task 4 logic in `vdx/settings.gradle.kts`**

Replace `extractModuleNamesFromSettings(idkSettingsFile)` + single `includeBuild("edk/idk")` module loop with `includeIdkLocalPackBuilds(file("edk/idk"))` gated by `useIdkComposite` / empty packs message.

- [ ] **Step 2: Mirror in `vdx/edk/settings.gradle.kts`**

`includeIdkLocalPackBuilds(file("idk"))`.

- [ ] **Step 3: Unknown pack token fails**

```powershell
$env:USE_LOCAL_IDK = "true"
$env:IDK_LOCAL_PACKS = "nope"
# from vdx or infra:
.\gradlew.bat help --no-configuration-cache
```

Expected: configuration fails with `Unknown IDK_LOCAL_PACKS entry 'nope'`.

- [ ] **Step 4: Commit** each repo that owns the file (`vdx`, `edk` via their git repos / gitlinks as the checkout normally requires). If nested gitlinks are dirty, commit inside `vdx/edk` and `vdx` per existing Sphereon submodule practice, then advance gitlinks from the parent only when the user asks.

---

### Task 6: Docs + progress closeout

**Files:**
- Modify: `phase1-progress.md` — mark Phase 1m; Next → EDK artifact pilot / selective packs in daily use
- Modify: `phase1m-gates.md` — full gate table
- Modify: `docs/superpowers/specs/2026-09-19-idk-thin-root-hybrid-packs-design.md` — status `implemented` (pilot)
- Test: doc-only review

- [ ] **Step 1: Complete gate table**

| Gate | Expectation |
| --- | --- |
| M1 Helper extract | ps1 PASS |
| M2 Thin root | pack modules absent from root `projects` |
| M3 Core `-p` smoke | BUILD SUCCESSFUL |
| M4 Protocols `-p` smoke | BUILD SUCCESSFUL |
| M5 Infra artifacts | USE_LOCAL_IDK=false no pack configure |
| M6 Infra selective | IDK_LOCAL_PACKS=core prints pack include |
| M7 Invalid pack | configuration fails |

- [ ] **Step 2: Update progress Next**

```markdown
## Also done (Phase 1m — thin root + hybrid packs)
- Spec: docs/superpowers/specs/2026-09-19-idk-thin-root-hybrid-packs-design.md
- Plan: docs/superpowers/plans/2026-09-19-idk-thin-root-hybrid-packs.md
- Gates: phase1m-gates.md

## Next
- EDK artifact-mode pilot (authorized separately)
- Habituate IDK_LOCAL_PACKS in local VDX-infra workflows; keep enterprise on USE_LOCAL_IDK=false
```

- [ ] **Step 3: Commit**

```bash
git add phase1-progress.md phase1m-gates.md docs/superpowers/specs/2026-09-19-idk-thin-root-hybrid-packs-design.md
git commit -m "Close phase 1m thin-root hybrid pack gates and progress."
```

---

## Spec coverage self-check

| Spec requirement | Task |
| --- | --- |
| Thin root drops pack `includeProject` | Task 2 |
| Packs stay standalone artifact-default | Task 3 (no flag changes required) |
| `IDK_LOCAL_PACKS` selective includeBuild | Tasks 1, 2, 4, 5 |
| Enterprise/default artifacts | Task 4 Step 2 |
| Migrate VDX-infra / vdx / edk off monolith substitute | Tasks 4–5 |
| Verification table | Tasks 3, 6 |
| examples/tests retained at root | Task 2 |
| No coordinator | Global constraints |

## Placeholder scan

None intentional; infra gitlink timing called out explicitly in Task 4.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-19-idk-thin-root-hybrid-packs.md`. Two execution options:

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  

**2. Inline Execution** — execute tasks in this session with checkpoints  

Which approach?
