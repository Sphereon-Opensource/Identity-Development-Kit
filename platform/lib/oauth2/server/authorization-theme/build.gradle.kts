import com.sphereon.gradle.plugin.configureStandardTargets

plugins {
    `maven-publish`
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
}

metro {
}

kotlin {
    configureStandardTargets()

    sourceSets {
        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core.js)
        }
        val commonMain by getting {
            dependencies {
                implementation(sphereonlib.co.touchlab.skie.configuration.annotations)
                api(if (rootProject.findProperty("idk.consumeInfrastructureAsArtifacts") == "true") "com.sphereon.idk:lib-conf-theme-core-public:$version" else project(":lib-conf-theme-core-public"))
                api(
                    if (rootProject.findProperty("idk.consumeProtocolsAsArtifacts") == "true") {
                        "com.sphereon.idk:lib-oauth2-server-authorization-public:$version"
                    } else {
                        project(":lib-oauth2-server-authorization-public")
                    },
                )
                api(
                    if (rootProject.findProperty("idk.consumeIdentitySecurityAsArtifacts") == "true") {
                        "com.sphereon.idk:lib-software-registry-public:$version"
                    } else {
                        project(":lib-software-registry-public")
                    },
                )
                api(
                    if (rootProject.findProperty("idk.consumeCoreAsArtifacts") == "true") {
                        "com.sphereon.idk:lib-core-api-public:$version"
                    } else {
                        project(":lib-core-api-public")
                    },
                )
                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }
    }
}
