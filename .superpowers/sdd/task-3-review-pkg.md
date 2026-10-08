# Review package Task 3
BASE: d3398e6430841b75766f76d1aeabf69daf037cad
HEAD: 6d7a6ef2c94cc87911f7ff5e0899cebde16e2422
## Commits

## Stat
 phase1m-gates.md | 28 ++++++++++++++++++++++++++++
 1 file changed, 28 insertions(+)

## Diff
diff --git a/phase1m-gates.md b/phase1m-gates.md
new file mode 100644
index 000000000..61c4f8703
--- /dev/null
+++ b/phase1m-gates.md
@@ -0,0 +1,28 @@
+# Phase 1m gates — thin IDK root + pack smokes
+
+Date: 2026-09-19  
+Branch: `pilot/artifact-mode`  
+Worktree: `D:\git\VDX-infra\vdx\edk\idk\.worktrees\artifact-pilot`  
+Env: `GRADLE_USER_HOME=<worktree>\.gradle-home-solo`, `WORKTREE_MAVEN_REPO=<worktree>\.worktree-m2`, direct `gradlew` (no coordinator)
+
+| Gate | Result | Evidence |
+| --- | --- | --- |
+| M1 Thin root project list | PASS | Empty `IDK_LOCAL_PACKS`: banner `IDK pack source builds: none`; Select-String finds no `lib-core-api-public` / `lib-openid-oid4vp-holder-public` in `phase1m-root-projects-after.txt`. Pack modules remain available via `gradlew -p core projects` (`phase1m-core-projects.txt`, **BUILD SUCCESSFUL in 44s**). Thin-root `projects` still fails configuring kept examples that use type-safe `projects.*` accessors (Task 2 follow-up). |
+| M2 Core pack smoke | PASS | `gradlew -p core compileKotlinJvm` **BUILD SUCCESSFUL in 1m 2s** (`phase1m-core-smoke.log`; 57 actionable). |
+| M3 Protocols pack smoke | PASS | `gradlew -p protocols compileKotlinJvm` **BUILD SUCCESSFUL in 2m 25s** (`phase1m-protocols-smoke.log`; 286 actionable). Resolved lower layers from existing `.worktree-m2` / Nexus — no republish required. |
+
+## Commands
+
+```powershell
+cd <idk-worktree>
+$env:GRADLE_USER_HOME = "$pwd\.gradle-home-solo"
+$env:WORKTREE_MAVEN_REPO = "$pwd\.worktree-m2"
+.\gradlew.bat -p core compileKotlinJvm --no-configuration-cache --console=plain "-Dkmp.targets=jvm" --max-workers=4
+.\gradlew.bat -p protocols compileKotlinJvm --no-configuration-cache --console=plain "-Dkmp.targets=jvm" --max-workers=4
+```
+
+## Notes
+
+- Quote `"-Dkmp.targets=jvm"` under PowerShell so `.targets=jvm` is not parsed as a task name.
+- Pack smokes used a dedicated `.gradle-home-solo` (not `.gradle-home`) per Task 3 brief.
+- Did not restore monolith `includeProject` graph; packs compile standalone after thin root.

