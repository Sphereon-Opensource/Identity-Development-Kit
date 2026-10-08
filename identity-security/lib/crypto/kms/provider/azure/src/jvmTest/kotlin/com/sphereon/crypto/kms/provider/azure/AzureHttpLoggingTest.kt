/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.provider.azure

import com.azure.core.http.HttpPipeline
import com.azure.core.http.policy.HttpLogDetailLevel
import com.azure.core.http.policy.HttpLoggingPolicy
import com.azure.core.util.Configuration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Key Vault request and response bodies carry imported private key material, digests and signatures.
 * These tests inspect the pipelines of the clients the provider actually builds, so an added body
 * logging policy or a body level in the builder's log options fails here.
 */
class AzureHttpLoggingTest {
    @Test
    fun keyClientNeverLogsBodies() {
        assertNoBodyLogging("key client", config().buildKeyAsyncClient().httpPipeline())
    }

    @Test
    fun certificateClientNeverLogsBodies() {
        assertNoBodyLogging("certificate client", config().buildCertificateAsyncClient().httpPipeline())
    }

    @Test
    fun bodyLevelsRequestedThroughConfigurationAreDowngraded() {
        val expected =
            mapOf(
                HttpLogDetailLevel.NONE to HttpLogDetailLevel.NONE,
                HttpLogDetailLevel.BASIC to HttpLogDetailLevel.BASIC,
                HttpLogDetailLevel.HEADERS to HttpLogDetailLevel.HEADERS,
                HttpLogDetailLevel.BODY to HttpLogDetailLevel.BASIC,
                HttpLogDetailLevel.BODY_AND_HEADERS to HttpLogDetailLevel.HEADERS,
            )
        assertEquals(HttpLogDetailLevel.entries.toSet(), expected.keys, "every SDK log level must be covered")
        expected.forEach { (requested, applied) ->
            val level = azureHttpLogOptions(requested).logLevel
            assertEquals(applied, level, "requested $requested")
            assertFalse(level.shouldLogBody(), "requested $requested must not log bodies")
        }
    }

    private fun assertNoBodyLogging(
        client: String,
        pipeline: HttpPipeline,
    ) {
        val levels =
            (0 until pipeline.policyCount)
                .map(pipeline::getPolicy)
                .filterIsInstance<HttpLoggingPolicy>()
                .map { it.detailLevel() }
        assertEquals(1, levels.size, "$client must have exactly the SDK's own logging policy, found $levels")
        val level = levels.single()
        assertFalse(level.shouldLogBody(), "$client logs HTTP bodies at $level")
        if (Configuration.getGlobalConfiguration().get(Configuration.PROPERTY_AZURE_HTTP_LOG_DETAIL_LEVEL) == null) {
            assertEquals(HttpLogDetailLevel.NONE, level, "$client must not log HTTP traffic unless an operator opts in")
        }
    }

    private fun Any.httpPipeline(): HttpPipeline =
        javaClass.getDeclaredMethod("getHttpPipeline").apply { isAccessible = true }.invoke(this) as HttpPipeline

    private fun HttpLoggingPolicy.detailLevel(): HttpLogDetailLevel =
        HttpLoggingPolicy::class.java.getDeclaredField("httpLogDetailLevel").apply { isAccessible = true }.get(this)
            as HttpLogDetailLevel

    private fun config(): AzureKmsProviderConfig =
        AzureKmsProviderConfig(
            id = "azure-http-logging-test",
            applicationId = "azure-http-logging-test",
            keyvaultUrl = "https://configured.vault.azure.net",
            tenantId = "tenant-id",
            hsmType = HSMType.KEYVAULT,
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
        )
}
