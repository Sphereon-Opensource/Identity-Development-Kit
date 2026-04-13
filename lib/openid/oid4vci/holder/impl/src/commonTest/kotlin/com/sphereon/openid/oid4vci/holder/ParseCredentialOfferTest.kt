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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vci.holder.impl.ParseCredentialOfferCommandImpl
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for ParseCredentialOfferCommandImpl.
 *
 * Tests the parsing logic directly without needing HTTP or DI.
 */
class ParseCredentialOfferTest {
    // Raw JSON for a simple credential offer
    private val jsonOffer =
        """
        {
          "credential_issuer": "https://issuer.example.com",
          "credential_configuration_ids": ["UniversityDegreeCredential"],
          "grants": {
            "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
              "pre-authorized_code": "oaKazRN8I0IbtZ0C7JuMn5"
            }
          }
        }
        """.trimIndent()

    private fun makeImpl(): ParseCredentialOfferCommandImpl =
        ParseCredentialOfferCommandImpl(
            execution = TestSessionExecution(),
            httpClientFactory = NoOpHttpClientFactory(),
        )

    @Test
    fun parseFromJsonString() =
        runTest {
            val impl = makeImpl()
            val result = impl.execute(ParseCredentialOfferArgs(rawOffer = jsonOffer))

            assertTrue(result.isOk, "Expected Ok but got: $result")
            val offer = result.getOrNull()!!
            assertEquals("https://issuer.example.com", offer.credentialIssuer)
            assertEquals(listOf("UniversityDegreeCredential"), offer.credentialConfigurationIds)
            assertNotNull(offer.grants?.preAuthorizedCode)
        }

    @Test
    fun parseFromOpenidCredentialOfferUri() =
        runTest {
            val impl = makeImpl()
            val encoded = encodeForUri(jsonOffer)
            val uri = "openid-credential-offer://?credential_offer=$encoded"

            val result = impl.execute(ParseCredentialOfferArgs(rawOffer = uri))

            assertTrue(result.isOk, "Expected Ok but got: $result")
            val offer = result.getOrNull()!!
            assertEquals("https://issuer.example.com", offer.credentialIssuer)
            assertEquals(listOf("UniversityDegreeCredential"), offer.credentialConfigurationIds)
        }

    @Test
    fun parseFromHttpsUri() =
        runTest {
            val impl = makeImpl()
            val encoded = encodeForUri(jsonOffer)
            val uri = "https://wallet.example.com/callback?credential_offer=$encoded"

            val result = impl.execute(ParseCredentialOfferArgs(rawOffer = uri))

            assertTrue(result.isOk, "Expected Ok but got: $result")
            val offer = result.getOrNull()!!
            assertEquals("https://issuer.example.com", offer.credentialIssuer)
        }

    @Test
    fun errorOnEmptyCredentialConfigurationIds() =
        runTest {
            val impl = makeImpl()
            val emptyIdsJson =
                """
                {
                  "credential_issuer": "https://issuer.example.com",
                  "credential_configuration_ids": []
                }
                """.trimIndent()

            val result = impl.execute(ParseCredentialOfferArgs(rawOffer = emptyIdsJson))

            assertTrue(result.isErr, "Expected Err but got Ok")
            val error = result.errorOrNull()
            assertNotNull(error)
            assertTrue(
                error.message.defaultMessage.contains("credential_configuration_ids", ignoreCase = true),
                "Error message should mention credential_configuration_ids, got: ${error.message.defaultMessage}",
            )
        }

    @Test
    fun errorOnBlankCredentialIssuer() =
        runTest {
            val impl = makeImpl()
            val blankIssuerJson =
                """
                {
                  "credential_issuer": "   ",
                  "credential_configuration_ids": ["SomeCredential"]
                }
                """.trimIndent()

            val result = impl.execute(ParseCredentialOfferArgs(rawOffer = blankIssuerJson))

            assertTrue(result.isErr, "Expected Err but got Ok")
            val error = result.errorOrNull()
            assertNotNull(error)
            assertTrue(
                error.message.defaultMessage.contains("credential_issuer", ignoreCase = true),
                "Error message should mention credential_issuer, got: ${error.message.defaultMessage}",
            )
        }

    @Test
    fun errorOnCredentialOfferUriFetchFailure() =
        runTest {
            // Per OID4VCI 1.1 Section 4, credential_offer_uri triggers a HTTP GET fetch.
            // NoOpHttpClientFactory always throws, so we expect a CREDENTIAL_OFFER_FETCH_FAILED error.
            val impl = makeImpl()
            val uri = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example.com%2Foffer%2F123"

            val result = impl.execute(ParseCredentialOfferArgs(rawOffer = uri))

            assertTrue(result.isErr, "Expected Err but got Ok")
            val error = result.errorOrNull()
            assertNotNull(error)
            assertTrue(
                error.message.defaultMessage.contains("credential_offer_uri", ignoreCase = true) ||
                    error.message.defaultMessage.contains("fetch", ignoreCase = true) ||
                    error.message.defaultMessage.contains("CREDENTIAL_OFFER_FETCH_FAILED", ignoreCase = true),
                "Error should relate to credential_offer_uri fetch failure, got: ${error.message.defaultMessage}",
            )
        }

    @Test
    fun percentDecodeHandlesPlusAsSpace() {
        val impl = makeImpl()
        assertEquals("hello world", impl.percentDecode("hello+world"))
    }

    @Test
    fun percentDecodeHandlesPercentEncoding() {
        val impl = makeImpl()
        assertEquals("{\"a\":\"b\"}", impl.percentDecode("%7B%22a%22%3A%22b%22%7D"))
    }

    /**
     * Simple percent-encoder for test URIs (encodes JSON for use as a query param value).
     */
    private fun encodeForUri(input: String): String =
        buildString {
            for (c in input) {
                when (c) {
                    in 'A'..'Z', in 'a'..'z', in '0'..'9', '-', '_', '.', '~' -> {
                        append(c)
                    }

                    else -> {
                        val bytes = c.toString().encodeToByteArray()
                        for (b in bytes) {
                            append('%')
                            append((b.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase())
                        }
                    }
                }
            }
        }
}

// ============================================================================
// Test support types — no-op implementations for unit tests
// ============================================================================

private class NoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "test-oid4vci-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("Not needed for unit tests")

    override suspend fun setConfig(config: LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for unit tests")
}

private class NoOpContextConfig : ContextConfig {
    override val app: AppConfigService get() = throw NotImplementedError("Not needed for unit tests")
    override val tenant: TenantConfigService get() = throw NotImplementedError("Not needed for unit tests")
    override val principal: PrincipalConfigService get() = throw NotImplementedError("Not needed for unit tests")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for unit tests")
}

private class TestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager
        get() = throw NotImplementedError("Not needed for unit tests")
    override val log: SessionLogService = NoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = NoOpContextConfig()
}

/**
 * No-op HttpClientFactory that always throws — used to test credential_offer_uri fetch failure path.
 */
private class NoOpHttpClientFactory : HttpClientFactory {
    override fun createClient(options: HttpClientOptions): HttpClient = throw UnsupportedOperationException("NoOpHttpClientFactory does not create real HTTP clients")

    override fun isSupportedOptions(options: HttpClientOptions): Boolean = false

    override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
}
