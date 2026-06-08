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
 *
 */

package com.sphereon.openid.oid4vci.issuer.impl.hosting

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.did.hosting.DidHostingProvider
import com.sphereon.did.hosting.HostedDid
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethodConfig
import com.sphereon.did.models.VerificationPurpose
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * Serves the SINGLE `did:web` document for the deployment host over the generic DID-hosting endpoint
 * (`:services-did-hosting-rest`). The document carries every key this deployment publishes, each at a
 * descriptive per-alias fragment (e.g. `did:web:<host>#PID`, `#oid4vp-verifier-signing`): the issuer's
 * credential-signing keys under `assertionMethod`, and the verifier's request-object signing key under
 * `authentication`. Verification methods are derived on demand from the public keys resolved out of
 * this service's KMS — the same path the signing `kid` is resolved with — so the served document and
 * the JWT `kid`s are always consistent. No persistence / KeyReferenceStore is required.
 *
 * Keys listed in config but absent from this service's KMS are skipped, so the document still serves
 * whatever is available.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidHostingProvider>())
class IssuerDidWebHostingProvider(
    private val kms: KeyManagerService,
    private val didProviderRegistry: DidProviderRegistry,
    private val configService: PrincipalConfigService,
) : DidHostingProvider {
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        }

    override val method: String = "web"

    override suspend fun resolveDidJson(
        tenantId: String?,
        webLocation: String,
    ): IdkResult<HostedDid?, IdkError> {
        val host = resolveHost() ?: return Ok(null)
        // We host exactly the deployment's own path-less did:web (web location == host).
        if (!webLocation.equals(host, ignoreCase = true)) {
            return Ok(null)
        }
        val provider = didProviderRegistry.getProvider("web") ?: return Ok(null)

        val verificationMethods = mutableListOf<VerificationMethodConfig>()
        for (alias in assertionAliases()) {
            vmConfigOrNull(alias, VerificationPurpose.ASSERTION_METHOD)?.let { verificationMethods += it }
        }
        for (alias in aliasList(AUTHENTICATION_KEY_ALIASES_KEY)) {
            vmConfigOrNull(alias, VerificationPurpose.AUTHENTICATION)?.let { verificationMethods += it }
        }
        if (verificationMethods.isEmpty()) {
            return Ok(null)
        }

        val created =
            provider
                .create(DidCreateOptions(method = "web", domain = host, verificationMethods = verificationMethods))
                .getOrElse { return Err(it) }
        return Ok(
            HostedDid(
                json = json.encodeToString(DidDocument.serializer(), created.didDocument),
                method = method,
                cacheMaxAgeSeconds = CACHE_MAX_AGE_SECONDS,
            ),
        )
    }

    private fun resolveHost(): String? {
        configService.getPropertyAsString(DID_WEB_DOMAIN_KEY)?.takeIf { it.isNotBlank() }?.let { return hostOf(it) }
        return configService.getPropertyAsString(IDENTIFIER_KEY)?.let { hostOf(it) }
    }

    /** Issuer signing keys to publish under assertionMethod; falls back to the single signing-key-alias. */
    private fun assertionAliases(): List<String> {
        val configured = aliasList(ASSERTION_KEY_ALIASES_KEY)
        if (configured.isNotEmpty()) return configured
        return listOfNotNull(configService.getPropertyAsString(SIGNING_KEY_ALIAS_KEY)?.takeIf { it.isNotBlank() })
    }

    private fun aliasList(key: String): List<String> =
        configService
            .getPropertyAsString(key)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()

    /**
     * Verification-method config for [alias] (fragment == alias, public key resolved from this KMS),
     * or `null` when the alias is not present in this service's KMS (so it is simply omitted).
     */
    private suspend fun vmConfigOrNull(
        alias: String,
        purpose: VerificationPurpose,
    ): VerificationMethodConfig? {
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = alias))
        if (keyResult.isErr) {
            return null
        }
        val jwk = keyResult.value.key?.key as? Jwk ?: return null
        return VerificationMethodConfig(
            kmsKeyAlias = alias,
            // Not used to build the did:web document (the public key comes from publicKeyJwk); the
            // model requires a non-blank value.
            kmsProviderId = KMS_PROVIDER_ID,
            verificationMethodId = alias,
            purposes = listOf(purpose),
            publicKeyJwk = jwk.toPublicKey(),
        )
    }

    private companion object {
        const val IDENTIFIER_KEY = "oid4vci.issuer.identifier"
        const val DID_WEB_DOMAIN_KEY = "oid4vci.issuer.signing.did-web-domain"
        const val SIGNING_KEY_ALIAS_KEY = "oid4vci.issuer.signing-key-alias"

        // Comma-separated KMS key aliases to publish in the single hosted did:web document.
        const val ASSERTION_KEY_ALIASES_KEY = "oid4vci.issuer.hosting.did-web.assertion-key-aliases"
        const val AUTHENTICATION_KEY_ALIASES_KEY = "oid4vci.issuer.hosting.did-web.authentication-key-aliases"
        const val KMS_PROVIDER_ID = "software"
        const val CACHE_MAX_AGE_SECONDS = 300L

        fun hostOf(url: String): String? {
            val authority =
                url
                    .substringAfter("://", "")
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
            val host = authority.substringBefore('@').substringBefore(':')
            return host.takeIf { it.isNotBlank() }
        }
    }
}
