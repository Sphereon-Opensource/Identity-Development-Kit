/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyDTOType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwkDTOType
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.X509VerificationResultType
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.crypto.resolution.IdentifierTypeUtils
import kotlinx.serialization.Serializable


/**
 * Type for external identifiers
 */
typealias ExternalIdentifierType = Any


/**
 * Base class for external identifier options and results.
 *
 * Implements [ExternalIdentifierBase] marker interface for the registry pattern.
 * External libraries can extend this to add custom identifier types.
 */
abstract class ExternalIdentifierOptsOrResult(
    override val method: IIdentifierMethod? = null,
    override val identifier: Any,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : IdentifierOptsOrResult(method = method, identifier = identifier, context = context, lookup = lookup),
    ExternalIdentifierBase {

    override fun asOpts() = this as ExternalIdentifierOpts
    override fun asResult() = this as ExternalIdentifierResult
}

/**
 * Base options for external identifiers
 */
abstract class ExternalIdentifierOpts(
    override val method: IIdentifierMethod? = null,
    override val identifier: ExternalIdentifierType,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : ExternalIdentifierOptsOrResult(method = method, identifier = identifier, context = context, lookup = lookup) {
    override val isResolved: Boolean = false
}

/**
 * Options for external DID identifiers
 */
data class ExternalIdentifierDidOpts(
    override val identifier: String,
    /*    val noVerificationMethodFallback: Boolean? = null,
        val vmRelationship: String? = null,
        val localResolution: Boolean? = null,
        val uniresolverResolution: Boolean? = null,
        val resolverResolution: Boolean? = null*/
) : ExternalIdentifierOpts(method = IdentifierMethodDefaults.DID, identifier = identifier)

/**
 * Options for external KID identifiers
 */
data class ExternalIdentifierKidOpts(
    override val identifier: String
) : ExternalIdentifierOpts(method = IdentifierMethodDefaults.KID, identifier = identifier)

/**
 * Options for external JWK identifiers
 */
data class ExternalIdentifierJwkOpts(
    override val identifier: JwkType,
    val x5c: ExternalIdentifierX5cOpts? = null
) : ExternalIdentifierOpts(method = IdentifierMethodDefaults.JWK, identifier = identifier)

/**
 * Options for external COSE Key identifiers
 */
data class ExternalIdentifierCoseKeyOpts(
    override val identifier: CoseKeyDTOType,
    val x5c: ExternalIdentifierX5cOpts? = null
) : ExternalIdentifierOpts(method = IdentifierMethodDefaults.COSE_KEY, identifier = identifier)

/**
 * Options for external OIDC Discovery identifiers
 */
data class ExternalIdentifierOidcDiscoveryOpts(
    override val identifier: String
) : ExternalIdentifierOpts(method = IdentifierMethodDefaults.OIDC_DISCOVERY, identifier = identifier)

/**
 * Options for external JWKS URL identifiers
 */
data class ExternalIdentifierJwksUrlOpts(
    override val identifier: String,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : ExternalIdentifierOpts(method = IdentifierMethodDefaults.JWKS_URL, identifier = identifier, context = context, lookup = lookup)

/**
 * Options for external OIDF Entity ID identifiers
 */
data class ExternalIdentifierOIDFEntityIdOpts(
    override val identifier: String,
    val trustAnchors: List<String>? = null
) : ExternalIdentifierOpts(method = IdentifierMethodDefaults.ENTITY_ID, identifier = identifier)

/**
 * Options for external X5C identifiers
 */
data class ExternalIdentifierX5cOpts(
    override val identifier: List<String>,
    val verify: Boolean? = null,
    val verificationTime: String? = null,
    val trustAnchors: List<String>? = null
) : ExternalIdentifierOpts(method = IdentifierMethodDefaults.X5C, identifier = identifier)

/**
 * Options for external CNF (Confirmation) identifiers.
 *
 * RFC 7800 defines the CNF (Confirmation) claim structure which can contain:
 * - `jwk`: The public key as JWK (most common)
 * - `kid`: Key identifier - if it's a DID, it will be resolved; otherwise requires jku
 * - `jku`: JWK Set URL from which to fetch keys (kid used to select specific key)
 *
 * Resolution priority:
 * 1. If kid is a DID → resolve DID to get public key
 * 2. If jwk is present → use directly
 * 3. If jku is present → fetch JWK Set and select key (by kid if provided)
 * 4. If only non-DID kid → error (cannot resolve without jwk or jku)
 *
 * @property identifier The CNF claim as a map (typically from JSON parsing)
 * @property kid Optional key identifier extracted from CNF
 * @property jwk Optional JWK extracted from CNF
 * @property jku Optional JWK Set URL extracted from CNF
 */
data class ExternalIdentifierCnfOpts(
    override val identifier: Map<String, Any?>,
    val kid: String? = null,
    val jwk: JwkType? = null,
    val jku: String? = null
) : ExternalIdentifierOpts(method = IdentifierMethodDefaults.CNF, identifier = identifier)

/**
 * Base interface for external identifier results
 */
abstract class ExternalIdentifierResult(
    open val identifierOpts: ExternalIdentifierOpts,
    override val method: IIdentifierMethod,
    open val jwks: Array<ExternalJwkInfo>,

    open val keyInfo: ResolvedKeyInfoType<KeyType>,

    ) : ExternalIdentifierOptsOrResult(method = method, identifier = identifierOpts.identifier) {
    override val isResolved: Boolean = true

    data class Jwk(
        override val identifierOpts: ExternalIdentifierJwkOpts,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
        val x5c: X5c? = null
    ) : ExternalIdentifierResult(method = IdentifierMethodDefaults.JWK, jwks = jwks, keyInfo = keyInfo, identifierOpts = identifierOpts)


    /**
     * Result for external COSE Key identifiers
     */
    data class CoseKey(
        override val identifierOpts: ExternalIdentifierCoseKeyOpts,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<com.sphereon.crypto.core.cose.CoseKeyType>,
        val x5c: X5c? = null
    ) : ExternalIdentifierResult(method = IdentifierMethodDefaults.COSE_KEY, jwks = jwks, keyInfo = keyInfo, identifierOpts = identifierOpts)


    /**
     * Result for external X5C identifiers
     */
    data class X5c(
        override val identifierOpts: ExternalIdentifierX5cOpts,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
        val x5c: List<String>,
        val verificationResult: X509VerificationResultType,
        val certificates: List<Certificate>
    ) : ExternalIdentifierResult(method = IdentifierMethodDefaults.X5C, jwks = jwks, keyInfo = keyInfo, identifierOpts = identifierOpts)


    /**
     * Result for external OIDF Entity ID identifiers
     */

    data class OIDFEntityId(
        override val identifierOpts: ExternalIdentifierOIDFEntityIdOpts,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
        val trustedAnchors: List<TrustedAnchor>,
        val errorList: Map<TrustedAnchor, ErrorMessage>? = null,
        val jwtPayload: Map<String, Any>? = null,
        val trustEstablished: Boolean
    ) : ExternalIdentifierResult(method = IdentifierMethodDefaults.ENTITY_ID, jwks = jwks, keyInfo = keyInfo, identifierOpts = identifierOpts)


    /**
     * Result for external DID identifiers
     */
    data class Did(
        override val identifierOpts: ExternalIdentifierDidOpts,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
        val did: String,
        val didDocument: DIDDocument? = null,
        val didJwks: DidDocumentJwks? = null,
        val didResolutionResult: DIDResolutionResult,
        val didParsed: ParsedDID?
    ) : ExternalIdentifierResult(method = IdentifierMethodDefaults.DID, jwks = jwks, keyInfo = keyInfo, identifierOpts = identifierOpts)

    /**
     * Result for external JWKS URL identifiers.
     *
     * This represents a JWKS that was fetched from a URL (for example an OpenID `jwks_uri`).
     * A single key is selected as [keyInfo] based on the provided lookup (typically `kid`) or by
     * requiring the set to contain exactly one key.
     */
    data class JwksUrl(
        override val identifierOpts: ExternalIdentifierJwksUrlOpts,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
        val jwksUrl: String,
        val selectedKid: String? = null
    ) : ExternalIdentifierResult(method = IdentifierMethodDefaults.JWKS_URL, jwks = jwks, keyInfo = keyInfo, identifierOpts = identifierOpts)

    /**
     * Result for external CNF (Confirmation) identifiers.
     *
     * This represents a resolved holder binding key from an RFC 7800 CNF claim.
     * The resolution may have come from:
     * - Direct JWK in the CNF claim
     * - DID resolution (if kid was a DID)
     * - JWKS URL fetch (if jku was provided)
     *
     * @property identifierOpts The original CNF options
     * @property jwks All resolved keys (typically just one for CNF)
     * @property keyInfo The selected/resolved key for holder binding verification (includes kid if available)
     * @property resolvedFrom Indicates how the key was resolved (DID, JWK, JKU)
     */
    data class Cnf(
        override val identifierOpts: ExternalIdentifierCnfOpts,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
        val resolvedFrom: CnfResolutionSource
    ) : ExternalIdentifierResult(method = IdentifierMethodDefaults.CNF, jwks = jwks, keyInfo = keyInfo, identifierOpts = identifierOpts)

}

/**
 * Indicates how a CNF claim was resolved to obtain key material.
 */
enum class CnfResolutionSource {
    /** Key was obtained from cnf.jwk directly */
    JWK,
    /** Key was obtained by resolving a DID from cnf.kid */
    DID,
    /** Key was obtained by fetching JWK Set from cnf.jku */
    JKU
}

/**
 * JWK information for external identifiers
 */
//@Serializable
typealias ExternalJwkInfo = ResolvedKeyInfoType<JwkType>
/*
data class ExternalJwkInfo(
    override val key: JwkType,
    override val kid: String? = null
) : KeyInfo<JwkType>
*/

/**
 * Result for external JWK identifiers
 */


/**
 * X509 validation result
 */
data class X509ValidationResult(
    val valid: Boolean,
    val certificateChain: List<Certificate>? = null,
    val errors: List<String>? = null
)

/**
 * Type alias for trusted anchor
 */
typealias TrustedAnchor = String

/**
 * Type alias for error message
 */
typealias ErrorMessage = String

/**
 * Parsed DID
 */
@Serializable
data class ParsedDID(
    val did: String,
    val method: String,
    val id: String,
    val path: String? = null,
    val fragment: String? = null
)

/**
 * DID document
 */
@Serializable
data class DIDDocument(
    val id: String,
    val verificationMethod: List<VerificationMethod>? = null,
    val authentication: List<String>? = null,
    val assertionMethod: List<String>? = null,
    val keyAgreement: List<String>? = null,
    val capabilityInvocation: List<String>? = null,
    val capabilityDelegation: List<String>? = null,
    val service: List<Service>? = null
)

/**
 * Verification method
 */
@Serializable
data class VerificationMethod(
    val id: String,
    val type: String,
    val controller: String,
    val publicKeyJwk: JwkType? = null,
    val publicKeyMultibase: String? = null
)

/**
 * Service
 */
@Serializable
data class Service(
    val id: String,
    val type: String,
    val serviceEndpoint: String
)

/**
 * DID document JWKs. From VM relationship Id to JWK
 */
typealias DidDocumentJwks = Map<String, List<JwkType>>

/**
 * DID resolution result
 */
data class DIDResolutionResult(
    val didResolutionMetadata: Map<String, Any>? = null,
    val didDocumentMetadata: Map<String, Any>? = null
)


/**
 * Type guard functions for external identifier options
 */
object ExternalIdentifierOptsGuards {
    fun isExternalIdentifierDidOpts(opts: ExternalIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.DID) || IdentifierTypeUtils.isDidIdentifier(opts.identifier)
    }

    fun isExternalIdentifierKidOpts(opts: ExternalIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.KID) || IdentifierTypeUtils.isKidIdentifier(opts.identifier)
    }

    fun isExternalIdentifierJwkOpts(opts: ExternalIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.JWK) || IdentifierTypeUtils.isJwkIdentifier(opts.identifier)
    }

    fun isExternalIdentifierCoseKeyOpts(opts: ExternalIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.COSE_KEY) || IdentifierTypeUtils.isCoseKeyIdentifier(opts.identifier)
    }

    fun isExternalIdentifierOidcDiscoveryOpts(opts: ExternalIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.OIDC_DISCOVERY) || IdentifierTypeUtils.isOidcDiscoveryIdentifier(opts.identifier)
    }

    fun isExternalIdentifierJwksUrlOpts(opts: ExternalIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.JWKS_URL) || IdentifierTypeUtils.isJwksUrlIdentifier(opts.identifier)
    }

    fun isExternalIdentifierOIDFEntityIdOpts(opts: ExternalIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.ENTITY_ID) || IdentifierTypeUtils.isOIDFEntityIdIdentifier(opts.identifier)
    }

    fun isExternalIdentifierX5cOpts(opts: ExternalIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.X5C) || IdentifierTypeUtils.isX5cIdentifier(opts.identifier)
    }

    fun isExternalIdentifierCnfOpts(opts: ExternalIdentifierOpts): Boolean {
        return opts.method == IdentifierMethodDefaults.CNF && opts is ExternalIdentifierCnfOpts
    }


    // Safe (non-throwing) variants - return IdkResult

    /**
     * Safely casts opts to ExternalIdentifierKidOpts.
     * @return IdkResult containing the casted opts, or an error if the cast would fail.
     */
    fun tryAsExternalIdentifierKidOpts(opts: ExternalIdentifierOpts): IdkResult<ExternalIdentifierKidOpts, IdkError> {
        return if (isExternalIdentifierKidOpts(opts)) Ok(opts as ExternalIdentifierKidOpts)
        else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "opts is not an instance of ExternalIdentifierKidOpts"))
    }

    /**
     * Safely casts opts to ExternalIdentifierDidOpts.
     * @return IdkResult containing the casted opts, or an error if the cast would fail.
     */
    fun tryAsExternalIdentifierDidOpts(opts: ExternalIdentifierOpts): IdkResult<ExternalIdentifierDidOpts, IdkError> {
        return if (isExternalIdentifierDidOpts(opts)) Ok(opts as ExternalIdentifierDidOpts)
        else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "opts is not an instance of ExternalIdentifierDidOpts"))
    }

    /**
     * Safely casts opts to ExternalIdentifierJwkOpts.
     * @return IdkResult containing the casted opts, or an error if the cast would fail.
     */
    fun tryAsExternalIdentifierJwkOpts(opts: ExternalIdentifierOpts): IdkResult<ExternalIdentifierJwkOpts, IdkError> {
        return if (isExternalIdentifierJwkOpts(opts)) Ok(opts as ExternalIdentifierJwkOpts)
        else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "opts is not an instance of ExternalIdentifierJwkOpts"))
    }

    /**
     * Safely casts opts to ExternalIdentifierCoseKeyOpts.
     * @return IdkResult containing the casted opts, or an error if the cast would fail.
     */
    fun tryAsExternalIdentifierCoseKeyOpts(opts: ExternalIdentifierOpts): IdkResult<ExternalIdentifierCoseKeyOpts, IdkError> {
        return if (isExternalIdentifierCoseKeyOpts(opts)) Ok(opts as ExternalIdentifierCoseKeyOpts)
        else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "opts is not an instance of ExternalIdentifierCoseKeyOpts"))
    }

    /**
     * Safely casts opts to ExternalIdentifierOidcDiscoveryOpts.
     * @return IdkResult containing the casted opts, or an error if the cast would fail.
     */
    fun tryAsExternalIdentifierOidcDiscoveryOpts(opts: ExternalIdentifierOpts): IdkResult<ExternalIdentifierOidcDiscoveryOpts, IdkError> {
        return if (isExternalIdentifierOidcDiscoveryOpts(opts)) Ok(opts as ExternalIdentifierOidcDiscoveryOpts)
        else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "opts is not an instance of ExternalIdentifierOidcDiscoveryOpts"))
    }

    /**
     * Safely casts opts to ExternalIdentifierJwksUrlOpts.
     * @return IdkResult containing the casted opts, or an error if the cast would fail.
     */
    fun tryAsExternalIdentifierJwksUrlOpts(opts: ExternalIdentifierOpts): IdkResult<ExternalIdentifierJwksUrlOpts, IdkError> {
        return if (isExternalIdentifierJwksUrlOpts(opts)) Ok(opts as ExternalIdentifierJwksUrlOpts)
        else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "opts is not an instance of ExternalIdentifierJwksUrlOpts"))
    }

    /**
     * Safely casts opts to ExternalIdentifierOIDFEntityIdOpts.
     * @return IdkResult containing the casted opts, or an error if the cast would fail.
     */
    fun tryAsExternalIdentifierOIDFEntityIdOpts(opts: ExternalIdentifierOpts): IdkResult<ExternalIdentifierOIDFEntityIdOpts, IdkError> {
        return if (isExternalIdentifierOIDFEntityIdOpts(opts)) Ok(opts as ExternalIdentifierOIDFEntityIdOpts)
        else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "opts is not an instance of ExternalIdentifierOIDFEntityIdOpts"))
    }

    /**
     * Safely casts opts to ExternalIdentifierX5cOpts.
     * @return IdkResult containing the casted opts, or an error if the cast would fail.
     */
    fun tryAsExternalIdentifierX5cOpts(opts: ExternalIdentifierOpts): IdkResult<ExternalIdentifierX5cOpts, IdkError> {
        return if (isExternalIdentifierX5cOpts(opts)) Ok(opts as ExternalIdentifierX5cOpts)
        else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "opts is not an instance of ExternalIdentifierX5cOpts"))
    }

    /**
     * Safely casts opts to ExternalIdentifierCnfOpts.
     * @return IdkResult containing the casted opts, or an error if the cast would fail.
     */
    fun tryAsExternalIdentifierCnfOpts(opts: ExternalIdentifierOpts): IdkResult<ExternalIdentifierCnfOpts, IdkError> {
        return if (isExternalIdentifierCnfOpts(opts)) Ok(opts as ExternalIdentifierCnfOpts)
        else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "opts is not an instance of ExternalIdentifierCnfOpts"))
    }

    // Throwing variants - for backwards compatibility and cases where type is guaranteed

    /**
     * Casts opts to ExternalIdentifierKidOpts.
     * @throws IllegalArgumentException if opts is not an ExternalIdentifierKidOpts
     */
    fun asExternalIdentifierKidOpts(opts: ExternalIdentifierOpts): ExternalIdentifierKidOpts {
        return tryAsExternalIdentifierKidOpts(opts).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Casts opts to ExternalIdentifierDidOpts.
     * @throws IllegalArgumentException if opts is not an ExternalIdentifierDidOpts
     */
    fun asExternalIdentifierDidOpts(opts: ExternalIdentifierOpts): ExternalIdentifierDidOpts {
        return tryAsExternalIdentifierDidOpts(opts).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Casts opts to ExternalIdentifierJwkOpts.
     * @throws IllegalArgumentException if opts is not an ExternalIdentifierJwkOpts
     */
    fun asExternalIdentifierJwkOpts(opts: ExternalIdentifierOpts): ExternalIdentifierJwkOpts {
        return tryAsExternalIdentifierJwkOpts(opts).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Casts opts to ExternalIdentifierCoseKeyOpts.
     * @throws IllegalArgumentException if opts is not an ExternalIdentifierCoseKeyOpts
     */
    fun asExternalIdentifierCoseKeyOpts(opts: ExternalIdentifierOpts): ExternalIdentifierCoseKeyOpts {
        return tryAsExternalIdentifierCoseKeyOpts(opts).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Casts opts to ExternalIdentifierOidcDiscoveryOpts.
     * @throws IllegalArgumentException if opts is not an ExternalIdentifierOidcDiscoveryOpts
     */
    fun asExternalIdentifierOidcDiscoveryOpts(opts: ExternalIdentifierOpts): ExternalIdentifierOidcDiscoveryOpts {
        return tryAsExternalIdentifierOidcDiscoveryOpts(opts).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Casts opts to ExternalIdentifierJwksUrlOpts.
     * @throws IllegalArgumentException if opts is not an ExternalIdentifierJwksUrlOpts
     */
    fun asExternalIdentifierJwksUrlOpts(opts: ExternalIdentifierOpts): ExternalIdentifierJwksUrlOpts {
        return tryAsExternalIdentifierJwksUrlOpts(opts).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Casts opts to ExternalIdentifierOIDFEntityIdOpts.
     * @throws IllegalArgumentException if opts is not an ExternalIdentifierOIDFEntityIdOpts
     */
    fun asExternalIdentifierOIDFEntityIdOpts(opts: ExternalIdentifierOpts): ExternalIdentifierOIDFEntityIdOpts {
        return tryAsExternalIdentifierOIDFEntityIdOpts(opts).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Casts opts to ExternalIdentifierX5cOpts.
     * @throws IllegalArgumentException if opts is not an ExternalIdentifierX5cOpts
     */
    fun asExternalIdentifierX5cOpts(opts: ExternalIdentifierOpts): ExternalIdentifierX5cOpts {
        return tryAsExternalIdentifierX5cOpts(opts).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Casts opts to ExternalIdentifierCnfOpts.
     * @throws IllegalArgumentException if opts is not an ExternalIdentifierCnfOpts
     */
    fun asExternalIdentifierCnfOpts(opts: ExternalIdentifierOpts): ExternalIdentifierCnfOpts {
        return tryAsExternalIdentifierCnfOpts(opts).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

}


/**
 * Type guard functions for external identifier results
 */
object ExternalIdentifierResultGuards {

    fun isExternalIdentifierDidResult(result: ExternalIdentifierResult): Boolean {
        return result.method == IdentifierMethodDefaults.DID && result is ExternalIdentifierResult.Did
    }

    fun isExternalIdentifierJwkResult(result: ExternalIdentifierResult): Boolean {
        return result.method == IdentifierMethodDefaults.JWK && result is ExternalIdentifierResult.Jwk
    }

    fun isExternalIdentifierCoseKeyResult(result: ExternalIdentifierResult): Boolean {
        return result.method == IdentifierMethodDefaults.COSE_KEY && result is ExternalIdentifierResult.CoseKey
    }

    fun isExternalIdentifierX5cResult(result: ExternalIdentifierResult): Boolean {
        return result.method == IdentifierMethodDefaults.X5C && result is ExternalIdentifierResult.X5c
    }

    fun isExternalIdentifierOidfEntityIdResult(result: ExternalIdentifierResult): Boolean {
        return result.method == IdentifierMethodDefaults.ENTITY_ID && result is ExternalIdentifierResult.OIDFEntityId
    }

    fun isExternalIdentifierOidcResult(result: ExternalIdentifierResult): Boolean {
        // OIDC discovery result type is not yet implemented
        return result.method == IdentifierMethodDefaults.OIDC_DISCOVERY
    }

    fun isExternalIdentifierJwksResult(result: ExternalIdentifierResult): Boolean {
        return result.method == IdentifierMethodDefaults.JWKS_URL && result is ExternalIdentifierResult.JwksUrl
    }

    fun isExternalIdentifierCnfResult(result: ExternalIdentifierResult): Boolean {
        return result.method == IdentifierMethodDefaults.CNF && result is ExternalIdentifierResult.Cnf
    }

    fun asExternalIdentifierDidResult(result: ExternalIdentifierResult): ExternalIdentifierResult.Did {
        return require(isExternalIdentifierDidResult(result)) { "result is not an instance of ExternalIdentifierResult.Did" }.let { result as ExternalIdentifierResult.Did }
    }

    fun asExternalIdentifierJwkResult(result: ExternalIdentifierResult): ExternalIdentifierResult.Jwk {
        return require(isExternalIdentifierJwkResult(result)) { "result is not an instance of ExternalIdentifierResult.Jwk" }.let { result as ExternalIdentifierResult.Jwk }
    }

    fun asExternalIdentifierCoseKeyResult(result: ExternalIdentifierResult): ExternalIdentifierResult.CoseKey {
        return require(isExternalIdentifierCoseKeyResult(result)) { "result is not an instance of ExternalIdentifierResult.CoseKey" }.let { result as ExternalIdentifierResult.CoseKey }
    }

    fun asExternalIdentifierX5cResult(result: ExternalIdentifierResult): ExternalIdentifierResult.X5c {
        return require(isExternalIdentifierX5cResult(result)) { "result is not an instance of ExternalIdentifierResult.X5c" }.let { result as ExternalIdentifierResult.X5c }
    }

    fun asExternalIdentifierOidfEntityIdResult(result: ExternalIdentifierResult): ExternalIdentifierResult.OIDFEntityId {
        return require(isExternalIdentifierOidfEntityIdResult(result)) { "result is not an instance of ExternalIdentifierResult.OIDFEntityId" }.let { result as ExternalIdentifierResult.OIDFEntityId }
    }

    fun asExternalIdentifierOidcResult(result: ExternalIdentifierResult): ExternalIdentifierResult {
        require(isExternalIdentifierOidcResult(result)) { "result is not an OIDC discovery result" }
        // OIDC discovery result type is not yet implemented as a specific subclass.
        // For now, return the result as-is after validating it's an OIDC discovery result.
        return result
    }

    fun asExternalIdentifierJwksUrlResult(result: ExternalIdentifierResult): ExternalIdentifierResult.JwksUrl {
        return require(isExternalIdentifierJwksResult(result)) { "result is not an instance of ExternalIdentifierResult.JwksUrl" }.let { result as ExternalIdentifierResult.JwksUrl }
    }

    fun asExternalIdentifierCnfResult(result: ExternalIdentifierResult): ExternalIdentifierResult.Cnf {
        return require(isExternalIdentifierCnfResult(result)) { "result is not an instance of ExternalIdentifierResult.Cnf" }.let { result as ExternalIdentifierResult.Cnf }
    }

}