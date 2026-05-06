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

import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * RFC 8628 (OAuth 2.0 Device Authorization Grant) "Server" role conformance.
 *
 * Coverage:
 *  1. Discovery advertises `device_authorization_endpoint` + the device-code grant URN once the
 *     `oauth2.servers.default.device-flow` policy flips to SUPPORTED.
 *  2. POST `/device_authorization` issues a device + user code pair shaped per §3.2.
 *  3. Token-endpoint polling before the user approves yields `authorization_pending` (§3.5).
 *  4. Polling again within the per-record `interval` yields `slow_down` (§3.5).
 *  5. After approval, the token endpoint mints an access_token + id_token bound to the approved
 *     subject (§3.4). Test 5 takes the direct-storage shortcut described in the task brief: tests
 *     1/2/3/4 already exercise the HTTP wire, and test 5's value is the END of the flow (token
 *     issuance) rather than the HTML form plumbing.
 *  6. After the device-code lifetime elapses, the token endpoint yields `expired_token` (§3.5).
 *  7. After denial, the token endpoint yields `access_denied` (§3.5).
 */
class OidfOpDeviceFlowTest {
    private lateinit var fixture: OidfOpServerFixture
    private lateinit var client: HttpClient
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var overrides: HarnessPropertyOverride

    @BeforeTest
    fun setUp() {
        // Flip the `oidf-op-basic` client onto the device-code grant + flip the AS-wide
        // `device-flow` policy to SUPPORTED. Both must be in place before the fixture starts so
        // the lazy-loaded ConfigAwareClientRegistry observes the extra grant on first session.
        overrides =
            HarnessPropertyOverride(
                "oauth2.servers.default.device-flow" to "SUPPORTED",
                "oauth2.clients.oidf-op-basic.grant-types" to
                    (
                        "authorization_code,refresh_token,urn:ietf:params:oauth:grant-type:token-exchange," +
                            "urn:ietf:params:oauth:grant-type:device_code"
                    ),
            )
        fixture = OidfOpServerFixture()
        client = HttpClient(CIO) { followRedirects = false }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        fixture.stop()
        fixture.testClock.clearOverride()
        overrides.close()
    }

    @Test
    fun discoveryAdvertisesDeviceAuthorizationEndpoint() =
        runTest {
            val response = client.get("${fixture.baseUrl}/.well-known/openid-configuration")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val deviceEndpoint = body["device_authorization_endpoint"]?.jsonPrimitive?.content
            assertNotNull(deviceEndpoint, "discovery must advertise device_authorization_endpoint when device-flow is SUPPORTED")
            assertEquals(
                "${fixture.baseUrl}/device_authorization",
                deviceEndpoint,
                "device_authorization_endpoint must equal {baseUrl}/device_authorization",
            )
            val grantTypes =
                body["grant_types_supported"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?: emptyList()
            assertTrue(
                DEVICE_CODE_GRANT in grantTypes,
                "grant_types_supported must include $DEVICE_CODE_GRANT once device-flow is SUPPORTED; got $grantTypes",
            )
        }

    @Test
    fun deviceAuthorizationRequestIssuesCodes() =
        runTest {
            val response = issueDeviceCodes()
            assertEquals(HttpStatusCode.OK, response.status, "POST /device_authorization must return 200")
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject

            val deviceCode = body["device_code"]?.jsonPrimitive?.content
            val userCode = body["user_code"]?.jsonPrimitive?.content
            val verificationUri = body["verification_uri"]?.jsonPrimitive?.content
            val verificationUriComplete = body["verification_uri_complete"]?.jsonPrimitive?.content
            val expiresIn = body["expires_in"]?.jsonPrimitive?.content?.toIntOrNull()
            val interval = body["interval"]?.jsonPrimitive?.content?.toIntOrNull()

            assertNotNull(deviceCode, "response must include device_code")
            assertTrue(deviceCode.isNotBlank(), "device_code must not be blank")
            assertNotNull(userCode, "response must include user_code")
            assertTrue(
                USER_CODE_PATTERN.matches(userCode),
                "user_code must match RFC 8628 §6.1 unambiguous alphabet pattern; got $userCode",
            )
            assertEquals(
                "${fixture.baseUrl}/device",
                verificationUri,
                "verification_uri must equal {baseUrl}/device",
            )
            assertNotNull(verificationUriComplete, "response must include verification_uri_complete")
            assertTrue(
                verificationUriComplete.startsWith("${fixture.baseUrl}/device?user_code="),
                "verification_uri_complete must prefix /device?user_code=; got $verificationUriComplete",
            )
            assertNotNull(expiresIn, "response must include expires_in")
            assertTrue(expiresIn > 0, "expires_in must be positive")
            assertNotNull(interval, "response must include interval")
            assertTrue(interval > 0, "interval must be positive")
        }

    @Test
    fun tokenPollPriorToApprovalReturnsAuthorizationPending() =
        runTest {
            val codes = issueDeviceCodesParsed()
            val tokenResponse = pollToken(codes.deviceCode)
            assertEquals(
                HttpStatusCode.BadRequest,
                tokenResponse.status,
                "polling a PENDING device_code must return 400",
            )
            val body = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
            assertEquals(
                "authorization_pending",
                body["error"]?.jsonPrimitive?.content,
                "first poll on a PENDING record must yield authorization_pending",
            )
        }

    @Test
    fun tokenPollTooFastReturnsSlowDown() =
        runTest {
            val codes = issueDeviceCodesParsed()
            // First poll: stamps lastPolledAt, returns authorization_pending.
            val first = pollToken(codes.deviceCode)
            assertEquals(HttpStatusCode.BadRequest, first.status)
            val firstBody = json.parseToJsonElement(first.bodyAsText()).jsonObject
            assertEquals("authorization_pending", firstBody["error"]?.jsonPrimitive?.content)

            // Immediate re-poll: TestClock has not advanced past the per-record interval, so the
            // verifier yields slow_down.
            val second = pollToken(codes.deviceCode)
            assertEquals(HttpStatusCode.BadRequest, second.status)
            val secondBody = json.parseToJsonElement(second.bodyAsText()).jsonObject
            assertEquals(
                "slow_down",
                secondBody["error"]?.jsonPrimitive?.content,
                "polling within the per-record interval must yield slow_down",
            )
        }

    @Test
    fun userApprovalCompletesFlow() =
        runTest {
            val codes = issueDeviceCodesParsed()
            // Take the direct-storage shortcut described in the task brief: pre-flip the record
            // to APPROVED with a real subject + auth_time so the verifier mints a token. The
            // HTML approval form is exercised by the renderer's unit tests + the entry-form
            // wire test; this test's value is the /token wire shape.
            val now = fixture.testClock.now()
            val record =
                runBlocking {
                    fixture.deviceAuthorizationStorage
                        .findByDeviceCode(codes.deviceCode)
                        .let { lookup ->
                            check(lookup.isOk) { "storage lookup failed" }
                            lookup.value ?: error("device record disappeared")
                        }
                }
            val updated =
                runBlocking {
                    fixture.deviceAuthorizationStorage.update(
                        record.copy(
                            state = DeviceAuthorizationState.APPROVED,
                            approvedSub = "urn:sphereon:oidf:op:alice",
                            approvedAuthTime = now,
                            approvedSessionId = "device-flow-test-session",
                            grantedScope = record.scope,
                        ),
                    )
                }
            assertTrue(updated.isOk, "storage update must succeed")

            val tokenResponse = pollToken(codes.deviceCode)
            assertTrue(
                tokenResponse.status.isSuccess(),
                "POST /token after APPROVED record must succeed; got ${tokenResponse.status}: ${tokenResponse.bodyAsText()}",
            )
            val body = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
            val accessToken = body["access_token"]?.jsonPrimitive?.content
            val idToken = body["id_token"]?.jsonPrimitive?.content
            assertNotNull(accessToken, "token response must include access_token")
            assertNotNull(idToken, "token response must include id_token")
            assertEquals("Bearer", body["token_type"]?.jsonPrimitive?.content)

            val payload = decodeJwsPayload(idToken)
            assertEquals(
                "urn:sphereon:oidf:op:alice",
                payload["sub"]?.jsonPrimitive?.content,
                "id_token sub must equal the approved record's approvedSub",
            )
        }

    @Test
    fun expiredDeviceCodeReturnsExpiredToken() =
        runTest {
            val codes = issueDeviceCodesParsed()
            // The default device_code lifetime is 1800s. Advance the test clock past that ceiling
            // so the verify command observes `expiresAt <= now` and transitions the record to
            // EXPIRED.
            fixture.testClock.advance((codes.expiresIn + 60).seconds)

            val tokenResponse = pollToken(codes.deviceCode)
            assertEquals(
                HttpStatusCode.BadRequest,
                tokenResponse.status,
                "polling an expired device_code must return 400",
            )
            val body = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
            assertEquals(
                "expired_token",
                body["error"]?.jsonPrimitive?.content,
                "polling past the device_code lifetime must yield expired_token",
            )
        }

    @Test
    fun userDenialReturnsAccessDenied() =
        runTest {
            val codes = issueDeviceCodesParsed()
            val record =
                runBlocking {
                    fixture.deviceAuthorizationStorage
                        .findByDeviceCode(codes.deviceCode)
                        .let { lookup ->
                            check(lookup.isOk) { "storage lookup failed" }
                            lookup.value ?: error("device record disappeared")
                        }
                }
            val updated =
                runBlocking {
                    fixture.deviceAuthorizationStorage.update(
                        record.copy(state = DeviceAuthorizationState.DENIED),
                    )
                }
            assertTrue(updated.isOk, "storage update to DENIED must succeed")

            val tokenResponse = pollToken(codes.deviceCode)
            assertEquals(
                HttpStatusCode.BadRequest,
                tokenResponse.status,
                "polling a DENIED device_code must return 400",
            )
            val body = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
            assertEquals(
                "access_denied",
                body["error"]?.jsonPrimitive?.content,
                "polling a DENIED record must yield access_denied",
            )
        }

    private suspend fun issueDeviceCodes(): io.ktor.client.statement.HttpResponse =
        client.submitForm(
            url = "${fixture.baseUrl}/device_authorization",
            formParameters =
                Parameters.build {
                    append("client_id", CLIENT_ID)
                    append("scope", "openid")
                },
        )

    private suspend fun issueDeviceCodesParsed(): IssuedCodes {
        val response = issueDeviceCodes()
        assertEquals(
            HttpStatusCode.OK,
            response.status,
            "POST /device_authorization must succeed; body='${response.bodyAsText()}'",
        )
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return IssuedCodes(
            deviceCode = body["device_code"]?.jsonPrimitive?.content ?: error("no device_code"),
            userCode = body["user_code"]?.jsonPrimitive?.content ?: error("no user_code"),
            expiresIn = body["expires_in"]?.jsonPrimitive?.content?.toIntOrNull() ?: error("no expires_in"),
            interval = body["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: error("no interval"),
        )
    }

    private suspend fun pollToken(deviceCode: String): io.ktor.client.statement.HttpResponse =
        client.submitForm(
            url = "${fixture.baseUrl}/token",
            formParameters =
                Parameters.build {
                    append("grant_type", DEVICE_CODE_GRANT)
                    append("device_code", deviceCode)
                    append("client_id", CLIENT_ID)
                },
        ) {
            header("Authorization", "Basic ${basicAuth()}")
        }

    private fun basicAuth(): String =
        Base64
            .getEncoder()
            .encodeToString("$CLIENT_ID:oidf-op-basic-secret-2026".encodeToByteArray())

    private fun decodeJwsPayload(jws: String): JsonObject {
        val payloadSegment = jws.split(".").getOrNull(1) ?: error("JWS must have three segments")
        val padded =
            when (payloadSegment.length % 4) {
                0 -> payloadSegment
                2 -> "$payloadSegment=="
                3 -> "$payloadSegment="
                else -> error("Invalid base64url length: ${payloadSegment.length}")
            }
        val decoded = Base64.getUrlDecoder().decode(padded).decodeToString()
        return json.parseToJsonElement(decoded).jsonObject
    }

    private data class IssuedCodes(
        val deviceCode: String,
        val userCode: String,
        val expiresIn: Int,
        val interval: Int,
    )

    companion object {
        private const val CLIENT_ID: String = "oidf-op-basic"
        private const val DEVICE_CODE_GRANT: String = "urn:ietf:params:oauth:grant-type:device_code"
        private val USER_CODE_PATTERN: Regex = Regex("^[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$")
    }
}
