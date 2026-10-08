plugins {
    `java-library`
}

dependencies {
    // Deliberately a Maven coordinate. No project dependency can satisfy this.
    implementation("com.sphereon.idk:fixture-core:0.26.0-SNAPSHOT")
}
