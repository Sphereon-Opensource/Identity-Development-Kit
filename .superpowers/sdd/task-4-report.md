# Task 4 report: VDX-infra selective IDK composite

**Status:** PASS  
**Date:** 2026-09-19  
**Primary change:** `D:\git\VDX-infra\settings.gradle.kts`  
**Commit (VDX-infra):** `68f95f8e`  

## What changed

Replaced monolith `includeBuild("vdx/edk/idk")` + `extractModuleNamesFromSettings(includeProject…)` for IDK with:

- `IDK_LOCAL_PACKS`-driven selective pack `includeBuild`s (`includeIdkLocalPackBuilds`)
- Helpers duplicated from `gradle/idk-local-packs.gradle.kts` (pack allow-list, parsers, substitutions)
- `IDK_CHECKOUT` env override for pack-layout worktrees (main submodule still lacks pack dirs)
- Empty `IDK_LOCAL_PACKS` (with `USE_LOCAL_IDK` not false) → Maven artifacts, no monolith IDK composite
- `USE_LOCAL_IDK=false` → same disabled path as before
- GBS `includeBuild` behavior left unchanged (still from `vdx/edk/idk/gradle-build-support`)

## Smokes

Ran with `VDX_BUILD_DEVELOPMENT=1` (brief: no coordinator; wrapper otherwise blocks direct Gradle). Used light `:shared:tasks --all` instead of `:enterprise-platform` (`:shared` depends on `com.sphereon.idk`).

| Smoke | Env | Result | Evidence |
| --- | --- | --- | --- |
| Artifact-default | `USE_LOCAL_IDK=false`, `USE_LOCAL_EDK=false` | **PASS** — `BUILD SUCCESSFUL in 1m 2s`; log shows `IDK Composite Build: DISABLED (USE_LOCAL_IDK=false…)`; no pack include | `phase1m-infra-idk-artifacts.txt` |
| Selective core pack | `USE_LOCAL_IDK=true`, `IDK_LOCAL_PACKS=core`, `IDK_CHECKOUT=<artifact-pilot>`, `USE_LOCAL_EDK=false` | **PASS** — `BUILD SUCCESSFUL in 37s`; `IDK pack source build: core (13 modules)`; no `pack source build: protocols` | `phase1m-infra-idk-core-pack.txt` |

Evidence paths (under artifact-pilot worktree):

- `phase1m-infra-idk-artifacts.txt`
- `phase1m-infra-idk-core-pack.txt`

## Commits

| Repo | Commit | Message |
| --- | --- | --- |
| **VDX-infra** (`D:\git\VDX-infra`) | `68f95f8e5132fc02d356c5dbbe659f6cc51b16d9` (`68f95f8e`) | `Use IDK_LOCAL_PACKS for selective IDK source composites.` |
| IDK (artifact-pilot / submodule) | none | Infra-only change; helpers already in pilot worktree |

## Concerns / follow-ups

1. **Nested VDX still monolith-includes IDK.** When infra `includeBuild("vdx")` runs with `USE_LOCAL_IDK=true`, `vdx/settings.gradle.kts` still does `includeBuild("edk/idk")` against the **submodule** path (not `IDK_CHECKOUT`). Core-pack smoke log shows both infra pack include and VDX’s `IDK Composite Build: ENABLED (using local edk/idk/ sources)`. True end-to-end selective composites need a matching VDX (and possibly EDK) settings change in a later task.
2. **Default submodule has no packs.** Without `IDK_CHECKOUT` pointing at artifact-pilot (or packs merged into the gitlink), `IDK_LOCAL_PACKS=core` fails with missing pack settings. Blocked on IDK gitlink bump until packs land on the revision infra pins.
3. **Helper duplication.** Infra embeds a copy of `idk-local-packs.gradle.kts` logic; keep in sync with IDK Task 1 until a shared apply path is wired.
4. **GBS path.** GBS remains on submodule `vdx/edk/idk/gradle-build-support`, not `IDK_CHECKOUT`. Fine for this smoke; pack worktrees with divergent GBS need care.
5. **Behavior change:** `USE_LOCAL_IDK=true` without `IDK_LOCAL_PACKS` no longer source-couples all IDK modules — it uses Maven. Callers that relied on full monolith composite must set packs explicitly.

## Checklist

- [x] Step 1: Replace IDK monolith `extractModuleNamesFromSettings` usage
- [x] Step 2: Artifact-default configure smoke
- [x] Step 3: Selective pack smoke (`IDK_CHECKOUT` + `IDK_LOCAL_PACKS=core`)
- [x] Step 4: Commit in VDX-infra
