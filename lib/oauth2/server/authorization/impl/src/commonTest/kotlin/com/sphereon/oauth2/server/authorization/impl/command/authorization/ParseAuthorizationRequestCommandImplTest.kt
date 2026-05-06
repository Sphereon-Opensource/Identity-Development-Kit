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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.model.Prompt
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for OIDC authorization-request parsing.
 *
 * Exercises every parameter the parser now extracts from a flat `queryParameters` map,
 * the PKCE default (absent method must stay `null`, not silently upgrade to `S256`),
 * the optional-`redirect_uri` behavior, and malformed-input rejection at the boundary.
 */
class ParseAuthorizationRequestCommandImplTest {
    private val ctx = OAuth2ServerTestContext("parse-auth-request-test", this)
    private val command = ParseAuthorizationRequestCommandImpl(ctx.execution)

    private fun minimalParams(vararg extras: Pair<String, String>): Map<String, String> =
        buildMap {
            put("response_type", "code")
            put("client_id", "test-client")
            putAll(extras)
        }

    // ─── PKCE default behavior ────────────────────────────────────

    @Test
    fun parseAbsentCodeChallengeMethodLeavesNullNotS256() =
        runTest {
            // An authorization request without `code_challenge_method` must leave the field
            // null. RFC 7636 §4.3 defines the default as "plain", and whether "plain" is
            // acceptable is a server-policy decision delegated to the verifier. Silently
            // upgrading the absent form to "S256" at parse time masks non-compliant clients.
            val params =
                minimalParams(
                    "code_challenge" to "a".repeat(43),
                )

            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk)
            assertNull(result.value.codeChallengeMethod, "absent method must stay null, not default to S256")
            assertEquals("a".repeat(43), result.value.codeChallenge)
        }

    @Test
    fun parseCodeChallengeMethodExplicitPlainIsPlain() =
        runTest {
            val params =
                minimalParams(
                    "code_challenge" to "a".repeat(43),
                    "code_challenge_method" to "plain",
                )
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk)
            assertEquals(PkceMethod.PLAIN, result.value.codeChallengeMethod)
        }

    @Test
    fun parseCodeChallengeMethodExplicitS256IsS256() =
        runTest {
            val params =
                minimalParams(
                    "code_challenge" to "a".repeat(43),
                    "code_challenge_method" to "S256",
                )
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk)
            assertEquals(PkceMethod.S256, result.value.codeChallengeMethod)
        }

    @Test
    fun parseInvalidCodeChallengeMethodRejected() =
        runTest {
            val params =
                minimalParams(
                    "code_challenge" to "a".repeat(43),
                    "code_challenge_method" to "MD5",
                )
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isErr)
        }

    // ─── redirect_uri is optional at parse time (Task 1.6 plumbing) ──────────

    @Test
    fun parseOptionalRedirectUriAbsentIsNull() =
        runTest {
            // Per OAuth2 RFC 6749 §3.1.2.3 / OIDC §3.1.2.1, redirect_uri may be omitted when
            // the client has exactly one registered URI. The parser must accept the absent
            // form; resolution happens in the verifier.
            val result = command.execute(ParseAuthorizationRequestArgs(minimalParams()))
            assertTrue(result.isOk)
            assertNull(result.value.redirectUri)
        }

    @Test
    fun parseExplicitRedirectUriPopulated() =
        runTest {
            val params = minimalParams("redirect_uri" to "https://rp.example.com/cb")
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk)
            assertEquals("https://rp.example.com/cb", result.value.redirectUri)
        }

    @Test
    fun parseBlankRedirectUriTreatedAsAbsent() =
        runTest {
            val params = minimalParams("redirect_uri" to "   ")
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk)
            assertNull(result.value.redirectUri)
        }

    // ─── OIDC Core §3.1.2.1 parameters ────────────────────────────

    @Test
    fun parseOidcCoreParamsAllPopulated() =
        runTest {
            val params =
                minimalParams(
                    "response_mode" to "form_post",
                    "prompt" to "consent",
                    "max_age" to "300",
                    "login_hint" to "jane@example.com",
                    "id_token_hint" to "eyJhbGciOiJIUzI1NiJ9.e30.sig",
                    "acr_values" to "urn:mace:incommon:iap:silver urn:mace:incommon:iap:bronze",
                    "display" to "page",
                    "ui_locales" to "en-US fr-CA",
                    "nonce" to "n-0S6_WzA2Mj",
                )

            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk, "execute failed: ${if (!result.isOk) result.error else "n/a"}")

            val data = result.value
            assertEquals("form_post", data.responseMode)
            assertEquals(setOf(Prompt.CONSENT), data.prompt)
            assertEquals(300, data.maxAge)
            assertEquals("jane@example.com", data.loginHint)
            assertEquals("eyJhbGciOiJIUzI1NiJ9.e30.sig", data.idTokenHint)
            assertEquals(listOf("urn:mace:incommon:iap:silver", "urn:mace:incommon:iap:bronze"), data.acrValues)
            assertEquals("page", data.display)
            assertEquals(listOf("en-US", "fr-CA"), data.uiLocales)
            assertEquals("n-0S6_WzA2Mj", data.nonce)
        }

    @Test
    fun parseMaxAgeMalformedRejected() =
        runTest {
            val result = command.execute(ParseAuthorizationRequestArgs(minimalParams("max_age" to "not-a-number")))
            assertTrue(result.isErr)
        }

    @Test
    fun parseMaxAgeNegativeRejected() =
        runTest {
            val result = command.execute(ParseAuthorizationRequestArgs(minimalParams("max_age" to "-1")))
            assertTrue(result.isErr)
        }

    // ─── claims parameter (OIDC §5.5) ────────────────────────────────────────

    @Test
    fun parseClaimsValidJsonObjectPopulated() =
        runTest {
            val claimsJson = """{"userinfo":{"email":{"essential":true}},"id_token":{"auth_time":{"essential":true}}}"""
            val result = command.execute(ParseAuthorizationRequestArgs(minimalParams("claims" to claimsJson)))
            assertTrue(result.isOk)
            val parsed: JsonObject? = result.value.claims
            assertNotNull(parsed)
            val userinfoEmail = parsed["userinfo"]?.jsonObject?.get("email")?.jsonObject
            assertNotNull(userinfoEmail)
            assertEquals(true, userinfoEmail["essential"]?.jsonPrimitive?.content?.toBoolean())
        }

    @Test
    fun parseClaimsMalformedJsonRejected() =
        runTest {
            val result = command.execute(ParseAuthorizationRequestArgs(minimalParams("claims" to "{bad json")))
            assertTrue(result.isErr)
        }

    @Test
    fun parseClaimsNonObjectRejected() =
        runTest {
            val result = command.execute(ParseAuthorizationRequestArgs(minimalParams("claims" to "[\"not-an-object\"]")))
            assertTrue(result.isErr)
        }

    // ─── request / request_uri (RFC 9101, RFC 9126) ─────────────────────────

    @Test
    fun parseRequestJwtParamPreserved() =
        runTest {
            val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJjbGFpbSI6InZhbCJ9.sig"
            val result = command.execute(ParseAuthorizationRequestArgs(minimalParams("request" to jwt)))
            assertTrue(result.isOk)
            assertEquals(jwt, result.value.request)
        }

    @Test
    fun parseRequestUriOnlyAllowsClientIdSibling() =
        runTest {
            val params =
                mapOf(
                    "client_id" to "test-client",
                    "request_uri" to "urn:ietf:params:oauth:request_uri:abc123",
                )
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk)
            assertEquals("urn:ietf:params:oauth:request_uri:abc123", result.value.requestUri)
        }

    @Test
    fun parseRequestUriWithExtraParamsAcceptedAtParseStageVerifierEnforcesPrecedence() =
        runTest {
            // OIDC Core §6.2 lets top-level Authentication Request parameters accompany a PAR
            // `request_uri` — they are fallback / supplementary, with the PAR-stored values
            // taking precedence on conflict. The parser therefore admits `client_id +
            // request_uri + scope` so the PAR-redeem path can compare against the stored
            // request later and surface `invalid_request` only on actual mismatches. This test
            // pins that "parser permissive, verifier strict" boundary so a future tightening of
            // the parser is forced to also update the FAPI / OIDF conformance suites that rely
            // on the verifier being the rejection point.
            val params =
                mapOf(
                    "client_id" to "test-client",
                    "request_uri" to "urn:ietf:params:oauth:request_uri:abc123",
                    "scope" to "openid",
                )
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk, "parser must let PAR request_uri + URL params through: ${if (!result.isOk) result.error else ""}")
            assertEquals("urn:ietf:params:oauth:request_uri:abc123", result.value.requestUri)
        }

    @Test
    fun parseNonParRequestUriPassesThroughForVerifierRejection() =
        runTest {
            // Non-PAR `request_uri` values must reach the verifier so it can emit
            // `request_uri_not_supported` post-redirect (OIDC Core §3.1.2.6). Rejecting at parse
            // time would force the AS to serve the error as JSON, which OIDF Basic RP tests
            // (e.g. OIDCCEnsureRequestObjectStandardClaimSupports) flag as a failure.
            val params =
                mapOf(
                    "client_id" to "test-client",
                    "response_type" to "code",
                    "redirect_uri" to "https://rp.example.com/cb",
                    "request_uri" to "https://example.com/not-a-par-urn",
                )
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk, "parser must let non-PAR request_uri through: ${if (!result.isOk) result.error else ""}")
            assertEquals("https://example.com/not-a-par-urn", result.value.requestUri)
        }

    // ─── response_type parsing (parser accepts; verifier enforces) ───────────

    @Test
    fun parseResponseTypeCode() =
        runTest {
            val result = command.execute(ParseAuthorizationRequestArgs(minimalParams()))
            assertTrue(result.isOk)
            assertEquals(listOf(ResponseType.CODE), result.value.responseType)
        }

    @Test
    fun parseResponseTypeIdTokenAccepted() =
        runTest {
            val params =
                mapOf(
                    "response_type" to "id_token",
                    "client_id" to "test-client",
                )
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk, "parser must accept id_token even if verifier later rejects: ${if (!result.isOk) result.error else ""}")
            assertEquals(listOf(ResponseType.ID_TOKEN), result.value.responseType)
        }

    @Test
    fun parseResponseTypeHybridCodeIdToken() =
        runTest {
            val params =
                mapOf(
                    "response_type" to "code id_token",
                    "client_id" to "test-client",
                )
            val result = command.execute(ParseAuthorizationRequestArgs(params))
            assertTrue(result.isOk)
            assertEquals(listOf(ResponseType.CODE, ResponseType.ID_TOKEN), result.value.responseType)
        }

    @Test
    fun parseMissingResponseTypeAcceptedAtParseStageVerifierEmitsUnsupportedResponseType() =
        runTest {
            // RFC 6749 §4.1.1 / OIDC Core §3.1.2.1 mark `response_type` REQUIRED but the parser
            // deliberately admits a missing / empty / unparseable value (passes through as
            // `responseType = emptyList()`) so the verifier can emit `unsupported_response_type`
            // AFTER `client_id` + `redirect_uri` have been validated. That ordering lets the
            // failure ride back to the client's redirect_uri (RFC 6749 §4.1.2.1) rather than
            // stranding the user on the AS error page. Pin the permissive parse-stage behavior
            // so a future tightening of the parser is forced to first update the verifier path
            // that conformance suites depend on.
            val result = command.execute(ParseAuthorizationRequestArgs(mapOf("client_id" to "test-client")))
            assertTrue(result.isOk, "parser must let missing response_type through: ${if (!result.isOk) result.error else ""}")
            assertEquals(emptyList<ResponseType>(), result.value.responseType)
        }

    @Test
    fun parseMissingClientIdRejected() =
        runTest {
            val result = command.execute(ParseAuthorizationRequestArgs(mapOf("response_type" to "code")))
            assertTrue(result.isErr)
        }

    // ─── authorization_details pass-through ─────────────────────────────────

    @Test
    fun parseAuthorizationDetailsPreservedInAdditionalParameters() =
        runTest {
            val ad = """[{"type":"openid_credential","credential_configuration_id":"EmployeeID"}]"""
            val result = command.execute(ParseAuthorizationRequestArgs(minimalParams("authorization_details" to ad)))
            assertTrue(result.isOk)
            assertEquals(ad, result.value.additionalParameters["authorization_details"])
        }
}
