package com.sphereon.crypto.kms.provider.aws

import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.model.AccessKeyCredentialOpts
import com.sphereon.crypto.core.kms.model.AwsKmsClientConfig
import com.sphereon.crypto.core.kms.model.CredentialMode
import com.sphereon.crypto.core.kms.model.CredentialOpts
import com.sphereon.crypto.core.kms.model.KeyProviderConfig
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.KeyProviderType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwsKmsProviderCapabilitiesTest {
    @Test
    fun awsDoesNotAdvertiseProviderNativeCertificateReads() {
        val provider =
            AwsKmsCryptoProvider(
                KeyProviderSettings(
                    id = "aws-capability-test",
                    config =
                        KeyProviderConfig(
                            type = KeyProviderType.AWS_KMS,
                            aws =
                                AwsKmsClientConfig(
                                    region = "eu-west-1",
                                    credentialOpts =
                                        CredentialOpts(
                                            credentialMode = CredentialMode.ACCESS_KEY,
                                            accessKeyCredentialOpts =
                                                AccessKeyCredentialOpts(
                                                    credentialsSecretId = "test-credentials",
                                                    accessKeyId = "test-access-key",
                                                    secretAccessKey = "test-secret-key",
                                                ),
                                        ),
                                ),
                        ),
                ),
            )

        try {
            val capabilities = provider.getCapabilities()
            assertFalse(capabilities.supportsOperation(KmsProviderOperation.GET_CERTIFICATE))
            assertFalse(capabilities.operations.any { it.operation == KmsProviderOperation.GET_CERTIFICATE })
        } finally {
            provider.close()
        }
    }

    @Test
    fun awsAdvertisesExistingKeyReferenceRegistrationSeparatelyFromImportAndGeneration() {
        val provider =
            AwsKmsCryptoProvider(
                KeyProviderSettings(
                    id = "aws-reference-capability-test",
                    config =
                        KeyProviderConfig(
                            type = KeyProviderType.AWS_KMS,
                            aws =
                                AwsKmsClientConfig(
                                    region = "eu-west-1",
                                    credentialOpts =
                                        CredentialOpts(
                                            credentialMode = CredentialMode.ACCESS_KEY,
                                            accessKeyCredentialOpts =
                                                AccessKeyCredentialOpts(
                                                    credentialsSecretId = "test-credentials",
                                                    accessKeyId = "test-access-key",
                                                    secretAccessKey = "test-secret-key",
                                                ),
                                        ),
                                ),
                        ),
                ),
            )

        try {
            val capabilities = provider.getCapabilities()
            assertTrue(capabilities.supportsOperation(KmsProviderOperation.REGISTER_KEY_REFERENCE))
            assertTrue(capabilities.supportsOperation(KmsProviderOperation.IMPORT_KEY))
            assertTrue(capabilities.supportsOperation(KmsProviderOperation.GENERATE_KEY))
        } finally {
            provider.close()
        }
    }
}
