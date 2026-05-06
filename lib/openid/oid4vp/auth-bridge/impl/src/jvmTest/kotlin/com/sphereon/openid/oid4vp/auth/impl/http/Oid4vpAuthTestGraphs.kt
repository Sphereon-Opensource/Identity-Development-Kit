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

package com.sphereon.openid.oid4vp.auth.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.credential.claims.mapper.api.store.ClaimMappingConfigurationStore
import com.sphereon.credential.claims.mapper.api.store.QueryConfigurationStore
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.crypto.HashedIdentifier
import com.sphereon.identity.matching.crypto.ReconciliationCryptoService
import com.sphereon.identity.matching.model.IdentityLinkBinding
import com.sphereon.identity.matching.store.IdentityLinkBindingStore
import com.sphereon.identity.reconciliation.api.OidcConnectionResolver
import com.sphereon.identity.reconciliation.api.ResolvedOidcConnection
import com.sphereon.oauth2.jwt.validation.JwtValidationError
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryMetadata
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryService
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.openid.oid4vp.auth.orchestration.ReconciliationCallbackResult
import com.sphereon.openid.oid4vp.auth.orchestration.ReconciliationInitiateResult
import com.sphereon.openid.oid4vp.auth.orchestration.ReconciliationOrchestratorApi
import com.sphereon.openid.oid4vp.auth.orchestration.ReconciliationStatusResult
import com.sphereon.openid.oid4vp.auth.orchestration.ResolvedKnownHolder
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Test app graph for OID4VP Auth Bridge HTTP endpoint testing.
 *
 * This graph merges all DI contributions from:
 * - lib-core-api-default (HTTP infrastructure, session management)
 * - lib-openid-oid4vp-auth-bridge-impl (commands, adapter, stores via @ContributesBinding)
 *
 * The commands and adapter are available via the merged session graph.
 */
@DependencyGraph(AppScope::class)
abstract class Oid4vpAuthTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @dev.zacsweers.metro.Provides application: Any,
            @dev.zacsweers.metro.Provides @dev.zacsweers.metro.Named("appId") appId: String,
            @dev.zacsweers.metro.Provides @dev.zacsweers.metro.Named("profile") profile: String,
            @dev.zacsweers.metro.Provides @dev.zacsweers.metro.Named("version") version: String,
            @dev.zacsweers.metro.Provides rootScopeProvider: com.sphereon.di.app.RootScopeProvider,
        ): Oid4vpAuthTestAppGraph
    }
}

fun createOid4vpAuthTestAppGraph(
    application: Any,
    appId: String = "oid4vp-auth-test",
    profile: String = "test",
    version: String = "1.0.0-test",
): Oid4vpAuthTestAppGraph {
    val graph =
        dev.zacsweers.metro.createGraphFactory<Oid4vpAuthTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}

// ============================================================
// Test-specific UserAuthenticationProvider binding (SessionScope)
// ============================================================

/**
 * Provides a no-op [UserAuthenticationProvider] for tests.
 *
 * Required because [Oid4vpAuthBridge.Graph] exposes
 * `oid4vpUserAuthenticationProvider: UserAuthenticationProvider` in SessionScope,
 * but the auto-binding was removed to allow explicit wiring at the service level.
 */
@ContributesTo(SessionScope::class)
interface Oid4vpAuthTestUserAuthProviderModule {
    @Provides
    fun provideUserAuthenticationProvider(): UserAuthenticationProvider = NoOpTestUserAuthenticationProvider()
}

private class NoOpTestUserAuthenticationProvider : UserAuthenticationProvider {
    override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
    ): IdkResult<String, AuthenticationError> =
        com.sphereon.core.api
            .Err(AuthenticationError.Generic(description = "Not implemented in test"))

    override suspend fun authenticateWithCredentials(credentials: UserCredentials): IdkResult<String?, AuthenticationError> =
        com.sphereon.core.api
            .Err(AuthenticationError.Generic(description = "Not implemented in test"))

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = Ok(Unit)

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> = Ok(UserInfo(userId = userId))

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = Ok(false)
}

// ============================================================
// Test-specific store bindings module
// ============================================================

/**
 * Provides test-specific store bindings for claims mapper.
 *
 * QueryConfigurationStore and DcqlClaimsMappingAdapter are auto-provided via
 * @ContributesBinding on InMemoryClaimMappingConfigurationStore and DcqlClaimsMappingAdapterImpl.
 * Only ClaimMappingConfigurationStore needs an explicit binding here (upcast from QueryConfigurationStore).
 */
@ContributesTo(SessionScope::class)
interface Oid4vpAuthTestStoreModule {
    /**
     * Binds ClaimMappingConfigurationStore to the QueryConfigurationStore implementation.
     */
    @Provides
    fun provideClaimMappingConfigurationStore(store: QueryConfigurationStore): ClaimMappingConfigurationStore = store

    /**
     * No-op [WalletAttributeProjector] for tests — returns raw credential attributes as-is.
     */
    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletAttributeProjector(): com.sphereon.openid.oid4vp.auth.claims.WalletAttributeProjector =
        object : com.sphereon.openid.oid4vp.auth.claims.WalletAttributeProjector {
            override suspend fun projectWalletAttributes(credentials: List<com.sphereon.openid.oid4vp.universal.VerifiedCredential>): Map<String, kotlinx.serialization.json.JsonElement> =
                credentials.flatMap { it.claims.entries }.associate { it.key to it.value }
        }
}

// ============================================================
// Test-specific verifier bindings
// ============================================================

/**
 * Test-only binding for request_uri JAR signing configuration.
 *
 * Required because the OID4VP Auth Bridge depends on the verifier module,
 * which contributes HTTP adapters that require RequestObjectSigningConfig.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestObjectSigningConfig>(), replaces = [com.sphereon.openid.oid4vp.verifier.impl.ConfigDrivenRequestObjectSigningConfig::class])
class TestRequestObjectSigningConfig
    @Inject
    constructor() : RequestObjectSigningConfig {
        override val enabled: Boolean = false

        override suspend fun resolveSigningKey() = KeyInfo<Nothing>(kid = "test-request-uri-signing-key")

        override val audience: String = "https://wallet.example.com"
        override val expirationSeconds: Long = 60
    }

/**
 * Provides Clock binding for the verifier module dependencies.
 */
@ContributesTo(SessionScope::class)
interface Oid4vpAuthTestClockModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideClock(): Clock = Clock.System
}

// ============================================================
// Test-specific OIDC discovery binding (for reconciliation)
// ============================================================

/**
 * Stub [OidcDiscoveryService] for tests.
 *
 * Required because lib-identity-reconciliation-impl's [CreateReconciliationSessionCommandImpl]
 * depends on [OidcDiscoveryService] for OIDC provider discovery during reconciliation session creation.
 * Returns metadata with conventional endpoints derived from the issuer URL.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OidcDiscoveryService>())
class TestOidcDiscoveryService : OidcDiscoveryService {
    override suspend fun discover(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError> = getMetadata(issuer)

    override suspend fun getMetadata(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError> {
        val normalizedIssuer = issuer.trimEnd('/')
        return Ok(
            OidcDiscoveryMetadata(
                issuer = normalizedIssuer,
                jwksUri = "$normalizedIssuer/.well-known/jwks.json",
                authorizationEndpoint = "$normalizedIssuer/authorize",
                tokenEndpoint = "$normalizedIssuer/token",
                userinfoEndpoint = "$normalizedIssuer/userinfo",
                responseTypesSupported = listOf("code"),
                subjectTypesSupported = listOf("public"),
                idTokenSigningAlgValuesSupported = listOf("RS256"),
                scopesSupported = listOf("openid", "profile", "email"),
            ),
        )
    }

    override suspend fun invalidateCache(issuer: String) {
        // no-op
    }
}

// ============================================================
// Test-specific ReconciliationCryptoService binding (SessionScope)
// ============================================================

/**
 * No-op [ReconciliationCryptoService] for tests.
 *
 * Returns deterministic hashes and passes plaintext through as "encrypted" payloads
 * so tests can verify the wiring without requiring a real KMS.
 */
@ContributesTo(SessionScope::class, replaces = [com.sphereon.identity.matching.impl.crypto.ReconciliationCryptoModule::class])
interface Oid4vpAuthTestReconciliationCryptoModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideReconciliationCryptoService(): ReconciliationCryptoService =
        object : ReconciliationCryptoService {
            override suspend fun hashHolderKey(holderKey: String) = HashedIdentifier(hash = "holder:$holderKey", keyVersion = "v1")

            override suspend fun hashExternalIdentifier(identifier: String) = HashedIdentifier(hash = "ext:$identifier", keyVersion = "v1")

            override suspend fun encrypt(plaintext: String) = EncryptedPayload(ciphertext = plaintext, keyVersion = "v1")

            override suspend fun decrypt(payload: EncryptedPayload) = payload.ciphertext

            override suspend fun hashHolderKeyWithPrevious(holderKey: String): HashedIdentifier? = null

            override suspend fun hashExternalIdentifierWithPrevious(identifier: String): HashedIdentifier? = null
        }
}

// ============================================================
// Test-specific IdentityLinkBindingStore binding (AppScope)
// ============================================================

/**
 * In-memory [IdentityLinkBindingStore] for tests.
 */
@ContributesTo(AppScope::class, replaces = [com.sphereon.identity.matching.impl.store.IdentityLinkBindingStoreModule::class])
interface Oid4vpAuthTestIdentityLinkBindingStoreModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideIdentityLinkBindingStore(): IdentityLinkBindingStore = InMemoryTestIdentityLinkBindingStore()
}

private class InMemoryTestIdentityLinkBindingStore : IdentityLinkBindingStore {
    private val bindings = mutableMapOf<String, IdentityLinkBinding>()

    override suspend fun create(binding: IdentityLinkBinding): IdentityLinkBinding {
        bindings[binding.id] = binding
        return binding
    }

    override suspend fun findByMatchId(
        tenantId: String,
        matchId: String,
    ): IdentityLinkBinding? = bindings.values.find { it.tenantId == tenantId && it.matchId == matchId }

    override suspend fun findByHolderHash(
        tenantId: String,
        holderHash: String,
    ): IdentityLinkBinding? = bindings.values.find { it.tenantId == tenantId && it.holderIdentifierHash == holderHash }

    override suspend fun update(binding: IdentityLinkBinding): IdentityLinkBinding {
        bindings[binding.id] = binding
        return binding
    }

    override suspend fun delete(
        tenantId: String,
        bindingId: String,
    ): Boolean = bindings.remove(bindingId) != null

    override suspend fun findExpired(
        tenantId: String,
        inactiveSince: Instant,
    ): List<IdentityLinkBinding> =
        bindings.values.filter {
            it.tenantId == tenantId && (it.lastUsedAt ?: it.createdAt) < inactiveSince
        }
}

// ============================================================
// Test-specific ReconciliationOrchestratorApi binding (SessionScope)
// ============================================================

/**
 * No-op [ReconciliationOrchestratorApi] for tests.
 *
 * Required because auth-bridge impl classes depend on this interface,
 * but the real implementation lives in the portal service layer (EDK/VDX).
 */
@ContributesTo(SessionScope::class)
interface Oid4vpAuthTestReconciliationOrchestratorModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideReconciliationOrchestratorApi(): ReconciliationOrchestratorApi =
        object : ReconciliationOrchestratorApi {
            override suspend fun resolveKnownHolder(
                holderKeyHash: String,
                tenantId: String,
                rawHolderKey: String?,
                walletAttributes: Map<String, JsonElement>?,
            ): IdkResult<ResolvedKnownHolder?, IdkError> = Ok(null)

            override suspend fun initiateReconciliation(
                oid4vpSessionId: String,
                redirectUri: String,
                baseUrl: String?,
            ): IdkResult<ReconciliationInitiateResult, IdkError> = Err(IdkError.fromString("Not implemented in test"))

            override suspend fun handleCallback(
                code: String,
                state: String,
                walletAttributes: Map<String, JsonElement>,
            ): IdkResult<ReconciliationCallbackResult, IdkError> = Err(IdkError.fromString("Not implemented in test"))

            override suspend fun handleCallbackWithClaims(
                oid4vpSessionId: String,
                claims: Map<String, JsonElement>,
                issuer: String,
                providerId: String,
            ): IdkResult<ReconciliationCallbackResult, IdkError> = Err(IdkError.fromString("Not implemented in test"))

            override suspend fun getStatus(oid4vpSessionId: String): IdkResult<ReconciliationStatusResult, IdkError> = Err(IdkError.fromString("Not implemented in test"))

            override suspend fun preEvaluateReconciliation(
                oid4vpSessionId: String,
                tenantId: String,
            ): IdkResult<ResolvedKnownHolder?, IdkError> = Ok(null)
        }
}

// ============================================================
// Test-specific OidcConnectionResolver binding (SessionScope)
// ============================================================

@ContributesTo(SessionScope::class)
interface Oid4vpAuthTestOidcConnectionResolverModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideOidcConnectionResolver(): OidcConnectionResolver =
        object : OidcConnectionResolver {
            override suspend fun resolve(oidcClientId: String): ResolvedOidcConnection =
                ResolvedOidcConnection(
                    discoveryUrl = "https://idp.example.com/.well-known/openid-configuration",
                    clientId = "test-client-id",
                    clientSecret = "test-client-secret",
                    scopes = listOf("openid", "profile"),
                )
        }
}
