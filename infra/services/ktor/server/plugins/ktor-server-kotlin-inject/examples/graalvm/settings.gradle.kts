import java.util.Properties

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/")
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-releases/")
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
            // Override Sphereon product coordinates from IDK platform-version.properties when present.
            var dir: java.io.File? = settings.rootDir
            var platformFile: java.io.File? = null
            while (dir != null) {
                val candidate = dir.resolve("platform-version.properties")
                if (candidate.isFile) {
                    platformFile = candidate
                    break
                }
                dir = dir.parentFile
            }
            if (platformFile != null) {
                val props = Properties().apply {
                    platformFile!!.reader(Charsets.UTF_8).use { load(it) }
                }
                val platformVersion = props.getProperty("platformVersion")?.trim()
                val gbsVersion = props.getProperty("gbsVersion")?.trim()
                if (!platformVersion.isNullOrEmpty()) {
                    version("sphereon-idk", platformVersion)
                    // Historical catalog key name; value is the GBS / plugin BOM version.
                    version("sphereon-plugin", gbsVersion ?: platformVersion)
                }
            }
        }
    }
}

rootProject.name = "ktor-server-kotlin-inject-graalvm-example"
