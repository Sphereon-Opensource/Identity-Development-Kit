/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.di.context.PrincipalType
import com.sphereon.ktor.http.client.createJvmHttpClientTestAppGraph
import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpClientFactoryRedirectSecurityTest {
    @Test
    fun redirectPolicyCannotBeOverriddenByAdditionalClientConfiguration() = runTest {
        val redirectRequests = AtomicInteger(0)
        val finalRequests = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/start") { exchange ->
            redirectRequests.incrementAndGet()
            exchange.responseHeaders.add("Location", "/final")
            exchange.sendResponseHeaders(HttpStatusCode.Found.value, 0)
            exchange.responseBody.use { }
        }
        server.createContext("/final") { exchange ->
            finalRequests.incrementAndGet()
            val body = "final".encodeToByteArray()
            exchange.sendResponseHeaders(HttpStatusCode.OK.value, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val app = createJvmHttpClientTestAppGraph(application = this)
            val session =
                app.userContextManager
                    .getAnonymous()
                    .sessionContextManager
                    .createOrGetFromId("http-client-redirect-security", principalType = PrincipalType.USER)
            val factory =
                HttpClientFactoryJvmImpl(
                    execution = session.asCoreApiServiceGraph().serviceExecution,
                    kms = dev.zacsweers.metro.Provider { error("KMS must not be resolved") },
                    keyStoreManager = dev.zacsweers.metro.Provider { error("keystore manager must not be resolved") },
                )
            val client =
                factory.createClient(
                    HttpClientOptions.createDefault().copy(
                        followRedirects = false,
                        additionalConfig = { followRedirects = true },
                    ),
                )
            client.use {
                val response = it.get("http://127.0.0.1:${server.address.port}/start")
                assertEquals(HttpStatusCode.Found, response.status)
                assertEquals("", response.bodyAsText())
            }
        } finally {
            server.stop(0)
        }

        assertEquals(1, redirectRequests.get())
        assertEquals(0, finalRequests.get())
    }
}
