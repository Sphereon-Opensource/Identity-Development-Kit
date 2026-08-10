package com.sphereon.crypto.kms.rest.server

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmsRestSpecModelSourceGateTest {
    @Test
    fun publishesSelfContainedGenericKmsApiWithoutEnterpriseLifecycleRoutes() {
        val root = repositoryRoot()
        val spec = root.resolve("openapi/kms-openapi.yml").readText()
        assertTrue(spec.contains("/providers/{providerId}/keys:"), "provider/key management must be public")
        assertTrue(spec.contains("/certificates/csr:"), "certificate lifecycle must be public")
        assertTrue(spec.contains("/encryption/encrypt:"), "encryption must be public")
        assertTrue(spec.contains("/signatures/raw/create:"), "signing must be public")
        assertTrue(spec.contains("/resolvers/{resolverId}/resolve:"), "resolver API must be public")
        assertFalse(spec.contains("/{tenantId}/kms/"), "enterprise resource lifecycle does not belong in IDK")
    }

    @Test
    fun serverIncludesGenericRouting() {
        val root = repositoryRoot()
        val server = root.resolve("services/kms/rest/src/jvmMain/kotlin/com/sphereon/crypto/kms/rest/server/KmsKtorServer.kt").readText()
        assertTrue(server.contains("installUniversalHttpAdapters()"), "self-contained KMS routes must be mounted")
    }

    @Test
    fun optionalRestKeyUseIsNormalizedBeforeTheProviderBoundary() {
        val root = repositoryRoot()
        val services =
            listOf("KmsRestServiceImpl.kt", "ProvidersRestServiceImpl.kt")
                .map {
                    root.resolve("services/kms/rest/src/commonMain/kotlin/com/sphereon/crypto/kms/rest/server/service/$it")
                        .readText()
                }
        assertTrue(
            services.all { it.contains("use = use ?: JwkUse.sig") },
            "global and provider-scoped REST key generation must pass an explicit signing use",
        )
    }

    @Test
    fun everyKmsHttpDescriptorUsesTheThreeSegmentCommandIdGrammar() {
        val descriptors =
            repositoryRoot()
                .resolve("services/kms/rest/src/commonMain/kotlin/com/sphereon/crypto/kms/rest/server/adapter/describe/KmsHttpAdapterDescriptors.kt")
                .readText()
        val commandIds = Regex("""commandId\s*=\s*\"([^\"]+)\"""").findAll(descriptors).map { it.groupValues[1] }.toList()
        assertTrue(commandIds.isNotEmpty(), "KMS HTTP descriptors must declare command IDs")
        val grammar = Regex("""^[a-z][a-z0-9-]*\.[a-z][a-z0-9-]*\.[a-z][a-z0-9-]*$""")
        assertTrue(commandIds.all(grammar::matches), "Invalid KMS HTTP command ID: ${commandIds.firstOrNull { !grammar.matches(it) }}")
    }

    private fun repositoryRoot(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) && Files.isRegularFile(it.resolve("openapi/kms-openapi.yml")) }
            ?: error("Unable to locate the IDK repository root")
}
