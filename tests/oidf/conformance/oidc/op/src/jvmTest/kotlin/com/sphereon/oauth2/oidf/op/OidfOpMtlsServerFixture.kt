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
 */

package com.sphereon.oauth2.oidf.op

import com.sphereon.ktor.server.inject.ClientCertificateChainAttributeKey
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationCall
import io.netty.handler.ssl.SslHandler
import kotlinx.coroutines.runBlocking
import java.security.KeyPair
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * In-process HTTPS test fixture exercising RFC 8705 (mTLS) client authentication and
 * cnf.x5t#S256 access-token binding against the OIDF OP harness. Sibling to
 * [OidfOpServerFixture] which boots the same harness over plain HTTP for the 57 non-mTLS
 * conformance tests.
 *
 * Layout:
 *  - A self-signed RSA-2048 CA is created at construction time. The same CA signs the server
 *    cert (CN=localhost, SAN dnsName=localhost + ipAddress=127.0.0.1) and the per-client certs
 *    issued for the mTLS test clients. Tests pull [trustStore] for their HTTP client side and
 *    [clientKeyPairGood] / [clientKeyPairUntrusted] for the cert presentation.
 *  - Ktor Netty engine with `sslConnector(...)` whose trustStore is set: per Netty's
 *    `NettyChannelInitializer.configurePipeline` (Ktor 3.4) this triggers
 *    `SSLEngine.setNeedClientAuth(true)`, so the TLS handshake refuses any client that does
 *    not present a cert chaining to the CA.
 *  - A pre-routing application interceptor reads the SslHandler peer chain from the Netty
 *    pipeline and writes it into [ClientCertificateChainAttributeKey] so commonMain code (the
 *    OAuth2 AS client-cert extractor and the resource-server cnf.x5t#S256 validator) sees the
 *    leaf cert without depending on Ktor engine internals.
 *
 * Discovery is served on the same HTTPS endpoint; for the in-process fixture there is no
 * separate `mtls.example.com` host. Tests that need the spec-aligned `mtls_endpoint_aliases`
 * read it from the discovery JSON.
 */
class OidfOpMtlsServerFixture {
    private val caKeyPair: KeyPair = OidfOpMtlsCerts.generateRsaKeyPair()
    private val caCert: X509Certificate =
        OidfOpMtlsCerts.createSelfSignedCa(subjectDn = "CN=OidfOpMtls-TestCA", keyPair = caKeyPair)

    private val serverKeyPair: KeyPair = OidfOpMtlsCerts.generateRsaKeyPair()
    private val serverCert: X509Certificate =
        OidfOpMtlsCerts.createSignedCertificate(
            subjectDn = "CN=localhost,O=Sphereon-OIDF-Mtls-Test",
            subjectPublicKey = serverKeyPair.public,
            issuerCert = caCert,
            issuerPrivateKey = caKeyPair.private,
            addLocalhostSans = true,
        )

    /** RSA-2048 keypair the test client presents at the TLS handshake; published as `oidf-op-mtls.jwks.0.*`. */
    val clientKeyPairGood: KeyPair = OidfOpMtlsCerts.generateRsaKeyPair()
    val clientCertGood: X509Certificate =
        OidfOpMtlsCerts.createSignedCertificate(
            subjectDn = "CN=oidf-op-mtls,O=Sphereon-OIDF-Mtls-Test",
            subjectPublicKey = clientKeyPairGood.public,
            issuerCert = caCert,
            issuerPrivateKey = caKeyPair.private,
            addLocalhostSans = false,
        )

    /**
     * Untrusted-by-the-AS client keypair: chains to the same CA so the TLS handshake itself
     * succeeds, but the public key is NOT in the registered `oidf-op-mtls.jwks` list, so RFC
     * 8705 self_signed_tls_client_auth verification at /token MUST fail.
     */
    val clientKeyPairUntrusted: KeyPair = OidfOpMtlsCerts.generateRsaKeyPair()
    val clientCertUntrusted: X509Certificate =
        OidfOpMtlsCerts.createSignedCertificate(
            subjectDn = "CN=oidf-op-mtls-untrusted,O=Sphereon-OIDF-Mtls-Test",
            subjectPublicKey = clientKeyPairUntrusted.public,
            issuerCert = caCert,
            issuerPrivateKey = caKeyPair.private,
            addLocalhostSans = false,
        )

    private val keyStorePassword: CharArray = "password".toCharArray()

    /** Server-side keystore: holds the localhost server cert + private key, alias `server-cert`. */
    private val serverKeyStore: KeyStore =
        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("server-cert", serverKeyPair.private, keyStorePassword, arrayOf(serverCert, caCert))
        }

    /** Server-side trust store: the CA cert. Presence triggers `setNeedClientAuth(true)`. */
    private val serverTrustStore: KeyStore =
        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry("oidf-op-mtls-ca", caCert)
        }

    /**
     * Trust store the test HTTP client uses to verify the server cert. Same CA, exposed so each
     * test can build a Ktor client with a `trustManager` bound to it.
     */
    val trustStore: KeyStore = serverTrustStore

    val graph: OidfOpTestAppGraph = createOidfOpTestAppGraph()
    val testClock: TestClock = graph.testClock

    private val server =
        run {
            runBlocking { OidfOpBootstrap.seed(graph) }
            embeddedServer(
                factory = Netty,
                environment = applicationEnvironment {},
                configure = {
                    sslConnector(
                        keyStore = serverKeyStore,
                        keyAlias = "server-cert",
                        keyStorePassword = { keyStorePassword },
                        privateKeyPassword = { keyStorePassword },
                    ) {
                        host = "127.0.0.1"
                        port = 0
                        this.trustStore = serverTrustStore
                    }
                },
                module = {
                    intercept(ApplicationCallPipeline.Setup) {
                        // Surface peer-cert chain on the call so commonMain code (OAuth2 AS
                        // client-cert extractor + cnf.x5t#S256 validator) receives the leaf
                        // without engine coupling. Failures here are tolerated: a request that
                        // somehow reaches a route without a TLS chain is treated as "no cert"
                        // downstream, surfacing the right RFC 8705 error rather than a 500.
                        val nettyCall = call as? NettyApplicationCall
                        if (nettyCall != null) {
                            val sslHandler = nettyCall.context.pipeline().get(SslHandler::class.java)
                            if (sslHandler != null) {
                                val chainBytes =
                                    try {
                                        sslHandler.engine().session.peerCertificates.map { cert ->
                                            (cert as X509Certificate).encoded
                                        }
                                    } catch (_: SSLPeerUnverifiedException) {
                                        emptyList()
                                    }
                                if (chainBytes.isNotEmpty()) {
                                    call.attributes.put(ClientCertificateChainAttributeKey, chainBytes)
                                }
                            }
                        }
                    }
                    configureOidfOp(graph)
                },
            )
        }

    init {
        server.start(wait = false)
    }

    val port: Int by lazy {
        runBlocking {
            server.engine
                .resolvedConnectors()
                .first()
                .port
        }
    }

    /**
     * HTTPS base URL for the live mTLS engine. Uses `localhost` (not `127.0.0.1`) so the Ktor
     * CIO client's TLS server-name verification matches the server cert SAN dnsName=localhost
     * without surfacing whatever hostname Windows / Docker injects when the resolved socket
     * address is converted back to a name (`kubernetes.docker.internal` on Windows hosts, etc.).
     */
    val baseUrl: String get() = "https://localhost:$port"

    fun stop() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
    }
}
