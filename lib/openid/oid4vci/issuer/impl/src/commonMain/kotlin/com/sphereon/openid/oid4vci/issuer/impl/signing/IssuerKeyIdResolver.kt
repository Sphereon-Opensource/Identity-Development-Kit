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
) : IssuerKeyIdResolver {
    override suspend fun resolveDidVerificationMethodId(
        keyAlias: String,
        didMethod: String,
    ): IdkResult<String, IdkError> {
        val jwk = loadPublicJwk(keyAlias).getOrElse { return Err(it) }
        val provider =
            didProviderRegistry.getProvider(didMethod)
                ?: return Err(IdkError.fromString(code = "unsupported_did_method", message = "DID method '$didMethod' is not registered"))
        val created =
            provider
                .create(DidCreateOptions(method = didMethod, publicKeyJwk = jwk.toPublicKey()))
                .getOrElse { return Err(it) }
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
    }
}
