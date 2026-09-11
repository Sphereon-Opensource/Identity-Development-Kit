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
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.etsi.lote.model.EidasRole
import kotlin.time.Instant

/**
 * How to match a certificate against trust list entries.
 */
@JsExportCompat
enum class MatchStrategy {
    /** Only exact certificate match */
    DIRECT_ONLY,

    /** Only CA chain match (e.g., RP verification) */
    CA_CHAIN_ONLY,

    /** Try direct first, fall back to CA chain */
    DIRECT_THEN_CA_CHAIN,
}

/**
 * Request to verify whether a certificate is authorized for a specific eIDAS role.
 */
@JsExportCompat
data class RoleVerificationRequest(
    /** KeyInfo containing x5c with the certificate to verify */
    val certificate: KeyInfoType<KeyType>,
    /** The eIDAS role to verify */
    val role: EidasRole,
    /** LOTL URI (defaults to EU LOTL) */
    val lotlUri: String = DEFAULT_EU_LOTL,
    /** Optional territory filter (e.g., "NL") */
    val territory: String? = null,
    /** Certificate matching strategy */
    val matchStrategy: MatchStrategy = MatchStrategy.DIRECT_THEN_CA_CHAIN,
    /** Whether to use cached trust lists */
    val useCache: Boolean = true,
    /** Maximum cache age in milliseconds */
    val maxCacheAge: Long = 3600000,
    /** Explicitly configured signer roots used when resolving the trust lists. */
    val trustedSignerRoots: List<ByteArray>? = null,
)

/**
 * Result of verifying a certificate against an eIDAS role.
 */
@JsExportCompat
data class RoleVerificationResult(
    /** Whether the certificate is verified for the role */
    val verified: Boolean,
    /** The role that was checked */
    val role: EidasRole,
    /** Trust status derived from the service status */
    val trustStatus: TrustStatus,
    /** Information about the matched entity (null if not found) */
    val matchedEntity: MatchedEntityInfo?,
    /** Trust list metadata (null if trust list could not be resolved) */
    val trustListInfo: TrustListValidationInfo?,
    /** Human-readable details */
    val details: String?,
    /** When the verification was performed */
    val verifiedAt: Instant,
    /** Stable machine-readable reasons for a fail-closed decision. */
    val reasonCodes: List<String> = emptyList(),
)

/**
 * Information about the entity that matched the certificate in the trust list.
 */
@JsExportCompat
data class MatchedEntityInfo(
    /** Entity name */
    val entityName: String,
    /** Entity identifier (if available) */
    val entityIdentifier: String?,
    /** Service name */
    val serviceName: String,
    /** Service type URI */
    val serviceType: String,
    /** Service status URI */
    val serviceStatus: String,
    /** When the service status became effective */
    val statusStartingTime: Instant,
    /** How the match was found */
    val matchType: TspMatchType,
    /** Territory of the trust list where the match was found */
    val territory: String?,
)

/**
 * Request to discover all eIDAS roles a certificate is authorized for.
 */
@JsExportCompat
data class RoleDiscoveryRequest(
    /** KeyInfo containing x5c with the certificate to check */
    val certificate: KeyInfoType<KeyType>,
    /** LOTL URI (defaults to EU LOTL) */
    val lotlUri: String = DEFAULT_EU_LOTL,
    /** Optional territory filter */
    val territory: String? = null,
    /** Certificate matching strategy */
    val matchStrategy: MatchStrategy = MatchStrategy.DIRECT_THEN_CA_CHAIN,
    /** Whether to use cached trust lists */
    val useCache: Boolean = true,
    /** Maximum cache age in milliseconds */
    val maxCacheAge: Long = 3600000,
    /** Explicitly configured signer roots used when resolving the trust lists. */
    val trustedSignerRoots: List<ByteArray>? = null,
)

/**
 * Result of discovering all eIDAS roles for a certificate.
 */
@JsExportCompat
data class RoleDiscoveryResult(
    /** Verification results for each role that was checked */
    val verifiedRoles: List<RoleVerificationResult>,
    /** When the discovery was performed */
    val discoveredAt: Instant,
)

internal const val DEFAULT_EU_LOTL = "https://ec.europa.eu/tools/lotl/eu-lotl.xml"
