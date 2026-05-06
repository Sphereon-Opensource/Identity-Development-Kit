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
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Positive-flow conformance for OAuth 2.0 Attestation-Based Client Authentication
 * (draft-ietf-oauth-attestation-based-client-auth-07).
 *
 * The default OIDF harness leaves attestation [com.sphereon.oauth2.common.config.FeaturePolicy.DISABLED];
 * [OidfOpAttestationTest] keeps that fixture and asserts the gate-off behaviour. This class flips
 * the AS to `attestation = SUPPORTED` plus `attestation-challenge-required = true` for one client
 * (`oidf-op-attestation`) via [DefaultPrincipalMapPropertySource], minting an attester RSA key
 * and a client-instance RSA key per test class. The attester public JWK is published onto
 * `oauth2.clients.oidf-op-attestation.trusted-attester-jwks.0.*`; the instance public JWK is
 * embedded in the attestation's `cnf.jwk` claim per the spec.
 *
 * Override mechanism mirrors [OidfOpBackChannelLogoutCaptureTest]:
 * [DefaultPrincipalMapPropertySource] is a globally mirrored MEDIUM-priority source that wins
 * over the file-backed LOW-priority source the harness's `application.properties` populates.
 * Tests publish all overrides BEFORE the fixture builds so the lazy-loaded
 * [com.sphereon.oauth2.server.authorization.impl.storage.memory.ConfigAwareClientRegistry]
 * picks them up on first session resolution.
 *
 * Coverage:
 *  - `attestationChallengeEndpointReturnsChallenge`: GET `/attestation-challenge` returns 200
 *    with `{"attestation_challenge":"<nonce>"}` once the feature is opted in.
 *  - `tokenGrantWithValidAttestationAndPoPSucceeds`: full auth-code flow ending at /token with
 *    valid attestation + PoP headers yields a Bearer access_token.
 *  - `tokenGrantRejectsAttestationFromUntrustedAttester`: attester key not in the trust list →
 *    401 invalid_client.
 *  - `tokenGrantRejectsPopWithStaleIat`: PoP `iat` older than `attestationPopMaxAgeSeconds` →
 *    401 invalid_client.
 *  - `tokenGrantRejectsPopWithReplayedJti`: same challenge nonce reused on a second /token
 *    request → 401 invalid_client (challenge replay protection).
 *  - `tokenGrantRejectsAttestationCnfJwkMismatch`: PoP signed by a key OTHER than the one
 *    declared in the attestation's `cnf.jwk` → 401 invalid_client.
 */
class OidfOpAttestationFlowTest {
    private lateinit var fixture: OidfOpServerFixture
    private lateinit var client: HttpClient
    private val json = Json { ignoreUnknownKeys = true }
    private val overrides = HarnessPropertyOverride()

    @BeforeTest
    fun setUp() {
        // Flip the AS to SUPPORTED + challenge-required so both the discovery surface and the
        // /attestation-challenge endpoint engage. The verifier already handles challenge-required
        // by minting one when the PoP arrives without a nonce, but tests opt in to the explicit
        // mint-then-bind flow per the spec.
        publishProperty("oauth2.servers.default.attestation", "SUPPORTED")
        publishProperty("oauth2.servers.default.attestation-challenge-required", "true")

        // Register the attestation client. token_endpoint_auth_method = attest_jwt_client_auth
        // tells the verifier to take the attestation/PoP headers from the request rather than
        // any other auth method. Redirect URIs match the happy-path callback so we can reuse
        // alice's login fixture.
        publishProperty("oauth2.clients.$CLIENT_ID.client-id", CLIENT_ID)
        publishProperty("oauth2.clients.$CLIENT_ID.client-name", "OIDF Conformance Attestation Client")
        publishProperty("oauth2.clients.$CLIENT_ID.client-type", "CONFIDENTIAL")
        publishProperty("oauth2.clients.$CLIENT_ID.grant-types", "authorization_code,refresh_token")
        publishProperty("oauth2.clients.$CLIENT_ID.response-types", "code")
        publishProperty(
            "oauth2.clients.$CLIENT_ID.token-endpoint-auth-method",
            "attest_jwt_client_auth",
        )
        publishProperty("oauth2.clients.$CLIENT_ID.allowed-scopes", "openid,profile,email")
        publishProperty(
            "oauth2.clients.$CLIENT_ID.redirect-uris.0",
            "http://localhost:8080/test-callback",
        )
        publishProperty("oauth2.clients.$CLIENT_ID.require-pkce", "true")
        publishProperty("oauth2.clients.$CLIENT_ID.trusted-attester-issuers.0", ATTESTER_ISSUER)

        // Publish the trusted attester's public JWK (RSA-2048, RS256) onto the inline trust list.
        AttestationFixtureKeys.attesterPublicJwkProperties().forEach { (suffix, value) ->
            publishProperty("oauth2.clients.$CLIENT_ID.trusted-attester-jwks.0.$suffix", value)
        }

        fixture = OidfOpServerFixture()
        client = HttpClient(CIO) { followRedirects = false }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        fixture.stop()
        // Restore the property source so the disabled-mode tests in OidfOpAttestationTest stay
        // honest about the gate-off discovery shape on the next class instantiation.
        overrides.close()
    }

    @Test
    fun attestationChallengeEndpointReturnsChallenge() =
        runTest {
            val discovery = client.get("${fixture.baseUrl}/.well-known/openid-configuration")
            val discoveryBody = json.parseToJsonElement(discovery.bodyAsText()).jsonObject
            val challengeEndpoint = discoveryBody["challenge_endpoint"]?.jsonPrimitive?.content
            val attestationAlgs = discoveryBody["client_attestation_signing_alg_values_supported"]
            assertNotNull(
                challengeEndpoint,
                "discovery must advertise challenge_endpoint when attestation is SUPPORTED + challenge-required is true; algs=$attestationAlgs full body=$discoveryBody",
            )
            val response = client.get("${fixture.baseUrl}/attestation-challenge")
            assertEquals(
                HttpStatusCode.OK,
                response.status,
                "/attestation-challenge must return 200; got ${response.status} body='${response.bodyAsText()}'",
            )
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val nonce = body["attestation_challenge"]?.jsonPrimitive?.content
            assertNotNull(nonce, "response must include attestation_challenge field")
            assertTrue(nonce.isNotBlank(), "attestation_challenge must be non-blank")
        }

    @Test
    fun tokenGrantWithValidAttestationAndPoPSucceeds() =
        runTest {
            val (login, code, pkce) = drivePkceLoginAndAuthorize("attest-happy")
            val challenge = fetchChallenge()
            val attestationJwt =
                AttestationFixtureKeys.signAttestation(
                    iss = ATTESTER_ISSUER,
                    sub = CLIENT_ID,
                    cnfJwk = AttestationFixtureKeys.instancePublicJwk(),
                )
            val popJwt =
                AttestationFixtureKeys.signPop(
                    iss = CLIENT_ID,
                    aud = "${fixture.baseUrl}/token",
                    nonce = challenge,
                )

            val tokenResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", code)
                            append("redirect_uri", "http://localhost:8080/test-callback")
                            append("code_verifier", pkce.verifier)
                            append("client_id", CLIENT_ID)
                        },
                ) {
                    header("OAuth-Client-Attestation", attestationJwt)
                    header("OAuth-Client-Attestation-PoP", popJwt)
                    header("Cookie", login.loginCookie)
                }
            assertTrue(
                tokenResponse.status.isSuccess(),
                "POST /token with valid attestation must succeed; got ${tokenResponse.status}: ${tokenResponse.bodyAsText()}",
            )
            val body = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
            val accessToken = body["access_token"]?.jsonPrimitive?.content
            assertNotNull(accessToken, "token response must include access_token")
            assertTrue(accessToken.isNotBlank(), "access_token must be non-blank")
            assertEquals(
                "Bearer",
                body["token_type"]?.jsonPrimitive?.content,
                "token_type must be Bearer for non-DPoP attestation auth",
            )
            assertNotNull(
                body["id_token"]?.jsonPrimitive?.content,
                "openid scope must produce an id_token",
            )
        }

    @Test
    fun tokenGrantRejectsAttestationFromUntrustedAttester() =
        runTest {
            val (login, code, pkce) = drivePkceLoginAndAuthorize("attest-untrusted")
            val challenge = fetchChallenge()
            // Sign with an attester key that was NEVER published as trusted. The trust list still
            // contains only AttestationFixtureKeys.attesterPublicJwkProperties() so this attester
            // key MUST be refused.
            val rogueAttester = RogueAttesterKey
            val attestationJwt =
                rogueAttester.signAttestation(
                    iss = ATTESTER_ISSUER,
                    sub = CLIENT_ID,
                    cnfJwk = AttestationFixtureKeys.instancePublicJwk(),
                )
            val popJwt =
                AttestationFixtureKeys.signPop(
                    iss = CLIENT_ID,
                    aud = "${fixture.baseUrl}/token",
                    nonce = challenge,
                )

            val tokenResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", code)
                            append("redirect_uri", "http://localhost:8080/test-callback")
                            append("code_verifier", pkce.verifier)
                            append("client_id", CLIENT_ID)
                        },
                ) {
                    header("OAuth-Client-Attestation", attestationJwt)
                    header("OAuth-Client-Attestation-PoP", popJwt)
                    header("Cookie", login.loginCookie)
                }
            assertEquals(
                HttpStatusCode.Unauthorized,
                tokenResponse.status,
                "Untrusted attester key must yield 401; got ${tokenResponse.status}: ${tokenResponse.bodyAsText()}",
            )
            assertOauthError(tokenResponse.bodyAsText(), expectedError = "invalid_client")
        }

    @Test
    fun tokenGrantRejectsPopWithStaleIat() =
        runTest {
            val (login, code, pkce) = drivePkceLoginAndAuthorize("attest-stale-iat")
            val challenge = fetchChallenge()
            val attestationJwt =
                AttestationFixtureKeys.signAttestation(
                    iss = ATTESTER_ISSUER,
                    sub = CLIENT_ID,
                    cnfJwk = AttestationFixtureKeys.instancePublicJwk(),
                )
            // attestationPopMaxAgeSeconds defaults to 120 in OAuth2ServerInstanceConfig. Push the
            // PoP iat 60 seconds beyond that ceiling so the freshness check fires.
            val staleIat = (System.currentTimeMillis() / 1000L) - (POP_MAX_AGE_SECONDS + 60)
            val popJwt =
                AttestationFixtureKeys.signPop(
                    iss = CLIENT_ID,
                    aud = "${fixture.baseUrl}/token",
                    nonce = challenge,
                    iatOverride = staleIat,
                )

            val tokenResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", code)
                            append("redirect_uri", "http://localhost:8080/test-callback")
                            append("code_verifier", pkce.verifier)
                            append("client_id", CLIENT_ID)
                        },
                ) {
                    header("OAuth-Client-Attestation", attestationJwt)
                    header("OAuth-Client-Attestation-PoP", popJwt)
                    header("Cookie", login.loginCookie)
                }
            assertEquals(
                HttpStatusCode.Unauthorized,
                tokenResponse.status,
                "Stale PoP iat must yield 401; got ${tokenResponse.status}: ${tokenResponse.bodyAsText()}",
            )
            assertOauthError(tokenResponse.bodyAsText(), expectedError = "invalid_client")
        }

    @Test
    fun tokenGrantRejectsPopWithReplayedJti() =
        runTest {
            // First request: full happy-path through /token. Capture the attestation+PoP and reuse
            // them on a SECOND /token invocation: the challenge nonce is single-use per
            // AttestationChallengeStorage, so the replay must be refused. (jti replay would also
            // protect us if challenges were optional, but the harness requires the challenge.)
            val first = drivePkceLoginAndAuthorize("attest-replay-1")
            val challenge = fetchChallenge()
            val attestationJwt =
                AttestationFixtureKeys.signAttestation(
                    iss = ATTESTER_ISSUER,
                    sub = CLIENT_ID,
                    cnfJwk = AttestationFixtureKeys.instancePublicJwk(),
                )
            val popJwt =
                AttestationFixtureKeys.signPop(
                    iss = CLIENT_ID,
                    aud = "${fixture.baseUrl}/token",
                    nonce = challenge,
                )

            val firstTokenResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", first.second)
                            append("redirect_uri", "http://localhost:8080/test-callback")
                            append("code_verifier", first.third.verifier)
                            append("client_id", CLIENT_ID)
                        },
                ) {
                    header("OAuth-Client-Attestation", attestationJwt)
                    header("OAuth-Client-Attestation-PoP", popJwt)
                    header("Cookie", first.first.loginCookie)
                }
            assertTrue(
                firstTokenResponse.status.isSuccess(),
                "first /token call must succeed before testing replay; got ${firstTokenResponse.status}: ${firstTokenResponse.bodyAsText()}",
            )

            // Second request reuses the SAME PoP (and thus the same nonce). It also drives a
            // fresh login so the auth-code is valid. The verifier must reject the challenge as
            // already consumed.
            val second = drivePkceLoginAndAuthorize("attest-replay-2")
            val replayResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", second.second)
                            append("redirect_uri", "http://localhost:8080/test-callback")
                            append("code_verifier", second.third.verifier)
                            append("client_id", CLIENT_ID)
                        },
                ) {
                    header("OAuth-Client-Attestation", attestationJwt)
                    header("OAuth-Client-Attestation-PoP", popJwt)
                    header("Cookie", second.first.loginCookie)
                }
            assertEquals(
                HttpStatusCode.Unauthorized,
                replayResponse.status,
                "Replayed challenge nonce must yield 401; got ${replayResponse.status}: ${replayResponse.bodyAsText()}",
            )
            assertOauthError(replayResponse.bodyAsText(), expectedError = "invalid_client")
        }

    @Test
    fun tokenGrantRejectsAttestationCnfJwkMismatch() =
        runTest {
            val (login, code, pkce) = drivePkceLoginAndAuthorize("attest-cnf-mismatch")
            val challenge = fetchChallenge()
            // Bind the attestation's cnf.jwk to the canonical instance key, but sign the PoP with
            // a DIFFERENT key. The verifier MUST refuse the PoP signature because the trustedJwks
            // pinned to cnf.jwk is the only acceptable signer.
            val attestationJwt =
                AttestationFixtureKeys.signAttestation(
                    iss = ATTESTER_ISSUER,
                    sub = CLIENT_ID,
                    cnfJwk = AttestationFixtureKeys.instancePublicJwk(),
                )
            val popJwt =
                RogueAttesterKey.signPop(
                    iss = CLIENT_ID,
                    aud = "${fixture.baseUrl}/token",
                    nonce = challenge,
                )

            val tokenResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", code)
                            append("redirect_uri", "http://localhost:8080/test-callback")
                            append("code_verifier", pkce.verifier)
                            append("client_id", CLIENT_ID)
                        },
                ) {
                    header("OAuth-Client-Attestation", attestationJwt)
                    header("OAuth-Client-Attestation-PoP", popJwt)
                    header("Cookie", login.loginCookie)
                }
            assertEquals(
                HttpStatusCode.Unauthorized,
                tokenResponse.status,
                "PoP signed by a key other than cnf.jwk must yield 401; got ${tokenResponse.status}: ${tokenResponse.bodyAsText()}",
            )
            assertOauthError(tokenResponse.bodyAsText(), expectedError = "invalid_client")
        }

    private fun publishProperty(
        key: String,
        value: String,
    ) {
        overrides.publish(key, value)
    }

    /** GET /attestation-challenge and return the nonce. */
    private suspend fun fetchChallenge(): String {
        val response = client.get("${fixture.baseUrl}/attestation-challenge")
        assertEquals(HttpStatusCode.OK, response.status, "/attestation-challenge must succeed")
        return json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["attestation_challenge"]
            ?.jsonPrimitive
            ?.content
            ?: error("response must include attestation_challenge")
    }

    private fun assertOauthError(
        body: String,
        expectedError: String,
    ) {
        val parsed =
            try {
                json.parseToJsonElement(body).jsonObject
            } catch (expected: Throwable) {
                error("response body must be JSON; got '$body' (parse: ${expected.message})")
            }
        assertEquals(
            expectedError,
            parsed["error"]?.jsonPrimitive?.content,
            "error field must equal '$expectedError'; body='$body'",
        )
    }

    /**
     * Drives the auth-code flow as alice for [CLIENT_ID]: GET /authorize → /login → POST /login →
     * resume /authorize → callback with `code`. Returns the login cookie + auth code + PKCE pair
     * so the caller can reuse the cookie when fetching the next leg of the same fixture (e.g.
     * the /attestation-challenge call below picks up alice's session for free).
     */
    private suspend fun drivePkceLoginAndAuthorize(suffix: String): Triple<LoginResult, String, AttestationPkceFixture> {
        val pkce = AttestationPkceFixture(suffix)
        val authorizeUrl =
            "${fixture.baseUrl}/authorize?response_type=code" +
                "&client_id=$CLIENT_ID" +
                "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                "&scope=openid" +
                "&state=$suffix-state" +
                "&nonce=$suffix-nonce" +
                "&code_challenge=${pkce.challenge}" +
                "&code_challenge_method=S256"
        val authorizeResponse = client.get(authorizeUrl)
        assertEquals(HttpStatusCode.Found, authorizeResponse.status, "/authorize must redirect to /login")
        val loginRedirect =
            authorizeResponse.headers["Location"]
                ?: error("/authorize must emit a Location header")
        val loginUrl = if (loginRedirect.startsWith("http")) loginRedirect else "${fixture.baseUrl}$loginRedirect"
        val sessionId = extractQueryParam(loginUrl, "session_id") ?: error("no session_id")
        val returnUrl = extractQueryParam(loginUrl, "return_url") ?: error("no return_url")

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
        assertEquals(HttpStatusCode.Found, loginResponse.status)
        val loginCookie = loginResponse.headers["Set-Cookie"] ?: error("POST /login must Set-Cookie")

        val resumed =
            client.get(loginResponse.headers["Location"]!!) {
                header("Cookie", loginCookie)
            }
        assertEquals(HttpStatusCode.Found, resumed.status, "resumed /authorize must 302 to redirect_uri")
        val callback = resumed.headers["Location"] ?: error("missing callback Location")
        val code = extractQueryParam(callback, "code") ?: error("missing code")
        return Triple(LoginResult(loginCookie.substringBefore(";")), code, pkce)
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

    private data class LoginResult(
        val loginCookie: String,
    )

    companion object {
        private const val CLIENT_ID: String = "oidf-op-attestation"
        private const val ATTESTER_ISSUER: String = "https://attester.oidf.example"

        // attestationPopMaxAgeSeconds default (OAuth2ServerInstanceConfig). Mirrored here so the
        // stale-iat test stays in sync with the AS without depending on a runtime read.
        private const val POP_MAX_AGE_SECONDS: Long = 120
    }
}

/**
 * Per-suite RSA-2048 keypairs for the attester and the client instance. Keys are generated on
 * class load so each test JVM run gets fresh material; the public JWKs are exposed as either a
 * config-property map (for the trusted-attester-jwks publication) or a [JsonObject] (for the
 * attestation's `cnf.jwk` claim).
 */
private object AttestationFixtureKeys {
    private val attesterKeyPair: KeyPair = generateRsa2048()
    private val instanceKeyPair: KeyPair = generateRsa2048()

    private val attesterPublicKey: RSAPublicKey = attesterKeyPair.public as RSAPublicKey
    private val attesterPrivateKey: RSAPrivateKey = attesterKeyPair.private as RSAPrivateKey
    private val instancePublicKey: RSAPublicKey = instanceKeyPair.public as RSAPublicKey
    private val instancePrivateKey: RSAPrivateKey = instanceKeyPair.private as RSAPrivateKey

    private val attesterKid: String = "attester-${UUID.randomUUID()}"
    private val instanceKid: String = "instance-${UUID.randomUUID()}"

    fun attesterPublicJwkProperties(): Map<String, String> =
        mapOf(
            "kty" to "RSA",
            "alg" to "RS256",
            "use" to "sig",
            "kid" to attesterKid,
            "n" to base64Url(stripLeadingZero(attesterPublicKey.modulus.toByteArray())),
            "e" to base64Url(stripLeadingZero(attesterPublicKey.publicExponent.toByteArray())),
        )

    fun instancePublicJwk(): JsonObject =
        buildJsonObject {
            put("kty", "RSA")
            put("alg", "RS256")
            put("use", "sig")
            put("kid", instanceKid)
            put("n", base64Url(stripLeadingZero(instancePublicKey.modulus.toByteArray())))
            put("e", base64Url(stripLeadingZero(instancePublicKey.publicExponent.toByteArray())))
        }

    fun signAttestation(
        iss: String,
        sub: String,
        cnfJwk: JsonObject,
    ): String {
        val now = System.currentTimeMillis() / 1000
        val header =
            buildJsonObject {
                put("alg", "RS256")
                put("typ", "oauth-client-attestation+jwt")
                put("kid", attesterKid)
            }
        val payload =
            buildJsonObject {
                put("iss", iss)
                put("sub", sub)
                put("iat", now)
                // 30-minute lifetime stays inside the AS's attestationMaxLifetimeSeconds=3600 default.
                put("exp", now + 1800)
                put("cnf", buildJsonObject { put("jwk", cnfJwk) })
            }
        return signRs256(header, payload, attesterPrivateKey)
    }

    fun signPop(
        iss: String,
        aud: String,
        nonce: String,
        iatOverride: Long? = null,
    ): String {
        val iat = iatOverride ?: (System.currentTimeMillis() / 1000)
        val header =
            buildJsonObject {
                put("alg", "RS256")
                put("typ", "oauth-client-attestation-pop+jwt")
                put("kid", instanceKid)
            }
        val payload =
            buildJsonObject {
                put("iss", iss)
                put("aud", aud)
                put("iat", iat)
                put("jti", UUID.randomUUID().toString())
                put("nonce", nonce)
            }
        return signRs256(header, payload, instancePrivateKey)
    }

    private fun generateRsa2048(): KeyPair =
        KeyPairGenerator
            .getInstance("RSA")
            .apply { initialize(2048, SecureRandom()) }
            .generateKeyPair()

    private fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }
}

/**
 * Untrusted RSA-2048 keypair used by negative-path tests. Signs attestations the AS MUST refuse
 * (because the public JWK was never added to `trusted-attester-jwks`) and also signs PoPs against
 * a `cnf.jwk` declared with a different key (the cnf-mismatch case).
 */
private object RogueAttesterKey {
    private val keyPair: KeyPair =
        KeyPairGenerator
            .getInstance("RSA")
            .apply { initialize(2048, SecureRandom()) }
            .generateKeyPair()

    private val privateKey: RSAPrivateKey = keyPair.private as RSAPrivateKey
    private val kid: String = "rogue-${UUID.randomUUID()}"

    fun signAttestation(
        iss: String,
        sub: String,
        cnfJwk: JsonObject,
    ): String {
        val now = System.currentTimeMillis() / 1000
        val header =
            buildJsonObject {
                put("alg", "RS256")
                put("typ", "oauth-client-attestation+jwt")
                put("kid", kid)
            }
        val payload =
            buildJsonObject {
                put("iss", iss)
                put("sub", sub)
                put("iat", now)
                put("exp", now + 1800)
                put("cnf", buildJsonObject { put("jwk", cnfJwk) })
            }
        return signRs256(header, payload, privateKey)
    }

    fun signPop(
        iss: String,
        aud: String,
        nonce: String,
    ): String {
        val now = System.currentTimeMillis() / 1000
        val header =
            buildJsonObject {
                put("alg", "RS256")
                put("typ", "oauth-client-attestation-pop+jwt")
                put("kid", kid)
            }
        val payload =
            buildJsonObject {
                put("iss", iss)
                put("aud", aud)
                put("iat", now)
                put("jti", UUID.randomUUID().toString())
                put("nonce", nonce)
            }
        return signRs256(header, payload, privateKey)
    }
}

/** RFC 7519 signed JWT with `RS256`: `base64url(header).base64url(payload).base64url(sig)`. */
private fun signRs256(
    header: JsonObject,
    payload: JsonObject,
    privateKey: RSAPrivateKey,
): String {
    val headerB64 = base64Url(header.toString().encodeToByteArray())
    val payloadB64 = base64Url(payload.toString().encodeToByteArray())
    val signingInput = "$headerB64.$payloadB64".encodeToByteArray()
    val signer = Signature.getInstance("SHA256withRSA")
    signer.initSign(privateKey)
    signer.update(signingInput)
    val sig = signer.sign()
    return "$headerB64.$payloadB64.${base64Url(sig)}"
}

private fun base64Url(bytes: ByteArray): String =
    Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes)

/** Per-suite duplicate of the shared S256 PKCE fixture (the original is `private` to its file). */
private class AttestationPkceFixture(
    suffix: String
) {
    val verifier: String = ("oidf-op-attestation-$suffix-2026" + "-padding-padding-padding-padding").take(64)
    val challenge: String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
}
