package com.sphereon.crypto.kms.provider.azure

import com.azure.core.exception.ResourceNotFoundException
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.ProviderCertificateLookup
import com.sphereon.crypto.core.kms.ProviderCertificateReference
import com.sphereon.crypto.core.x509.certificateFromPem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AzureProviderCertificateReferenceTest {
    @Test
    fun publicGetCertificateResolvesLatestAliasToCanonicalPublicReference() =
        runTest {
            val provider =
                providerWithReader { alias, version ->
                    require(alias == SENSITIVE_ALIAS)
                    require(version == null)
                    certificateRead(version = LATEST_VERSION)
                }

            try {
                val result = provider.getCertificate(ProviderCertificateLookup(alias = SENSITIVE_ALIAS))

                assertTrue(result.isOk)
                assertPublicLeaf(result.value, LATEST_VERSION)
            } finally {
                provider.close()
            }
        }

    @Test
    fun publicGetCertificateResolvesRequestedImmutableVersionToCanonicalPublicReference() =
        runTest {
            val provider =
                providerWithReader { alias, version ->
                    require(alias == SENSITIVE_ALIAS)
                    require(version == IMMUTABLE_VERSION)
                    certificateRead(version = IMMUTABLE_VERSION)
                }

            try {
                val result =
                    provider.getCertificate(
                        ProviderCertificateLookup(
                            alias = SENSITIVE_ALIAS,
                            id = IMMUTABLE_VERSION,
                        ),
                    )

                assertTrue(result.isOk)
                assertPublicLeaf(result.value, IMMUTABLE_VERSION)
            } finally {
                provider.close()
            }
        }

    @Test
    fun publicGetCertificateMapsResourceNotFoundWithoutBackendDetails() =
        runTest {
            val provider =
                providerWithReader { _, _ ->
                    throw ResourceNotFoundException(
                        "SDK missing $SENSITIVE_ALIAS at $VAULT_URL using $SECRET_MATERIAL",
                        null,
                    )
                }

            try {
                assertSanitizedError(
                    provider.getCertificate(ProviderCertificateLookup(alias = SENSITIVE_ALIAS)),
                    expectedCode = "NOT_FOUND_ERROR",
                    forbidden = listOf(SENSITIVE_ALIAS, VAULT_URL, SECRET_MATERIAL, "SDK missing"),
                )
            } finally {
                provider.close()
            }
        }

    @Test
    fun publicGetCertificateMapsGenericSdkFailureWithoutBackendDetails() =
        runTest {
            val provider =
                providerWithReader { _, _ ->
                    throw IllegalStateException(
                        "SDK failed for $SENSITIVE_ALIAS at $VAULT_URL with $SECRET_MATERIAL",
                    )
                }

            try {
                assertSanitizedError(
                    provider.getCertificate(ProviderCertificateLookup(alias = SENSITIVE_ALIAS)),
                    expectedCode = "UNKNOWN_ERROR",
                    forbidden = listOf(SENSITIVE_ALIAS, VAULT_URL, SECRET_MATERIAL, "SDK failed"),
                )
            } finally {
                provider.close()
            }
        }

    @Test
    fun publicGetCertificateRejectsInvalidReturnedCertificateOrKeyIdentityWithoutBackendDetails() =
        runTest {
            val invalidReads =
                listOf(
                    certificateRead(
                        version = IMMUTABLE_VERSION,
                        certificateId =
                            "https://other.vault.azure.net/certificates/$SENSITIVE_ALIAS/$IMMUTABLE_VERSION",
                    ),
                    certificateRead(
                        version = IMMUTABLE_VERSION,
                        keyId = "https://other.vault.azure.net/keys/$SENSITIVE_ALIAS/$IMMUTABLE_VERSION",
                    ),
                )

            invalidReads.forEach { invalidRead ->
                val provider = providerWithReader { _, _ -> invalidRead }
                try {
                    assertSanitizedError(
                        provider.getCertificate(
                            ProviderCertificateLookup(
                                alias = SENSITIVE_ALIAS,
                                id = IMMUTABLE_VERSION,
                            ),
                        ),
                        expectedCode = "ILLEGAL_ARGUMENT_ERROR",
                        forbidden =
                            listOf(
                                SENSITIVE_ALIAS,
                                VAULT_URL,
                                "other.vault.azure.net",
                                SECRET_MATERIAL,
                            ),
                    )
                } finally {
                    provider.close()
                }
            }
        }

    @Test
    fun publicGetCertificatePropagatesCoroutineCancellation() =
        runTest {
            val cancellation = CancellationException("cancel provider read")
            val provider = providerWithReader { _, _ -> throw cancellation }

            try {
                val propagated =
                    assertFailsWith<CancellationException> {
                        provider.getCertificate(ProviderCertificateLookup(alias = SENSITIVE_ALIAS))
                    }
                assertSame(cancellation, propagated)
            } finally {
                provider.close()
            }
        }

    @Test
    fun resolvesAliasAndImmutableVersionInsideConfiguredVault() {
        val identity =
            resolveAzureCertificateIdentity(
                configuredVaultUrl = "https://configured.vault.azure.net",
                lookup =
                    ProviderCertificateLookup(
                        alias = "signing-certificate",
                        id = "version1",
                    ),
                returnedCertificateId = "https://configured.vault.azure.net/certificates/signing-certificate/version1",
                returnedKeyId = "https://configured.vault.azure.net/keys/signing-certificate/version1",
            )

        assertEquals("signing-certificate", identity.alias)
        assertEquals("signing-certificate:version1", identity.id)
    }

    @Test
    fun rejectsCertificateOrKeyIdOutsideConfiguredVaultWithoutEchoingLocator() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                resolveAzureCertificateIdentity(
                    configuredVaultUrl = "https://configured.vault.azure.net",
                    lookup =
                        ProviderCertificateLookup(
                            alias = "signing-certificate",
                            id = "https://other.vault.azure.net/certificates/signing-certificate/version1",
                        ),
                    returnedCertificateId = "https://other.vault.azure.net/certificates/signing-certificate/version1",
                    returnedKeyId = "https://other.vault.azure.net/keys/signing-certificate/version1",
                )
            }

        assertFalse(failure.message.orEmpty().contains("other.vault.azure.net"))
    }

    @Test
    fun mapsCertificateDerToStandardBase64X5cNotBase64Url() {
        val derFixture = byteArrayOf(0xfb.toByte(), 0xff.toByte())

        assertEquals("+/8=", derFixture.encodeToBase64())
        assertEquals("-_8", derFixture.encodeToBase64Url())
        assertContentEquals(arrayOf("+/8="), azureCertificateX5c(derFixture))
    }

    @Test
    fun preservesProviderJwkCertificateMetadataAndDerivesMissingThumbprints() {
        val der = byteArrayOf(1, 2, 3)
        val supplied =
            Jwk(
                kty = JwaKeyType.EC,
                x5c = arrayOf("AQID"),
                x5t = "provider-sha1",
                x5t_S256 = "provider-sha256",
            )
        val preserved = preserveAzurePublicJwkCertificateMetadata(supplied)

        assertContentEquals(supplied.x5c, preserved.x5c)
        assertEquals("provider-sha1", preserved.x5t)
        assertEquals("provider-sha256", preserved.x5t_S256)

        val derived = preserveAzurePublicJwkCertificateMetadata(supplied.copy(x5t = null, x5t_S256 = null))
        assertEquals(MessageDigest.getInstance("SHA-1").digest(der).encodeToBase64Url(), derived.x5t)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(der).encodeToBase64Url(), derived.x5t_S256)
    }

    @Test
    fun managedHsmDoesNotAdvertiseOrAttemptCertificateChainReads() =
        runTest {
            val provider =
                AzureKeyVaultCryptoProvider(
                    AzureKmsProviderConfig(
                        id = "azure-chain-capability-test",
                        applicationId = "azure-chain-capability-test",
                        keyvaultUrl = "https://configured.vault.azure.net",
                        tenantId = "tenant-id",
                        hsmType = HSMType.MANAGED_HSM,
                        credentialOpts =
                            CredentialOpts(
                                credentialMode = CredentialMode.SERVICE_CLIENT_SECRET,
                                secretCredentialOpts =
                                    SecretCredentialOpts(
                                        clientId = "client-id",
                                        clientSecretId = "secret-id",
                                        clientSecretMaterial = "secret-material",
                                    ),
                            ),
                    ),
                )

            try {
                assertFalse(provider.getCapabilities().supportsOperation(KmsProviderOperation.GET_CERTIFICATE))

                val result = provider.getCertificate(ProviderCertificateLookup(alias = "certificate"))
                assertTrue(result.isErr)
                assertEquals("UNSUPPORTED_OPERATION", result.error.code)
            } finally {
                provider.close()
            }
        }

    private fun providerWithReader(
        read: suspend (alias: String, version: String?) -> AzureCertificateClientRead,
    ): AzureKeyVaultCryptoProvider =
        AzureKeyVaultCryptoProvider(
            config = azureConfig(),
            certificateClientReader = AzureCertificateClientReader(read),
        )

    private fun azureConfig(): AzureKmsProviderConfig =
        AzureKmsProviderConfig(
            id = LOGICAL_PROVIDER_ID,
            applicationId = LOGICAL_PROVIDER_ID,
            keyvaultUrl = VAULT_URL,
            tenantId = "tenant-id",
            hsmType = HSMType.KEYVAULT,
            credentialOpts =
                CredentialOpts(
                    credentialMode = CredentialMode.SERVICE_CLIENT_SECRET,
                    secretCredentialOpts =
                        SecretCredentialOpts(
                            clientId = "client-id",
                            clientSecretId = "secret-id",
                            clientSecretMaterial = SECRET_MATERIAL,
                        ),
                ),
        )

    private fun certificateRead(
        version: String,
        certificateId: String = "$VAULT_URL/certificates/$SENSITIVE_ALIAS/$version",
        keyId: String = "$VAULT_URL/keys/$SENSITIVE_ALIAS/$version",
    ): AzureCertificateClientRead =
        AzureCertificateClientRead(
            certificateId = certificateId,
            keyId = keyId,
            certificateDer = CERTIFICATE_DER,
        )

    private fun assertPublicLeaf(
        reference: ProviderCertificateReference,
        version: String,
    ) {
        assertEquals(LOGICAL_PROVIDER_ID, reference.providerId)
        assertEquals(SENSITIVE_ALIAS, reference.alias)
        assertEquals("$SENSITIVE_ALIAS:$version", reference.id)
        assertEquals(reference.id, reference.providerCertificateId)
        assertContentEquals(CERTIFICATE_DER, reference.certificateDer)

        val serialized = Json.encodeToString(reference)
        listOf(VAULT_URL, "/keys/", SECRET_MATERIAL, "credential", "private", "accessToken")
            .forEach { forbidden -> assertFalse(serialized.contains(forbidden, ignoreCase = true)) }
    }

    private fun assertSanitizedError(
        result: IdkResult<ProviderCertificateReference, IdkError>,
        expectedCode: String,
        forbidden: List<String>,
    ) {
        assertTrue(result.isErr)
        val error = result.error
        assertEquals(expectedCode, error.code)
        assertFalse(error.hasException())
        val publicError =
            listOf(
                error.code,
                error.message.i18nKey,
                error.message.defaultMessage,
                error.message.i18nParams.toString(),
                error.meta.toString(),
                error.causes.toString(),
            ).joinToString("|")
        forbidden.forEach { value -> assertFalse(publicError.contains(value, ignoreCase = true)) }
    }

    private companion object {
        const val LOGICAL_PROVIDER_ID = "azure-logical-provider"
        const val VAULT_URL = "https://configured.vault.azure.net"
        const val SENSITIVE_ALIAS = "tenant-secret-certificate"
        const val LATEST_VERSION = "latestVersion1"
        const val IMMUTABLE_VERSION = "immutableVersion2"
        const val SECRET_MATERIAL = "client-secret-material"

        val CERTIFICATE_DER =
            certificateFromPem(
                """
                -----BEGIN CERTIFICATE-----
                MIIB3DCCAYMCFA6bjsh9CB8NbtINaWK8WNgBMx2iMAoGCCqGSM49BAMCMHExCzAJ
                BgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlBbXN0
                ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQdGVz
                dC5leGFtcGxlLmNvbTAeFw0yNTA1MDEwOTE5NDFaFw0yNjA1MDEwOTE5NDFaMHEx
                CzAJBgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlB
                bXN0ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQ
                dGVzdC5leGFtcGxlLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABP7W2xjU
                4raapzyctjNDkRLGHP7RgAtVqAHRnS5LWz2oXhgKHyhCcwlLrfCOCEIHta+gajwz
                2mxZ8j6ix1SNXvkwCgYIKoZIzj0EAwIDRwAwRAIgF9E2jWW+qMnmL3qpB5VvJ/8J
                e/K96UVYWQ2T23OA1SYCIAaD8LNo+RgwA0rE7wKKOrogIfQUy+qFPVKjmcDTcHln
                -----END CERTIFICATE-----
                """.trimIndent(),
            ).der
    }
}
