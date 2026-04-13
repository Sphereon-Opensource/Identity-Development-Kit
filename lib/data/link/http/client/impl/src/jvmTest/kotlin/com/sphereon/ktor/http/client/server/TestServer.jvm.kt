/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.ktor.http.client.server

import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import java.io.File
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

actual interface TestServer {
    actual fun actualPort(): Int

    actual fun stop()
}

actual fun startServer(opts: ServerOpts): TestServer {
    // 1) Load keystore & truststore
    val ksOpts = opts.keyStoreOpts!!
    val keyStore = loadKeyStore(ksOpts.path, ksOpts.keyStorePassword, ksOpts.type)
    val trustStore =
        opts.trustStoreOpts?.let { ts ->
            loadKeyStore(ts.path, ts.trustStorePassword, ts.type)
        } ?: keyStore

    // 2) Set up KeyManager & TrustManager
    val kmf =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, ksOpts.privateKeyPassword.toCharArray())
        }
    val tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }

    // 3) Initialize SSLContext for TLS
    val sslContext =
        SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, tmf.trustManagers, null)
        }

    // 4) Create HttpsServer (port 0 = ephemeral) and enforce client‐auth
    val server =
        HttpsServer.create(InetSocketAddress(opts.port), 0).apply {
            httpsConfigurator =
                object : HttpsConfigurator(sslContext) {
                    override fun configure(params: HttpsParameters) {
                        // Use default SSLParameters and require client certs
                        val sslParams = sslContext.defaultSSLParameters
                        sslParams.needClientAuth = true
                        params.setSSLParameters(sslParams)
                    }
                }

            // 5) Define simple endpoints
            createContext("/") { exchange ->
                val resp = "handshake established"
                val responseBytes = resp.toByteArray()
                exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                exchange.responseBody.use { os ->
                    os.write(responseBytes)
                    os.flush() // Explicitly flush the stream
                }
            }
            createContext("/hello") { exchange ->
                val resp = "Hello from mTLS Server"
                val responseBytes = resp.toByteArray()
                exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                exchange.responseBody.use { os ->
                    os.write(responseBytes)
                    os.flush() // Explicitly flush the stream
                }
            }

            executor = null
            start()
        }

    // 6) Return a TestServer that wraps this HttpsServer
    return object : TestServer {
        override fun actualPort(): Int = (server.address as InetSocketAddress).port

        override fun stop() {
            server.stop(0)
        }
    }
}

// Reuse your existing loader
private fun loadKeyStore(
    path: String,
    password: String,
    type: PredefinedKeyStoreTypes,
): KeyStore {
    require(
        type == PredefinedKeyStoreTypes.PKCS12 ||
            type == PredefinedKeyStoreTypes.JKS,
    ) {
        "A keystore needs to be of config type ${PredefinedKeyStoreTypes.PKCS12} or ${PredefinedKeyStoreTypes.JKS}"
    }
    return KeyStore.getInstance(type.name).apply {
        File(path).inputStream().use { load(it, password.toCharArray()) }
    }
}
