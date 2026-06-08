/*
 * PUBLIC, unauthenticated, cacheable REST surface that HOSTS did:web / did:webvh documents at the
 * locations a wallet or resolver fetches: `/.well-known/did.json` and `/<path>/did.json`. The
 * endpoints return the RAW DID document JSON (`application/did+json`) with a `Cache-Control` header
 * derived from the method's cache TTL — never a JSON envelope.
 *
 * Method-agnostic by design: it delegates to the `DidHostingRegistry`, which fans out to every
 * contributed `DidHostingProvider` (did:web serves its stored document; did:webvh serves the
 * did:web companion translation). The deployable service decides which method modules are on the
 * classpath — this module depends on none of them directly, so a new hostable method is picked up
 * via DI without touching this surface.
 *
 * Lives on the IDK services side (open core): hosting a DID document is a basic capability. Phase A
 * serves `did.json` only; the did:webvh verifiable log (`did.jsonl`) + witness file are a later phase.
 */
plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.multiplatform)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    alias(sphereonplug.plugins.com.sphereon.gradle.plugin.project.publication)
    id("maven-publish")
}

metro {
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // The method-agnostic hosting SPI + registry this surface delegates to.
                api(projects.libDidHostingPublic)
                // IDK core API (HTTP adapter types + response helpers).
                api(projects.libCoreApiPublic)

                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)

                implementation(libs.bundles.app.platform.di)
                api(sphereonlib.software.amazon.app.platform.metro.public)
                api(sphereonlib.software.amazon.app.platform.metro.impl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
                implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
            }
        }
    }
}
