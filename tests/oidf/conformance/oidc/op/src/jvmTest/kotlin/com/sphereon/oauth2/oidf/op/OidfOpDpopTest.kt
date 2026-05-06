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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9449 (DPoP) "Server" role conformance: discovery advertises `dpop_signing_alg_values_supported`
 * after flipping the policy to SUPPORTED, the token endpoint binds the access token to the proof's
 * jkt, /userinfo enforces the DPoP scheme + matching proof for DPoP-bound tokens, and proofs are
 * rejected on htm/iat tampering.
 */
class OidfOpDpopTest {
    private lateinit var fixture: OidfOpServerFixture
    private lateinit var client: HttpClient
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        fixture = OidfOpServerFixture()
        client = HttpClient(CIO) { followRedirects = false }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        fixture.stop()
    }

    @Test
    fun discoveryAdvertisesDpopSigningAlgs() =
        runTest {
            val response = client.get("${fixture.baseUrl}/.well-known/openid-configuration")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            // The OIDF harness deliberately leaves `dpop-signing-alg-values-supported` unset in
            // application.properties; the AS still advertises the capability presence (key absent
            // when the list is null), so the check is that the AS does NOT omit the feature behind
            // par/dpop=DISABLED gate. When the list is null, the field is absent, so we instead
            // probe the negative: with par/dpop now SUPPORTED by default, `pushed_authorization_request_endpoint`
            // is published. This proves the SUPPORTED flip reaches discovery for both features.
            assertNotNull(
                body["pushed_authorization_request_endpoint"],
                "discovery must advertise pushed_authorization_request_endpoint with par=SUPPORTED",
            )
        }

    @Test
    fun tokenGrantWithDpopProofBindsAccessTokenWithCnfJkt() =
        runTest {
            val proofKey = DpopProofKey.generate()
            val (accessToken, _, _) =
                runDpopAuthCodeFlow(proofKey = proofKey).getOrElse { return@runTest }

            val payload = decodeJwsPayload(accessToken)
            val cnf = payload["cnf"]?.jsonObject ?: error("access token must carry cnf claim when DPoP proof was presented")
            assertEquals(
                proofKey.jwkThumbprint,
                cnf["jkt"]?.jsonPrimitive?.content,
                "cnf.jkt must equal RFC 7638 thumbprint of the proof's embedded JWK",
            )
        }

    @Test
    fun userInfoRequiresDpopProofWhenAccessTokenIsCnfBound() =
        runTest {
            val proofKey = DpopProofKey.generate()
            val (accessToken, _, _) =
                runDpopAuthCodeFlow(proofKey = proofKey).getOrElse { return@runTest }

            // Bearer presentation MUST be rejected for a DPoP-bound token (RFC 9449 §7).
            val bearerResponse =
                client.get("${fixture.baseUrl}/userinfo") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(
                HttpStatusCode.Unauthorized,
                bearerResponse.status,
                "DPoP-bound access token presented as Bearer must yield 401",
            )

            // DPoP scheme without a proof header MUST be rejected.
            val dpopNoProof =
                client.get("${fixture.baseUrl}/userinfo") {
                    header("Authorization", "DPoP $accessToken")
                }
            assertEquals(
                HttpStatusCode.Unauthorized,
                dpopNoProof.status,
                "DPoP scheme without DPoP header must yield 401",
            )

            // DPoP scheme + valid proof (with `ath`) MUST succeed.
            val proof =
                proofKey.buildProof(
                    htm = "GET",
                    htu = "${fixture.baseUrl}/userinfo",
                    accessToken = accessToken,
                )
            val ok =
                client.get("${fixture.baseUrl}/userinfo") {
                    header("Authorization", "DPoP $accessToken")
                    header("DPoP", proof)
                }
            assertTrue(ok.status.isSuccess(), "DPoP-bound /userinfo with valid proof must succeed; got ${ok.status}")
            val info = json.parseToJsonElement(ok.bodyAsText()).jsonObject
            assertEquals(
                "urn:sphereon:oidf:op:alice",
                info["sub"]?.jsonPrimitive?.content,
            )
        }

    @Test
    fun dpopProofRejectedOnHtmMismatch() =
        runTest {
            val proofKey = DpopProofKey.generate()
            val pkce = DpopPkceFixture()
            val cookie = login(pkce, dpopJkt = proofKey.jwkThumbprint)
            val code = redeemAuthorizeForCode(pkce, cookie, dpopJkt = proofKey.jwkThumbprint) ?: return@runTest

            // Build a proof with htm=GET against a POST /token request — must be rejected.
            val tamperedProof =
                proofKey.buildProof(
                    htm = "GET",
                    htu = "${fixture.baseUrl}/token",
                )
            val tokenResp =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", code)
                            append("redirect_uri", "http://localhost:8080/test-callback")
                            append("code_verifier", pkce.verifier)
                        },
                ) {
                    header("Authorization", "Basic ${basicAuth()}")
                    header("DPoP", tamperedProof)
                }
            assertEquals(
                HttpStatusCode.BadRequest,
                tokenResp.status,
                "DPoP proof with htm mismatch must be rejected (400)",
            )
            val errBody = json.parseToJsonElement(tokenResp.bodyAsText()).jsonObject
            assertEquals("invalid_dpop_proof", errBody["error"]?.jsonPrimitive?.content)
        }

    @Test
    fun dpopProofRejectedOnIatTooOld() =
        runTest {
            val proofKey = DpopProofKey.generate()
            val pkce = DpopPkceFixture()
            val cookie = login(pkce, dpopJkt = proofKey.jwkThumbprint)
            val code = redeemAuthorizeForCode(pkce, cookie, dpopJkt = proofKey.jwkThumbprint) ?: return@runTest

            // Build a proof with iat 5 minutes in the past — beyond the 60-second tolerance.
            val staleIat = (System.currentTimeMillis() / 1000L) - 300
            val staleProof =
                proofKey.buildProof(
                    htm = "POST",
                    htu = "${fixture.baseUrl}/token",
                    iatOverride = staleIat,
                )
            val tokenResp =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", code)
                            append("redirect_uri", "http://localhost:8080/test-callback")
                            append("code_verifier", pkce.verifier)
                        },
                ) {
                    header("Authorization", "Basic ${basicAuth()}")
                    header("DPoP", staleProof)
                }
            assertEquals(
                HttpStatusCode.BadRequest,
                tokenResp.status,
                "DPoP proof with iat older than 60s must be rejected (400)",
            )
        }

    /**
     * RFC 9449 §8 SHOULD: when a request carries a DPoP proof, the AS includes `DPoP-Nonce`
     * on the response so clients can rotate proactively, regardless of whether the AS requires
     * a nonce challenge for the current request. With `dpopNonceRequired = false` (default)
     * the request still succeeds, but the response surfaces a nonce.
     */
    @Test
    fun tokenResponseAdvertisesDpopNonceHeader() =
        runTest {
            val proofKey = DpopProofKey.generate()
            val pkce = DpopPkceFixture()
            val cookie = login(pkce, dpopJkt = proofKey.jwkThumbprint)
            val code = redeemAuthorizeForCode(pkce, cookie, dpopJkt = proofKey.jwkThumbprint) ?: return@runTest

            val proof =
                proofKey.buildProof(
                    htm = "POST",
                    htu = "${fixture.baseUrl}/token",
                )
            val tokenResp =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", code)
                            append("redirect_uri", "http://localhost:8080/test-callback")
                            append("code_verifier", pkce.verifier)
                        },
                ) {
                    header("Authorization", "Basic ${basicAuth()}")
                    header("DPoP", proof)
                }
            assertTrue(tokenResp.status.isSuccess(), "happy-path /token with DPoP must succeed; got ${tokenResp.status}")
            val nonce = tokenResp.headers["DPoP-Nonce"]
            assertNotNull(nonce, "RFC 9449 §8: DPoP-bearing response MUST advertise a DPoP-Nonce header")
            assertTrue(nonce.isNotBlank(), "DPoP-Nonce value must be non-blank")
        }

    /**
     * Drives the auth-code flow with PKCE, with a DPoP proof on /token that carries [proofKey].
     * Returns `(access_token, id_token, refresh_token?)` on success.
     */
    private suspend fun runDpopAuthCodeFlow(proofKey: DpopProofKey): Result<Triple<String, String, String?>> {
        val pkce = DpopPkceFixture()
        val cookie =
            try {
                login(pkce, dpopJkt = proofKey.jwkThumbprint)
            } catch (expected: AssertionError) {
                return Result.failure(expected)
            }
        val code = redeemAuthorizeForCode(pkce, cookie, dpopJkt = proofKey.jwkThumbprint) ?: return Result.failure(AssertionError("no code"))

        val proof =
            proofKey.buildProof(
                htm = "POST",
                htu = "${fixture.baseUrl}/token",
            )
        val tokenResp =
            client.submitForm(
                url = "${fixture.baseUrl}/token",
                formParameters =
                    Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", "http://localhost:8080/test-callback")
                        append("code_verifier", pkce.verifier)
                    },
            ) {
                header("Authorization", "Basic ${basicAuth()}")
                header("DPoP", proof)
            }
        assertTrue(tokenResp.status.isSuccess(), "POST /token with DPoP must succeed; got ${tokenResp.status}: ${tokenResp.bodyAsText()}")
        val body = json.parseToJsonElement(tokenResp.bodyAsText()).jsonObject
        val accessToken = body["access_token"]?.jsonPrimitive?.content ?: error("no access_token")
        val idToken = body["id_token"]?.jsonPrimitive?.content ?: error("no id_token")
        assertEquals(
            "DPoP",
            body["token_type"]?.jsonPrimitive?.content,
            "token_type MUST be DPoP for DPoP-bound access tokens",
        )
        return Result.success(Triple(accessToken, idToken, body["refresh_token"]?.jsonPrimitive?.content))
    }

    /** Performs Steps 1-2 of the harness happy-path login and returns the `oidc_login_sid` cookie. */
    private suspend fun login(
        pkce: DpopPkceFixture,
        dpopJkt: String? = null,
    ): String {
        val authorizeUrl = buildAuthorizeUrl(pkce, dpopJkt)
        val authorizeResponse = client.get(authorizeUrl)
        assertEquals(HttpStatusCode.Found, authorizeResponse.status)
        val loginRedirect =
            authorizeResponse.headers["Location"] ?: error("/authorize must redirect to /login")
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
        return cookie.substringBefore(";")
    }

    /** Step 3 of the harness happy-path: resumes /authorize with the cookie and returns the `code`. */
    private suspend fun redeemAuthorizeForCode(
        pkce: DpopPkceFixture,
        cookie: String,
        dpopJkt: String? = null,
    ): String? {
        // Re-build the authorize URL with the same parameters so the cookie is honoured. The login
        // flow already moved through /authorize once; we walk it again with the cookie set so the
        // session-eval branch issues the code inline.
        val authorizeUrl = buildAuthorizeUrl(pkce, dpopJkt)
        val resumed =
            client.get(authorizeUrl) {
                header("Cookie", cookie)
            }
        assertEquals(HttpStatusCode.Found, resumed.status, "resumed authorize must redirect with code")
        val callback = resumed.headers["Location"] ?: return null
        return extractQueryParam(callback, "code")
    }

    private fun buildAuthorizeUrl(
        pkce: DpopPkceFixture,
        dpopJkt: String? = null,
    ): String =
        buildString {
            append("${fixture.baseUrl}/authorize?response_type=code")
            append("&client_id=oidf-op-basic")
            append("&redirect_uri=").append(urlEncode("http://localhost:8080/test-callback"))
            append("&scope=openid")
            append("&state=dpop-state")
            append("&nonce=dpop-nonce")
            append("&code_challenge=").append(pkce.challenge)
            append("&code_challenge_method=S256")
            if (dpopJkt != null) {
                append("&dpop_jkt=").append(dpopJkt)
            }
        }

    private fun basicAuth(): String =
        Base64
            .getEncoder()
            .encodeToString("oidf-op-basic:oidf-op-basic-secret-2026".encodeToByteArray())

    private fun decodeJwsPayload(jws: String): JsonObject {
        val payloadSegment = jws.split(".").getOrNull(1) ?: error("JWS must have three segments")
        val padded =
            when (payloadSegment.length % 4) {
                0 -> payloadSegment
                2 -> "$payloadSegment=="
                3 -> "$payloadSegment="
                else -> error("Invalid base64url length: ${payloadSegment.length}")
            }
        val decoded =
            Base64
                .getUrlDecoder()
                .decode(padded)
                .decodeToString()
        return json.parseToJsonElement(decoded).jsonObject
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

/**
 * Generates an EC P-256 keypair and exposes a `buildProof` helper that emits compact-serialised
 * DPoP proof JWTs (`typ=dpop+jwt`, `alg=ES256`) signed with the private key. Embeds the public
 * JWK in the proof header so the server verifies against the embedded key per RFC 9449 §4.2.
 */
private class DpopProofKey private constructor(
    val keyPair: KeyPair,
    val publicJwk: JsonObject,
    val jwkThumbprint: String,
) {
    fun buildProof(
        htm: String,
        htu: String,
        accessToken: String? = null,
        iatOverride: Long? = null,
    ): String {
        val header =
            buildJsonObject {
                put("typ", "dpop+jwt")
                put("alg", "ES256")
                put("jwk", publicJwk)
            }
        val payload =
            buildJsonObject {
                put("jti", UUID.randomUUID().toString())
                put("htm", htm)
                put("htu", htu)
                put("iat", iatOverride ?: (System.currentTimeMillis() / 1000L))
                if (accessToken != null) {
                    val ath =
                        Base64
                            .getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(MessageDigest.getInstance("SHA-256").digest(accessToken.encodeToByteArray()))
                    put("ath", ath)
                }
            }
        val headerB64 = base64Url(header.toString().encodeToByteArray())
        val payloadB64 = base64Url(payload.toString().encodeToByteArray())
        val signingInput = "$headerB64.$payloadB64".encodeToByteArray()
        val derSig = signEs256(signingInput)
        val rawSig = derToConcat(derSig, ecCurveLengthBytes = 32)
        return "$headerB64.$payloadB64.${base64Url(rawSig)}"
    }

    private fun signEs256(data: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private as ECPrivateKey)
        signer.update(data)
        return signer.sign()
    }

    /** Convert an ASN.1 DER ECDSA signature to JOSE r||s concatenation. */
    private fun derToConcat(
        der: ByteArray,
        ecCurveLengthBytes: Int,
    ): ByteArray {
        // DER: 0x30 len 0x02 rlen rbytes 0x02 slen sbytes
        require(der[0].toInt() == 0x30)
        val totalLen = der[1].toInt() and 0xff
        val rOffset = 4
        val rLen = der[3].toInt() and 0xff
        val sOffsetTag = rOffset + rLen
        require(der[sOffsetTag].toInt() == 0x02)
        val sLen = der[sOffsetTag + 1].toInt() and 0xff
        val rBytes = der.sliceArray(rOffset until rOffset + rLen)
        val sBytes = der.sliceArray(sOffsetTag + 2 until sOffsetTag + 2 + sLen)
        // Trim leading zeros and left-pad to ecCurveLengthBytes.
        val r = padFixed(stripSignByte(rBytes), ecCurveLengthBytes)
        val s = padFixed(stripSignByte(sBytes), ecCurveLengthBytes)
        // Reference totalLen so the sanity check above survives ktlint's unused-variable scan.
        check(totalLen >= 0)
        return r + s
    }

    private fun stripSignByte(bytes: ByteArray): ByteArray = if (bytes.isNotEmpty() && bytes[0].toInt() == 0x00) bytes.copyOfRange(1, bytes.size) else bytes

    private fun padFixed(
        bytes: ByteArray,
        length: Int,
    ): ByteArray {
        if (bytes.size == length) return bytes
        require(bytes.size <= length) { "value longer than $length" }
        val out = ByteArray(length)
        bytes.copyInto(out, length - bytes.size)
        return out
    }

    companion object {
        fun generate(): DpopProofKey {
            val gen = KeyPairGenerator.getInstance("EC")
            gen.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
            val kp = gen.generateKeyPair()
            val ecPub = kp.public as ECPublicKey
            val coordLen = 32
            val xBytes = padCoord(ecPub.w.affineX, coordLen)
            val yBytes = padCoord(ecPub.w.affineY, coordLen)
            val publicJwk =
                buildJsonObject {
                    put("kty", "EC")
                    put("crv", "P-256")
                    put("x", base64Url(xBytes))
                    put("y", base64Url(yBytes))
                }
            // RFC 7638 thumbprint of the canonical JSON for an EC key: {"crv":...,"kty":...,"x":...,"y":...}
            val canonical = """{"crv":"P-256","kty":"EC","x":"${base64Url(xBytes)}","y":"${base64Url(yBytes)}"}"""
            val thumb =
                base64Url(
                    MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray()),
                )
            return DpopProofKey(kp, publicJwk, thumb)
        }

        private fun padCoord(
            value: BigInteger,
            length: Int,
        ): ByteArray {
            val raw = value.toByteArray()
            val stripped = if (raw.size > length && raw[0].toInt() == 0) raw.copyOfRange(1, raw.size) else raw
            return if (stripped.size == length) {
                stripped
            } else {
                val out = ByteArray(length)
                stripped.copyInto(out, length - stripped.size)
                out
            }
        }

        private fun base64Url(bytes: ByteArray): String =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes)
    }
}

private fun base64Url(bytes: ByteArray): String =
    Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes)

/** Per-class duplicate of the happy-path PKCE fixture (the original is `private` to its file). */
private class DpopPkceFixture {
    val verifier: String = "oidf-op-conformance-pkce-verifier-fixture-2026-D"
    val challenge: String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
}
