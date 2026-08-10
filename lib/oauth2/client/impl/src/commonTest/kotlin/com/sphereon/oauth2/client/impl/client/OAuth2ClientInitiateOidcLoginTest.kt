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

package com.sphereon.oauth2.client.impl.client

import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.client.transaction.OidcLoginTransactionStore
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Access the app-scoped transaction store from the test graph so we can verify persistence
// without reaching into the impl's constructor. The binding is discovered via
// @ContributesBinding(AppScope) on InMemoryOidcLoginTransactionStore.
@ContributesTo(scope = AppScope::class)
interface OidcLoginTransactionStoreTestGraph {
    val oidcLoginTransactionStore: OidcLoginTransactionStore
}

class OAuth2ClientInitiateOidcLoginTest {
    private val app = createOAuth2ClientTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("oidc-login-initiate-test", principalType = com.sphereon.di.context.PrincipalType.USER)
    private val oauth2Client: OAuth2Client = (session.graph as OAuth2ClientImpl.Graph).oauth2Client
    private val transactionStore: OidcLoginTransactionStore =
        (app as OidcLoginTransactionStoreTestGraph).oidcLoginTransactionStore

    private val metadata =
        AuthorizationServerMetadata(
            issuer = "https://as.example.com",
            authorizationEndpoint = "https://as.example.com/authorize",
            tokenEndpoint = "https://as.example.com/token",
            jwksUri = "https://as.example.com/.well-known/jwks.json",
            codeChallengeMethodsSupported = listOf("S256"),
            grantTypesSupported = listOf("authorization_code"),
            responseTypesSupported = listOf("code"),
        )

    @Test
    fun initiateOidcLogin_generatesStateAndNonce() =
        runTest {
            val result =
                oauth2Client.initiateOidcLogin(
                    authorizationServerMetadata = metadata,
                    clientId = "test-client",
                    redirectUri = "https://rp.example.com/callback",
                )
            assertTrue(result.isOk, "initiate should succeed: ${if (result.isErr) result.error else ""}")
            val initiation = result.value

            assertTrue(initiation.state.isNotBlank(), "state must be populated")
            val url = initiation.authorizationUrl
            assertTrue(url.contains("state=${initiation.state}"), "authorization URL must include state. URL: $url")
            assertTrue(Regex("[?&]nonce=[^&]+").containsMatchIn(url), "URL must include nonce. URL: $url")
            assertTrue(Regex("[?&]state=[^&]+").containsMatchIn(url), "URL must include state query param")
        }

    @Test
    fun initiateOidcLogin_persistsTransaction() =
        runTest {
            val result =
                oauth2Client.initiateOidcLogin(
                    authorizationServerMetadata = metadata,
                    clientId = "test-client",
                    redirectUri = "https://rp.example.com/callback",
                    tenantId = "tenant-a",
                )
            assertTrue(result.isOk)
            val state = result.value.state

            val consumed = transactionStore.consumeByState(state, tenantId = "tenant-a")
            assertTrue(consumed.isOk, "transaction must have been persisted under the tenant")
            val tx = consumed.value
            assertEquals(state, tx.state)
            assertTrue(tx.nonce.isNotBlank())
            assertTrue(tx.pkceVerifier.isNotBlank())
            assertEquals("https://rp.example.com/callback", tx.redirectUri)
            assertEquals(metadata.issuer, tx.issuer)
            assertEquals(OAuth2ResponseMode.QUERY, tx.responseMode)
            assertEquals("tenant-a", tx.tenantId)
        }

    @Test
    fun initiateOidcLogin_usesS256Pkce() =
        runTest {
            val result =
                oauth2Client.initiateOidcLogin(
                    authorizationServerMetadata = metadata,
                    clientId = "test-client",
                    redirectUri = "https://rp.example.com/callback",
                )
            assertTrue(result.isOk)
            val url = result.value.authorizationUrl

            assertTrue(url.contains("code_challenge_method=S256"), "method must be S256. URL: $url")
            assertTrue(Regex("[?&]code_challenge=[^&]+").containsMatchIn(url), "challenge must be in URL")
        }

    @Test
    fun initiateOidcLogin_distinctStatesAcrossCalls() =
        runTest {
            val first =
                oauth2Client.initiateOidcLogin(
                    authorizationServerMetadata = metadata,
                    clientId = "test-client",
                    redirectUri = "https://rp.example.com/callback",
                )
            val second =
                oauth2Client.initiateOidcLogin(
                    authorizationServerMetadata = metadata,
                    clientId = "test-client",
                    redirectUri = "https://rp.example.com/callback",
                )
            assertTrue(first.isOk && second.isOk)
            assertNotNull(first.value.state)
            assertNotNull(second.value.state)
            assertTrue(first.value.state != second.value.state, "state must be unique per initiate call")
        }

    @Test
    fun initiateOidcLogin_includesPromptAndLoginHint() =
        runTest {
            val result =
                oauth2Client.initiateOidcLogin(
                    authorizationServerMetadata = metadata,
                    clientId = "test-client",
                    redirectUri = "https://rp.example.com/callback",
                    prompt = "login",
                    loginHint = "user@example.com",
                )
            assertTrue(result.isOk)
            val url = result.value.authorizationUrl
            assertTrue(url.contains("prompt=login"), "URL must carry prompt. URL: $url")
            assertTrue(
                url.contains("login_hint=user%40example.com") || url.contains("login_hint=user@example.com"),
                "URL must carry login_hint (possibly URL-encoded). URL: $url",
            )
        }

    @Test
    fun initiateOidcLogin_defaultScopeIsOpenid() =
        runTest {
            val result =
                oauth2Client.initiateOidcLogin(
                    authorizationServerMetadata = metadata,
                    clientId = "test-client",
                    redirectUri = "https://rp.example.com/callback",
                )
            assertTrue(result.isOk)
            val url = result.value.authorizationUrl
            assertTrue(url.contains("scope=openid"), "scope must default to openid. URL: $url")
        }

    @Test
    fun initiateOidcLogin_responseModeFormPost_isPropagated() =
        runTest {
            val result =
                oauth2Client.initiateOidcLogin(
                    authorizationServerMetadata = metadata,
                    clientId = "test-client",
                    redirectUri = "https://rp.example.com/callback",
                    responseMode = OAuth2ResponseMode.FORM_POST,
                )
            assertTrue(result.isOk)
            val url = result.value.authorizationUrl
            assertTrue(
                url.contains("response_mode=form_post"),
                "URL must include response_mode=form_post. URL: $url",
            )

            val consumed = transactionStore.consumeByState(result.value.state)
            assertTrue(consumed.isOk)
            assertEquals(OAuth2ResponseMode.FORM_POST, consumed.value.responseMode)
        }

    @Test
    fun initiateOidcLogin_withoutResource_omitsResourceAndCustomAudienceButPersistsExpectedAudience() =
        runTest {
            val result = oauth2Client.initiateOidcLogin(
                authorizationServerMetadata = metadata,
                clientId = "selected-public-client",
                redirectUri = "https://rp.example.com/callback",
                tenantId = "tenant-a",
                resource = null,
                audience = "selected-client-default-audience",
                ownerHandleDigest = "owner-hmac",
                grantBinding = "issuer:issuer-one",
                clientCorrelation = "raw-opaque-owner-handle",
            )

            assertTrue(result.isOk)
            assertFalse(Regex("[?&]resource=").containsMatchIn(result.value.authorizationUrl))
            assertFalse(Regex("[?&]audience=").containsMatchIn(result.value.authorizationUrl))
            val transaction = transactionStore.consumeByState(result.value.state, "tenant-a")
            assertTrue(transaction.isOk)
            assertEquals(null, transaction.value.resource)
            assertEquals("selected-client-default-audience", transaction.value.audience)
            assertEquals("owner-hmac", transaction.value.ownerHandleDigest)
            assertEquals("issuer:issuer-one", transaction.value.grantBinding)
            assertEquals("raw-opaque-owner-handle", transaction.value.clientCorrelation)
        }

    @Test
    fun initiateOidcLogin_withResource_propagatesExactResourceSemantics() =
        runTest {
            val result = oauth2Client.initiateOidcLogin(
                authorizationServerMetadata = metadata,
                clientId = "selected-public-client",
                redirectUri = "https://rp.example.com/callback",
                tenantId = "tenant-a",
                resource = "https://issuer-api.example.com",
                audience = "selected-client-default-audience",
            )

            assertTrue(result.isOk)
            assertTrue(Regex("[?&]resource=https(%3A|:)").containsMatchIn(result.value.authorizationUrl))
            assertTrue(Regex("[?&]audience=selected-client-default-audience").containsMatchIn(result.value.authorizationUrl))
            val transaction = transactionStore.consumeByState(result.value.state, "tenant-a")
            assertEquals("https://issuer-api.example.com", transaction.value.resource)
            assertEquals("selected-client-default-audience", transaction.value.audience)
        }
}
