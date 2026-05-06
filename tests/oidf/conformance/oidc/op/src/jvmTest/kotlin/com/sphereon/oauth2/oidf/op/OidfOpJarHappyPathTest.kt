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
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.ServerSocket
import java.net.URLEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.routing.get as serverGet

/**
 * RFC 9101 (JAR) "Server" role conformance: signed authorization-request happy paths.
 *
 * Where [OidfOpJarTest] covers discovery + the rejection layer (alg=none, both `request` and
 * `request_uri`), this suite exercises the verifier with a real RSA-keyed test client whose JWKS
 * is published to the AS through the inline `oauth2.clients.<id>.jwks.<n>.*` config-binder
 * surface. Coverage:
 *
 *  - `request` parameter with a valid signed JWT against `/authorize` redirects to /login.
 *  - JAR with mismatched `client_id` is refused (front-channel hint vs JWT `iss`).
 *  - `request_uri` pointing at a registered HTTPS allow-list entry is fetched and verified.
 *  - PAR body carrying `request=<jar>` merges the JAR claims into the pushed request, then
 *    `/authorize?request_uri=<urn>` resumes successfully.
 *
 * RSA key + JWKS publication is per-class, mutated on the AppScope-shared
 * [DefaultPrincipalMapPropertySource] before fixture instantiation so the lazy-loaded
 * [com.sphereon.oauth2.server.authorization.impl.storage.memory.ConfigAwareClientRegistry]
 * picks the keys up on first session resolution.
 */
class OidfOpJarHappyPathTest {
    private lateinit var fixture: OidfOpServerFixture
    private lateinit var client: HttpClient
    private val json = Json { ignoreUnknownKeys = true }
    private val overrides = HarnessPropertyOverride()

    @BeforeTest
    fun setUp() {
        // Publish the public JWK on `oidf-op-basic` BEFORE the fixture starts so the registry's
        // lazy load picks it up. Re-publishing the same keys per test is idempotent because the
        // property source is a JVM-wide map keyed by name.
        publishClientJwks(CLIENT_ID, JarClientKey.publicJwkProperties())
        fixture = OidfOpServerFixture()
        client = HttpClient(CIO) { followRedirects = false }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        fixture.stop()
        overrides.close()
    }

    @Test
    fun authorizeAcceptsSignedRequestObject() =
        runTest {
            val state = "jar-happy-state-1"
            val nonce = "jar-happy-nonce-1"
            val pkce = JarPkceFixture()
            val jar =
                JarClientKey.signRequestObject(
                    issuer = CLIENT_ID,
                    clientId = CLIENT_ID,
                    audience = fixture.baseUrl,
                    extraClaims =
                        buildJsonObject {
                            put("response_type", "code")
                            put("redirect_uri", "http://localhost:8080/test-callback")
                            put("scope", "openid")
                            put("state", state)
                            put("nonce", nonce)
                            put("code_challenge", pkce.challenge)
                            put("code_challenge_method", "S256")
                        },
                )

            val response =
                client.get(
                    "${fixture.baseUrl}/authorize?client_id=$CLIENT_ID&request=" +
                        URLEncoder.encode(jar, "UTF-8"),
                )

            assertEquals(
                HttpStatusCode.Found,
                response.status,
                "Signed JAR must be accepted and redirect to /login; body='${response.bodyAsText()}'",
            )
            val location = response.headers["Location"] ?: error("missing Location header")
            assertTrue(location.contains("/login"), "redirect must target /login; got $location")
            assertTrue(location.contains("session_id="), "/login redirect must carry session_id; got $location")
        }

    @Test
    fun authorizeRejectsRequestObjectWithMismatchedClientId() =
        runTest {
            // JAR is internally signed for `other-client` but invoked with client_id=oidf-op-basic.
            val pkce = JarPkceFixture()
            val jar =
                JarClientKey.signRequestObject(
                    issuer = "other-client",
                    clientId = "other-client",
                    audience = fixture.baseUrl,
                    extraClaims =
                        buildJsonObject {
                            put("response_type", "code")
                            put("redirect_uri", "http://localhost:8080/test-callback")
                            put("scope", "openid")
                            put("state", "jar-happy-state-mismatch")
                            put("code_challenge", pkce.challenge)
                            put("code_challenge_method", "S256")
                        },
                )

            val response =
                client.get(
                    "${fixture.baseUrl}/authorize?client_id=$CLIENT_ID&request=" +
                        URLEncoder.encode(jar, "UTF-8"),
                )

            assertTrue(
                response.status.value in setOf(302, 400),
                "Mismatched JAR iss/client_id must be rejected; got ${response.status}",
            )
            val combined =
                response.bodyAsText().lowercase() + " " +
                    (response.headers["Location"] ?: "").lowercase()
            assertTrue(
                "invalid_request_object" in combined || "invalid_request" in combined,
                "Response must surface invalid_request_object or invalid_request; got $combined",
            )
        }

    @Test
    fun authorizeAcceptsRequestUriFromRegisteredAllowList() =
        runTest {
            val pkce = JarPkceFixture()
            val jar =
                JarClientKey.signRequestObject(
                    issuer = CLIENT_ID,
                    clientId = CLIENT_ID,
                    audience = fixture.baseUrl,
                    extraClaims =
                        buildJsonObject {
                            put("response_type", "code")
                            put("redirect_uri", "http://localhost:8080/test-callback")
                            put("scope", "openid")
                            put("state", "jar-happy-state-uri")
                            put("code_challenge", pkce.challenge)
                            put("code_challenge_method", "S256")
                        },
                )

            val capture = JarRequestUriCaptureServer.start(jar)
            try {
                // The fixture's `requireRequestUriRegistration` is left at its default `false`, so
                // the allow-list publication is documentary; the AS still fetches the URL inline
                // and verifies the body. Pre-publishing the URI keeps the test honest about the
                // intended registered-allow-list flow even though the server config does not
                // enforce it here. (Discovery in OidfOpJarTest already verifies that
                // `require_request_uri_registration` stays false.)
                publishRequestUri(CLIENT_ID, capture.url)

                val response =
                    client.get(
                        "${fixture.baseUrl}/authorize?client_id=$CLIENT_ID&request_uri=" +
                            URLEncoder.encode(capture.url, "UTF-8"),
                    )
                assertEquals(
                    HttpStatusCode.Found,
                    response.status,
                    "request_uri-resolved JAR must be accepted; body='${response.bodyAsText()}'",
                )
                val location = response.headers["Location"] ?: error("missing Location")
                assertTrue(location.contains("/login"), "redirect must target /login; got $location")

                // The capture server's GET deferred MUST resolve, proving the AS fetched the URI.
                withTimeout(5.seconds) {
                    capture.fetched.await()
                }
            } finally {
                capture.stop()
                clearRequestUri(CLIENT_ID)
            }
        }

    @Test
    fun parWithJarBodySucceeds() =
        runTest {
            val pkce = JarPkceFixture()
            val state = "jar-par-state"
            val jar =
                JarClientKey.signRequestObject(
                    issuer = CLIENT_ID,
                    clientId = CLIENT_ID,
                    audience = fixture.baseUrl,
                    extraClaims =
                        buildJsonObject {
                            put("response_type", "code")
                            put("redirect_uri", "http://localhost:8080/test-callback")
                            put("scope", "openid")
                            put("state", state)
                            put("code_challenge", pkce.challenge)
                            put("code_challenge_method", "S256")
                        },
                )

            val parResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/par",
                    formParameters =
                        Parameters.build {
                            append("client_id", CLIENT_ID)
                            // Front-channel parameters mirror the JAR claims so the parse step has
                            // a valid response_type / client_id BEFORE the JAR merge runs.
                            append("response_type", "code")
                            append("request", jar)
                        },
                ) {
                    val basicAuth =
                        Base64
                            .getEncoder()
                            .encodeToString("$CLIENT_ID:oidf-op-basic-secret-2026".encodeToByteArray())
                    header("Authorization", "Basic $basicAuth")
                }
            assertEquals(
                HttpStatusCode.Created,
                parResponse.status,
                "POST /par with JAR body must return 201; body='${parResponse.bodyAsText()}'",
            )
            val parBody = json.parseToJsonElement(parResponse.bodyAsText()).jsonObject
            val requestUri =
                parBody["request_uri"]?.jsonPrimitive?.content
                    ?: error("missing request_uri")
            assertTrue(
                requestUri.startsWith("urn:ietf:params:oauth:request_uri:"),
                "PAR must return urn:ietf:params:oauth:request_uri:; got $requestUri",
            )

            val authorize =
                client.get(
                    "${fixture.baseUrl}/authorize?client_id=$CLIENT_ID&request_uri=" +
                        URLEncoder.encode(requestUri, "UTF-8"),
                )
            assertEquals(
                HttpStatusCode.Found,
                authorize.status,
                "PAR-redeemed authorize must redirect; body='${authorize.bodyAsText()}'",
            )
            val location = authorize.headers["Location"] ?: error("missing Location")
            assertTrue(location.contains("/login"), "redirect must target /login; got $location")
        }

    private fun publishClientJwks(
        clientId: String,
        jwkProperties: Map<String, String>,
    ) {
        jwkProperties.forEach { (suffix, value) ->
            overrides.publish(
                "oauth2.clients.$clientId.jwks.0.$suffix",
                value,
            )
        }
    }

    private fun publishRequestUri(
        clientId: String,
        uri: String,
    ) {
        overrides.publish(
            "oauth2.clients.$clientId.request-uris.0",
            uri,
        )
    }

    private fun clearRequestUri(clientId: String) {
        // Re-set to an empty marker so subsequent tests don't see a stale capture URL. The
        // binder skips empty values.
        overrides.publish(
            "oauth2.clients.$clientId.request-uris.0",
            "",
        )
    }

    companion object {
        private const val CLIENT_ID = "oidf-op-basic"
    }
}

/**
 * Per-suite RSA-2048 keypair used to sign all JARs in this file. The public JWK is published
 * onto `oauth2.clients.<id>.jwks.0.*` so the AS resolves the same key at verification time.
 */
private object JarClientKey {
    private val keyPair: KeyPair =
        KeyPairGenerator
            .getInstance("RSA")
            .apply {
                initialize(2048, SecureRandom())
            }.generateKeyPair()

    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey

    private val nB64: String = base64Url(stripLeadingZero(publicKey.modulus.toByteArray()))
    private val eB64: String = base64Url(stripLeadingZero(publicKey.publicExponent.toByteArray()))

    fun publicJwkProperties(): Map<String, String> =
        mapOf(
            "kty" to "RSA",
            "alg" to "RS256",
            "use" to "sig",
            "kid" to "jar-happy-test-key",
            "n" to nB64,
            "e" to eB64,
        )

    fun signRequestObject(
        issuer: String,
        clientId: String,
        audience: String,
        extraClaims: JsonObject,
    ): String {
        val now = System.currentTimeMillis() / 1000
        val header =
            buildJsonObject {
                put("alg", "RS256")
                put("typ", "oauth-authz-req+jwt")
                put("kid", "jar-happy-test-key")
            }
        val payload =
            buildJsonObject {
                put("iss", issuer)
                put("aud", audience)
                put("client_id", clientId)
                put("iat", now)
                put("exp", now + 300)
                put("jti", UUID.randomUUID().toString())
                extraClaims.forEach { (key, value) -> put(key, value) }
            }
        val headerB64 = base64Url(header.toString().encodeToByteArray())
        val payloadB64 = base64Url(payload.toString().encodeToByteArray())
        val signingInput = "$headerB64.$payloadB64".encodeToByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(signingInput)
        val sig = signer.sign()
        return "$headerB64.$payloadB64.${base64Url(sig)}"
    }

    private fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }
}

/**
 * In-process Ktor server that publishes a fixed JAR JWT body at `/jar` over HTTP. Used by the
 * `request_uri` test so the AS's [com.sphereon.ktor.http.client.FetchRequestUriCommand] resolves
 * the JWT body in-band. Records the first GET on [fetched] so the test asserts the AS actually
 * walked the registered URL.
 */
private interface JarRequestUriCaptureServer {
    val url: String
    val fetched: CompletableDeferred<Unit>

    fun stop()

    companion object {
        fun start(jarBody: String): JarRequestUriCaptureServer {
            val port = ServerSocket(0).use { it.localPort }
            return JarRequestUriCaptureServerImpl(port, jarBody)
        }
    }
}

private class JarRequestUriCaptureServerImpl(
    port: Int,
    private val jarBody: String,
) : JarRequestUriCaptureServer {
    override val fetched: CompletableDeferred<Unit> = CompletableDeferred()
    override val url: String = "http://127.0.0.1:$port/jar"

    private val server =
        embeddedServer(ServerCIO, port = port, host = "127.0.0.1") {
            routing {
                serverGet("/jar") {
                    fetched.complete(Unit)
                    call.respondText(
                        text = jarBody,
                        contentType = ContentType("application", "oauth-authz-req+jwt"),
                    )
                }
            }
        }

    init {
        server.start(wait = false)
    }

    override fun stop() {
        server.stop(gracePeriodMillis = 50, timeoutMillis = 500)
    }
}

private class JarPkceFixture {
    val verifier: String = "oidf-op-conformance-pkce-verifier-fixture-2026-J"
    val challenge: String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
}

private fun base64Url(bytes: ByteArray): String =
    Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes)
