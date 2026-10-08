plugins {
    `java-library`
    `maven-publish`
}

group = "com.sphereon.idk"
version = "0.26.0-SNAPSHOT"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "fixture-core"
            from(components["java"])
        }
    }
}

// The finite :jar smoke validates init-script wiring without publishing artifacts.
check(repositories.findByName("ciPrerequisites") == null)
check(publishing.repositories.findByName("ciPrerequisites") != null)
check(publishing.repositories.findByName("sphereon-opensource") == null)
check(tasks.findByName("publishAllPublicationsToCiPrerequisitesRepository") != null)
