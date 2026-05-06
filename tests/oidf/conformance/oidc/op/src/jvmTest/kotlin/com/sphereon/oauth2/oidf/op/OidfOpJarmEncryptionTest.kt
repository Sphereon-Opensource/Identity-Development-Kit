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

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jwe.DecryptJweArgs
import com.sphereon.crypto.jose.jwe.JweCompact
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jwe.JweServiceImpl
import com.sphereon.crypto.resolution.managed.ManagedOptsJwk
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * OIDF JARM (https://openid.net/specs/oauth-v2-jarm.html) encryption-mode conformance probes.
 *
 * Phase 2.2 wired the signed-only happy paths in [OidfOpJarmTest]. This suite drives the
 * encryption surfaces:
 *
 *  - `signed_encrypted` (nested JWT: signed JWS wrapped in JWE) — the standard high-assurance
 *    JARM mode where both algs are configured.
 *  - `encrypted` (bare JWE around a plain claims set) — chosen when only
 *    `authorization_encrypted_response_alg` is configured, no signing alg.
 *  - decryption integrity test: confirms the JWE plaintext is a real AS-signed JWS verifiable
 *    against the AS's published JWKS, ruling out passthrough / sham decryption.
 *  - encryption mis-config (alg configured, no encryption JWK reachable): asserts the AS does
 *    NOT silently downgrade to bare-mode, since that erases the confidentiality guarantee the
 *    client requested. Per OIDF JARM §6.1 the AS must reject the request.
 *
 * Test client setup mirrors [OidfOpJarHappyPathTest]: an RSA-2048 keypair is generated per JVM
 * test class and the public modulus + exponent is published to `oauth2.clients.<id>.jwks.0.*` via
 * [DefaultPrincipalMapPropertySource] (a MEDIUM-priority source, beats the LOW-priority
 * `application.properties`). The test holds the matching private key for decryption. The static
 * `application.properties` only carries the algorithm config + grant/redirect-URI scaffolding.
 *
 * JWE wire-format decoding uses the IDK's own `JweService.decryptJwe`. The test wraps the test
 * RSA private key as a `Jwk` (loaded into a `ManagedOptsJwk`), then asks the AS-side session
 * graph's JweService to unwrap. This dogfoods the same crypto primitives the AS relies on, so
 * the test exercises the JARM AS wiring + transport + payload shape end to end without
 * depending on JCA-vs-cryptography-kotlin OAEP/MGF compatibility quirks.
 */
class OidfOpJarmEncryptionTest {
    private lateinit var fixture: OidfOpServerFixture
    private lateinit var client: HttpClient
    private val json = Json { ignoreUnknownKeys = true }
    private val overrides = HarnessPropertyOverride()

    @BeforeTest
    fun setUp() {
        publishClientFromTemplate(SIGNED_ENC_CLIENT_ID, JARM_ENC_CLIENT_TEMPLATE_SIGNED_ENCRYPTED)
        publishClientFromTemplate(ENCRYPT_ONLY_CLIENT_ID, JARM_ENC_CLIENT_TEMPLATE_ENCRYPTED_ONLY)
        publishClientFromTemplate(MISCONFIG_CLIENT_ID, JARM_ENC_CLIENT_TEMPLATE_MISCONFIG)
        publishClientJwks(SIGNED_ENC_CLIENT_ID, JarmEncClientKey.publicJwkProperties())
        publishClientJwks(ENCRYPT_ONLY_CLIENT_ID, JarmEncClientKey.publicJwkProperties())
        // MISCONFIG_CLIENT_ID intentionally has NO jwks so the AS encryption-key resolver fails.
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
    fun signedEncryptedJarmModeReturnsNestedJWE() =
        runTest {
            val pkce = JarmEncPkceFixture("SE")
            val callbackUrl =
                runJarmAuthorizeFlow(SIGNED_ENC_CLIENT_ID, "query.jwt", pkce, state = "jarm-enc-state-se")
                    ?: fail("query.jwt must redirect, got null callback")
            val jweCompact =
                extractQueryParam(callbackUrl, "response")
                    ?: fail("query.jwt callback must carry a `response` query parameter: $callbackUrl")

            // JARM signed-encrypted is JWS-inside-JWE. Compact JWE has 5 segments.
            val jweSegments = jweCompact.split(".")
            assertEquals(5, jweSegments.size, "signed_encrypted JARM response MUST be a JWE compact serialization (5 segments): $jweCompact")

            val plaintext = decryptJwe(jweCompact)
            val innerJws = plaintext.decodeToString()
            val jwsSegments = innerJws.split(".")
            assertEquals(3, jwsSegments.size, "inner content MUST be a JWS compact serialization (3 segments): $innerJws")

            // Verify the inner JWS signature against the AS's published JWKS.
            val header = decodeJwsHeader(innerJws)
            assertEquals(
                "RS256",
                header["alg"]?.jsonPrimitive?.content,
                "JARM signing alg MUST be RS256 per the test client's authorization-signed-response-alg",
            )
            val kid =
                header["kid"]?.jsonPrimitive?.content
                    ?: fail("inner JWS header must carry kid")
            val asJwk = fetchSigningJwk(kid)
            assertTrue(verifyRs256(innerJws, asJwk), "inner JWS signature MUST verify against the AS's published RS256 JWK")

            // Verify the JWS payload claims.
            val payload = decodeJwsPayload(innerJws)
            assertJarmPayloadShape(payload, expectedAud = SIGNED_ENC_CLIENT_ID)
        }

    @Test
    fun encryptedOnlyJarmModeReturnsBareJWE() =
        runTest {
            val pkce = JarmEncPkceFixture("EO")
            val callbackUrl =
                runJarmAuthorizeFlow(ENCRYPT_ONLY_CLIENT_ID, "query.jwt", pkce, state = "jarm-enc-state-eo")
                    ?: fail("query.jwt must redirect, got null callback")
            val jweCompact =
                extractQueryParam(callbackUrl, "response")
                    ?: fail("query.jwt callback must carry a `response` query parameter: $callbackUrl")

            val jweSegments = jweCompact.split(".")
            assertEquals(5, jweSegments.size, "encrypted JARM response MUST be a JWE compact serialization (5 segments): $jweCompact")

            val plaintext = decryptJwe(jweCompact)
            val plaintextString = plaintext.decodeToString()

            // Encrypted-only JARM: inner content is a JSON claims object directly, NOT a nested JWS.
            val innerSegments = plaintextString.split(".")
            assertNotEquals3SegmentJws(innerSegments, plaintextString)
            val payload = json.parseToJsonElement(plaintextString).jsonObject
            assertJarmPayloadShape(payload, expectedAud = ENCRYPT_ONLY_CLIENT_ID)
        }

    @Test
    fun jarmDecryptionRequiresMatchingPrivateKey() =
        runTest {
            // Drive the signed_encrypted flow, then verify decryption is real on two axes:
            //  (1) with the CORRECT private key the IDK MUST decrypt and the inner JWS payload +
            //      signature MUST match what the AS minted (proves the positive test isn't a
            //      passthrough sham — the bytes we read are genuine AS-emitted claims).
            //  (2) with an UNRELATED RSA-2048 private key the IDK MUST refuse to decrypt
            //      (regression guard against the JWE-resolver substitution bug where a stored
            //      KMS key was silently used in place of the caller-supplied JWK).
            val pkce = JarmEncPkceFixture("WK")
            val callbackUrl =
                runJarmAuthorizeFlow(SIGNED_ENC_CLIENT_ID, "query.jwt", pkce, state = "jarm-enc-state-wk")
                    ?: fail("query.jwt must redirect, got null callback")
            val jweCompact =
                extractQueryParam(callbackUrl, "response")
                    ?: fail("response missing from $callbackUrl")

            val correctSession =
                fixture.graph.userContextManager
                    .getAnonymous()
                    .sessionContextManager
                    .createOrGetFromId("jarm-enc-test-decrypt-correct")
            val correctJweService = (correctSession.graph as JweServiceImpl.Graph).jweService
            val correctResult =
                correctJweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = JweCompact.parse(jweCompact),
                        decryptor = ManagedOptsJwk(identifier = JarmEncClientKey.privateJwk()),
                    ),
                )
            assertTrue(correctResult.isOk, "Decryption with the correct private key MUST succeed")
            val plaintext =
                correctResult.value.plaintext?.decodeToString()
                    ?: fail("decrypted plaintext was null")
            // The signed_encrypted JARM JWE wraps a real RS256 JWS. Verify both shape and the
            // signature against the AS's published JWKS to prove the bytes are genuine.
            val innerSegments = plaintext.split(".")
            assertEquals(3, innerSegments.size, "inner content MUST be a JWS compact serialization (3 segments): $plaintext")
            val header = decodeJwsHeader(plaintext)
            val kid =
                header["kid"]?.jsonPrimitive?.content
                    ?: fail("inner JWS header must carry kid")
            val asJwk = fetchSigningJwk(kid)
            assertTrue(verifyRs256(plaintext, asJwk), "inner JWS signature MUST verify against the AS's published RS256 JWK")

            val wrongSession =
                fixture.graph.userContextManager
                    .getAnonymous()
                    .sessionContextManager
                    .createOrGetFromId("jarm-enc-test-decrypt-wrong")
            val wrongJweService = (wrongSession.graph as JweServiceImpl.Graph).jweService
            val wrongResult =
                wrongJweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = JweCompact.parse(jweCompact),
                        decryptor = ManagedOptsJwk(identifier = JarmEncClientKey.unrelatedPrivateJwk()),
                    ),
                )
            assertTrue(
                wrongResult.isErr,
                "Decryption with an UNRELATED RSA-2048 private key MUST fail; got Ok with plaintext " +
                    "'${if (wrongResult.isOk) wrongResult.value.plaintext?.decodeToString() else "<n/a>"}'",
            )
        }

    @Test
    fun jarmEncryptionFallsBackToBareWhenNoEncryptionJwkRegistered() =
        runTest {
            // The misconfig client has authorization_encrypted_response_alg=RSA-OAEP-256 but NO
            // jwks registered. Per OIDF JARM §6.1, the AS MUST NOT silently downgrade to bare-mode
            // on the SUCCESS path: the client asked for confidentiality, can't get it, so the AS
            // refuses. Asserting `invalid_client` post-redirect is the spec-aligned outcome.
            val pkce = JarmEncPkceFixture("MC")
            // Drive the flow but capture the resumed-authorize response directly, since on the
            // misconfig path the AS may return a non-302 status when it cannot mint the JARM JWT.
            val authorizeUrl =
                "${fixture.baseUrl}/authorize?response_type=code" +
                    "&client_id=$MISCONFIG_CLIENT_ID" +
                    "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                    "&scope=openid" +
                    "&state=jarm-enc-state-mc" +
                    "&nonce=jarm-enc-nonce-mc" +
                    "&code_challenge=${pkce.challenge}" +
                    "&code_challenge_method=S256" +
                    "&response_mode=query.jwt"
            val authorizeResponse = client.get(authorizeUrl)
            assertEquals(HttpStatusCode.Found, authorizeResponse.status, "initial authorize must 302 to /login")
            val loginRedirect = authorizeResponse.headers["Location"] ?: error("no Location")
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
            val cookie = loginResponse.headers["Set-Cookie"] ?: error("no Set-Cookie")
            val resumed =
                client.get(loginResponse.headers["Location"]!!) {
                    header("Cookie", cookie)
                }

            // Three possible outcomes the AS could choose:
            // CASE A (spec-aligned, redirect with error): 302 to redirect_uri with `error=invalid_client`
            //   and NO `response` parameter. This is the desired outcome.
            // CASE B (concern, silent downgrade): 302 to redirect_uri with `response=<jwt>` (a JARM
            //   JWT, possibly bare-signed without encryption). This violates the client's
            //   confidentiality request.
            // CASE C (server-error JSON): non-302 with a JSON error body. Acceptable because the
            //   client never sees a downgraded JARM payload, but less helpful than CASE A because
            //   the client can't recover gracefully.
            val resumedStatus = resumed.status
            val callbackUrl = resumed.headers["Location"]
            if (resumedStatus == HttpStatusCode.Found && callbackUrl != null) {
                val responseParam = extractQueryParam(callbackUrl, "response")
                val errorParam = extractQueryParam(callbackUrl, "error")
                if (errorParam != null) {
                    assertNull(responseParam, "spec-aligned rejection MUST NOT also carry a JARM `response` parameter")
                    assertTrue(
                        errorParam == "invalid_client" || errorParam == "invalid_request",
                        "encryption-misconfigured client MUST surface invalid_client (preferred) or invalid_request, got '$errorParam'",
                    )
                } else {
                    assertNotNull(responseParam, "callback must carry either error or response")
                    val segments = responseParam.split(".")
                    fail(
                        "AS did not reject the encryption-misconfigured client. " +
                            "Got `response` with ${segments.size} segments instead of `error=invalid_client`. " +
                            "Silent downgrade erases the confidentiality guarantee the client requested.",
                    )
                }
            } else {
                // CASE C: non-302 server error. Acceptable for this test since no downgraded
                // JARM JWT reaches the client, but a Concern: the client can't surface a clean
                // OAuth error to its user.
                assertTrue(
                    resumedStatus.value in 400..599,
                    "non-302 outcome must be a 4xx/5xx error; got $resumedStatus",
                )
            }
        }

    /**
     * Drive the harness happy-path (authorize → /login → resumed authorize) for the supplied JARM
     * client and `response_mode`. Returns the final callback URL or `null` on form_post (not used
     * here).
     */
    private suspend fun runJarmAuthorizeFlow(
        clientId: String,
        responseMode: String,
        pkce: JarmEncPkceFixture,
        state: String,
    ): String? {
        val authorizeUrl =
            "${fixture.baseUrl}/authorize?response_type=code" +
                "&client_id=$clientId" +
                "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                "&scope=openid" +
                "&state=$state" +
                "&nonce=jarm-enc-nonce-${state.replace('.', '-')}" +
                "&code_challenge=${pkce.challenge}" +
                "&code_challenge_method=S256" +
                "&response_mode=$responseMode"
        val authorizeResponse = client.get(authorizeUrl)
        assertEquals(HttpStatusCode.Found, authorizeResponse.status, "authorize must 302 to /login")
        val loginRedirect = authorizeResponse.headers["Location"] ?: error("no Location")
        val loginUrl = if (loginRedirect.startsWith("http")) loginRedirect else "${fixture.baseUrl}$loginRedirect"
        val sessionId = extractQueryParam(loginUrl, "session_id") ?: error("no session_id in $loginUrl")
        val returnUrl = extractQueryParam(loginUrl, "return_url") ?: error("no return_url in $loginUrl")

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
        val cookie = loginResponse.headers["Set-Cookie"] ?: error("no Set-Cookie")
        val resumed =
            client.get(loginResponse.headers["Location"]!!) {
                header("Cookie", cookie)
            }
        if (resumed.status != HttpStatusCode.Found) {
            return null
        }
        return resumed.headers["Location"]
    }

    private fun assertJarmPayloadShape(
        payload: JsonObject,
        expectedAud: String,
    ) {
        assertEquals(fixture.baseUrl, payload["iss"]?.jsonPrimitive?.content, "iss must equal AS issuer URL")
        assertEquals(expectedAud, payload["aud"]?.jsonPrimitive?.content, "aud must equal client_id")
        assertNotNull(payload["exp"], "exp must be present")
        assertNotNull(payload["code"], "code must be present in JARM payload")
        assertNotNull(payload["state"], "state must round-trip in JARM payload")
    }

    /**
     * Decrypt a JWE compact serialization using the IDK's own [JweService]. Wraps the test's
     * held RSA private key as a [Jwk] and feeds it through `ManagedOptsJwk` so the same crypto
     * pipeline the AS uses to encrypt is the one used to decrypt here, dodging JCA/cryptography-
     * kotlin OAEP/MGF1 compatibility issues.
     */
    private suspend fun decryptJwe(jwe: String): ByteArray {
        val privateJwk = JarmEncClientKey.privateJwk()
        val session =
            fixture.graph.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("jarm-enc-test-decrypt")
        val jweService = (session.graph as JweServiceImpl.Graph).jweService

        val parsed = JweCompact.parse(jwe)
        val result =
            jweService.decryptJwe(
                DecryptJweArgs(
                    jwe = parsed,
                    decryptor = ManagedOptsJwk(identifier = privateJwk),
                ),
            )
        if (result.isErr) {
            error("JweService decryption failed: ${result.error.message.defaultMessage}")
        }
        return result.value.plaintext ?: error("JweService returned null plaintext for $jwe")
    }

    private suspend fun fetchSigningJwk(kid: String): JsonObject {
        val discovery =
            json
                .parseToJsonElement(client.get("${fixture.baseUrl}/.well-known/openid-configuration").bodyAsText())
                .jsonObject
        val jwksUri =
            discovery["jwks_uri"]?.jsonPrimitive?.content
                ?: error("discovery must advertise jwks_uri")
        val jwksObject = json.parseToJsonElement(client.get(jwksUri).bodyAsText()).jsonObject
        val keys = jwksObject["keys"] as? kotlinx.serialization.json.JsonArray ?: error("JWKS missing keys array")
        return keys
            .map { it.jsonObject }
            .firstOrNull { it["kid"]?.jsonPrimitive?.content == kid }
            ?: error("JWKS does not advertise kid '$kid' the JARM JWS header carried")
    }

    private fun verifyRs256(
        compactJws: String,
        jwk: JsonObject,
    ): Boolean {
        val segments = compactJws.split(".")
        require(segments.size == 3) { "compact JWS must have three segments" }
        val signingInput = "${segments[0]}.${segments[1]}".encodeToByteArray()
        val signature = Base64.getUrlDecoder().decode(padBase64Url(segments[2]))
        val nB64 = jwk["n"]?.jsonPrimitive?.content ?: error("JWK missing n")
        val eB64 = jwk["e"]?.jsonPrimitive?.content ?: error("JWK missing e")
        val modulus = BigInteger(1, Base64.getUrlDecoder().decode(padBase64Url(nB64)))
        val exponent = BigInteger(1, Base64.getUrlDecoder().decode(padBase64Url(eB64)))
        val publicKey: PublicKey =
            KeyFactory
                .getInstance("RSA")
                .generatePublic(RSAPublicKeySpec(modulus, exponent))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(signingInput)
        return verifier.verify(signature)
    }

    private fun decodeJwsHeader(jws: String): JsonObject {
        val seg = jws.split(".").getOrNull(0) ?: error("JWS must have 3 segments")
        val decoded =
            Base64
                .getUrlDecoder()
                .decode(padBase64Url(seg))
                .decodeToString()
        return json.parseToJsonElement(decoded).jsonObject
    }

    private fun decodeJwsPayload(jws: String): JsonObject {
        val seg = jws.split(".").getOrNull(1) ?: error("JWS must have 3 segments")
        val decoded =
            Base64
                .getUrlDecoder()
                .decode(padBase64Url(seg))
                .decodeToString()
        return json.parseToJsonElement(decoded).jsonObject
    }

    private fun assertNotEquals3SegmentJws(
        segments: List<String>,
        plaintext: String,
    ) {
        // A 3-segment string COULD coincidentally be a JWS, but encrypted-only JARM payload is JSON
        // which contains "{" — that won't base64url-decode as a JOSE header. Heuristic: try parsing
        // as JSON and accept; if it's not JSON, fail noisily.
        try {
            json.parseToJsonElement(plaintext)
        } catch (expected: Exception) {
            fail(
                "encrypted-only JARM plaintext MUST be a JSON claims object, " +
                    "not a nested structure with ${segments.size} segments: $plaintext (parse error: ${expected.message})",
            )
        }
    }

    private fun publishClientFromTemplate(
        clientId: String,
        properties: Map<String, String>,
    ) {
        properties.forEach { (suffix, value) ->
            overrides.publish(
                "oauth2.clients.$clientId.$suffix",
                value,
            )
        }
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

    private fun padBase64Url(segment: String): String =
        when (segment.length % 4) {
            0 -> segment
            2 -> "$segment=="
            3 -> "$segment="
            else -> error("Invalid base64url length: ${segment.length}")
        }

    companion object {
        private const val SIGNED_ENC_CLIENT_ID = "oidf-op-jarm-enc"
        private const val ENCRYPT_ONLY_CLIENT_ID = "oidf-op-jarm-encrypt-only"
        private const val MISCONFIG_CLIENT_ID = "oidf-op-jarm-enc-misconfig"

        // ---------------------------------------------------------------------------
        // Static client templates (algorithm config + grant/redirect URIs). The test
        // private key + public JWK live in JarmEncClientKey and are published
        // separately so the same RSA modulus is wired into the AS at startup time.
        // ---------------------------------------------------------------------------
        private val JARM_ENC_CLIENT_TEMPLATE_SIGNED_ENCRYPTED =
            mapOf(
                "client-id" to SIGNED_ENC_CLIENT_ID,
                "client-name" to "JARM signed_encrypted test client",
                "client-type" to "PUBLIC",
                "grant-types" to "authorization_code",
                "response-types" to "code",
                "token-endpoint-auth-method" to "none",
                "allowed-scopes" to "openid",
                "redirect-uris.0" to "http://localhost:8080/test-callback",
                "require-pkce" to "true",
                "authorization-signed-response-alg" to "RS256",
                "authorization-encrypted-response-alg" to "RSA-OAEP-256",
                "authorization-encrypted-response-enc" to "A256GCM",
            )

        private val JARM_ENC_CLIENT_TEMPLATE_ENCRYPTED_ONLY =
            mapOf(
                "client-id" to ENCRYPT_ONLY_CLIENT_ID,
                "client-name" to "JARM encrypted-only test client",
                "client-type" to "PUBLIC",
                "grant-types" to "authorization_code",
                "response-types" to "code",
                "token-endpoint-auth-method" to "none",
                "allowed-scopes" to "openid",
                "redirect-uris.0" to "http://localhost:8080/test-callback",
                "require-pkce" to "true",
                // No authorization-signed-response-alg → JarmConfig.fromClientMetadata picks ENCRYPTED.
                "authorization-encrypted-response-alg" to "RSA-OAEP-256",
                "authorization-encrypted-response-enc" to "A256GCM",
            )

        private val JARM_ENC_CLIENT_TEMPLATE_MISCONFIG =
            mapOf(
                "client-id" to MISCONFIG_CLIENT_ID,
                "client-name" to "JARM encryption-misconfigured client (no jwks)",
                "client-type" to "PUBLIC",
                "grant-types" to "authorization_code",
                "response-types" to "code",
                "token-endpoint-auth-method" to "none",
                "allowed-scopes" to "openid",
                "redirect-uris.0" to "http://localhost:8080/test-callback",
                "require-pkce" to "true",
                "authorization-signed-response-alg" to "RS256",
                "authorization-encrypted-response-alg" to "RSA-OAEP-256",
                "authorization-encrypted-response-enc" to "A256GCM",
                // Note: deliberately no jwks.0.* entries so the AS encryption-key resolver fails.
            )
    }
}

/**
 * Suite-shared RSA-2048 keypair. Tests publish the public JWK to the AS via
 * [DefaultPrincipalMapPropertySource] and hold the matching private key for decryption.
 */
private object JarmEncClientKey {
    val keyPair: KeyPair =
        KeyPairGenerator
            .getInstance("RSA")
            .apply { initialize(2048, SecureRandom()) }
            .generateKeyPair()

    val publicKey: RSAPublicKey = keyPair.public as RSAPublicKey
    val privateKey: RSAPrivateKey = keyPair.private as RSAPrivateKey

    private val nB64: String = base64Url(stripLeadingZero(publicKey.modulus.toByteArray()))
    private val eB64: String = base64Url(stripLeadingZero(publicKey.publicExponent.toByteArray()))

    // RFC 7518 §6.3.2 RSA private key components. Java's KeyPairGenerator returns CRT keys
    // for RSA, so the CRT factors (p, q, dp, dq, qi) are always available.
    private val privateCrt: java.security.interfaces.RSAPrivateCrtKey =
        privateKey as java.security.interfaces.RSAPrivateCrtKey
    private val dB64: String = base64Url(stripLeadingZero(privateCrt.privateExponent.toByteArray()))
    private val pB64: String = base64Url(stripLeadingZero(privateCrt.primeP.toByteArray()))
    private val qB64: String = base64Url(stripLeadingZero(privateCrt.primeQ.toByteArray()))
    private val dpB64: String = base64Url(stripLeadingZero(privateCrt.primeExponentP.toByteArray()))
    private val dqB64: String = base64Url(stripLeadingZero(privateCrt.primeExponentQ.toByteArray()))
    private val qiB64: String = base64Url(stripLeadingZero(privateCrt.crtCoefficient.toByteArray()))

    fun publicJwkProperties(): Map<String, String> =
        mapOf(
            "kty" to "RSA",
            "alg" to "RSA-OAEP-256",
            "use" to "enc",
            "kid" to "jarm-enc-test-key",
            "n" to nB64,
            "e" to eB64,
        )

    /**
     * Build the test's private JWK in the IDK [Jwk] data class. Used to hand the matching
     * decryption key to [JweService.decryptJwe] via [ManagedOptsJwk] so the IDK's own crypto
     * primitives unwrap the AS-emitted JWE.
     */
    fun privateJwk(): Jwk =
        Jwk(
            kty = JwaKeyType.RSA,
            alg = JwaAlgorithm.fromValue("RSA-OAEP-256"),
            use = "enc",
            kid = "jarm-enc-test-key",
            n = nB64,
            e = eB64,
            d = dB64,
            p = pB64,
            q = qB64,
            dP = dpB64,
            dQ = dqB64,
            qInv = qiB64,
        )

    /**
     * Build a fresh, unrelated RSA-2048 private JWK on each call. Used by the negative test
     * to confirm the JWE service rejects a non-matching key.
     */
    fun unrelatedPrivateJwk(): Jwk {
        val kp =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(2048, SecureRandom()) }
                .generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val crt = kp.private as java.security.interfaces.RSAPrivateCrtKey
        return Jwk(
            kty = JwaKeyType.RSA,
            alg = JwaAlgorithm.fromValue("RSA-OAEP-256"),
            use = "enc",
            kid = "jarm-enc-unrelated-key",
            n = base64Url(stripLeadingZero(pub.modulus.toByteArray())),
            e = base64Url(stripLeadingZero(pub.publicExponent.toByteArray())),
            d = base64Url(stripLeadingZero(crt.privateExponent.toByteArray())),
            p = base64Url(stripLeadingZero(crt.primeP.toByteArray())),
            q = base64Url(stripLeadingZero(crt.primeQ.toByteArray())),
            dP = base64Url(stripLeadingZero(crt.primeExponentP.toByteArray())),
            dQ = base64Url(stripLeadingZero(crt.primeExponentQ.toByteArray())),
            qInv = base64Url(stripLeadingZero(crt.crtCoefficient.toByteArray())),
        )
    }

    private fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }

    private fun base64Url(bytes: ByteArray): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
}

private class JarmEncPkceFixture(
    seed: String,
) {
    val verifier: String = "oidf-op-conformance-pkce-verifier-fixture-2026-jarm-enc-$seed"
    val challenge: String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
}
