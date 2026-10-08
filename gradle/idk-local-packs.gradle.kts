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
                if (pack !in IDK_PACK_DIRS) {
                    throw org.gradle.api.GradleException(
                        "Unknown IDK_LOCAL_PACKS entry '$pack'. Allowed: ${IDK_PACK_DIRS.joinToString()}",
                    )
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
        if (!packSettings.isFile) {
            throw org.gradle.api.GradleException("Missing pack settings: ${packSettings.absolutePath}")
        }
        val modules = extractIdkPackModuleNames(packSettings)
        if (modules.isEmpty()) {
            throw org.gradle.api.GradleException(
                "No includeLocal/includeMapped modules in ${packSettings.absolutePath}",
            )
        }
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
