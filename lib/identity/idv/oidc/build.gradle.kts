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

                api(projects.libIdvPublic)
                api(projects.libCoreApiPublic)
                api(projects.libCryptoCorePublic)
                api(projects.libOauth2ClientPublic)
                api(projects.libOauth2JwtValidationApi)
                api(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                implementation(sphereonlib.software.amazon.app.platform.metro.impl)
                api(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                api(sphereonlib.org.jetbrains.kotlinx.datetime)
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
