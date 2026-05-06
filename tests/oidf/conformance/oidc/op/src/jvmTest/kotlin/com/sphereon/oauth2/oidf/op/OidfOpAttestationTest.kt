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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OAuth 2.0 Attestation-Based Client Authentication
 * (draft-ietf-oauth-attestation-based-client-auth) conformance probes.
 *
 * The default OIDF harness config does not enable attestation, so the negative paths confirm:
 *  - `challenge_endpoint` is absent from discovery,
 *  - `client_attestation_*` alg lists are absent from discovery,
 *  - `GET /attestation-challenge` returns 404 when the feature is gated off.
 *
 * The positive challenge / verification paths require deployments to flip
 * `oauth2.servers.<id>.attestation = SUPPORTED` plus
 * `attestation-challenge-required = true`, plus per-client trusted-attester JWKS / issuers; that
 * configuration surface is exercised in the unit tests at
 * `lib/oauth2/server/authorization/impl/.../command/clientauth/VerifyClientAuthenticationCommandImplTest.kt`
 * (full PoP verification path) and
 * `lib/oauth2/server/authorization/impl/.../command/attestation/CreateAttestationChallengeCommandImplTest.kt`
 * (challenge minting + replay rejection).
 */
class OidfOpAttestationTest {
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
    fun discoveryOmitsAttestationFieldsWhenDisabled() =
        runTest {
            val response = client.get("${fixture.baseUrl}/.well-known/openid-configuration")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject

            // When attestation is disabled at the server level the AS must not advertise the
            // feature surface, so the discovery document stays clean.
            assertEquals(
                null,
                body["challenge_endpoint"],
                "challenge_endpoint must be absent when attestation is not enabled",
            )
            assertEquals(
                null,
                body["client_attestation_signing_alg_values_supported"],
                "client_attestation_signing_alg_values_supported must be absent when attestation is not enabled",
            )
            assertEquals(
                null,
                body["client_attestation_pop_signing_alg_values_supported"],
                "client_attestation_pop_signing_alg_values_supported must be absent when attestation is not enabled",
            )
        }

    @Test
    fun attestationChallengeEndpointReturns404WhenDisabled() =
        runTest {
            val response = client.get("${fixture.baseUrl}/attestation-challenge")
            assertEquals(
                HttpStatusCode.NotFound,
                response.status,
                "GET /attestation-challenge must 404 when attestation is not enabled",
            )
        }
}
