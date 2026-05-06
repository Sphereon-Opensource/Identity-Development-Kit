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

package com.sphereon.oauth2.client.impl.oidc

import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.impl.client.OAuth2ClientImpl
import com.sphereon.oauth2.client.impl.client.OidcLoginTransactionStoreTestGraph
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.client.transaction.OidcLoginTransaction
import com.sphereon.oauth2.client.transaction.OidcLoginTransactionStore
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Failure-path tests for `CompleteOidcLoginCommand`.
 *
 * Happy-path coverage lives in the loopback smoke test (needs a live AS stub with signed ID
 * tokens + JWKS + token endpoint). Here we verify the control-flow branches that reject BEFORE
 * any network call or crypto work runs: unknown state, AS error response, missing state in
 * callback, and atomic single-use of the stored state.
 */
class CompleteOidcLoginCommandImplTest {
    private val app = createOAuth2ClientTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("complete-oidc-login-test")
    private val oauth2Client: OAuth2Client = (session.graph as OAuth2ClientImpl.Graph).oauth2Client
    private val transactionStore: OidcLoginTransactionStore =
        (app as OidcLoginTransactionStoreTestGraph).oidcLoginTransactionStore

    private val clientAuth =
        ClientAuthenticationConfig.Post(
            credentials = ClientCredentials(clientId = "test-client", clientSecret = "test-secret"),
        )

    private suspend fun complete(callbackUrl: String,) =
        oauth2Client.oidcLogin.complete(
            clientId = "test-client",
            clientAuthentication = clientAuth,
            callbackUrl = callbackUrl,
        )

    @Test
    fun complete_stateInvalid_rejects() =
        runTest {
            val result = complete("https://rp.example.com/callback?code=abc&state=never-seen")
            assertTrue(result.isErr)
            assertTrue(
                result.error.code == "invalid_grant",
                "expected invalid_grant, got ${result.error.code} / ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun complete_errorResponseFromAS_rejects() =
        runTest {
            val result =
                complete(
                    "https://rp.example.com/callback?error=access_denied" +
                        "&error_description=user+refused&state=ignored",
                )
            assertTrue(result.isErr)
            assertTrue(
                result.error.code == "access_denied",
                "expected AS error to propagate; got ${result.error.code}",
            )
        }

    @Test
    fun complete_missingStateInCallback_rejects() =
        runTest {
            val result = complete("https://rp.example.com/callback?code=abc")
            assertTrue(result.isErr)
            assertTrue(
                result.error.code == "invalid_grant",
                "missing state must reject as invalid_grant, got ${result.error.code}",
            )
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("state") == true,
                "rejection must cite state; got: ${result.error.message.defaultMessage}",
            )
        }

    @Test
    fun complete_stateConsumed_secondReplayRejects() =
        runTest {
            val now = Clock.System.now()
            val tx =
                OidcLoginTransaction(
                    state = "replay-state",
                    nonce = "n",
                    pkceVerifier = "v",
                    issuer = "https://issuer.example.com",
                    redirectUri = "https://rp.example.com/callback",
                    responseMode = OAuth2ResponseMode.QUERY,
                    createdAt = now,
                    expiresAt = now + 5.minutes,
                )
            assertTrue(transactionStore.put(tx).isOk)

            // First attempt reaches the metadata fetch and fails there (unreachable issuer) but
            // still consumes the stored state along the way. The SECOND call must fail with
            // invalid_grant even though its callback URL is well-formed.
            complete("https://rp.example.com/callback?code=abc&state=replay-state")

            val replay = complete("https://rp.example.com/callback?code=abc&state=replay-state")
            assertTrue(replay.isErr)
            assertTrue(
                replay.error.code == "invalid_grant",
                "replay must reject as invalid_grant, got ${replay.error.code}",
            )
        }
}
