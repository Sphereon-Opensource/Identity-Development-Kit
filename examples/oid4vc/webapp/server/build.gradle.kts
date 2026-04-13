plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    id("com.sphereon.gradle.plugin.service-deployable")
}

serviceDeployable {
    mainClass.set("com.sphereon.examples.oid4vc.Oid4vcDemoServerKt")
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // Ktor server
                implementation(sphereonlib.io.ktor.server.core)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.server.content.negotiation)
                implementation(sphereonlib.io.ktor.server.status.pages)
                implementation(sphereonlib.io.ktor.serialization.kotlinx.json)

                // Ktor client (for proxying to backend services)
                implementation(sphereonlib.io.ktor.client.core)
                implementation(sphereonlib.io.ktor.client.cio)
                implementation(sphereonlib.io.ktor.client.content.negotiation)

                // Serialization
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
            }
        }
    }
}

// Copy React build output into resources before JAR packaging.
// Run `npm run build` in frontend/ first, then this task copies dist/ to resources.
val copyFrontendBuild by tasks.registering(Copy::class) {
    from("${project.projectDir}/../frontend/dist")
    into("${project.projectDir}/src/jvmMain/resources/webapp")
}

tasks.named("jvmProcessResources") {
    val frontendDist = file("${project.projectDir}/../frontend/dist")
    if (frontendDist.exists()) {
        dependsOn(copyFrontendBuild)
    }
}
