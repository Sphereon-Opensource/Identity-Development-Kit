# IDK Packs Layout (`git mv`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace overlay/symlink compounds with real pack trees under ``, moving owned modules via `git mv` only, while keeping Gradle project names unchanged.

**Architecture:** Each publishable pack is a standalone Gradle root under `<pack>/`. Modules live physically under that pack (preserving their relative path suffix). Pack `settings.gradle.kts` includes them from the pack root (no `../../lib` overlays). The monolith IDK root continues to include the same project names with updated `projectDir` paths. Artifact dual-mode flags are unchanged.

**Tech Stack:** Git (`git mv` only for tracked moves), Gradle KMP settings/`includeMapped`, existing pack publish/smoke scripts.

## Global Constraints

- Moves: **`git mv` only** — never plain FS moves for tracked paths.
- Top folder: `` → **``**.
- Pack dir rename: `infrastructure` → **`infra`** (`infra`).
- **Gradle project names stay unchanged** (e.g. `:lib-core-api-public`, `:ktor-server-jwt-auth`).
- No symlink/junction overlays as the target design; delete overlay links and ensure scripts once modules live under packs.
- Cross-platform: no reliance on `core.symlinks` or Windows Developer Mode for pack layout.
- Commit clean baselines before each structural batch; leave unrelated untracked files (e.g. accidental graalvm example settings) out of commits.
- Wallet dual-mode changes live in the **wallet** submodule on `pilot/artifact-mode`; IDK only advances the gitlink.

## Target layout

```

  core/                 # was core
  platform/             # was platform
  infra/                # was infra
  identity-security/
  protocols/
  wallet/
```

Module path rule (preserve suffix after IDK root):

| Old path | New path |
| --- | --- |
| `lib/cbor/public` | `core/lib/cbor/public` |
| `lib/catalog/public` | `platform/lib/catalog/public` |
| `services/ktor/server/plugins/ktor-server-jwt-auth` | `infra/services/ktor/server/plugins/ktor-server-jwt-auth` |
| `lib/crypto/core` | `identity-security/lib/crypto/core` |
| `lib/openid/oid4vp/...` | `protocols/lib/openid/oid4vp/...` |
| `lib/wallet/...` (IDK-owned) | `wallet-lib/lib/wallet/...` |

Wallet **submodule** (`wallet/` at IDK root) stays a submodule; only IDK-owned wallet bridge modules under `lib/wallet` move into `wallet-lib`. Pack settings that currently map into the submodule keep pointing at the submodule path until a later wallet-repo decision.

## Pack settings shape after move

Replace `includeMapped(name, relativeFromIdk)` that resolves via `idkRoot` with pack-local includes:

```kotlin
fun includeLocal(name: String, relativeFromPack: String) {
    include(":" + name)
    project(":" + name).projectDir = settings.rootDir.resolve(relativeFromPack)
}

includeLocal("lib-cbor-public", "lib/cbor/public")
```

`idkRoot` remains for `platform-version.properties`, `.worktree-m2`, and `gradle/libs.versions.toml` only.

Monolith `settings.gradle.kts` / any root include map must retarget `projectDir` to `<pack>/...` for moved modules.

---

### Task 0: Commit clean baseline

**Files:**
- Modify: `wallet` gitlink → wallet `pilot/artifact-mode` commit with dual-mode deps
- Create: `docs/superpowers/plans/2026-09-19-packs-git-mv-layout.md`
- Modify: `phase1-progress.md` (Next section points at this plan)

- [x] **Step 1: Confirm wallet submodule committed** on `pilot/artifact-mode`
- [x] **Step 2: Restore accidental overlay working-tree noise** (`git checkout -- `)
- [x] **Step 3: Commit IDK** wallet pointer + this plan + progress pointer
- [x] **Step 4: Do not add** untracked `services/ktor/.../graalvm/settings.gradle.kts` unless intentional

---

### Task 1: Rename tree `compounds` → `packs`, `infrastructure` → `infra`

**Files:**
- `git mv compounds packs`
- `git mv infra infra`
- Update every reference: pack settings comments, publish/smoke scripts, phase1\* docs, `.gitignore`, `ensure-overlays.ps1` paths (or delete in Task 3), root docs

- [ ] **Step 1:** `git mv compounds packs`
- [ ] **Step 2:** `git mv infra infra`
- [x] **Step 3:** Retarget strings `compounds/` → ``, `infra` → `infra`, and root names/comments that say `infrastructure-compound` path (keep `rootProject.name` / project names unless they embed only the folder — **project names stay**; `rootProject.name = "idk-infrastructure-compound"` may become `idk-infra-pack` only if we treat that as a banner name, not a Gradle project id of a module — prefer renaming rootProject.name to `idk-infra-pack` for clarity; do **not** rename module projects)
- [ ] **Step 4:** Commit: `Rename compounds/ to  and infrastructure pack to infra.`

---

### Task 2: `git mv` core pack modules (smallest closed set)

**Files (from `core/settings.gradle.kts` includes):**

```
lib/cbor/public          → core/lib/cbor/public
lib/cbor/impl            → core/lib/cbor/impl
lib/compression          → core/lib/compression
lib/core/api/public      → core/lib/core/api/public
lib/core/api/default     → core/lib/core/api/default
lib/core/compat-annotations → core/lib/core/compat-annotations
lib/core/events/public   → core/lib/core/events/public
lib/core/events/impl     → core/lib/core/events/impl
lib/core/idn/public      → core/lib/core/idn/public
lib/core/loggers/mobile-logger → core/lib/core/loggers/mobile-logger
lib/core/test            → core/lib/core/test
lib/conf/settings        → core/lib/conf/settings
lib/conf/yaml            → core/lib/conf/yaml
```

- [ ] **Step 1:** For each path, `git mv <old> core/<old>` (create parents as needed with `git mv` into existing dirs)
- [ ] **Step 2:** Update `core/settings.gradle.kts` to `includeLocal` relative to pack root
- [ ] **Step 3:** Update monolith root `settings.gradle.kts` `projectDir` for these 13 projects
- [ ] **Step 4:** Remove `core/{lib,openapi,gradle-build-support}` git symlinks
- [ ] **Step 5:** Point OpenAPI/GBS path helpers used by core modules at `idkRoot` (or pack-relative openapi if specs move later — for this task, keep openapi at IDK root and resolve via `idkRoot`)
- [ ] **Step 6:** Smoke: `gradlew.bat -p core :lib-core-api-public:compileKotlinJvm -Dkmp.targets=jvm`
- [ ] **Step 7:** Commit: `git mv core pack modules under core and drop core overlays.`

---

### Task 3: Repeat Task 2 per remaining pack (order)

Order (dependency / size):

1. `platform` (19)
2. `infra` (36)
3. `identity-security` (67)
4. `protocols` (~59)
5. `wallet` IDK-owned `lib/wallet/**` only; submodule paths unchanged

After each pack: update pack settings + monolith settings, delete that pack’s overlays, smoke one compile task, commit.

Final cleanup commit: delete `ensure-overlays.ps1` and per-pack `ensure-junctions.ps1`; update `phase1-progress.md` / gates for packs language.

---

### Task 4: Path resolution hardening (if smoke fails without overlays)

**Files:** conventions / OpenApiResolver / any `rootProject.file("lib|openapi|gradle-build-support")` used from pack builds.

- [ ] Resolve `openapi/` and `gradle-build-support/` via `idkRoot` (two levels up from pack, or settings.extra)
- [ ] Do not reintroduce symlinks
- [ ] Commit when green

---

### Task 5: Progress / gates

- [ ] Add `phase1k-gates.md` with rename + core move + subsequent pack move results
- [ ] Update `phase1-progress.md` Next → remaining packs / EDK
- [ ] Commit docs

## Out of scope

- EDK / VDX / infra assembly consumption
- Renaming Gradle module project names or Maven coordinates
- Moving the `wallet/` git submodule tree into `wallet-lib`
- Pushing branches to origin (unless explicitly requested)
