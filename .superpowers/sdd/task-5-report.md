# Task 5 report: VDX + EDK settings parity

**Status:** PASS  
**Date:** 2026-09-19  
**Primary changes:**
- `D:\git\VDX-infra\vdx\settings.gradle.kts`
- `D:\git\VDX-infra\vdx\edk\settings.gradle.kts`

## What changed

Mirrored Task 4 selective `IDK_LOCAL_PACKS` composite into nested VDX and EDK settings:

- Replaced monolith `includeBuild("edk/idk")` / `includeBuild("idk")` + `extractModuleNamesFromSettings(includeProject…)` for IDK with `includeIdkLocalPackBuilds`
- Helpers duplicated from `gradle/idk-local-packs.gradle.kts` (pack allow-list, parsers, substitutions)
- `IDK_CHECKOUT` env override (VDX default `edk/idk`; EDK default `idk`)
- Empty `IDK_LOCAL_PACKS` (with `USE_LOCAL_IDK` not false) → Maven artifacts, no monolith IDK composite
- EDK preserves parent-include behavior: when nested under VDX/infra, packs are still included for Kotlin JS discovery but substitutions are omitted (`registerSubstitutions = false`); forms profile still filters pack module substitutions via `formsIdkBuildProfileProjects`
- GBS `includeBuild` behavior left unchanged

## Smokes

Ran with `VDX_BUILD_DEVELOPMENT=1` (wrapper otherwise blocks direct Gradle).

| Smoke | Root | Env | Result | Evidence |
| --- | --- | --- | --- | --- |
| Unknown pack | infra | `USE_LOCAL_IDK=true`, `IDK_LOCAL_PACKS=nope` | **PASS** — fails with `Unknown IDK_LOCAL_PACKS entry 'nope'` (infra Task 4 path) | `task-5-unknown-pack-infra.txt` |
| Unknown pack | vdx | same + `IDK_CHECKOUT=<artifact-pilot>` | **PASS** — fails from `vdx/settings.gradle.kts` line ~233 | `task-5-unknown-pack-vdx.txt` |
| Unknown pack | edk | same + `IDK_CHECKOUT=<artifact-pilot>` | **PASS** — fails from `vdx/edk/settings.gradle.kts` line ~320 | `task-5-unknown-pack-edk.txt` |
| Selective core pack | vdx | `USE_LOCAL_IDK=true`, `IDK_LOCAL_PACKS=core`, `IDK_CHECKOUT=<artifact-pilot>`, `USE_LOCAL_EDK=false` | **PASS** — `BUILD SUCCESSFUL in 2m 55s`; `IDK pack source build: core (13 modules)`; no protocols pack | `task-5-core-pack-vdx.txt` |

Evidence paths (under artifact-pilot worktree `.superpowers/sdd/`):

- `task-5-unknown-pack-infra.txt`
- `task-5-unknown-pack-vdx.txt`
- `task-5-unknown-pack-edk.txt`
- `task-5-core-pack-vdx.txt`

## Commits

| Repo | Commit | Message |
| --- | --- | --- |
| **EDK** (`D:\git\VDX-infra\vdx\edk`) | `23befb1efeb67210d0133f38ca04f58bba6f76be` (`23befb1ef`) | `Use IDK_LOCAL_PACKS for selective IDK source composites.` |
| **VDX** (`D:\git\VDX-infra\vdx`) | `d25df73234cbaafc5f359bd1d9c804e8967f10d0` (`d25df7323`) | `Use IDK_LOCAL_PACKS for selective IDK source composites.` |
| VDX-infra | none | Nested-only; no infra file change this task |
| IDK | none | Helpers already in pilot worktree |

**Gitlink note:** VDX working tree shows `M edk` after the EDK commit. Parent gitlinks were **not** advanced (per brief / controller: advance only when the user asks).

## Concerns / follow-ups

1. **Default submodule still has no packs.** Without `IDK_CHECKOUT` pointing at artifact-pilot (or packs merged into the pinned IDK revision), `IDK_LOCAL_PACKS=core` fails with missing pack settings.
2. **Helper duplication.** VDX, EDK, and infra each embed a copy of `idk-local-packs.gradle.kts` logic; keep in sync with IDK Task 1 until a shared apply path is wired.
3. **Nested double-include.** When infra and VDX both run with the same `IDK_LOCAL_PACKS`, each settings script includes the pack builds in its own composite tree (infra siblings + VDX nested; EDK may nest packs again without substitutions). Acceptable for configure smoke; watch for name/path conflicts in heavier multi-root runs.
4. **Behavior change:** `USE_LOCAL_IDK=true` without `IDK_LOCAL_PACKS` no longer source-couples all IDK modules — Maven artifacts. Callers that relied on full monolith composite must set packs explicitly.
5. **GBS path.** GBS remains on the default submodule `…/gradle-build-support`, not `IDK_CHECKOUT`. Pack worktrees with divergent GBS need care.
6. **Unrelated dirty files** left uncommitted in EDK (`lib/oauth2/server/rest-tenant/…`) and VDX (`CreateAuthorizationServerCommandImpl.kt`).

## Checklist

- [x] Step 1: Mirror Task 4 logic in `vdx/settings.gradle.kts`
- [x] Step 2: Mirror in `vdx/edk/settings.gradle.kts`
- [x] Step 3: Unknown pack token fails (vdx + edk; infra regression)
- [x] Step 4: Commit in owning repos (vdx, edk); gitlinks not advanced
