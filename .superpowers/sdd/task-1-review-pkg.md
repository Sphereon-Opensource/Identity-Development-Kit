# Review package Task 1
BASE: 15dabea95282186bf49f7a166bc89fd3acd039e8
HEAD: 621e5b4ca533470ad681e92cd4823516fbf6493d

## Commits
621e5b4ca Add IDK_LOCAL_PACKS parsing and pack module extraction helper.


## Stat
 gradle/idk-local-packs-parse.test.ps1 | 18 +++++++++++
 gradle/idk-local-packs.gradle.kts     | 57 +++++++++++++++++++++++++++++++++++
 2 files changed, 75 insertions(+)


## Diff
diff --git a/gradle/idk-local-packs-parse.test.ps1 b/gradle/idk-local-packs-parse.test.ps1
new file mode 100644
index 000000000..eaebd9f92
--- /dev/null
+++ b/gradle/idk-local-packs-parse.test.ps1
@@ -0,0 +1,18 @@
+$ErrorActionPreference = "Stop"
+$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
+if (-not (Test-Path "$PSScriptRoot/idk-local-packs.gradle.kts")) {
+  throw "missing idk-local-packs.gradle.kts"
+}
+# Inline expected regex behavior matching the helper (keep in sync):
+function Extract-PackModules([string]$settingsPath) {
+  Get-Content $settingsPath | Where-Object {
+    $t = $_.Trim()
+    -not ($t.StartsWith("//") -or $t.StartsWith("/*") -or $t.StartsWith("*"))
+  } | ForEach-Object {
+    if ($_ -match 'include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,') { $Matches[1] }
+  }
+}
+$core = Extract-PackModules "$PSScriptRoot/../core/settings.gradle.kts"
+if ($core -notcontains "lib-core-api-public") { throw "expected lib-core-api-public in core extract" }
+if ($core -contains "lib-openid-oid4vp-holder-public") { throw "core must not list protocols modules" }
+Write-Output "PASS extract core count=$($core.Count)"
diff --git a/gradle/idk-local-packs.gradle.kts b/gradle/idk-local-packs.gradle.kts
new file mode 100644
index 000000000..60f9234e5
--- /dev/null
+++ b/gradle/idk-local-packs.gradle.kts
@@ -0,0 +1,57 @@
+import java.io.File
+
+val IDK_PACK_DIRS: Set<String> =
+    setOf("core", "platform", "infra", "identity-security", "protocols", "wallet-lib")
+
+fun parseIdkLocalPacks(): List<String> {
+    val raw = System.getenv("IDK_LOCAL_PACKS")?.trim().orEmpty()
+    if (raw.isEmpty()) return emptyList()
+    return raw.split(",")
+        .map { it.trim() }
+        .filter { it.isNotEmpty() }
+        .also { packs ->
+            packs.forEach { pack ->
+                require(pack in IDK_PACK_DIRS) {
+                    "Unknown IDK_LOCAL_PACKS entry '$pack'. Allowed: ${IDK_PACK_DIRS.joinToString()}"
+                }
+            }
+        }
+        .distinct()
+}
+
+fun extractIdkPackModuleNames(settingsFile: File): List<String> {
+    if (!settingsFile.isFile) return emptyList()
+    val localOrMapped =
+        Regex("""include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,\s*"[^"]+"\s*\)""")
+    return settingsFile.readLines()
+        .filter { line ->
+            val t = line.trim()
+            !t.startsWith("//") && !t.startsWith("/*") && !t.startsWith("*")
+        }
+        .mapNotNull { line -> localOrMapped.find(line)?.groupValues?.get(1) }
+        .distinct()
+}
+
+fun Settings.includeIdkLocalPackBuilds(idkRoot: File) {
+    val packs = parseIdkLocalPacks()
+    if (packs.isEmpty()) {
+        println("==> IDK pack source builds: none (IDK_LOCAL_PACKS empty; using Maven artifacts for IDK modules)")
+        return
+    }
+    packs.forEach { pack ->
+        val packRoot = idkRoot.resolve(pack)
+        val packSettings = packRoot.resolve("settings.gradle.kts")
+        require(packSettings.isFile) { "Missing pack settings: ${packSettings.absolutePath}" }
+        val modules = extractIdkPackModuleNames(packSettings)
+        require(modules.isNotEmpty()) { "No includeLocal/includeMapped modules in ${packSettings.absolutePath}" }
+        includeBuild(packRoot) {
+            name = "idk-$pack"
+            dependencySubstitution {
+                modules.forEach { moduleName ->
+                    substitute(module("com.sphereon.idk:$moduleName")).using(project(":$moduleName"))
+                }
+            }
+        }
+        println("==> IDK pack source build: $pack (${modules.size} modules)")
+    }
+}

