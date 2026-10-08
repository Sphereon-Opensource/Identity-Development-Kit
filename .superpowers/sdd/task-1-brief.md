### Task 1: Shared `IDK_LOCAL_PACKS` settings helper

**Files:**
- Create: `gradle/idk-local-packs.gradle.kts` (IDK worktree root)
- Create: `gradle/idk-local-packs-parse.test.ps1` (parser smoke; run with PowerShell)
- Test: run the ps1 script

**Interfaces:**
- Consumes: env `IDK_LOCAL_PACKS`, pack `settings.gradle.kts` files under `<idkRoot>/<pack>/`
- Produces: Settings-script API used by later tasks:
  - `val IDK_PACK_DIRS: Set<String>`
  - `fun parseIdkLocalPacks(): List<String>`
  - `fun extractIdkPackModuleNames(settingsFile: File): List<String>`
  - `fun Settings.includeIdkLocalPackBuilds(idkRoot: File)` â€” for each parsed pack, `includeBuild(idkRoot.resolve(pack)) { name = "idk-$pack"; dependencySubstitution { modules.forEach { substitute(module("com.sphereon.idk:$it")).using(project(":$it")) } } }`

- [ ] **Step 1: Write the parser smoke script (fail first if helper missing)**

Create `gradle/idk-local-packs-parse.test.ps1`:

```powershell
$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not (Test-Path "$PSScriptRoot/idk-local-packs.gradle.kts")) {
  throw "missing idk-local-packs.gradle.kts"
}
# Inline expected regex behavior matching the helper (keep in sync):
function Extract-PackModules([string]$settingsPath) {
  Get-Content $settingsPath | Where-Object {
    $t = $_.Trim()
    -not ($t.StartsWith("//") -or $t.StartsWith("/*") -or $t.StartsWith("*"))
  } | ForEach-Object {
    if ($_ -match 'include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,') { $Matches[1] }
  }
}
$core = Extract-PackModules "$PSScriptRoot/../core/settings.gradle.kts"
if ($core -notcontains "lib-core-api-public") { throw "expected lib-core-api-public in core extract" }
if ($core -contains "lib-openid-oid4vp-holder-public") { throw "core must not list protocols modules" }
Write-Output "PASS extract core count=$($core.Count)"
```

Fix `$root` / paths so `$PSScriptRoot` is `â€¦/idk/gradle` and core settings is `â€¦/idk/core/settings.gradle.kts` (`"$PSScriptRoot/../core/settings.gradle.kts"`).

- [ ] **Step 2: Run script â€” expect FAIL (helper file missing) or FAIL extract if you create script before helper**

Run (from IDK worktree):

```powershell
powershell -NoProfile -File gradle/idk-local-packs-parse.test.ps1
```

Expected: FAIL with `missing idk-local-packs.gradle.kts` if Step 1 ran before Step 3.

- [ ] **Step 3: Implement `gradle/idk-local-packs.gradle.kts`**

```kotlin
import java.io.File

val IDK_PACK_DIRS: Set<String> =
    setOf("core", "platform", "infra", "identity-security", "protocols", "wallet-lib")

fun parseIdkLocalPacks(): List<String> {
    val raw = System.getenv("IDK_LOCAL_PACKS")?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()
    return raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .also { packs ->
            packs.forEach { pack ->
                require(pack in IDK_PACK_DIRS) {
                    "Unknown IDK_LOCAL_PACKS entry '$pack'. Allowed: ${IDK_PACK_DIRS.joinToString()}"
                }
            }
        }
        .distinct()
}

fun extractIdkPackModuleNames(settingsFile: File): List<String> {
    if (!settingsFile.isFile) return emptyList()
    val localOrMapped =
        Regex("""include(?:Local|Mapped)\s*\(\s*"([^"]+)"\s*,\s*"[^"]+"\s*\)""")
    return settingsFile.readLines()
        .filter { line ->
            val t = line.trim()
            !t.startsWith("//") && !t.startsWith("/*") && !t.startsWith("*")
        }
        .mapNotNull { line -> localOrMapped.find(line)?.groupValues?.get(1) }
        .distinct()
}

fun Settings.includeIdkLocalPackBuilds(idkRoot: File) {
    val packs = parseIdkLocalPacks()
    if (packs.isEmpty()) {
        println("==> IDK pack source builds: none (IDK_LOCAL_PACKS empty; using Maven artifacts for IDK modules)")
        return
    }
    packs.forEach { pack ->
        val packRoot = idkRoot.resolve(pack)
        val packSettings = packRoot.resolve("settings.gradle.kts")
        require(packSettings.isFile) { "Missing pack settings: ${packSettings.absolutePath}" }
        val modules = extractIdkPackModuleNames(packSettings)
        require(modules.isNotEmpty()) { "No includeLocal/includeMapped modules in ${packSettings.absolutePath}" }
        includeBuild(packRoot) {
            name = "idk-$pack"
            dependencySubstitution {
                modules.forEach { moduleName ->
                    substitute(module("com.sphereon.idk:$moduleName")).using(project(":$moduleName"))
                }
            }
        }
        println("==> IDK pack source build: $pack (${modules.size} modules)")
    }
}
```

Note: in a `settings.gradle.kts` applied via `apply(from = â€¦)`, prefer inlining these functions into each consumer settings file **or** use `apply(from = rootDir.resolve("gradle/idk-local-packs.gradle.kts"))` only where `rootDir` is the IDK root. If `apply(from)` cannot define top-level functions for the caller in this Gradle version, **copy the three functions verbatim** into each of the four settings files (IDK thin root, VDX-infra, vdx, edk) and keep the ps1 extract test as the contract. Prefer one shared apply if it works in a 5-minute spike; otherwise duplicate and add a comment `// keep in sync with gradle/idk-local-packs.gradle.kts`.

- [ ] **Step 4: Re-run parser smoke**

```powershell
powershell -NoProfile -File gradle/idk-local-packs-parse.test.ps1
```

Expected: `PASS extract core count=â€¦`

- [ ] **Step 5: Commit**

```bash
git add gradle/idk-local-packs.gradle.kts gradle/idk-local-packs-parse.test.ps1
git commit -m "Add IDK_LOCAL_PACKS parsing and pack module extraction helper."
```

---

