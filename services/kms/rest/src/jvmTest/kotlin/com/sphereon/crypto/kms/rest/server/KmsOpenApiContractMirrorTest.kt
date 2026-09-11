package com.sphereon.crypto.kms.rest.server

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmsOpenApiContractMirrorTest {
    @Test
    fun generatorCacheTracksReferencedComponentFiles() {
        val buildScript =
            repositoryRoot()
                .resolve("lib/crypto/kms/rest/api/build.gradle.kts")
                .readText()

        assertTrue(buildScript.contains("openapiSpec(\"kms-components.yml\")"))
        assertTrue(buildScript.contains("openapiSpec(\"common-components.yml\")"))
    }

    @Test
    fun allKmsOpenApiMirrorsAreByteIdentical() {
        val root = repositoryRoot()
        listOf("kms-openapi.yml", "kms-components.yml").forEach { fileName ->
            val mirrors =
                listOf(
                    root.resolve("../../openapi/$fileName").normalize(),
                    root.resolve("../openapi/$fileName").normalize(),
                    root.resolve("openapi/$fileName").normalize(),
                    Path.of("D:/d/openapi/$fileName"),
                )
            val canonical = mirrors.first()
            assertTrue(Files.isRegularFile(canonical), "canonical KMS OpenAPI file is missing: $canonical")
            val expected = canonical.readText()
            mirrors.drop(1).forEach { mirror ->
                assertTrue(Files.isRegularFile(mirror), "KMS OpenAPI mirror is missing: $mirror")
                assertEquals(expected, mirror.readText(), "KMS OpenAPI mirror differs: $mirror")
            }
        }
    }

    @Test
    fun externalKmsRegistrationContractDescribesImplementedLifecycle() {
        val root = repositoryRoot()
        val spec = root.resolve("../../openapi/kms-openapi.yml").normalize().readText()
        val components = root.resolve("../../openapi/kms-components.yml").normalize().readText()
        val combinedContract = "$spec\n$components"
        val normalizedContract = combinedContract.replace(Regex("\\s+"), " ")

        assertTrue(spec.contains("  /certificates/register:"))
        assertTrue(combinedContract.contains("trusted_certificate"))
        assertTrue(combinedContract.contains("key_certificate_chain"))
        assertTrue(combinedContract.contains("stored_public_material"))
        assertTrue(combinedContract.contains("provider_native"))
        assertTrue(combinedContract.contains("Base64-encoded DER"))
        assertTrue(combinedContract.contains("linkedKeyAlias"))
        assertTrue(combinedContract.contains("linkedKeyKid"))
        assertTrue(combinedContract.contains("providerCertificateId"))
        assertTrue(combinedContract.contains("x5t#S256"))
        assertTrue(combinedContract.contains("Externally managed DELETE removes only the EDK reference."))
        assertTrue(combinedContract.contains("The provider resource remains untouched."))
        assertTrue(normalizedContract.contains("already deleted externally managed local reference is idempotent and returns 204"))
        assertTrue(combinedContract.contains("Registration does not create/import key material."))
        assertTrue(combinedContract.contains("ResourceControlMode"))

        val requestStart = spec.indexOf("    RegisterKeyReferenceRequest:\n", spec.indexOf("components:"))
        val requestEnd = spec.indexOf("    RegisterKeyReferenceResponse:", requestStart)
        assertTrue(requestStart >= 0 && requestEnd > requestStart, "key registration request schema is missing")
        val requestSchema = spec.substring(requestStart, requestEnd)
        val requiredStart = requestSchema.indexOf("      required:\n")
        val requiredEnd = requestSchema.indexOf("      properties:\n", requiredStart)
        assertTrue(requiredStart >= 0 && requiredEnd > requiredStart, "key registration required list is missing")
        assertFalse(requestSchema.substring(requiredStart, requiredEnd).contains("- kid"), "kid must remain optional")

        val certificateRequestStart = components.indexOf("    CertificateReferenceRegistrationRequest:")
        val certificateRequestEnd = components.indexOf("    CertificateReferenceResponse:", certificateRequestStart)
        assertTrue(certificateRequestStart >= 0 && certificateRequestEnd > certificateRequestStart)
        val certificateRequestSchema = components.substring(certificateRequestStart, certificateRequestEnd)
        assertTrue(
            certificateRequestSchema.contains("not:") && certificateRequestSchema.contains("anyOf:"),
            "certificate registration must reject every invalid source and kind combination",
        )
        assertTrue(
            certificateRequestSchema.contains("title: Stored public material without a chain") &&
                certificateRequestSchema.contains("title: Provider-native material with a supplied chain") &&
                certificateRequestSchema.contains("title: Key certificate chain without a linked key selector"),
            "certificate registration must reject all three conditional invalid states",
        )
        val requestBodyStart = spec.indexOf("    RegisterCertificateReferenceRequest:")
        val requestBodyEnd = spec.indexOf("    ProviderQueryRequest:", requestBodyStart)
        assertTrue(requestBodyStart >= 0 && requestBodyEnd > requestBodyStart)
        assertTrue(
            spec.substring(requestBodyStart, requestBodyEnd)
                .contains("\$ref: '#/components/schemas/CertificateReferenceRegistrationRequest'"),
            "POST /certificates/register must use the condition-bearing registration schema",
        )
        assertTrue(certificateRequestSchema.contains("minItems: 1"))
        assertTrue(certificateRequestSchema.contains("not:"))
        assertTrue(certificateRequestSchema.contains("- certificateChain"))
        assertTrue(certificateRequestSchema.contains("anyOf:"))
        assertTrue(certificateRequestSchema.contains("- linkedKeyAlias"))
        assertTrue(certificateRequestSchema.contains("- linkedKeyKid"))
    }

    private fun repositoryRoot(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull {
                Files.isRegularFile(it.resolve("settings.gradle.kts")) &&
                    Files.isRegularFile(it.resolve("openapi/kms-openapi.yml"))
            }
            ?: error("Unable to locate the IDK repository root")
}
