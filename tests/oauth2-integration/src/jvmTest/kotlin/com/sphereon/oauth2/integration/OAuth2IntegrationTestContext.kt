/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.integration

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.time.Clock

/**
 * Boots a single app graph carrying the OP (lib-oauth2-server-authorization-impl +
 * services-oauth2-as-rest), the RS (lib-oauth2-server-resource-impl), and the RP
 * (lib-oauth2-client-impl). Registers the software KMS provider and pre-generates the signing
 * key under the alias the `DefaultOAuth2ConfigModule` advertises (`oauth2-server-signing`), so
 * the OP's sign-token commands resolve a real key without additional config wiring.
 *
 * Lives at `@AppScope`. A single [SessionInstance] is created for the tests — sufficient since
 * the integration scenarios don't exercise per-user partitioning.
 */
class OAuth2IntegrationTestContext(
    testInstance: Any,
    sessionId: String = "oauth2-integration-test",
) {
    val app: AppGraph = createOAuth2IntegrationTestAppGraph(application = testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)
    val execution = session.asCoreApiServiceGraph().serviceExecution
    val keyManagerService: KeyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
    val signingKeyStore: SigningKeyStore = (app as OAuth2SigningKeyStoreGraph).signingKeyStore

    init {
        val providerConfig = SoftwareKmsProviderConfig(id = "oauth2-integration-kms")
        val providerFactory = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
        val provider = providerFactory.create(providerConfig, execution)
        keyManagerService.registerProvider(provider, makeDefaultKms = true)
    }

    /**
     * Generate (or reuse) the OP signing key under the alias the default config module points
     * at, then register the resulting [KeyInfo] with the [SigningKeyStore] so the AS sign
     * paths (`CreateAccessTokenCommand` / `CreateIdTokenCommand`) and `GetJwksCommand` resolve
     * the same key. Returns the alias so callers can fold it into their `IdTokenValidationOptions`.
     */
    suspend fun ensureOpSigningKey(
        alias: String = OP_SIGNING_KEY_ALIAS,
        alg: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
        tenantId: String = DEFAULT_TENANT_ID,
    ): String {
        val result =
            keyManagerService.generateKeyResult(
                alias = alias,
                use = JwkUse.sig,
                alg = alg,
            )
        check(result.isOk) {
            "Failed to generate OP signing key '$alias': ${if (result.isErr) result.error.message.defaultMessage else "<unknown>"}"
        }
        val keyPair = checkNotNull(result.value.keyPair) { "generateKeyResult succeeded but keyPair is null" }
        val now = Clock.System.now()
        val signingKey =
            OAuth2SigningKey(
                tenantId = tenantId,
                keyInfo =
                    KeyInfo<KeyType>(
                        kid = keyPair.kid ?: keyPair.alias,
                        alias = alias,
                        providerId = keyPair.providerId,
                        signatureAlgorithm = alg,
                    ),
                state = OAuth2SigningKeyState.ACTIVE,
                priority = 1,
                createdAt = now,
                notBefore = now,
            )
        val register = signingKeyStore.register(signingKey)
        check(register.isOk) {
            "Failed to register OP signing key in SigningKeyStore: ${if (register.isErr) register.error else "<unknown>"}"
        }
        return alias
    }

    companion object {
        /**
         * Matches `DefaultOAuth2ConfigModule.DEFAULT_SIGNING_KEY_ALIAS`. When we pre-generate a
         * key under this alias before the OP touches it, the lazy `ManagedOptsAlias` resolution
         * inside the token commands finds it at signing time.
         */
        const val OP_SIGNING_KEY_ALIAS = "oauth2-server-signing"

        /**
         * The integration tests run a single-tenant deployment, so all signing keys land under a
         * fixed tenant. Matches the [com.sphereon.ktor.server.inject.resolver.FixedTenantResolver]
         * default the standalone OAuth2 AS Ktor server hands to incoming requests.
         */
        const val DEFAULT_TENANT_ID = "default"
    }
}

/**
 * Graph extension that exposes the AppScope-bound [SigningKeyStore] so the integration
 * tests can register pre-generated keys before the AS sign paths run.
 */
@ContributesTo(AppScope::class)
interface OAuth2SigningKeyStoreGraph {
    val signingKeyStore: SigningKeyStore
}
