package com.sphereon.crypto.kms.rest.server.adapter

import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStoreErrorCodes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CertificateReferenceRouteOrderingTest {
    @Test
    fun canonicalKeyMismatchMapsToConflict() {
        assertEquals(
            409,
            certificateReferenceRegistrationHttpStatus(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
                    message = "certificate mismatch",
                ),
            ),
        )
    }

    @Test
    fun registerRoutePrecedesTheAliasTemplateRoute() {
        val source = repositoryRoot()
            .resolve("services/kms/rest/src/commonMain/kotlin/com/sphereon/crypto/kms/rest/server/adapter/CertificatesHttpAdapter.kt")
            .toFile()
            .readText()

        val register = source.indexOf("post(\"/certificates/register\")")
        val alias = source.indexOf("post(\"/certificates/{alias}\")")

        assertTrue(register >= 0, "certificate registration route must be declared")
        assertTrue(alias >= 0, "legacy certificate alias route must be declared")
        assertTrue(register < alias, "literal registration route must precede the alias template route")
    }

    @Test
    fun registrationParseFailureDoesNotExposeSerializerDiagnostics() {
        val source = repositoryRoot()
            .resolve("services/kms/rest/src/commonMain/kotlin/com/sphereon/crypto/kms/rest/server/adapter/CertificatesHttpAdapter.kt")
            .toFile()
            .readText()
        val registrationHandler = source.substring(
            source.indexOf("private suspend fun handleRegisterCertificateReference"),
            source.indexOf("private suspend fun handleListTrustedCertificateAliases"),
        )

        assertTrue("errorResponse(400, \"Invalid request body\")" in registrationHandler)
        assertTrue("expected.message" !in registrationHandler)
    }

    private fun repositoryRoot(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) && Files.isRegularFile(it.resolve("openapi/kms-openapi.yml")) }
            ?: error("Unable to locate the IDK repository root")
}
