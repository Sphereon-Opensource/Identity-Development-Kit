/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.provider.aws

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import com.sphereon.crypto.core.kms.ProviderNativeObjectLookup
import com.sphereon.crypto.core.kms.ProviderNativeObjectType

class AwsTenantAssignmentCapabilityTest {
    @Test
    fun awsProviderExposesTheOptionalTenantAssignmentCapability() {
        assertTrue(
            AwsKmsCryptoProvider::class.java.interfaces.any {
                it.simpleName == "ProviderTenantAssignmentVerifier"
            },
            "AWS KMS must expose the optional provider tenant-assignment capability",
        )
    }

    @Test
    fun matchingTenantTagOnSecondTagPageAssignsTheExactAliasedKey() =
        runTest {
            val keyId = "00000000-0000-0000-0000-000000000001"
            val keyArn = "arn:aws:kms:eu-west-1:123456789012:key/$keyId"
            val reader =
                FakeAwsTenantAssignmentReader(
                    descriptions =
                        mapOf(
                            "alias/tenant-signing" to AwsKmsKeyIdentity(keyId, keyArn),
                            keyArn to AwsKmsKeyIdentity(keyId, keyArn),
                        ),
                    tagPages =
                        listOf(
                            AwsKmsResourceTagPage(
                                tags = listOf("unrelated" to "value"),
                                truncated = true,
                                nextMarker = "page-2",
                            ),
                            AwsKmsResourceTagPage(
                                tags = listOf("sphereon-tenant-id" to "tenant-a"),
                                truncated = false,
                                nextMarker = null,
                            ),
                        ),
                )
            val provider = provider(reader)

            assertTrue(
                provider.isAssignedToTenant(
                    ProviderNativeObjectLookup(
                        type = ProviderNativeObjectType.KEY,
                        alias = "tenant-signing",
                        id = keyArn,
                    ),
                    tenantId = "tenant-a",
                ),
            )
            assertEquals(listOf("alias/tenant-signing", keyArn), reader.describeReferences)
            assertEquals(listOf(null, "page-2"), reader.tagMarkers)
            assertEquals(listOf(keyId, keyId), reader.tagKeyIds)
        }

    @Test
    fun missingOrWrongTenantTagDoesNotAssignTheKey() =
        runTest {
            val keyId = "00000000-0000-0000-0000-000000000002"
            val keyArn = "arn:aws:kms:eu-west-1:123456789012:key/$keyId"
            val reader =
                FakeAwsTenantAssignmentReader(
                    descriptions = mapOf("alias/tenant-signing" to AwsKmsKeyIdentity(keyId, keyArn)),
                    tagPages =
                        listOf(
                            AwsKmsResourceTagPage(
                                tags = listOf("sphereon-tenant-id" to "tenant-b"),
                                truncated = false,
                                nextMarker = null,
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
                FakeAwsTenantAssignmentReader(
                    descriptions = mapOf("alias/tenant-signing" to AwsKmsKeyIdentity(keyId, keyArn)),
                    tagPages =
                        listOf(
                            AwsKmsResourceTagPage(
                                tags = emptyList(),
                                truncated = false,
                                nextMarker = null,
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
    fun aliasAndKeyIdentityMismatchFailsClosedBeforeTagInspection() =
        runTest {
            val aliasKeyId = "00000000-0000-0000-0000-000000000003"
            val aliasArn = "arn:aws:kms:eu-west-1:123456789012:key/$aliasKeyId"
            val requestedKeyId = "00000000-0000-0000-0000-000000000004"
            val requestedArn = "arn:aws:kms:eu-west-1:123456789012:key/$requestedKeyId"
            val reader =
                FakeAwsTenantAssignmentReader(
                    descriptions =
                        mapOf(
                            "alias/tenant-signing" to AwsKmsKeyIdentity(aliasKeyId, aliasArn),
                            requestedArn to AwsKmsKeyIdentity(requestedKeyId, requestedArn),
                        ),
                    tagPages =
                        listOf(
                            AwsKmsResourceTagPage(
                                tags = listOf("sphereon-tenant-id" to "tenant-a"),
                                truncated = false,
                                nextMarker = null,
                            ),
                        ),
                )

            assertFalse(
                provider(reader).isAssignedToTenant(
                    ProviderNativeObjectLookup(ProviderNativeObjectType.KEY, "tenant-signing", requestedArn),
                    "tenant-a",
                ),
            )
            assertTrue(reader.tagKeyIds.isEmpty())
        }

    @Test
    fun certificateAssignmentIsUnsupportedWithoutCallingAwsKms() =
        runTest {
            val reader =
                FakeAwsTenantAssignmentReader(
                    descriptions = emptyMap(),
                    tagPages = emptyList(),
                )

            assertFalse(
                provider(reader).isAssignedToTenant(
                    ProviderNativeObjectLookup(ProviderNativeObjectType.CERTIFICATE, "certificate"),
                    "tenant-a",
                ),
            )
            assertTrue(reader.describeReferences.isEmpty())
            assertTrue(reader.tagKeyIds.isEmpty())
        }

    @Test
    fun cancellationFromAwsLookupIsPropagated() =
        runTest {
            val cancellation = CancellationException("cancel assignment check")
            val reader =
                FakeAwsTenantAssignmentReader(
                    descriptions = emptyMap(),
                    tagPages = emptyList(),
                    failure = cancellation,
                )

            val propagated =
                kotlin.test.assertFailsWith<CancellationException> {
                    provider(reader).isAssignedToTenant(
                        ProviderNativeObjectLookup(ProviderNativeObjectType.KEY, "tenant-signing"),
                        "tenant-a",
                    )
                }
            assertTrue(propagated === cancellation)
        }

    private fun provider(reader: AwsKmsTenantAssignmentReader): AwsKmsCryptoProvider =
        AwsKmsCryptoProvider(
            settings = testSettings(),
            tenantAssignmentReader = reader,
        )

    private fun testSettings() =
        com.sphereon.crypto.core.kms.model.KeyProviderSettings(
            id = "aws-assignment-test",
            config =
                com.sphereon.crypto.core.kms.model.KeyProviderConfig(
                    type = com.sphereon.crypto.core.kms.model.KeyProviderType.AWS_KMS,
                    aws =
                        com.sphereon.crypto.core.kms.model.AwsKmsClientConfig(
                            region = "eu-west-1",
                            credentialOpts =
                                com.sphereon.crypto.core.kms.model.CredentialOpts(
                                    credentialMode = com.sphereon.crypto.core.kms.model.CredentialMode.DEFAULT_CHAIN,
                                ),
                        ),
                ),
        )

    private class FakeAwsTenantAssignmentReader(
        private val descriptions: Map<String, AwsKmsKeyIdentity>,
        private val tagPages: List<AwsKmsResourceTagPage>,
        private val failure: Throwable? = null,
    ) : AwsKmsTenantAssignmentReader {
        val describeReferences = mutableListOf<String>()
        val tagMarkers = mutableListOf<String?>()
        val tagKeyIds = mutableListOf<String>()

        override suspend fun describeKey(keyReference: String): AwsKmsKeyIdentity {
            failure?.let { throw it }
            describeReferences += keyReference
            return descriptions.getValue(keyReference)
        }

        override suspend fun listResourceTags(keyId: String, marker: String?): AwsKmsResourceTagPage {
            failure?.let { throw it }
            tagKeyIds += keyId
            tagMarkers += marker
            return tagPages[tagMarkers.lastIndex]
        }
    }
}
