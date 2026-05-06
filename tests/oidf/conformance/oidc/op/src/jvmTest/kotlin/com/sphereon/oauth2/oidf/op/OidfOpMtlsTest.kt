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

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.network.tls.addKeyStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyPair
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * RFC 8705 (OAuth 2.0 Mutual-TLS Client Authentication and Certificate-Bound Access Tokens)
 * conformance suite, sibling to the 57 plain-HTTP OIDF tests.
 *
 * Coverage:
 *
 *  1. [discoveryAdvertisesMtlsAliases] — discovery JSON publishes
 *     `tls_client_certificate_bound_access_tokens=true`, `mtls_endpoint_aliases.token_endpoint`,
 *     and `self_signed_tls_client_auth` in `token_endpoint_auth_methods_supported` whenever the
 *     server's `mtls` policy is enabled.
 *  2. [tokenGrantWithSelfSignedTlsClientAuthSucceeds] — full PKCE auth-code flow, then `/token`
 *     over mTLS using the registered client cert. Asserts the access token carries
 *     `cnf.x5t#S256 = base64url(SHA256(clientCertDer))`.
 *  3. [tokenGrantRejectsMtlsCertNotMatchingClientJwks] — same flow, but the test client presents
 *     a TLS cert whose public key is NOT in the registered `oidf-op-mtls.jwks` list. AS MUST
 *     respond `401 invalid_client`.
 *  4. [userInfoRequiresMatchingTlsCertWhenAccessTokenIsCertBound] — issue a cert-bound token,
 *     then call `/userinfo` with the matching cert (expect 200), with a different cert (expect
 *     401), and without any client cert (expect 401 from TLS handshake refusal because the
 *     server is configured `setNeedClientAuth(true)`).
 *
 * The fixture itself is parallel to [OidfOpServerFixture]: a separate Netty engine with a
 * sslConnector(...) trust store is started. Existing 57 OIDF tests stay on plain HTTP and
 * never see mTLS code.
 */
class OidfOpMtlsTest {
    private lateinit var fixture: OidfOpMtlsServerFixture
    private val json = Json { ignoreUnknownKeys = true }
    private val overrides = HarnessPropertyOverride()

    @BeforeTest
    fun setUp() {
        // Server-wide mTLS opt-in. MEDIUM-priority overrides the LOW-priority
        // application.properties so the existing OIDF clients can stay password-based.
        overrides.publish("oauth2.servers.default.mtls", "SUPPORTED")
        overrides.publish("oauth2.servers.default.tls-client-certificate-bound-access-tokens", "true")

        // Add `self_signed_tls_client_auth` to the advertised token-endpoint auth methods so
        // discovery surfaces it. Discovery's BuildServerMetadataCommandImpl additively appends
        // mTLS auth methods when `mtls` is enabled, so this overrides the application.properties
        // MEDIUM list with the same plus the mTLS pair.
        overrides.publish(
            "oauth2.servers.default.token-endpoint-auth-methods-supported",
            "client_secret_basic,client_secret_post,none,self_signed_tls_client_auth",
        )

        // mTLS test client. PUBLIC client + self_signed_tls_client_auth + jwks.0 = the cert's
        // public key. fixture must publish jwks AFTER it has generated the client keypair, so
        // fixture init runs first then we inject the JWK.
        publishMtlsClientStaticConfig()

        fixture = OidfOpMtlsServerFixture()

        publishMtlsClientPublicJwk(fixture.clientCertGood.publicKey as RSAPublicKey)
    }

    @AfterTest
    fun tearDown() {
        fixture.stop()
        overrides.close()
    }

    @Test
    fun discoveryAdvertisesMtlsAliases() =
        runTest {
            // The in-process fixture serves discovery over the same TLS engine that requires
            // client-cert presentation, so the request presents the good client cert. RFC 8705
            // §5 lets deployments host the `mtls_endpoint_aliases.*` surface on a separate
            // hostname; production deployments should split the proxy. The conformance probe
            // here only validates the *content* of the discovery JSON, which is independent
            // of the host-split decision.
            val authedClient = newHttpsClient(presentClientCert = true, useGoodCert = true)
            try {
                val response = authedClient.get("${fixture.baseUrl}/.well-known/openid-configuration")
                assertEquals(HttpStatusCode.OK, response.status, "discovery must return 200 over mTLS")
                val body = json.parseToJsonElement(response.bodyAsText()).jsonObject

                assertEquals(
                    true,
                    body["tls_client_certificate_bound_access_tokens"]?.jsonPrimitive?.content?.toBoolean(),
                    "discovery MUST advertise tls_client_certificate_bound_access_tokens=true when the server enables mTLS",
                )

                val aliases = body["mtls_endpoint_aliases"]?.jsonObject
                assertNotNull(aliases, "discovery MUST advertise mtls_endpoint_aliases when mTLS is enabled")
                val mtlsTokenEndpoint = aliases["token_endpoint"]?.jsonPrimitive?.content
                assertNotNull(mtlsTokenEndpoint, "mtls_endpoint_aliases MUST include token_endpoint")
                assertTrue(
                    mtlsTokenEndpoint.startsWith("http"),
                    "mtls token_endpoint alias must be an absolute URL: $mtlsTokenEndpoint",
                )

                val authMethods =
                    body["token_endpoint_auth_methods_supported"]
                        ?.jsonArray
                        ?.map { it.jsonPrimitive.content }
                        ?: emptyList()
                assertTrue(
                    authMethods.contains("self_signed_tls_client_auth"),
                    "token_endpoint_auth_methods_supported MUST include self_signed_tls_client_auth, got: $authMethods",
                )
            } finally {
                authedClient.close()
            }
        }

    @Test
    fun tokenGrantWithSelfSignedTlsClientAuthSucceeds() =
        runTest {
            val pkce = MtlsPkceFixture("OK")
            val flow = MtlsAuthCodeFlow(fixture, useGoodCert = true)
            val code = flow.driveAuthorize(pkce, MTLS_CLIENT_ID, state = "mtls-state-ok")

            val tokenJson = flow.exchangeForTokens(code, pkce)
            val accessToken =
                tokenJson["access_token"]?.jsonPrimitive?.content
                    ?: fail("token response missing access_token: $tokenJson")
            val idToken =
                tokenJson["id_token"]?.jsonPrimitive?.content
                    ?: fail("token response missing id_token (oidc scope was 'openid')")
            assertEquals("Bearer", tokenJson["token_type"]?.jsonPrimitive?.content)

            val expectedThumbprint = sha256B64u(fixture.clientCertGood.encoded)
            val payload = decodeJwsPayload(accessToken)
            val cnf = payload["cnf"]?.jsonObject
            assertNotNull(cnf, "RFC 8705 §3.2: cert-bound access token MUST carry a cnf claim")
            assertEquals(
                expectedThumbprint,
                cnf["x5t#S256"]?.jsonPrimitive?.content,
                "cnf.x5t#S256 MUST equal base64url(SHA256(clientCertDer))",
            )

            // id_token is NOT cert-bound per RFC 8705 (only access tokens). Sanity check it's
            // a real signed JWT for the registered client.
            val idPayload = decodeJwsPayload(idToken)
            assertEquals(MTLS_CLIENT_ID, idPayload["aud"]?.jsonPrimitive?.content)
        }

    @Test
    fun tokenGrantRejectsMtlsCertNotMatchingClientJwks() =
        runTest {
            val pkce = MtlsPkceFixture("BAD")
            val flow = MtlsAuthCodeFlow(fixture, useGoodCert = false)
            val code = flow.driveAuthorize(pkce, MTLS_CLIENT_ID, state = "mtls-state-bad")

            val response = flow.postToken(code, pkce)
            // RFC 8705 §2.2: the AS rejects the request because the presented TLS cert's public
            // key is not in the client's registered JWKS. RFC 6749 §5.2 maps this to 401 +
            // invalid_client.
            assertEquals(
                HttpStatusCode.Unauthorized,
                response.status,
                "self_signed_tls_client_auth with an unregistered cert MUST return 401, got ${response.status}",
            )
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(
                "invalid_client",
                body["error"]?.jsonPrimitive?.content,
                "AS MUST reject with error=invalid_client when the TLS cert is not in client.jwks",
            )
        }

    @Test
    fun userInfoRequiresMatchingTlsCertWhenAccessTokenIsCertBound() =
        runTest {
            val pkce = MtlsPkceFixture("UI")
            val flow = MtlsAuthCodeFlow(fixture, useGoodCert = true)
            val code = flow.driveAuthorize(pkce, MTLS_CLIENT_ID, state = "mtls-state-ui")
            val tokenJson = flow.exchangeForTokens(code, pkce)
            val accessToken =
                tokenJson["access_token"]?.jsonPrimitive?.content
                    ?: fail("token response missing access_token")

            // (1) Same TLS cert at /userinfo: 200.
            val matchingClient = newHttpsClient(presentClientCert = true, useGoodCert = true)
            try {
                val response =
                    matchingClient.get("${fixture.baseUrl}/userinfo") {
                        header("Authorization", "Bearer $accessToken")
                    }
                assertTrue(
                    response.status.isSuccess(),
                    "/userinfo with matching cert MUST succeed, got ${response.status}: ${response.bodyAsText()}",
                )
            } finally {
                matchingClient.close()
            }

            // (2) Different TLS cert at /userinfo: 401 invalid_token. The handshake itself
            // succeeds because the untrusted cert chains to the same CA, but the cnf.x5t#S256
            // binding on the access token MUST be enforced by the resource-server validator.
            val mismatchingClient = newHttpsClient(presentClientCert = true, useGoodCert = false)
            try {
                val response =
                    mismatchingClient.get("${fixture.baseUrl}/userinfo") {
                        header("Authorization", "Bearer $accessToken")
                    }
                assertEquals(
                    HttpStatusCode.Unauthorized,
                    response.status,
                    "/userinfo with a non-binding cert MUST return 401, got ${response.status}: ${response.bodyAsText()}",
                )
                val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
                assertEquals(
                    "invalid_token",
                    body["error"]?.jsonPrimitive?.content,
                    "RFC 8705 §3.2: cnf.x5t#S256 mismatch MUST surface error=invalid_token",
                )
            } finally {
                mismatchingClient.close()
            }

            // (3) No client cert: TLS handshake itself is refused because the fixture's
            // sslConnector trustStore triggers Netty's setNeedClientAuth(true). We surface
            // that as a thrown exception (the request never reaches the AS).
            val noCertClient = newHttpsClient(presentClientCert = false)
            var handshakeFailed = false
            try {
                val response =
                    noCertClient.get("${fixture.baseUrl}/userinfo") {
                        header("Authorization", "Bearer $accessToken")
                    }
                if (response.status == HttpStatusCode.Unauthorized) {
                    handshakeFailed = true
                }
            } catch (_: Throwable) {
                handshakeFailed = true
            } finally {
                noCertClient.close()
            }
            assertTrue(
                handshakeFailed,
                "/userinfo without a client cert MUST be refused (TLS handshake or 401)",
            )
        }

    private fun publishMtlsClientStaticConfig() {
        val staticProps =
            mapOf(
                "client-id" to MTLS_CLIENT_ID,
                "client-name" to "OIDF mTLS conformance test client",
                "client-type" to "PUBLIC",
                "grant-types.0" to "authorization_code",
                "response-types.0" to "code",
                "token-endpoint-auth-method" to "self_signed_tls_client_auth",
                "tls-client-certificate-bound-access-tokens" to "true",
                "allowed-scopes" to "openid",
                "redirect-uris.0" to "http://localhost:8080/test-callback",
                "redirect-uris.1" to "https://www.certification.openid.net/test/a/oidf-op-mtls/callback",
                "require-pkce" to "true",
            )
        staticProps.forEach { (suffix, value) ->
            overrides.publish("oauth2.clients.$MTLS_CLIENT_ID.$suffix", value)
        }
    }

    private fun publishMtlsClientPublicJwk(rsaPublicKey: RSAPublicKey) {
        // RFC 7518 §6.3.1 RSA public key parameters as base64url-without-padding.
        val n = base64UrlNoPad(stripLeadingZero(rsaPublicKey.modulus.toByteArray()))
        val e = base64UrlNoPad(stripLeadingZero(rsaPublicKey.publicExponent.toByteArray()))
        val jwk =
            mapOf(
                "kty" to "RSA",
                "alg" to "RS256",
                "use" to "sig",
                "kid" to "oidf-op-mtls-client-key",
                "n" to n,
                "e" to e,
            )
        jwk.forEach { (suffix, value) ->
            overrides.publish(
                "oauth2.clients.$MTLS_CLIENT_ID.jwks.0.$suffix",
                value,
            )
        }
    }

    /**
     * Build a Ktor HTTP client over CIO with optional client-cert presentation. Trusts only the
     * fixture's ad-hoc CA (no JVM-default trust). When [presentClientCert] is true, the client
     * presents either the good or the untrusted cert at the TLS handshake.
     */
    private fun newHttpsClient(
        presentClientCert: Boolean,
        useGoodCert: Boolean = true,
    ): HttpClient {
        val (clientKp, clientCert) =
            if (presentClientCert) {
                if (useGoodCert) {
                    fixture.clientKeyPairGood to fixture.clientCertGood
                } else {
                    fixture.clientKeyPairUntrusted to fixture.clientCertUntrusted
                }
            } else {
                null to null
            }
        return buildHttpsClient(fixture.trustStore, clientKp, clientCert)
    }

    private fun buildHttpsClient(
        trustStore: java.security.KeyStore,
        clientKeyPair: KeyPair?,
        clientCert: X509Certificate?,
    ): HttpClient {
        val tmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(trustStore)
            }
        val clientKeyStore =
            if (clientKeyPair != null && clientCert != null) {
                java.security.KeyStore
                    .getInstance("PKCS12")
                    .apply {
                        load(null, null)
                        setKeyEntry(
                            "client",
                            clientKeyPair.private,
                            CLIENT_KS_PASSWORD,
                            arrayOf(clientCert),
                        )
                    }
            } else {
                null
            }

        return HttpClient(CIO) {
            followRedirects = false
            // The AS resolves outbound URLs (issuer, login redirects, mtls aliases) from the
            // request's `Host` + `X-Forwarded-Proto`. Without `X-Forwarded-Proto: https` the
            // scheme defaults to `http` (see `OAuth2HttpResponses.forwardedScheme`), which would
            // make the AS publish `http://...` URLs that fail when the test client follows
            // them over its HTTPS-only engine.
            defaultRequest {
                header("X-Forwarded-Proto", "https")
            }
            engine {
                https {
                    trustManager =
                        tmf.trustManagers.firstOrNull { it is X509TrustManager } as X509TrustManager
                    if (clientKeyStore != null) {
                        addKeyStore(store = clientKeyStore, password = CLIENT_KS_PASSWORD, alias = "client")
                    }
                }
            }
        }
    }

    private fun decodeJwsPayload(jws: String): JsonObject {
        val payloadSeg =
            jws.split(".").getOrNull(1)
                ?: error("JWS must have three segments")
        val padded =
            when (payloadSeg.length % 4) {
                0 -> payloadSeg
                2 -> "$payloadSeg=="
                3 -> "$payloadSeg="
                else -> error("Invalid base64url length: ${payloadSeg.length}")
            }
        val decoded =
            Base64
                .getUrlDecoder()
                .decode(padded)
                .decodeToString()
        return json.parseToJsonElement(decoded).jsonObject
    }

    private fun sha256B64u(bytes: ByteArray): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }

    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)

    /**
     * PKCE pair specific to one mTLS test invocation. Verifier seeded so the tests don't
     * depend on a wider PKCE helper module.
     */
    private class MtlsPkceFixture(
        seed: String,
    ) {
        // PKCE verifier MUST be 43-128 characters of [A-Z / a-z / 0-9 / "-" / "." / "_" / "~"]
        // per RFC 7636 §4.1. Pad the seed with the spec-allowed character set so each per-test
        // verifier is unique while staying inside the length window.
        val verifier: String =
            ("oidf-op-mtls-pkce-verifier-2026-fixture-$seed-" + "padpadpadpadpadpadpadpadpadpad")
                .take(64)
        val challenge: String =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
    }

    /**
     * Drives the OIDF browser-login chain over mTLS so each test gets an authorization code
     * tied to a real user (alice). Encapsulates the per-flow HttpClient lifecycle so the
     * test bodies stay focused on the assertion that follows the code exchange.
     */
    private class MtlsAuthCodeFlow(
        private val fixture: OidfOpMtlsServerFixture,
        useGoodCert: Boolean,
    ) {
        private val testKlass = OidfOpMtlsTest::class.java
        private val client: HttpClient
        private val json = Json { ignoreUnknownKeys = true }

        init {
            // Build the same kind of client the test methods would use: trust the fixture CA,
            // present the chosen client cert. PKCE auth-code flow goes over a single client
            // session (cookie persistence between login and resumed authorize) but each mTLS
            // request still must present the cert.
            val (clientKp, clientCert) =
                if (useGoodCert) {
                    fixture.clientKeyPairGood to fixture.clientCertGood
                } else {
                    fixture.clientKeyPairUntrusted to fixture.clientCertUntrusted
                }
            client = buildClient(fixture, clientKp, clientCert)
        }

        suspend fun driveAuthorize(
            pkce: MtlsPkceFixture,
            clientId: String,
            state: String,
        ): String {
            val authorizeUrl =
                "${fixture.baseUrl}/authorize?response_type=code" +
                    "&client_id=$clientId" +
                    "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                    "&scope=openid" +
                    "&state=$state" +
                    "&nonce=mtls-nonce-$state" +
                    "&code_challenge=${pkce.challenge}" +
                    "&code_challenge_method=S256"

            val authorizeResponse = client.get(authorizeUrl)
            assertEquals(
                HttpStatusCode.Found,
                authorizeResponse.status,
                "/authorize must redirect to /login (no existing session). Got ${authorizeResponse.status}.",
            )
            val loginRedirect =
                authorizeResponse.headers["Location"]
                    ?: error("/authorize must emit a Location header to /login")
            val loginUrl = if (loginRedirect.startsWith("http")) loginRedirect else "${fixture.baseUrl}$loginRedirect"
            val sessionId = extractQueryParam(loginUrl, "session_id") ?: error("/login redirect must carry session_id")
            val returnUrl = extractQueryParam(loginUrl, "return_url") ?: error("/login redirect must carry return_url")

            val loginResponse =
                submitLoginWithCsrf(
                    client = client,
                    baseUrl = fixture.baseUrl,
                    loginUrl = loginUrl,
                    sessionId = sessionId,
                    returnUrl = returnUrl,
                    username = "alice",
                    password = OidfOpBootstrap.FIXTURE_PASSWORD,
                )
            assertEquals(HttpStatusCode.Found, loginResponse.status, "POST /login must 302 back to authorize")
            val cookie = loginResponse.headers["Set-Cookie"] ?: error("POST /login must Set-Cookie")
            val resumed =
                client.get(loginResponse.headers["Location"]!!) {
                    header("Cookie", cookie)
                }
            assertEquals(
                HttpStatusCode.Found,
                resumed.status,
                "resumed /authorize must 302 to client redirect_uri after login. Got ${resumed.status}.",
            )
            val callback = resumed.headers["Location"] ?: error("resumed authorize missing Location")
            return extractQueryParam(callback, "code")
                ?: error("callback URL did not carry an authorization code: $callback")
        }

        suspend fun postToken(
            code: String,
            pkce: MtlsPkceFixture,
        ): io.ktor.client.statement.HttpResponse =
            client.submitForm(
                url = "${fixture.baseUrl}/token",
                formParameters =
                    Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", "http://localhost:8080/test-callback")
                        append("code_verifier", pkce.verifier)
                        append("client_id", MTLS_CLIENT_ID)
                    },
            )

        suspend fun exchangeForTokens(
            code: String,
            pkce: MtlsPkceFixture,
        ): JsonObject {
            val response = postToken(code, pkce)
            assertTrue(
                response.status.isSuccess(),
                "POST /token over mTLS MUST succeed, got ${response.status}: ${response.bodyAsText()}",
            )
            return json.parseToJsonElement(response.bodyAsText()).jsonObject
        }

        private fun buildClient(
            fixture: OidfOpMtlsServerFixture,
            clientKeyPair: KeyPair,
            clientCert: X509Certificate,
        ): HttpClient {
            val tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(fixture.trustStore)
                }
            val ks =
                java.security.KeyStore
                    .getInstance("PKCS12")
                    .apply {
                        load(null, null)
                        setKeyEntry("client", clientKeyPair.private, CLIENT_KS_PASSWORD, arrayOf(clientCert))
                    }

            return HttpClient(CIO) {
                followRedirects = false
                defaultRequest {
                    header("X-Forwarded-Proto", "https")
                }
                engine {
                    https {
                        trustManager =
                            tmf.trustManagers.firstOrNull { it is X509TrustManager } as X509TrustManager
                        addKeyStore(store = ks, password = CLIENT_KS_PASSWORD, alias = "client")
                    }
                }
            }
        }

        private fun extractQueryParam(
            url: String,
            key: String,
        ): String? {
            val q = url.substringAfter('?', missingDelimiterValue = "")
            return q
                .split("&")
                .map { it.split("=", limit = 2) }
                .firstOrNull { it.firstOrNull() == key }
                ?.getOrNull(1)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        }

        private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
    }

    companion object {
        private const val MTLS_CLIENT_ID: String = "oidf-op-mtls"
        private val CLIENT_KS_PASSWORD: CharArray = "password".toCharArray()
    }
}
