### Task 2: Thin IDK root â€” remove pack `includeProject` graph

**Files:**
- Modify: `settings.gradle.kts` (IDK worktree)
- Modify: move or fix `lib/core/benchmarks` include if present (`includeProject("lib-core-benchmarks", "lib/core/benchmarks")` â†’ `core/lib/core/benchmarks` if that tree exists, else delete the include)
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

At end of `settings.gradle.kts` (after pluginManagement / dependencyResolutionManagement as appropriate â€” `includeBuild` for packs must be outside `pluginManagement`):

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

