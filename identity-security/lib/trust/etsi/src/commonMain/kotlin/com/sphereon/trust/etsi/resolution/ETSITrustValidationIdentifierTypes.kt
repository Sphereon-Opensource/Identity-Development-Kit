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

package com.sphereon.trust.etsi.resolution

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalJwkInfo
import com.sphereon.trust.core.model.EntityDiscoveryOptions
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Identifier method for X.509 certificate validation against ETSI trust lists.
 */
object ETSIValidationIdentifierMethod {
    /**
     * X.509 validation against ETSI Trust Lists.
     * This is the primary use case: validate a certificate against appropriate trust list(s).
     */
    val X509_ETSI_VALIDATION =
        object : com.sphereon.crypto.resolution.IIdentifierMethod {
            override val methodName: String = "x509_etsi_validation"
        }
}

/**
 * Options for validating an X.509 certificate against ETSI trust lists.
 *
 * This is the primary identifier resolution use case for ETSI trust validation:
 * - Extract X.509 certificates from KeyInfo (from x5c or convert from key)
 * - Extract country from certificate's C attribute
 * - Navigate LOTL to find appropriate member state trust list
 * - Check if certificate is directly listed OR issued by a CA in the trust list
 * - Return trust validation result
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ExternalIdentifierX509ETSIValidationOpts", exact = true)
data class ExternalIdentifierX509ETSIValidationOpts(
    /**
     * KeyInfo containing the certificate/key to validate.
     *
     * Can contain:
     * - **x5c**: Array of X.509 certificates (DER-encoded, Base64)
     *   - First certificate in x5c will be validated
     *   - Remaining certificates used as chain for CA matching
     * - **key**: JWK or COSE key
     *   - COSE keys automatically converted to JWK
     *   - Public key used to find matching certificate in trust list
     *
     * The KeyInfo abstraction is used everywhere in the codebase and provides:
     * - Automatic COSE to JWK conversion
     * - X.509 certificate chain extraction (x5c)
     * - Key identifier (kid) tracking
     * - Provider and alias information
     * - Consistent interface across all identifier resolution
     */
    override val identifier: KeyInfoType<KeyType>,
    /**
     * LOTL URI (defaults to EU LOTL)
     */
    val lotlUri: String = "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
    /**
     * Explicit trust list URI (if you want to skip LOTL navigation)
     * If provided, this trust list will be used directly instead of
     * extracting country and navigating LOTL
     */
    val explicitTslUri: String? = null,
    /**
     * Validation time (defaults to current time)
     */
    val validationTime: Instant? = null,
    /**
     * Whether to check revocation status
     */
    val checkRevocation: Boolean = true,
    /**
     * Whether to allow validation if certificate is issued by a CA in the trust list
     * (not just exact certificate match)
     */
    val allowCAChainMatch: Boolean = true,
    /**
     * Whether to use cached trust lists
     */
    val useCache: Boolean = true,
    /**
     * Maximum cache age in milliseconds (default: 1 hour)
     */
    val maxCacheAge: Long = 3600000,
    /**
     * Service type filter (e.g., only validate against CA/QC services)
     */
    val serviceTypeFilter: List<String>? = null,
    /**
     * Entity discovery options. When enabled, the result's [ExternalIdentifierX509ETSIValidationResult.trustValidation]
     * will contain discovered entity info for the full trust chain (certs, TSP, TL operator, LOTL operator).
     */
    val entityDiscovery: EntityDiscoveryOptions? = null,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : ExternalIdentifierOpts(
        method = ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION,
        identifier = identifier,
        context = context,
        lookup = lookup,
    )

/**
 * Result of X.509 ETSI trust validation.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ExternalIdentifierX509ETSIValidationResult", exact = true)
data class ExternalIdentifierX509ETSIValidationResult(
    override val identifierOpts: ExternalIdentifierX509ETSIValidationOpts,
    /**
     * Whether the certificate is trusted according to ETSI trust list
     */
    val trusted: Boolean,
    /**
     * Trust status
     */
    val trustStatus: TrustStatus,
    /**
     * The trust anchor that validated this certificate (if trusted)
     */
    val trustAnchor: TrustAnchor?,
    /**
     * Validation path from certificate to trust anchor
     */
    val validationPath: List<String>,
    /**
     * Details about the validation
     */
    val details: String?,
    /**
     * Trust list that was used for validation
     */
    val trustListInfo: TrustListValidationInfo,
    /**
     * Matched TSP information (if found)
     */
    val matchedTSP: MatchedTSPInfo?,
    /**
     * JWKs involved in validation
     */
    override val jwks: Array<ExternalJwkInfo>,
    /**
     * Key info for the validated certificate
     */
    override val keyInfo: ResolvedKeyInfoType<JwkType>,
    /**
     * When the validation was performed
     */
    val validatedAt: Instant,
    /**
     * Standard trust validation result with entity discovery.
     * Populated when [ExternalIdentifierX509ETSIValidationOpts.entityDiscovery] is enabled.
     * Contains [TrustValidationResult.discoveredEntities] with the full trust chain info.
     */
    val trustValidation: TrustValidationResult? = null,
) : ExternalIdentifierResult(
        method = ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION,
        jwks = jwks,
        keyInfo = keyInfo,
        identifierOpts = identifierOpts,
    ) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }
        other as ExternalIdentifierX509ETSIValidationResult
        if (identifierOpts != other.identifierOpts) {
            return false
        }
        if (trusted != other.trusted) {
            return false
        }
        if (trustStatus != other.trustStatus) {
            return false
        }
        if (trustAnchor != other.trustAnchor) {
            return false
        }
        if (validationPath != other.validationPath) {
            return false
        }
        if (details != other.details) {
            return false
        }
        if (trustListInfo != other.trustListInfo) {
            return false
        }
        if (matchedTSP != other.matchedTSP) {
            return false
        }
        if (!jwks.contentEquals(other.jwks)) {
            return false
        }
        if (keyInfo != other.keyInfo) {
            return false
        }
        if (validatedAt != other.validatedAt) {
            return false
        }
        if (trustValidation != other.trustValidation) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = identifierOpts.hashCode()
        result = 31 * result + trusted.hashCode()
        result = 31 * result + trustStatus.hashCode()
        result = 31 * result + (trustAnchor?.hashCode() ?: 0)
        result = 31 * result + validationPath.hashCode()
        result = 31 * result + (details?.hashCode() ?: 0)
        result = 31 * result + trustListInfo.hashCode()
        result = 31 * result + (matchedTSP?.hashCode() ?: 0)
        result = 31 * result + jwks.contentHashCode()
        result = 31 * result + keyInfo.hashCode()
        result = 31 * result + validatedAt.hashCode()
        result = 31 * result + (trustValidation?.hashCode() ?: 0)
        return result
    }
}

/**
 * Information about the trust list used for validation.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustListValidationInfo", exact = true)
data class TrustListValidationInfo(
    /**
     * URI of the trust list used
     */
    val tslUri: String,
    /**
     * Territory/country of the trust list
     */
    val territory: String,
    /**
     * Scheme name
     */
    val schemeName: String,
    /**
     * TSL sequence number
     */
    val sequenceNumber: Int,
    /**
     * When the trust list was issued
     */
    val listIssueDateTime: Instant,
    /**
     * When the trust list should be updated
     */
    val nextUpdate: Instant,
    /**
     * Whether the trust list was loaded from cache
     */
    val fromCache: Boolean,
    /**
     * How the trust list was determined
     */
    val determinationMethod: TslDeterminationMethod,
)

/**
 * How the appropriate trust list was determined.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("TslDeterminationMethod", exact = true)
enum class TslDeterminationMethod {
    /**
     * Trust list was explicitly provided in options
     */
    EXPLICIT,

    /**
     * Trust list was determined by extracting country from certificate and navigating LOTL
     */
    LOTL_NAVIGATION,

    /**
     * Trust list was determined by some other method
     */
    OTHER,
}

/**
 * Information about the matched TSP in the trust list.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("MatchedTSPInfo", exact = true)
data class MatchedTSPInfo(
    /**
     * TSP name
     */
    val tspName: String,
    /**
     * TSP identifier
     */
    val tspIdentifier: String?,
    /**
     * Service name
     */
    val serviceName: String,
    /**
     * Service type identifier (URI)
     */
    val serviceTypeIdentifier: String,
    /**
     * Service status
     */
    val serviceStatus: String,
    /**
     * When the service status started
     */
    val statusStartingTime: Instant,
    /**
     * How the match was found
     */
    val matchType: TspMatchType,
    /**
     * If matched via CA chain, the chain from certificate to trust anchor
     */
    val caChain: List<String>? = null,
)

/**
 * How the certificate matched the TSP.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("TspMatchType", exact = true)
enum class TspMatchType {
    /**
     * Certificate exactly matches a service digital identity in the trust list
     */
    DIRECT_MATCH,

    /**
     * Certificate was issued by a CA that is in the trust list
     */
    CA_CHAIN_MATCH,

    /**
     * Certificate subject matches a TSP
     */
    SUBJECT_MATCH,
}

/**
 * Type guards for X.509 ETSI validation options.
 */
object X509ETSIValidationOptsGuards {
    fun isX509ETSIValidationOpts(opts: ExternalIdentifierOpts): Boolean =
        opts is ExternalIdentifierX509ETSIValidationOpts ||
            opts.method == ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION

    fun asX509ETSIValidationOpts(opts: ExternalIdentifierOpts): ExternalIdentifierX509ETSIValidationOpts =
        require(isX509ETSIValidationOpts(opts)) {
            "opts is not an ExternalIdentifierX509ETSIValidationOpts"
        }.let { opts as ExternalIdentifierX509ETSIValidationOpts }
}

/**
 * Type guards for X.509 ETSI validation results.
 */
object X509ETSIValidationResultGuards {
    fun isX509ETSIValidationResult(result: ExternalIdentifierResult): Boolean =
        result is ExternalIdentifierX509ETSIValidationResult ||
            result.method == ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION

    fun asX509ETSIValidationResult(result: ExternalIdentifierResult): ExternalIdentifierX509ETSIValidationResult =
        require(isX509ETSIValidationResult(result)) {
            "result is not an ExternalIdentifierX509ETSIValidationResult"
        }.let { result as ExternalIdentifierX509ETSIValidationResult }
}
