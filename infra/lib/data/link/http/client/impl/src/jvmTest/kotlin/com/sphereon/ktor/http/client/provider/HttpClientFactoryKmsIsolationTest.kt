/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.ktor.http.client.provider

import com.sphereon.ktor.http.client.TestSessionExecution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the default factory no longer takes KMS providers and fails closed on mTLS
 * without [lib-data-link-http-client-kms-impl] on the classpath.
 */
class HttpClientFactoryKmsIsolationTest {
    @Test
    fun defaultClientCreationDoesNotRequireKmsProviders() {
        val factory = HttpClientFactoryJvmImpl(execution = TestSessionExecution())
        factory.createClient(HttpClientOptions.createDefault()).close()
        assertEquals(HttpClientEngineType.OKHTTP, factory.getEngineTypeDefault())
    }

    @Test
    fun mtlsWithoutKmsFactoryFailsClosed() {
        val factory = HttpClientFactoryJvmImpl(execution = TestSessionExecution())
        val mtls =
            HttpClientOptions.createDefault().copy(
                sslConfig =
                    com.sphereon.ktor.http.client.config.SslConfig(
                        client =
                            com.sphereon.ktor.http.client.config.ClientSslConfig(
                                defaultCertificate =
                                    com.sphereon.ktor.http.client.config.KeystoreCertificateOpts(
                                        certificateAlias = "client-key",
                                        keyStoreId = "software",
                                    ),
                            ),
                    ),
            )
        assertFailsWith<IllegalStateException> {
            factory.createClient(mtls)
        }
    }
}
