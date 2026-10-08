/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.provider.azure

import com.sphereon.crypto.core.kms.ProviderNativeObjectLookup
import com.sphereon.crypto.core.kms.ProviderNativeObjectType
import com.sphereon.crypto.core.x509.certificateFromPem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AzureTenantAssignmentCapabilityTest {
    @Test
    fun standardKeyVaultKeyUsesExactNameAndVersionPropertiesForAssignment() =
        runTest {
            val reader =
                FakeAzureKeyTenantAssignmentReader(
                    reads =
                        mapOf(
                            ("tenant-signing" to "version1") to
                                AzureKeyClientRead(
                                    keyId = "$VAULT_URL/keys/tenant-signing/version1",
                                    name = "tenant-signing",
                                    version = "version1",
                                    tags = mapOf("sphereon-tenant-id" to "tenant-a"),
                                ),
                        ),
                )

            val result =
                provider(reader).isAssignedToTenant(
                    ProviderNativeObjectLookup(
                        type = ProviderNativeObjectType.KEY,
                        alias = "tenant-signing",
                        id = "tenant-signing:version1",
                    ),
                    tenantId = "tenant-a",
                )

            assertTrue(result)
            assertTrue(reader.readsSeen.single() == ("tenant-signing" to "version1"))
        }

    @Test
    fun standardKeyVaultCertificateUsesExactNameAndVersionPropertiesForAssignment() =
        runTest {
            val reader =
                AzureCertificateClientReader { alias, version ->
                    assertTrue(alias == "tenant-certificate")
                    assertTrue(version == "version2")
                    AzureCertificateClientRead(
                        certificateId = "$VAULT_URL/certificates/tenant-certificate/version2",
                        keyId = "$VAULT_URL/keys/tenant-certificate/version2",
                        certificateDer = CERTIFICATE_DER,
                        tags = mapOf("sphereon-tenant-id" to "tenant-a"),
                    )
                }

            val result =
                provider(
                    keyReader = FakeAzureKeyTenantAssignmentReader(emptyMap()),
                    certificateReader = reader,
                ).isAssignedToTenant(
                    ProviderNativeObjectLookup(
                        type = ProviderNativeObjectType.CERTIFICATE,
                        alias = "tenant-certificate",
                        id = "tenant-certificate:version2",
                    ),
                    tenantId = "tenant-a",
                )

            assertTrue(result)
        }

    @Test
    fun missingOrWrongTenantTagDoesNotAssignAzureObjects() =
        runTest {
            val reader =
                FakeAzureKeyTenantAssignmentReader(
                    reads =
                        mapOf(
                            ("tenant-signing" to null) to
                                AzureKeyClientRead(
                                    keyId = "$VAULT_URL/keys/tenant-signing/version3",
                                    name = "tenant-signing",
                                    version = "version3",
                                    tags = mapOf("other-tag" to "tenant-a"),
                                ),
                        ),
                )

            assertFalse(
                provider(reader).isAssignedToTenant(
                    ProviderNativeObjectLookup(ProviderNativeObjectType.KEY, "tenant-signing"),
                    "tenant-a",
                ),
            )

            val missingTagReader =
                FakeAzureKeyTenantAssignmentReader(
                    reads =
                        mapOf(
                            ("tenant-signing" to null) to
                                AzureKeyClientRead(
                                    keyId = "$VAULT_URL/keys/tenant-signing/version3",
                                    name = "tenant-signing",
                                    version = "version3",
                                    tags = emptyMap(),
                                ),
                        ),
                )
            assertFalse(
                provider(missingTagReader).isAssignedToTenant(
                    ProviderNativeObjectLookup(ProviderNativeObjectType.KEY, "tenant-signing"),
                    "tenant-a",
                ),
            )
        }

    @Test
    fun versionAndAliasMismatchFailsClosed() =
        runTest {
            val reader =
                FakeAzureKeyTenantAssignmentReader(
                    reads =
                        mapOf(
                            ("tenant-signing" to "version2") to
                                AzureKeyClientRead(
                                    keyId = "$VAULT_URL/keys/tenant-signing/version1",
                                    name = "tenant-signing",
                                    version = "version1",
                                    tags = mapOf("sphereon-tenant-id" to "tenant-a"),
                                ),
                        ),
                )

            assertFalse(
                provider(reader).isAssignedToTenant(
                    ProviderNativeObjectLookup(
                        type = ProviderNativeObjectType.KEY,
                        alias = "tenant-signing",
                        id = "other-signing:version1",
                    ),
                    "tenant-a",
                ),
            )
            assertFalse(
                provider(reader).isAssignedToTenant(
                    ProviderNativeObjectLookup(
                        type = ProviderNativeObjectType.KEY,
                        alias = "tenant-signing",
                        id = "tenant-signing:version2",
                    ),
                    "tenant-a",
                ),
            )
            assertEquals(listOf<Pair<String, String?>>("tenant-signing" to "version2"), reader.readsSeen)
        }

    @Test
    fun managedHsmCertificateAssignmentIsUnsupportedWithoutARead() =
        runTest {
            val result =
                managedHsmProvider().isAssignedToTenant(
                    ProviderNativeObjectLookup(ProviderNativeObjectType.CERTIFICATE, "certificate"),
                    "tenant-a",
                )

            assertFalse(result)
        }

    @Test
    fun cancellationFromAzureKeyReadIsPropagated() =
        runTest {
            val cancellation = CancellationException("cancel assignment check")
            val reader = FakeAzureKeyTenantAssignmentReader(emptyMap(), failure = cancellation)

            val propagated =
                kotlin.test.assertFailsWith<CancellationException> {
                    provider(reader).isAssignedToTenant(
                        ProviderNativeObjectLookup(ProviderNativeObjectType.KEY, "tenant-signing"),
                        "tenant-a",
                    )
                }
            assertSame(cancellation, propagated)
        }

    private fun provider(
        keyReader: FakeAzureKeyTenantAssignmentReader,
        certificateReader: AzureCertificateClientReader = AzureCertificateClientReader { _, _ -> error("not used") },
    ): AzureKeyVaultCryptoProvider =
        AzureKeyVaultCryptoProvider(
            config = azureConfig(),
            keyTenantAssignmentReader = keyReader,
            certificateClientReader = certificateReader,
        )

    private fun managedHsmProvider(): AzureKeyVaultCryptoProvider =
        AzureKeyVaultCryptoProvider(
            AzureKmsProviderConfig(
                id = "azure-managed-hsm-assignment-test",
                applicationId = "azure-managed-hsm-assignment-test",
                keyvaultUrl = VAULT_URL,
                tenantId = "tenant-id",
                hsmType = HSMType.MANAGED_HSM,
                credentialOpts = credentialOpts(),
            ),
        )

    private fun azureConfig(): AzureKmsProviderConfig =
        AzureKmsProviderConfig(
            id = "azure-assignment-test",
            applicationId = "azure-assignment-test",
            keyvaultUrl = VAULT_URL,
            tenantId = "tenant-id",
            hsmType = HSMType.KEYVAULT,
            credentialOpts = credentialOpts(),
        )

    private fun credentialOpts() =
        CredentialOpts(
            credentialMode = CredentialMode.SERVICE_CLIENT_SECRET,
            secretCredentialOpts =
                SecretCredentialOpts(
                    clientId = "client-id",
                    clientSecretId = "secret-id",
                    clientSecretMaterial = "secret-material",
                ),
        )

    private class FakeAzureKeyTenantAssignmentReader(
        private val reads: Map<Pair<String, String?>, AzureKeyClientRead>,
        private val failure: Throwable? = null,
    ) : AzureKeyTenantAssignmentReader {
        val readsSeen = mutableListOf<Pair<String, String?>>()

        override suspend fun read(name: String, version: String?): AzureKeyClientRead {
            failure?.let { throw it }
            readsSeen += name to version
            return reads.getValue(name to version)
        }
    }

    private companion object {
        const val VAULT_URL = "https://configured.vault.azure.net"
        val CERTIFICATE_DER =
            certificateFromPem(
                """
                -----BEGIN CERTIFICATE-----
                MIIB3DCCAYMCFA6bjsh9CB8NbtINaWK8WNgBMx2iMAoGCCqGSM49BAMCMHExCzAJ
                BgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlBbXN0
                ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQdGVz
                dC5leGFtcGxlLmNvbTAeFw0yNTA1MDEwOTE5NDFaFw0yNjA1MDEwOTE5NDFaMHEx
                CzAJBgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlB
                bXN0ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQdGVz
                dC5leGFtcGxlLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABP7W2xjU
                4raapzyctjNDkRLGHP7RgAtVqAHRnS5LWz2oXhgKHyhCcwlLrfCOCEIHta+gajwz
                2mxZ8j6ix1SNXvkwCgYIKoZIzj0EAwIDRwAwRAIgF9E2jWW+qMnmL3qpB5VvJ/8J
                e/K96UVYWQ2T23OA1SYCIAaD8LNo+RgwA0rE7wKKOrogIfQUy+qFPVKjmcDTcHln
                -----END CERTIFICATE-----
                """.trimIndent(),
            ).der
    }
}
