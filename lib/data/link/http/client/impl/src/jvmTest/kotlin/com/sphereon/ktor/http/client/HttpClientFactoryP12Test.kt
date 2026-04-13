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

package com.sphereon.ktor.http.client

import io.ktor.http.decodeURLPart

class HttpClientFactoryP12Test {
    val trustStorePath: String =
        this::class.java.classLoader
            .getResource("truststore.p12")
            .file
            .decodeURLPart() // Remove %20 etc from path
    val clientKeyStorePath: String =
        this::class.java.classLoader
            .getResource("client-keystore.p12")
            .path
            .decodeURLPart()
    val serverKeyStorePath: String =
        this::class.java.classLoader
            .getResource("server-keystore.p12")
            .path
            .decodeURLPart()
    val clientKeyStoreUntrustedPath: String =
        this::class.java.classLoader
            .getResource("client-keystore-untrusted.p12")
            .path
            .decodeURLPart()
    val serverKeyStoreUntrustedPath: String =
        this::class.java.classLoader
            .getResource("server-keystore-untrusted.p12")
            .path
            .decodeURLPart()
    val trustStorePassword = "password"
    val clientKeyStorePassword = "password"
    val serverKeyStorePassword = "password"
    val privateKeyPassword = "password"
    val clientCertificateAlias = "my-client-cert"
    val serverKeyAlias = "server-cert"
/*
 Disabled, code could maybe be recylcled for pfx keystore
    @Test
    fun `Should complete mTLS handshake between client and server`() {
        val sslConfig = ClientSslConfig(
            opts = ClientSslConfigOpts(
                certificateAliases = listOf(clientCertificateAlias),
                keyStoreOpts = KeyStoreOpts(
                    source = KeyStoreOpts.Source.File(clientKeyStorePath),
                    keyStorePassword = clientKeyStorePassword,
                    type = KeyStoreType.PKCS12,
                ),
                trustStoreOpts = KeyStoreOpts(
                    source = KeyStoreOpts.Source.File(trustStorePath),
                    keyStorePassword = trustStorePassword,
                    type = KeyStoreType.PKCS12
                )
            )
        )
        val client = HttpClientProvider().createClient(
            HttpClientOptions(
                engine = HttpClientEngineType.CIO,
                sslConfig
            )
        )
        assertNotNull(client)

        val server = startServer(
            ServerOpts(
                port = 0,
                keyStoreOpts = ServerKeyStoreOpts(
                    path = serverKeyStorePath,
                    keyStorePassword = serverKeyStorePassword,
                    keyAlias = serverKeyAlias,
                    privateKeyPassword = privateKeyPassword,
                    type = KeyStoreType.PKCS12
                ),
                trustStoreOpts = ServerTrustStoreOpts(
                    path = trustStorePath,
                    trustStorePassword = trustStorePassword,
                    type = KeyStoreType.PKCS12
                )
            )
        )
        assertNotNull(server)
        val serverUrl = "https://localhost:${server.actualPort()}/"

        client.use { client ->
            try {
                val response = runBlocking {
                    client.get(serverUrl).bodyAsText()
                }
                assertEquals("handshake established", response)
            } finally {
                server.stop()
            }
        }
    }

    @Test
    fun `Should reject mTLS handshake because of untrusted client certificate`() {
        val sslConfig = ClientSslConfig(
            opts = ClientSslConfigOpts(
                certificateAliases = listOf(clientCertificateAlias),
                keyStoreOpts = KeyStoreOpts(
                    source = KeyStoreOpts.Source.File(clientKeyStoreUntrustedPath),
                    keyStorePassword = clientKeyStorePassword,
                    type = KeyStoreType.PKCS12,
                ),
                trustStoreOpts = KeyStoreOpts(
                    source = KeyStoreOpts.Source.File(trustStorePath),
                    keyStorePassword = trustStorePassword,
                    type = KeyStoreType.PKCS12
                )
            )
        )
        val client = HttpClientProvider().createClient(
            HttpClientOptions(
                engine = HttpClientEngineType.CIO,
                sslConfig = sslConfig
            )
        )

        val server = startServer(
            ServerOpts(
                port = 0,
                keyStoreOpts = ServerKeyStoreOpts(
                    path = serverKeyStorePath,
                    keyStorePassword = serverKeyStorePassword,
                    keyAlias = serverKeyAlias,
                    privateKeyPassword = privateKeyPassword,
                    type = KeyStoreType.PKCS12
                ),
                trustStoreOpts = ServerTrustStoreOpts(
                    path = trustStorePath,
                    trustStorePassword = trustStorePassword,
                    type = KeyStoreType.PKCS12
                )
            )
        )
        assertNotNull(server)
        val serverUrl = "https://localhost:${server.actualPort()}/"

        client.use { client ->
            try {
                val exception = assertFailsWith<TlsException> {
                    runBlocking {
                        client.get(serverUrl).bodyAsText()
                    }
                }
                assert(exception.message == "Received alert during handshake. Level: FATAL, code: CertificateUnknown")
            } finally {
                server.stop()
            }
        }
    }

    @Test
    fun `Should reject mTLS handshake because of untrusted server certificate`() {
        val sslConfig = ClientSslConfig(
            opts = ClientSslConfigOpts(
                certificateAliases = listOf(clientCertificateAlias),
                keyStoreOpts = KeyStoreOpts(
                    source = KeyStoreOpts.Source.File(clientKeyStorePath),
                    keyStorePassword = clientKeyStorePassword,
                    type = KeyStoreType.PKCS12,
                ),
                trustStoreOpts = KeyStoreOpts(
                    source = KeyStoreOpts.Source.File(trustStorePath),
                    keyStorePassword = trustStorePassword,
                    type = KeyStoreType.PKCS12
                )
            )
        )
        val client = HttpClientProvider().createClient(
            HttpClientOptions(
                engine = HttpClientEngineType.CIO,
                sslConfig = sslConfig
            )
        )
        assertNotNull(client)

        val server = startServer(
            ServerOpts(
                port = 0,
                keyStoreOpts = ServerKeyStoreOpts(
                    path = serverKeyStoreUntrustedPath,
                    keyStorePassword = serverKeyStorePassword,
                    keyAlias = serverKeyAlias,
                    privateKeyPassword = privateKeyPassword,
                    type = KeyStoreType.PKCS12
                ),
                trustStoreOpts = ServerTrustStoreOpts(
                    path = trustStorePath,
                    trustStorePassword = trustStorePassword,
                    type = KeyStoreType.PKCS12
                )
            )
        )
        assertNotNull(server)
        val serverUrl = "https://localhost:${server.actualPort()}/"

        client.use { client ->
            try {
                val exception = assertFailsWith<CertPathValidatorException> {
                    runBlocking {
                        client.get(serverUrl).bodyAsText()
                    }
                }
                assert(exception.message == "signature check failed")
            } finally {
                server.stop()
            }
        }
    }

    @Test
    fun `Should return supported engine types`() {
        val engineTypes = HttpClientProvider().getSupportedEngineTypes()

        assertEquals(1, engineTypes.size)
        assertTrue(engineTypes.contains(HttpClientEngineType.CIO))
    }

    @Test
    fun `Should successfully perform HTTP request without SSL configuration`() {
        val client = HttpClientProvider().createClient(
            HttpClientOptions(
                engine = HttpClientEngineType.CIO
            )
        )
        assertNotNull(client)

        val server = startServer(ServerOpts(
            port = 8080
        ))
        assertNotNull(server)
        val serverUrl = "https://localhost:${server.actualPort()}/"

        client.use { client ->
            try {
                val response = runBlocking {
                    client.get(serverUrl).bodyAsText()
                }
                assertEquals("handshake established", response)
            } finally {
                server.stop()
            }
        }
    }
*/
}
