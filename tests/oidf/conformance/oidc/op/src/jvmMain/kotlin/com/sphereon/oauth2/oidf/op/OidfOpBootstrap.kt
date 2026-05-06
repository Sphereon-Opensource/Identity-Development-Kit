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

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.session.SessionInstance
import com.sphereon.oauth2.server.authorization.impl.provider.PasswordHasher
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.time.Clock

/**
 * One-shot bootstrap for the OIDF conformance OP harness. Three steps:
 *
 *  1. Pre-generate the RS256 signing key under [OP_SIGNING_KEY_ALIAS], so the AS commands
 *     resolve a real key at sign time. The software KMS provider itself is registered
 *     declaratively via `kms.providers.software.*` in `application.properties`, with an
 *     APP-scoped memory keystore so the seeded key remains visible from per-request
 *     sessions.
 *  2. Compute deterministic PBKDF2 hashes for the seeded fixture users (alice, bob) using the
 *     same pepper + iteration count the runtime [com.sphereon.oauth2.server.authorization.impl.provider.ConfigBackedUserAuthenticationProvider]
 *     reads from config, then publish them on [DefaultPrincipalMapPropertySource] so the
 *     provider sees them at lookup time. Computing the hashes once at startup keeps the
 *     `application.properties` file free of a brittle precomputed-hash literal.
 *  3. Return a [SeedResult] for the entry-point banner.
 *
 * Conformance clients (`oidf-op-basic`, `oidf-op-public`) come from `application.properties`
 * via `OAuth2ClientsConfigBinder` directly, so no runtime client registration is needed here.
 *
 * The fixture pepper and seeded passwords are conformance constants, not production secrets, and
 * must never be reused outside this harness.
 */
object OidfOpBootstrap {
    /** Matches the default the OAuth2 AS uses when `signing-key-alias` is unset. */
    const val OP_SIGNING_KEY_ALIAS: String = "oauth2-server-signing"

    /**
     * Deployment-wide pepper for the harness, base64-encoded. The plain-text source string is
     * `oidf-op-conformance-pepper-2026`. The runtime provider decodes this from base64 inside
     * [PasswordHasher].
     */
    const val FIXTURE_PEPPER_B64: String = "b2lkZi1vcC1jb25mb3JtYW5jZS1wZXBwZXItMjAyNg=="

    /** Iteration count matched by `oauth2.users.password.iterations` in `application.properties`. */
    const val FIXTURE_ITERATIONS: Int = 210_000

    /** Seeded password used for both alice and bob. Conformance fixture, not a production secret. */
    const val FIXTURE_PASSWORD: String = "Sphereon-OIDF-2026!"

    /**
     * Tenant id the Ktor plugin defaults to for unauthenticated requests. The bootstrap session
     * runs under the same tenant so the APP-scoped memory keystore picks up the seeded key from
     * subsequent per-request sessions.
     */
    const val DEFAULT_TENANT_ID: String = "default"

    /** Principal id the Ktor plugin defaults to for unauthenticated requests. */
    const val DEFAULT_PRINCIPAL_ID: String = "anonymous"

    /** Generates the signing key, hashes the fixture passwords, publishes them, returns a summary. */
    suspend fun seed(graph: AppGraph): SeedResult {
        val context: UserContextInstance =
            graph.userContextManager.createOrGetFromInputs(
                tenantInput = DefaultTenantInputString(DEFAULT_TENANT_ID),
                principalInput = DefaultPrincipalInputString(DEFAULT_PRINCIPAL_ID),
                makeActive = false,
            )
        val session: SessionInstance = context.sessionContextManager.createOrGetFromId("oidf-op-bootstrap")
        // Reach the session's CoreApi graph to anchor the execution scope; the KMS pulls its
        // active provider config from the same scope.
        session.asCoreApiServiceGraph().serviceExecution

        val keyManagerService: KeyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        val signingAlg = SignatureAlgorithm.RSA_SHA256
        val signingResult =
            keyManagerService.generateKeyResult(
                alias = OP_SIGNING_KEY_ALIAS,
                use = JwkUse.sig,
                alg = signingAlg,
            )
        check(signingResult.isOk) {
            val message = if (signingResult.isErr) signingResult.error.message.defaultMessage else "<unknown>"
            "Failed to generate OP signing key '$OP_SIGNING_KEY_ALIAS': $message"
        }
        // Register the key with the SigningKeyStore SPI so the AS sign paths and the JWKS
        // endpoint resolve the same active key. The SPI replaces the legacy
        // `OAuth2ServerInstanceConfig.signingKeyAlias` lookup; without registration JWKS publishes
        // an empty key set and every signed-token grant fails.
        val keyPair = checkNotNull(signingResult.value.keyPair) { "generateKeyResult succeeded but keyPair is null" }
        val signingKeyStore: SigningKeyStore = (graph as OidfOpSigningKeyStoreGraph).signingKeyStore
        val now = Clock.System.now()
        val registerResult =
            signingKeyStore.register(
                OAuth2SigningKey(
                    tenantId = DEFAULT_TENANT_ID,
                    keyInfo =
                        KeyInfo<KeyType>(
                            kid = keyPair.kid ?: keyPair.alias,
                            alias = OP_SIGNING_KEY_ALIAS,
                            providerId = keyPair.providerId,
                            signatureAlgorithm = signingAlg,
                        ),
                    state = OAuth2SigningKeyState.ACTIVE,
                    priority = 1,
                    createdAt = now,
                    notBefore = now,
                ),
            )
        check(registerResult.isOk) {
            "Failed to register OP signing key in SigningKeyStore: ${if (registerResult.isErr) registerResult.error else "<unknown>"}"
        }

        val hasher =
            PasswordHasher(
                deploymentSalt = FIXTURE_PEPPER_B64.decodeFromBase64(),
                iterations = FIXTURE_ITERATIONS,
            )
        val seededUsers = listOf("alice", "bob")
        seededUsers.forEach { username ->
            val hash = hasher.hash(username, FIXTURE_PASSWORD)
            DefaultPrincipalMapPropertySource.addProperty(
                "oauth2.users.accounts.$username.password",
                hash,
            )
        }

        return SeedResult(
            signingKeyAlias = OP_SIGNING_KEY_ALIAS,
            signingAlgorithm = "RS256",
            seededUsers = seededUsers,
        )
    }

    data class SeedResult(
        val signingKeyAlias: String,
        val signingAlgorithm: String,
        val seededUsers: List<String>,
    )
}

/**
 * Graph extension exposing the AppScope-bound [SigningKeyStore] so [OidfOpBootstrap]
 * can register the seeded OP signing key.
 */
@ContributesTo(AppScope::class)
interface OidfOpSigningKeyStoreGraph {
    val signingKeyStore: SigningKeyStore
}
