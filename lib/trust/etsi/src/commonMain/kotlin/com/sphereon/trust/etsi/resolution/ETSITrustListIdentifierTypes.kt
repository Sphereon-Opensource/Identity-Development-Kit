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
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalJwkInfo
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.etsi.model.ETSILoTE
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Identifier method for ETSI Trust Lists.
 */
object ETSIIdentifierMethods {
    /**
     * ETSI Trust List identifier method (URI to an ETSI TSL)
     */
    val ETSI_TSL =
        object : IIdentifierMethod {
            override val methodName: String = "etsi_tsl"
        }

    /**
     * ETSI Trust Service Provider identifier (identifies a specific TSP within a trust list)
     */
    val ETSI_TSP =
        object : IIdentifierMethod {
            override val methodName: String = "etsi_tsp"
        }
}

/**
 * Options for ETSI Trust List resolution.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ExternalIdentifierETSITslOpts", exact = true)
data class ExternalIdentifierETSITslOpts(
    /**
     * URI of the ETSI Trust List (e.g., https://ec.europa.eu/tools/lotl/eu-lotl.xml)
     */
    override val identifier: String,
    /**
     * Whether to verify the XML signature of the trust list
     */
    val verifySignature: Boolean = true,
    /**
     * Whether to use cached trust list if available
     */
    val useCache: Boolean = true,
    /**
     * Maximum cache age in milliseconds
     */
    val maxCacheAge: Long? = null,
    /**
     * Territory filter (e.g., "EU", "NL", "BE") - if specified, only services from this territory are considered
     */
    val territory: String? = null,
    /**
     * Service type filter (e.g., CA/QC, OCSP) - if specified, only services of this type are considered
     */
    val serviceTypeFilter: List<String>? = null,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : ExternalIdentifierOpts(
        method = ETSIIdentifierMethods.ETSI_TSL,
        identifier = identifier,
        context = context,
        lookup = lookup,
    )

/**
 * Options for ETSI Trust Service Provider resolution.
 * This resolves a specific TSP and its services from a trust list.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ExternalIdentifierETSITspOpts", exact = true)
data class ExternalIdentifierETSITspOpts(
    /**
     * TSP identifier (format: "tsl_uri#tsp_id" or just tsp_id if tslUri is provided separately)
     */
    override val identifier: String,
    /**
     * URI of the trust list containing this TSP
     */
    val tslUri: String? = null,
    /**
     * Certificate to match against TSP services (DER-encoded, Base64)
     */
    val certificateToMatch: String? = null,
    /**
     * Validation time for checking service status
     */
    val validationTime: Instant? = null,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : ExternalIdentifierOpts(
        method = ETSIIdentifierMethods.ETSI_TSP,
        identifier = identifier,
        context = context,
        lookup = lookup,
    )

/**
 * Result of ETSI Trust List resolution.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ExternalIdentifierETSITslResult", exact = true)
data class ExternalIdentifierETSITslResult(
    override val identifierOpts: ExternalIdentifierETSITslOpts,
    /**
     * The parsed ETSI trust list
     */
    val trustList: ETSILoTE,
    /**
     * Trust anchors extracted from the list
     */
    val trustAnchors: List<TrustAnchor>,
    /**
     * JWKs of all trust service provider certificates
     */
    override val jwks: Array<ExternalJwkInfo>,
    /**
     * Key info for the first/primary trust anchor
     */
    override val keyInfo: ResolvedKeyInfoType<JwkType>,
    /**
     * Whether the trust list signature was verified
     */
    val signatureVerified: Boolean,
    /**
     * Metadata about the resolution
     */
    val metadata: ETSITslResolutionMetadata,
) : ExternalIdentifierResult(
        method = ETSIIdentifierMethods.ETSI_TSL,
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
        other as ExternalIdentifierETSITslResult
        if (identifierOpts != other.identifierOpts) {
            return false
        }
        if (trustList != other.trustList) {
            return false
        }
        if (trustAnchors != other.trustAnchors) {
            return false
        }
        if (!jwks.contentEquals(other.jwks)) {
            return false
        }
        if (keyInfo != other.keyInfo) {
            return false
        }
        if (signatureVerified != other.signatureVerified) {
            return false
        }
        if (metadata != other.metadata) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = identifierOpts.hashCode()
        result = 31 * result + trustList.hashCode()
        result = 31 * result + trustAnchors.hashCode()
        result = 31 * result + jwks.contentHashCode()
        result = 31 * result + keyInfo.hashCode()
        result = 31 * result + signatureVerified.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Result of ETSI TSP resolution.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ExternalIdentifierETSITspResult", exact = true)
data class ExternalIdentifierETSITspResult(
    override val identifierOpts: ExternalIdentifierETSITspOpts,
    /**
     * The matched TSP name
     */
    val tspName: String,
    /**
     * The matched service name
     */
    val serviceName: String,
    /**
     * Service status
     */
    val serviceStatus: String,
    /**
     * Service type
     */
    val serviceType: String,
    /**
     * Trust anchor for this TSP/service
     */
    val trustAnchor: TrustAnchor,
    /**
     * Trust status
     */
    val trustStatus: TrustStatus,
    /**
     * JWKs for this TSP's certificates
     */
    override val jwks: Array<ExternalJwkInfo>,
    /**
     * Key info for the service certificate
     */
    override val keyInfo: ResolvedKeyInfoType<JwkType>,
    /**
     * Whether this TSP is trusted
     */
    val trusted: Boolean,
    /**
     * Validation path
     */
    val validationPath: List<String>,
) : ExternalIdentifierResult(
        method = ETSIIdentifierMethods.ETSI_TSP,
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
        other as ExternalIdentifierETSITspResult
        if (identifierOpts != other.identifierOpts) {
            return false
        }
        if (tspName != other.tspName) {
            return false
        }
        if (serviceName != other.serviceName) {
            return false
        }
        if (serviceStatus != other.serviceStatus) {
            return false
        }
        if (serviceType != other.serviceType) {
            return false
        }
        if (trustAnchor != other.trustAnchor) {
            return false
        }
        if (trustStatus != other.trustStatus) {
            return false
        }
        if (!jwks.contentEquals(other.jwks)) {
            return false
        }
        if (keyInfo != other.keyInfo) {
            return false
        }
        if (trusted != other.trusted) {
            return false
        }
        if (validationPath != other.validationPath) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = identifierOpts.hashCode()
        result = 31 * result + tspName.hashCode()
        result = 31 * result + serviceName.hashCode()
        result = 31 * result + serviceStatus.hashCode()
        result = 31 * result + serviceType.hashCode()
        result = 31 * result + trustAnchor.hashCode()
        result = 31 * result + trustStatus.hashCode()
        result = 31 * result + jwks.contentHashCode()
        result = 31 * result + keyInfo.hashCode()
        result = 31 * result + trusted.hashCode()
        result = 31 * result + validationPath.hashCode()
        return result
    }
}

/**
 * Metadata about ETSI TSL resolution.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSITslResolutionMetadata", exact = true)
data class ETSITslResolutionMetadata(
    /**
     * When the trust list was retrieved
     */
    val retrievedAt: Instant,
    /**
     * Whether the data came from cache
     */
    val fromCache: Boolean,
    /**
     * TSL sequence number
     */
    val sequenceNumber: Int,
    /**
     * Next update time
     */
    val nextUpdate: Instant,
    /**
     * Scheme territory
     */
    val territory: String,
    /**
     * Number of TSPs in the list
     */
    val tspCount: Int,
    /**
     * Number of trust anchors extracted
     */
    val trustAnchorCount: Int,
)

/**
 * Type guards for ETSI identifier options.
 */
object ETSIIdentifierOptsGuards {
    fun isETSITslOpts(opts: ExternalIdentifierOpts): Boolean =
        opts is ExternalIdentifierETSITslOpts ||
            opts.method == ETSIIdentifierMethods.ETSI_TSL

    fun isETSITspOpts(opts: ExternalIdentifierOpts): Boolean =
        opts is ExternalIdentifierETSITspOpts ||
            opts.method == ETSIIdentifierMethods.ETSI_TSP

    fun asETSITslOpts(opts: ExternalIdentifierOpts): ExternalIdentifierETSITslOpts =
        require(isETSITslOpts(opts)) {
            "opts is not an ExternalIdentifierETSITslOpts"
        }.let { opts as ExternalIdentifierETSITslOpts }

    fun asETSITspOpts(opts: ExternalIdentifierOpts): ExternalIdentifierETSITspOpts =
        require(isETSITspOpts(opts)) {
            "opts is not an ExternalIdentifierETSITspOpts"
        }.let { opts as ExternalIdentifierETSITspOpts }
}

/**
 * Type guards for ETSI identifier results.
 */
object ETSIIdentifierResultGuards {
    fun isETSITslResult(result: ExternalIdentifierResult): Boolean =
        result is ExternalIdentifierETSITslResult ||
            result.method == ETSIIdentifierMethods.ETSI_TSL

    fun isETSITspResult(result: ExternalIdentifierResult): Boolean =
        result is ExternalIdentifierETSITspResult ||
            result.method == ETSIIdentifierMethods.ETSI_TSP

    fun asETSITslResult(result: ExternalIdentifierResult): ExternalIdentifierETSITslResult =
        require(isETSITslResult(result)) {
            "result is not an ExternalIdentifierETSITslResult"
        }.let { result as ExternalIdentifierETSITslResult }

    fun asETSITspResult(result: ExternalIdentifierResult): ExternalIdentifierETSITspResult =
        require(isETSITspResult(result)) {
            "result is not an ExternalIdentifierETSITspResult"
        }.let { result as ExternalIdentifierETSITspResult }
}
