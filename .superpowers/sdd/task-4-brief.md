### Task 4: VDX-infra selective IDK composite

**Files:**
- Modify: `D:/git/VDX-infra/settings.gradle.kts` (functions ~250â€“360 and IDK `includeBuild` block)
- Test: `USE_LOCAL_IDK=false` configure of a small task; `IDK_LOCAL_PACKS=core` includeBuild smoke

**Interfaces:**
- Consumes: Task 1 parsers (duplicated or applied from IDK path `vdx/edk/idk/gradle/idk-local-packs.gradle.kts` â€” if pilot worktree is not the submodule path, either merge pilot to the submodule checkout used by infra **or** temporarily set path to the worktree; prefer merging/cherry-picking Tasks 1â€“3 into the IDK revision infra points at before this task)
- Produces: infra no longer substitutes every IDK module from monolith settings

- [ ] **Step 1: Replace `extractModuleNamesFromSettings` usage for IDK**

Remove reliance on parsing IDK root `includeProject` for IDK composites.

Replace the block:

```kotlin
includeBuild("vdx/edk/idk") {
    name = "Identity-Development-Kit"
    dependencySubstitution {
        idkModules.forEach { â€¦ }
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
        println("==> IDK Composite Build: DISABLED (set IDK_LOCAL_PACKS=core,protocols,â€¦ for selective source; else Maven artifacts)")
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

Expected: completes without configuring `vdx/edk/idk/core/...` projects (no `Configure project :lib-core-api-public` from an included IDK pack). Exact task may be adjusted to a lighter existing project if `enterprise-platform` is too heavy â€” use any infra project that depends on `com.sphereon.idk` coordinates.

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

(If infra must not commit until IDK is merged, leave the infra change in the worktree and note it in `phase1m-gates.md` as blocked on IDK gitlink â€” still implement and verify locally.)

---


## Controller resolution (binding)

Main submodule `D:\git\VDX-infra\vdx\edk\idk` is still `develop` without pack dirs. For this pilot:

1. Implement selective pack composite in `D:\git\VDX-infra\settings.gradle.kts`.
2. Resolve IDK root as: `System.getenv("IDK_CHECKOUT")?.trim()?.takeIf { it.isNotEmpty() }?.let { file(it) } ?: file("vdx/edk/idk")`.
3. For verification smokes set `IDK_CHECKOUT=D:\git\VDX-infra\vdx\edk\idk\.worktrees\artifact-pilot`.
4. Duplicate helper functions into infra settings (or apply from `IDK_CHECKOUT/gradle/idk-local-packs.gradle.kts` if that works).
5. Commit in VDX-infra repo if clean to do so; otherwise leave staged/working changes and note in report.
