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

package com.sphereon.openid.oid4vci.issuer.impl.signing

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.models.VerificationPurpose
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject

/**
 * Resolves the `kid` string and bare public JWK for a signing key alias, in exactly the
 * same way the credential issuance path does. Centralising this here guarantees the
 * JWT-protected-header `kid` and the `kid` published in the issuer's JWKS are always
 * byte-identical.
 *
 * The resolver is DID-method-aware: for a DID-bound issuer (`did:jwk`, `did:key`, ...)
 * the kid is the DID URL of the assertion-method verification method — e.g.
 * `did:jwk:<canonical-b64url-jwk>#0`. The JWK production path (`JwkDidProviderImpl`)
 * canonicalises the public key before encoding the DID, so `resolveDidKid` is
 * deterministic from the key material.
 */
interface IssuerKeyIdResolver {
    /**
     * Returns the DID verification-method id ("`<did>#<fragment>`") for [keyAlias]
     * under the requested [didMethod].
     *
     * Fails if the KMS lookup, DID provider lookup, or DID creation fails.
     */
    suspend fun resolveDidVerificationMethodId(
        keyAlias: String,
        didMethod: String,
    ): IdkResult<String, IdkError>

    /** Returns the bare public JWK (as a JsonObject) for [keyAlias]. */
    suspend fun resolvePublicJwk(keyAlias: String): IdkResult<JsonObject, IdkError>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IssuerKeyIdResolver>())
class DefaultIssuerKeyIdResolver(
    private val kms: KeyManagerService,
    private val didProviderRegistry: DidProviderRegistry,
    private val configService: PrincipalConfigService,
) : IssuerKeyIdResolver {
    override suspend fun resolveDidVerificationMethodId(
        keyAlias: String,
        didMethod: String,
    ): IdkResult<String, IdkError> {
        val jwk = loadPublicJwk(keyAlias).getOrElse { return Err(it) }
        val provider =
            didProviderRegistry.getProvider(didMethod)
                ?: return Err(IdkError.fromString(code = "unsupported_did_method", message = "DID method '$didMethod' is not registered"))
        // Web-resolved methods (did:web / did:webvh) bind the KMS key to a document hosted at a
        // host; unlike did:jwk/did:key the DID is NOT derived from the key, so the host must be
        // supplied. It is the host of the issuer identifier (the public HTTPS base URL).
        val domain =
            if (didMethod in WEB_RESOLVED_METHODS) {
                resolveDidWebDomain()
                    ?: return Err(
                        IdkError.fromString(
                            code = "did_web_domain_unresolved",
                            message =
                                "did:$didMethod signing requires a host: set '$DID_WEB_DOMAIN_KEY' or a valid " +
                                    "absolute '$IDENTIFIER_KEY'.",
                        ),
                    )
            } else {
                null
            }
        val created =
            provider
                .create(
                    DidCreateOptions(
                        method = didMethod,
                        publicKeyJwk = jwk.toPublicKey(),
                        domain = domain,
                        // The issuer's signing key is an assertion key — credential proofs use the
                        // assertionMethod relationship, so the published did:web document MUST list it there.
                        purposes = if (didMethod in WEB_RESOLVED_METHODS) listOf(VerificationPurpose.ASSERTION_METHOD) else null,
                        // Descriptive, per-key fragment so each signing key has its own VM in the
                        // hosted document (e.g. did:web:host#PID) — no shared "#key-1" collision.
                        verificationMethodId = if (didMethod in WEB_RESOLVED_METHODS) keyAlias else null,
                    ),
                ).getOrElse { return Err(it) }
        val vmId =
            created.verificationMethodsByPurpose[VerificationPurpose.ASSERTION_METHOD]
                ?.firstOrNull()
                ?.id
                ?: "${created.did}#0"
        return Ok(vmId)
    }

    override suspend fun resolvePublicJwk(keyAlias: String): IdkResult<JsonObject, IdkError> {
        val jwk = loadPublicJwk(keyAlias).getOrElse { return Err(it) }
        // Serialise the full JWK and strip non-public / private members — same rule as
        // JwkDidProviderImpl.canonicalBase64UrlJwk so the published JWKS entry key material
        // matches what the DID URL encodes for.
        val full =
            kotlinx.serialization.json
                .Json {
                    encodeDefaults = false
                }.let { j -> j.parseToJsonElement(j.encodeToString(jwk)) as JsonObject }
        val canonical =
            kotlinx.serialization.json.buildJsonObject {
                full.forEach { (k, v) -> if (k in PUBLIC_JWK_MEMBERS) put(k, v) }
            }
        return Ok(canonical)
    }

    /**
     * Resolve the did:web/webvh host: an explicit override, else the host of the issuer identifier
     * (the public HTTPS base URL, e.g. `https://issuer.example/oid4vci` -> `issuer.example`).
     */
    private fun resolveDidWebDomain(): String? {
        configService.getPropertyAsString(DID_WEB_DOMAIN_KEY)?.takeIf { it.isNotBlank() }?.let { return hostOf(it) }
        return configService.getPropertyAsString(IDENTIFIER_KEY)?.let { hostOf(it) }
    }

    private suspend fun loadPublicJwk(keyAlias: String): IdkResult<Jwk, IdkError> {
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = keyAlias))
        if (keyResult.isErr) {
            return Err(IdkError.fromString(code = "signing_key_unavailable", message = "KMS did not return key '$keyAlias': ${keyResult.error.message}"))
        }
        val jwk =
            keyResult.value.key?.key as? Jwk
                ?: return Err(IdkError.fromString(code = "signing_key_unavailable", message = "KMS key '$keyAlias' is not a JWK"))
        return Ok(jwk)
    }

    companion object {
        /**
         * Members of a JWK that are legitimate to publish. Excludes the identity members
         * (`kid`, `use`, `key_ops`, `alg`) so the JWKS entry carries only the key material
         * that forms the thumbprint / DID identifier; the `kid` is added back by the
         * caller from the same resolver that produces the JWT-header kid.
         */
        private val PUBLIC_JWK_MEMBERS = setOf("kty", "crv", "x", "y", "n", "e")

        /** Methods whose DID is hosted at a web host (so the host must be supplied, not derived from the key). */
        private val WEB_RESOLVED_METHODS = setOf("web", "webvh")
        private const val IDENTIFIER_KEY = "oid4vci.issuer.identifier"
        private const val DID_WEB_DOMAIN_KEY = "oid4vci.issuer.signing.did-web-domain"

        /** Host (authority without scheme/port/path) of an absolute http(s) URL, or null. */
        internal fun hostOf(url: String): String? {
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
