# IDK thin root + hybrid pack composite

Date: 2026-09-19  
Status: implemented (pilot)  
Worktree: `vdx/edk/idk/.worktrees/artifact-pilot`  
Branch: `pilot/artifact-mode`

## Problem

Phase 1 placed modules under six pack roots (`core/`, `platform/`, `infra/`, `identity-security/`, `protocols/`, `wallet-lib/`) while keeping a monolith IDK `settings.gradle.kts` that `includeProject`s every module. That layout enables per-pack artifact publish, but:

- Opening the IDK root still configures the full module graph.
- VDX-infra / VDX / EDK `includeBuild("…/idk")` + substitute-every-`com.sphereon.idk:*` does the same when `USE_LOCAL_IDK` is on.
- Enterprise image builds that source-couple IDK therefore still pay full configuration cost; pack folders alone do not shrink it.

## Goals

1. **Default path:** resolve IDK as Maven artifacts — configuration proportional to the consumer (service / pack), not the whole IDK tree.
2. **Local cross-boundary edits:** optionally source-couple **only** the pack(s) under change (and later EDK/VDX slices) without configuring untouched packs.
3. **Keep one IDK checkout path** for tooling that expects `vdx/edk/idk` (thin root), while packs remain the real module owners.
4. Preserve Gradle **project / artifact names** (`:lib-crypto-core`, `com.sphereon.idk:lib-crypto-core`).

## Non-goals (this cutover)

- EDK/VDX pack subdivision.
- Changing enterprise Docker/Compose release pipelines beyond wiring them to artifact (or selective) IDK consumption.
- Non-JVM matrix, umbrella flag renames, deleting dual-mode flags.
- Requiring coordinator for pilot verification (direct `gradlew` / pack `-p` is fine).

## Decision

**Hybrid (option C):** artifact boundaries by default; selective source `includeBuild` for packs listed in an env allow-list.

Rejected:

- Always `includeBuild` all six packs from the thin root (same configure cost as monolith).
- Delete the IDK root entirely (breaks `includeBuild("idk")` and checkout conventions).

## Design

### 1. Thin IDK root

Monolith `settings.gradle.kts` stops owning the production module list (~246 `includeProject` entries for pack-owned modules).

Root retains:

- `rootProject.name = "Identity-Development-Kit"` (or keep name for continuity).
- `platform-version.properties` loading / version propagation helpers as needed by pack discovery scripts.
- Plugin management / GBS wiring shared by the checkout.
- Optional small includes that are **not** pack-owned (decide in implementation plan): `examples/`, `tests/`, `lib/all`, `lib/core/benchmarks` — either move into a pack, keep as root-only includes, or defer and exclude from the thin-root default.

Pack directories remain standalone Gradle roots with existing `includeLocal` / `includeMapped` and `idk.consume*AsArtifacts=true`.

### 2. Default: artifacts

| Consumer | Default IDK resolution |
| --- | --- |
| Pack building another pack’s API | Maven (`consume*AsArtifacts`) → `.worktree-m2` / Nexus |
| EDK / VDX / VDX-infra services | Maven (`USE_LOCAL_IDK=false` or equivalent) |
| Enterprise fatJar / image | Same — no IDK source `includeBuild` |

Success: enterprise/service configure does not evaluate pack `build.gradle.kts` files.

### 3. Selective source escape hatch

Introduce an allow-list env (name finalized in plan; working name **`IDK_LOCAL_PACKS`**):

```text
IDK_LOCAL_PACKS=protocols,wallet-lib
```

Semantics:

- Empty / unset → no IDK pack source builds (artifacts only) for consumers that honor the flag.
- Comma-separated pack directory names from `{core,platform,infra,identity-security,protocols,wallet-lib}`.
- For each listed pack, settings that participate in local composites:
  - `includeBuild("<idk>/<pack>")` with a stable included-build name, and
  - `dependencySubstitution` only for modules published by that pack (derived from that pack’s `settings.gradle.kts` `includeLocal` / `includeMapped` names), mapping `com.sphereon.idk:<module>` → `project(":<module>")`.
- Unlisted packs are not `includeBuild`’d.

Upstream settings to migrate off monolith substitute-all:

- `VDX-infra/settings.gradle.kts` (`includeBuild("vdx/edk/idk")` + extract all modules)
- `vdx/settings.gradle.kts`
- `vdx/edk/settings.gradle.kts`

Thin IDK root may optionally mirror the same allow-list for developers who open the IDK root and want a multi-pack composite without VDX-infra — still never wire all packs unless listed.

### 4. Future VDX ↔ EDK ↔ IDK local develop

Same pattern scales:

- Default: each product consumes lower products as artifacts.
- Opt-in: `IDK_LOCAL_PACKS=…`, later `EDK_LOCAL_*=…` / selective VDX includes.
- Crossing a boundary means listing the pack/repo under edit, not flipping “couple entire IDK.”

### 5. Verification (pilot)

| Check | Expectation |
| --- | --- |
| `gradlew -p core compileKotlinJvm` | Configures core pack only (+ artifact deps) |
| Service/enterprise-style resolve with IDK artifacts | No IDK pack project configuration |
| `IDK_LOCAL_PACKS=protocols` + a consumer that substitutes | Only `protocols` included from source |
| Full allow-list of all six packs | Allowed but discouraged; documents cost |

## Migration notes

- Do not leave a dual path that still `includeBuild`s the thin root and expects every module as a project of that build.
- Pack publish to `.worktree-m2` remains the prerequisite for artifact-default local stacks (already proven in Phase 1).
- Wallet AGPL submodule stays under `wallet/`; `wallet-lib` remains the Apache pack root. Selective include of `wallet-lib` does not automatically source-couple the submodule’s extra roots unless listed via existing `includeMapped` inside that pack.

## Open points for the implementation plan

1. Exact env name and parsing (empty vs `*` vs invalid tokens).
2. Whether thin root itself ever `includeBuild`s packs by default for convenience, or only documents `-p <pack>`.
3. Placement of `examples/`, `tests/`, `lib/all`, leftover `lib/core/benchmarks`.
4. Helper to extract module names from a **pack** `settings.gradle.kts` (reuse/adapt `extractModuleNamesFromSettings`).
5. Order of landing: IDK thin root first vs VDX-infra substitution change first (prefer IDK thin root + pack smokes, then flip infra composite).

## References

- Layout: `docs/superpowers/plans/2026-09-19-packs-git-mv-layout.md`
- Progress: `phase1-progress.md` / `phase1l-gates.md`
- Current full substitute: `VDX-infra/settings.gradle.kts` (`USE_LOCAL_IDK` → `includeBuild("vdx/edk/idk")`)
