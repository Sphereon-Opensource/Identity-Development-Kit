/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LoggerConfig
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpClientFactoryLoggingTest {
    @Test
    fun defaultHttpClientLoggingDoesNotEnableHeaderLogging() {
        assertEquals(KtorLogLevel.INFO, LoggerConfig.Default.toKtorHttpClientLogLevel())
    }

    @Test
    fun traceHttpClientLoggingKeepsSensitiveHeadersSanitized() {
        assertEquals(KtorLogLevel.ALL, LoggerConfig(minLevel = LogLevel.TRACE).toKtorHttpClientLogLevel())

        assertTrue("Authorization".isSensitiveHttpClientLogHeader())
        assertTrue("authorization".isSensitiveHttpClientLogHeader())
        assertTrue("Proxy-Authorization".isSensitiveHttpClientLogHeader())
        assertTrue("Cookie".isSensitiveHttpClientLogHeader())
        assertTrue("Set-Cookie".isSensitiveHttpClientLogHeader())
        assertTrue("X-Api-Key".isSensitiveHttpClientLogHeader())
        assertFalse("Accept".isSensitiveHttpClientLogHeader())
    }
}
