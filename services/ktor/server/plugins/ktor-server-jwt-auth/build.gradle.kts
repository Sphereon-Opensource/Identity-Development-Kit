import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.com.vanniktech.maven.publish)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.io.ktor.plugin)
}
metro {
}

kotlin {
    kotlin.applyDefaultHierarchyTemplate()

    jvm()
    run {
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        if ("all" in kmpTargets || "js" in kmpTargets) {
            js {
                nodejs {
                    useEsModules()
                    binaries.library()
                    generateTypeScriptDefinitions()
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Core IDK (SessionContext, IdentityResolution, IdkResult)
                api(projects.libCoreApiPublic)

                // JWT validation API (JwtValidationService, ValidatedAccessToken)
                api(projects.libOauth2JwtValidationApi)

                // Ktor Server (multiplatform) for ApplicationPlugin / call attributes
                api(sphereonlib.io.ktor.server.core)

                // KotlinInjectPlugin supplies the default per-call service resolvers
                // (call.getAppService / call.getSessionService). Exposed as api so
                // downstream consumers can install it alongside JwtAuthentication
                // without an extra dependency declaration.
                api(projects.ktorServerKotlinInject)

                // Coroutines
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                // Serialization for JsonElement claim typing
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(sphereonlib.org.jetbrains.kotlin.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
            }
        }

        val jvmTest by getting {
            dependencies {
                // IDK defaults for wiring the resolution pipeline + session factory in tests.
                implementation(projects.libCoreApiDefault)

                // Ktor testing on JVM.
                implementation(sphereonlib.io.ktor.server.test.host)
                implementation(sphereonlib.io.ktor.server.cio)
                implementation(sphereonlib.io.ktor.client.content.negotiation)
            }
        }

        findByName("jsMain")?.dependencies {
            // JS support is compile-only; the plugin is consumed on JVM servers.
        }

        findByName("jsTest")?.dependencies {
            implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test.js)
        }
    }
}

// Disable configuration cache for publishing tasks due to vanniktech plugin limitations
tasks.withType<GenerateMavenPom>().configureEach {
    notCompatibleWithConfigurationCache("POM generation is not compatible with configuration cache due to vanniktech maven publish plugin")
}
tasks.withType<PublishToMavenRepository>().configureEach {
    notCompatibleWithConfigurationCache("Publishing tasks are not compatible with configuration cache due to vanniktech maven publish plugin")
}
tasks.withType<PublishToMavenLocal>().configureEach {
    notCompatibleWithConfigurationCache("Publishing tasks are not compatible with configuration cache due to vanniktech maven publish plugin")
}
