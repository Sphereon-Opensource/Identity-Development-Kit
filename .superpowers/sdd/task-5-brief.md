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


## Controller resolution

Use the same helper pattern as VDX-infra Task 4 (see D:\git\VDX-infra\settings.gradle.kts).
Add IDK_CHECKOUT override where the IDK path is resolved.
For nested vdx: default idk path edk/idk; for edk: idk.
Verification with IDK_CHECKOUT pointing at artifact-pilot worktree.
Unknown pack token must fail with GradleException.
Commit in the owning git repos (vdx and/or edk) if possible; note gitlink updates.
