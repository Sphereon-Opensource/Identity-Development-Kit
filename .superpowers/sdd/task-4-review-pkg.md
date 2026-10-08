# Review package Task 4 (VDX-infra)
BASE: 407f97c02202a1cc88f08bd45f2e902ca85059c2
HEAD: 68f95f8e5132fc02d356c5dbbe659f6cc51b16d9
## Commits

## Stat
 settings.gradle.kts | 100 ++++++++++++++++++++++++++++++++++++++++++++--------
 1 file changed, 86 insertions(+), 14 deletions(-)

## Diff settings.gradle.kts
diff --git a/settings.gradle.kts b/settings.gradle.kts
index 7cf8ae9a..74591b33 100644
--- a/settings.gradle.kts
+++ b/settings.gradle.kts
@@ -264,10 +264,11 @@ val useVdxCompositeBuild = isVdxCompositeBuildEnabled()
 
 /**
  * Determines if IDK composite build should be enabled within VDX composite build.
  * USE_LOCAL_IDK=false disables IDK source coupling (uses Maven artifacts instead).
  * Defaults to true when VDX composite build is active.
+ * When enabled, selective source coupling still requires IDK_LOCAL_PACKS.
  */
 fun isIdkCompositeBuildEnabled(): Boolean {
     (System.getenv("USE_LOCAL_IDK") ?: System.getProperty("USE_LOCAL_IDK") ?: providers.gradleProperty("USE_LOCAL_IDK").orNull)?.let { value ->
         if (value.equals("false", ignoreCase = true) || value == "0") return false
     }
@@ -284,10 +285,85 @@ fun isEdkCompositeBuildEnabled(): Boolean {
         if (value.equals("false", ignoreCase = true) || value == "0") return false
     }
     return true
 }
 
+/**
+ * Resolves the IDK checkout root. IDK_CHECKOUT overrides the default submodule path
+ * so pilot worktrees with pack layouts can be used before the submodule lands packs.
+ */
+fun resolveIdkRoot(): File {
+    val override = System.getenv("IDK_CHECKOUT")?.trim()?.takeIf { it.isNotEmpty() }
+    return if (override != null) file(override) else file("vdx/edk/idk")
+}
+
+// --- Selective IDK pack composites (mirrors idk/gradle/idk-local-packs.gradle.kts) ---
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
+                if (pack !in IDK_PACK_DIRS) {
+                    throw GradleException(
+                        "Unknown IDK_LOCAL_PACKS entry '$pack'. Allowed: ${IDK_PACK_DIRS.joinToString()}",
+                    )
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
+        if (!packSettings.isFile) {
+            throw GradleException("Missing pack settings: ${packSettings.absolutePath}")
+        }
+        val modules = extractIdkPackModuleNames(packSettings)
+        if (modules.isEmpty()) {
+            throw GradleException(
+                "No includeLocal/includeMapped modules in ${packSettings.absolutePath}",
+            )
+        }
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
+
 /**
  * Parses a settings.gradle.kts file and extracts module info from include() calls.
  * Matches patterns like: include(":versions:common-bom")
  * Returns pairs of (artifactName, projectPath) e.g. ("common-bom", ":versions:common-bom")
  * Skips commented lines.
@@ -337,26 +413,22 @@ if (useVdxCompositeBuild) {
         println("==> Gradle Build Support Composite Build: DISABLED (USE_LOCAL_IDK=false)")
     } else {
         println("==> Gradle Build Support Composite Build: DISABLED (generated catalogs unavailable; using Maven dependencies)")
     }
 
-    // --- IDK (must be included FIRST, before EDK) ---
+    // --- IDK (selective packs via IDK_LOCAL_PACKS; must be included FIRST, before EDK) ---
     if (useIdkComposite) {
-        val idkSettingsFile = file("vdx/edk/idk/settings.gradle.kts")
-        if (idkSettingsFile.exists()) {
-            val idkModules = extractModuleNamesFromSettings(idkSettingsFile)
-                .filterNot { it == "lib-all" }
-
-            includeBuild("vdx/edk/idk") {
-                name = "Identity-Development-Kit"
-                dependencySubstitution {
-                    idkModules.forEach { moduleName ->
-                        substitute(module("com.sphereon.idk:$moduleName")).using(project(":$moduleName"))
-                    }
-                }
+        val idkRoot = resolveIdkRoot()
+        val packs = parseIdkLocalPacks()
+        if (packs.isEmpty()) {
+            println("==> IDK Composite Build: DISABLED (set IDK_LOCAL_PACKS=core,protocols,… for selective source; else Maven artifacts)")
+        } else {
+            if (!idkRoot.isDirectory) {
+                throw GradleException("IDK root is not a directory: ${idkRoot.absolutePath}")
             }
-            println("==> IDK Composite Build: ENABLED (${idkModules.size} modules)")
+            println("==> IDK root: ${idkRoot.absolutePath}")
+            includeIdkLocalPackBuilds(idkRoot)
         }
     } else {
         println("==> IDK Composite Build: DISABLED (USE_LOCAL_IDK=false, using Maven artifacts)")
     }
 

