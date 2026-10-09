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

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.oauth2.common.config.AuthorizationServerMode
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.impl.config.ActiveSigningKeySnapshotCache
import com.sphereon.oauth2.server.authorization.impl.config.DefaultAsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemorySigningKeyStore
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.StubBuildSignedAuthorizationServerMetadataCommand
import com.sphereon.oauth2.server.authorization.impl.testutil.TenantOverrideSessionExecution
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.testutil.fixedAsInstanceIdProvider
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class CapturedServerMetadataSelectionTest {
    private val ctx = OAuth2ServerTestContext("captured-server-metadata-selection", this)
    private val tenant = "hosted-selection-tenant"
    private val execution = TenantOverrideSessionExecution(ctx.execution, tenant)

    private val ambientA = OAuth2ServerInstanceConfig(
        issuer = "https://ambient-a.example/oidc",
        tokenFormat = TokenFormat.JWT,
        oidc = FeaturePolicy.SUPPORTED,
        idTokenSigningAlgValuesSupported = setOf("ES256"),
    )
    private val selectedB = OAuth2ServerInstanceConfig(
        issuer = "https://selected-b.example/oauth",
        tokenFormat = TokenFormat.OPAQUE,
        oidc = FeaturePolicy.DISABLED,
        grantTypesEnabled = setOf("client_credentials"),
        responseTypesSupported = setOf("code"),
    )

    private fun command(
        store: InMemorySigningKeyStore,
        selected: OAuth2ServerInstanceConfig = selectedB,
    ): BuildServerMetadataCommandImpl {
        val provider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(defaultServer = "A", servers = mapOf("A" to ambientA, "B" to selected)),
        )
        return BuildServerMetadataCommandImpl(
            execution = execution,
            configProvider = provider,
            asInstanceIdProvider = fixedAsInstanceIdProvider("B"),
            signingIdentifierResolver = DefaultAsServerSigningIdentifierResolver(
                execution = execution,
                signingKeyStore = store,
                activeSigningKeySnapshotCache = ActiveSigningKeySnapshotCache(),
            ),
            grantHandlers = emptyMap(),
            kmsProviderRegistry = ctx.kmsProviderRegistry,
            buildSignedMetadata = StubBuildSignedAuthorizationServerMetadataCommand(execution),
        )
    }

    @Test
    fun selectedOpaqueHostedServerBuildsItsUnsignedMetadataWithoutAmbientSigningKey() = runTest {
        val outcome = runCatching { command(InMemorySigningKeyStore()).execute(BuildServerMetadataArgs(serverId = "B")) }

        assertTrue(outcome.isSuccess, "selected opaque B must not resolve ambient JWT/OIDC A's missing signing key")
        val result = outcome.getOrThrow()
        assertTrue(result.isOk, "selected hosted B should build discovery from its own configuration")
        assertEquals("https://selected-b.example/oauth", result.value.issuer)
        assertEquals("https://selected-b.example/oauth/token", result.value.tokenEndpoint)
        assertEquals("https://selected-b.example/oauth/authorize", result.value.authorizationEndpoint)
        assertEquals(listOf("client_credentials"), result.value.grantTypesSupported)
        assertEquals(listOf("code"), result.value.responseTypesSupported)
        assertNull(result.value.idTokenSigningAlgValuesSupported)
        assertNull(result.value.userinfoEndpoint)
        assertNull(result.value.signedMetadata)
    }

    @Test
    fun omittedServerIdUsesTrustedSelectedBInsteadOfAmbientDefaultA() = runTest {
        val result = command(InMemorySigningKeyStore()).execute(BuildServerMetadataArgs())
        assertTrue(result.isOk, "trusted routed selection B must win over config default A")
        assertEquals("https://selected-b.example/oauth", result.value.issuer)
        assertNull(result.value.idTokenSigningAlgValuesSupported)
        assertNull(result.value.signedMetadata)
    }

    @Test
    fun ambientJwtOidcServerUsesGenuinelyRegisteredActiveKey() = runTest {
        val store = InMemorySigningKeyStore()
        val kid = "ambient-a-active-key"
        val generated = ctx.keyManagerService.generateKeyResult(
            providerId = "oauth2-test-software-kms",
            alias = kid,
            use = JwkUse.sig,
            alg = SignatureAlgorithm.ECDSA_SHA256,
        )
        assertTrue(generated.isOk, "real software KMS must generate ambient A's signing key")
        val now = Clock.System.now()
        val registered = store.register(
            OAuth2SigningKey(
                tenantId = tenant,
                keyInfo = KeyInfo<KeyType>(
                    alias = kid,
                    kid = kid,
                    providerId = "oauth2-test-software-kms",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                ),
                state = OAuth2SigningKeyState.ACTIVE,
                priority = 1,
                createdAt = now,
                notBefore = now,
            ),
        )
        assertTrue(registered.isOk, "real in-memory store must contain ambient A's active descriptor")

        val result = command(store).execute(BuildServerMetadataArgs(serverId = "A"))

        assertTrue(result.isOk, "ambient A with its active key is a healthy default-resolver control")
        assertEquals("https://ambient-a.example/oidc", result.value.issuer)
        assertEquals(listOf("ES256"), result.value.idTokenSigningAlgValuesSupported)
        assertEquals("https://ambient-a.example/oidc/userinfo", result.value.userinfoEndpoint)
    }

    @Test
    fun unknownSelectedServerReturnsControlledMetadataErrorBeforeAmbientKeyResolution() = runTest {
        val outcome = runCatching { command(InMemorySigningKeyStore()).execute(BuildServerMetadataArgs(serverId = "missing")) }

        assertTrue(outcome.isSuccess, "unknown selection must not throw ambient A's missing-key exception")
        val result = outcome.getOrThrow()
        assertTrue(result.isErr)
        assertEquals("invalid_request", result.error.code)
        assertTrue(result.error.message.defaultMessage.contains("missing"))
    }

    @Test
    fun externalSelectedServerReturnsControlledMetadataErrorBeforeAmbientKeyResolution() = runTest {
        val externalB = selectedB.copy(mode = AuthorizationServerMode.EXTERNAL)
        val outcome = runCatching { command(InMemorySigningKeyStore(), externalB).execute(BuildServerMetadataArgs(serverId = "B")) }

        assertTrue(outcome.isSuccess, "EXTERNAL selection must not throw ambient A's missing-key exception")
        val result = outcome.getOrThrow()
        assertTrue(result.isErr)
        assertEquals("invalid_request", result.error.code)
        assertTrue(result.error.message.defaultMessage.contains("not hosted"))
    }
}
