/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.provider.aws

import com.sphereon.crypto.core.kms.model.CredentialMode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AwsKmsProviderConfigBindingTest {
    private val config = AwsKmsTestConfig(this)

    @AfterTest
    fun reset() = config.reset()

    @Test
    fun providerIsCreatedFromKebabCaseRuntimeConfiguration() {
        config.putIfAbsent(
            "kms.providers.aws-binding-test.id" to "aws-binding-test",
            "kms.providers.aws-binding-test.type" to "aws_kms",
            "kms.providers.aws-binding-test.region" to "eu-west-1",
            "kms.providers.aws-binding-test.endpoint-url" to "http://127.0.0.1:4566",
            "kms.providers.aws-binding-test.credential-opts.credential-mode" to "DEFAULT_CHAIN",
        )

        val providerConfig = assertIs<AwsKmsProviderConfig>(config.providerConfig("aws-binding-test"))
        assertEquals("aws-binding-test", providerConfig.id)
        assertEquals("eu-west-1", providerConfig.region)
        assertEquals("http://127.0.0.1:4566", providerConfig.endpointUrl)
        assertEquals(CredentialMode.DEFAULT_CHAIN, providerConfig.credentialOpts.credentialMode)

        val provider = assertIs<AwsKmsCryptoProvider>(config.createProvider("aws-binding-test"))
        try {
            assertEquals("aws-binding-test", provider.id)
        } finally {
            provider.close()
        }
    }

    @Test
    fun accessKeyCredentialsBindTheirSecretReferenceFromNestedKeys() {
        config.putIfAbsent(
            "kms.providers.aws-secret-ref-test.id" to "aws-secret-ref-test",
            "kms.providers.aws-secret-ref-test.type" to "aws_kms",
            "kms.providers.aws-secret-ref-test.region" to "eu-west-1",
            "kms.providers.aws-secret-ref-test.credential-opts.credential-mode" to "ACCESS_KEY",
            "kms.providers.aws-secret-ref-test.credential-opts.access-key-credential-opts.credentials-secret-id" to "sec_aws_binding_test",
            "kms.providers.aws-secret-ref-test.exponential-backoff-retry-opts.max-retries" to 3,
            "kms.providers.aws-secret-ref-test.exponential-backoff-retry-opts.base-delay-in-ms" to 250L,
        )

        val providerConfig = assertIs<AwsKmsProviderConfig>(config.providerConfig("aws-secret-ref-test"))
        assertEquals(CredentialMode.ACCESS_KEY, providerConfig.credentialOpts.credentialMode)
        val accessKeyOpts = assertNotNull(providerConfig.credentialOpts.accessKeyCredentialOpts)
        assertEquals("sec_aws_binding_test", accessKeyOpts.credentialsSecretId)
        assertNull(accessKeyOpts.accessKeyId)
        assertNull(accessKeyOpts.secretAccessKey)
        val retryOpts = assertNotNull(providerConfig.exponentialBackoffRetryOpts)
        assertEquals(3, retryOpts.maxRetries)
        assertEquals(250L, retryOpts.baseDelayInMS)
    }

    @Test
    fun accessKeyMaterialInConfigurationIsRejected() {
        config.putIfAbsent(
            "kms.providers.aws-material-test.id" to "aws-material-test",
            "kms.providers.aws-material-test.type" to "aws_kms",
            "kms.providers.aws-material-test.region" to "eu-west-1",
            "kms.providers.aws-material-test.credential-opts.credential-mode" to "ACCESS_KEY",
            "kms.providers.aws-material-test.credential-opts.access-key-credential-opts.credentials-secret-id" to "sec_aws_binding_test",
            "kms.providers.aws-material-test.credential-opts.access-key-credential-opts.secret-access-key" to "binding-test-secret",
        )

        assertFailsWith<IllegalArgumentException> {
            config.providerConfig("aws-material-test")
        }
    }

    @Test
    fun noBuildEnvironmentIsCompiledIntoTheModule() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName("com.sphereon.crypto.kms.aws.BuildKonfig")
        }
    }
}
