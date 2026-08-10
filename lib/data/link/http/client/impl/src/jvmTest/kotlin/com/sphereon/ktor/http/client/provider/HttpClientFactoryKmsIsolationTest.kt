/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStoreManager
import com.sphereon.di.context.PrincipalType
import com.sphereon.ktor.http.client.createJvmHttpClientTestAppGraph
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpClientFactoryKmsIsolationTest {
    @Test
    fun defaultClientCreationDoesNotEnterTheKmsProviderGraph() {
        val app = createJvmHttpClientTestAppGraph(application = this)
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("http-client-kms-isolation", principalType = PrincipalType.USER)
        val execution = session.asCoreApiServiceGraph().serviceExecution
        var kmsCalls = 0
        var keyStoreManagerCalls = 0
        val kms =
            proxy<KeyManagerService> { methodName ->
                kmsCalls += 1
                error("Default HTTP client creation must not call KeyManagerService.$methodName")
            }
        val keyStoreManager =
            proxy<KeyStoreManager> { methodName ->
                keyStoreManagerCalls += 1
                error("Default HTTP client creation must not call KeyStoreManager.$methodName")
            }

        HttpClientFactoryJvmImpl(
            execution = execution,
            kms = kms,
            keyStoreManager = keyStoreManager,
        ).createClient(HttpClientOptions.createDefault()).close()

        assertEquals(0, kmsCalls)
        assertEquals(0, keyStoreManagerCalls)
    }
}

private inline fun <reified T> proxy(crossinline invocation: (String) -> Any?): T =
    Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> invocation(method.name) } as T
