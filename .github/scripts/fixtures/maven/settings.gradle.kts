rootProject.name = "idk-ci-fixture-core"
include("consumer")

dependencyResolutionManagement {
    repositories.maven {
        name = "normalSettingsRepository"
        url = uri(settingsDir.resolve("build/normal-repository"))
    }
}

// Exercise the CI init-script listeners through a captured settings script.
System.setProperty("idk.ci.prerequisites", settingsDir.resolve("build/ci-prerequisites").absolutePath)
System.setProperty("idk.ci.publish", "false")
apply(from = "../../pack-ci.init.gradle")

// Check after the init listener has installed its settings-level repository.
val settingsRepositories = dependencyResolutionManagement
gradle.settingsEvaluated {
    check(settingsRepositories.repositoriesMode.get() == org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_SETTINGS)
    check(settingsRepositories.repositories.findByName("ciPrerequisites") != null)
    check(settingsRepositories.repositories.findByName("normalSettingsRepository") != null)
}
