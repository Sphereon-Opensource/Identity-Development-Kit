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

package com.sphereon.did.methods.jwk

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.AddKeyOptions
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidCreateResult
import com.sphereon.did.manager.DidDeactivateOptions
import com.sphereon.did.manager.DidDeactivateResult
import com.sphereon.did.manager.DidProvider
import com.sphereon.did.manager.DidUpdateOptions
import com.sphereon.did.manager.DidUpdateResult
import com.sphereon.did.models.DidService
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolverRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Provider for creating did:jwk DIDs.
 *
 * did:jwk is an immutable DID method - once created, the DID cannot be
 * updated, deactivated, or have keys added/removed. The DID is derived
 * directly from the base64url-encoded JWK.
 *
 * This provider creates did:jwk DIDs from existing JWK key material.
 *
 * @see <a href="https://github.com/quartzjer/did-jwk/blob/main/spec.md">did:jwk Specification</a>
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidProvider>())
class JwkDidProviderImpl(
    private val resolverRegistry: DidResolverRegistry,
) : DidProvider {
    private val json =
        Json {
            encodeDefaults = false
        }

    override val method: String = JwkDidCapabilities.METHOD

    override val capabilities: DidMethodCapabilities = JwkDidCapabilities.CAPABILITIES

    override suspend fun create(options: DidCreateOptions): IdkResult<DidCreateResult, IdkError> {
        // Validate that we have a JWK
        val jwk =
            options.publicKeyJwk
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "did:jwk creation requires publicKeyJwk in options",
                    ),
                )

        // Construct the DID using a canonicalised public JWK
        val did = "did:jwk:${canonicalBase64UrlJwk(jwk)}"

        // Resolve the DID to get the full document via the registry
        val resolutionResult =
            resolverRegistry.resolve(did, DidResolutionOptions()).getOrElse {
                return Err(it)
            }

        val document =
            resolutionResult.didDocument
                ?: return Err(IdkError.fromString(message = "Failed to generate DID document for: $did", code = "ILLEGAL_STATE"))

        return Ok(
            DidCreateResult(
                did = did,
                didDocument = document,
                verificationMethodsByPurpose = resolutionResult.verificationMethodsByPurpose,
            ),
        )
    }

    override suspend fun update(
        did: String,
        options: DidUpdateOptions,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:jwk is immutable - updates are not supported
        return Err(
            IdkError.fromString(
                message = "did:jwk does not support updates. DIDs are immutable and derived from the JWK.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun deactivate(
        did: String,
        options: DidDeactivateOptions,
    ): IdkResult<DidDeactivateResult, IdkError> {
        // did:jwk is immutable - deactivation is not supported
        return Err(
            IdkError.fromString(
                message = "did:jwk does not support deactivation. DIDs are immutable and always valid.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun addKey(
        did: String,
        options: AddKeyOptions,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:jwk supports only a single key per DID
        return Err(
            IdkError.fromString(
                message = "did:jwk does not support adding keys. Each DID corresponds to exactly one JWK.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun removeKey(
        did: String,
        keyId: String,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:jwk supports only a single key per DID
        return Err(
            IdkError.fromString(
                message = "did:jwk does not support removing keys. Each DID corresponds to exactly one JWK.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun addService(
        did: String,
        service: DidService,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:jwk does not support services
        return Err(
            IdkError.fromString(
                message = "did:jwk does not support services. Use did:web for DIDs with services.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun removeService(
        did: String,
        serviceId: String,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:jwk does not support services
        return Err(
            IdkError.fromString(
                message = "did:jwk does not support services.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    companion object {
        /**
         * JWK members allowed inside the base64url-encoded JWK that forms a did:jwk.
         *
         * The did:jwk identifier must be deterministic from the public key material,
         * so all metadata/identity members (kid/use/key_ops/alg) and any private key
         * parameters are stripped. Two equal public keys produce the same DID
         * regardless of what a KMS or caller attached to the wrapping JWK.
         *
         * The members retained are the union of required public-key members across
         * JWA key types (RFC 7518): EC (kty/crv/x/y), RSA (kty/n/e), OKP (kty/crv/x).
         */
        private val PUBLIC_KEY_JWK_MEMBERS = setOf("kty", "crv", "x", "y", "n", "e")

        /**
         * Serialise [jwk] as a canonical public-key JSON object and base64url-encode it.
         * Strips any non-public-key members (kid, alg, use, key_ops, private params).
         */
        private fun canonicalBase64UrlJwk(jwk: Jwk): String {
            val json = Json { encodeDefaults = false }
            val full = json.parseToJsonElement(json.encodeToString(jwk)) as JsonObject
            val canonical =
                buildJsonObject {
                    full.forEach { (k, v) -> if (k in PUBLIC_KEY_JWK_MEMBERS) put(k, v) }
                }
            return json.encodeToString(JsonObject.serializer(), canonical).encodeToByteArray().encodeToBase64Url()
        }

        /**
         * Creates a did:jwk from a JWK object.
         */
        fun didFromJwk(jwk: Jwk): String = "did:jwk:${canonicalBase64UrlJwk(jwk)}"
    }
}
