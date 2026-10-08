/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.provider.aws

import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.kms.BackendKeyOperationProofProvider
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.model.AccessKeyCredentialOpts
import com.sphereon.crypto.core.kms.model.AwsKmsClientConfig
import com.sphereon.crypto.core.kms.model.CredentialMode
import com.sphereon.crypto.core.kms.model.CredentialOpts
import com.sphereon.crypto.core.kms.model.KeyProviderConfig
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.KeyProviderType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AwsKmsCredentialsProviderTest {
    @Test
    fun explicitEndpointPerformsBackendProofAgainstConfiguredKms() {
        val endpoint = System.getenv("AWS_KMS_TEST_ENDPOINT") ?: return
        val keyArn = System.getenv("AWS_KMS_TEST_KEY_ARN") ?: return
        val provider =
            AwsKmsCryptoProvider(
                KeyProviderSettings(
                    id = "secret-management-tier0",
                    config =
                        KeyProviderConfig(
                            type = KeyProviderType.AWS_KMS,
                            aws =
                                AwsKmsClientConfig(
                                    region = "eu-central-1",
                                    endpointUrl = endpoint,
                                    credentialOpts = CredentialOpts(credentialMode = CredentialMode.DEFAULT_CHAIN),
                                ),
                        ),
                ),
            )

        try {
            val result =
                runBlocking {
                    (provider as BackendKeyOperationProofProvider).encryptWithBackendKeyProof(
                        keyInfo = KeyInfo<KeyType>(kid = keyArn, alias = keyArn, providerId = provider.id, noCache = true),
                        plaintext = ByteArray(32) { it.toByte() },
                        algorithm = ContentEncryptionAlgorithm.A256GCM,
                        additionalAuthenticatedData = "endpoint-smoke-test".encodeToByteArray(),
                    )
                }
            assertTrue(result.backendKeyIdentityDigest.matches(Regex("sha256:[0-9a-f]{64}")))
        } finally {
            provider.close()
        }
    }

    @Test
    fun defaultChainModeSupportsWorkloadAndEnvironmentCredentials() {
        val provider = awsKmsCredentialsProvider(
            AwsKmsClientConfig(
                region = "eu-west-1",
                credentialOpts = CredentialOpts(credentialMode = CredentialMode.DEFAULT_CHAIN),
            ),
        )

        assertIs<DefaultChainCredentialsProvider>(provider)
    }

    @Test
    fun accessKeyModeInstallsOnlyTheExplicitStagedMaterial() {
        val provider = awsKmsCredentialsProvider(
            AwsKmsClientConfig(
                region = "eu-west-1",
                credentialOpts = CredentialOpts(
                    credentialMode = CredentialMode.ACCESS_KEY,
                    accessKeyCredentialOpts = AccessKeyCredentialOpts(
                        credentialsSecretId = "sec_aws_platform_static_0001",
                        accessKeyId = "AKIAEXPLICIT",
                        secretAccessKey = "explicit-secret",
                        sessionToken = "explicit-session",
                    ),
                ),
            ),
        )

        val static = assertIs<StaticCredentialsProvider>(provider)
        assertEquals("AKIAEXPLICIT", static.credentials.accessKeyId)
        assertEquals("explicit-secret", static.credentials.secretAccessKey)
        assertEquals("explicit-session", static.credentials.sessionToken)
    }

    @Test
    fun accessKeyModeFailsClosedWhenMaterialWasNotStaged() {
        assertFailsWith<IllegalArgumentException> {
            awsKmsCredentialsProvider(
                AwsKmsClientConfig(
                    region = "eu-west-1",
                    credentialOpts = CredentialOpts(
                        credentialMode = CredentialMode.ACCESS_KEY,
                        accessKeyCredentialOpts = AccessKeyCredentialOpts(
                            credentialsSecretId = "sec_aws_platform_static_0001",
                        ),
                    ),
                ),
            )
        }
    }
}
