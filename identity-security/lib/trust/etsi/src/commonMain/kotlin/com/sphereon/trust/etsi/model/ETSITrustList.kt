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

package com.sphereon.trust.etsi.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.trust.etsi.lote.model.MultiLangString
import com.sphereon.trust.etsi.lote.model.MultiLangURI
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Represents an ETSI TS 119 612 Trust Service Status List (TSL),
 * using ETSI TS 119 602 LoTE terminology.
 *
 * Based on ETSI TS 119 612 V2.4.1 specification (August 2024),
 * renamed to 602 terminology per TS 119 602 Table A.1.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSILoTE", exact = true)
data class ETSILoTE(
    /**
     * LoTE version identifier (was TSLVersionIdentifier)
     */
    val versionIdentifier: Int = 5,
    /**
     * Sequence number of this LoTE
     */
    val sequenceNumber: Int,
    /**
     * Type of the LoTE (e.g., "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric")
     */
    val type: String,
    /**
     * Scheme operator name (multilingual)
     */
    val schemeOperatorName: List<MultiLangString>,
    /**
     * Scheme operator address (612 clause 5.3.5 / 602 clause 6.3.5)
     */
    val schemeOperatorAddress: ETSIOperatorAddress? = null,
    /**
     * Scheme name (multilingual)
     */
    val schemeName: List<MultiLangString> = emptyList(),
    /**
     * Scheme information URI (multilingual)
     */
    val schemeInformationURI: List<MultiLangURI> = emptyList(),
    /**
     * Status determination approach
     */
    val statusDeterminationApproach: String,
    /**
     * Scheme territory (e.g., "EU", "NL", "BE")
     */
    val schemeTerritory: String,
    /**
     * List issue date and time
     */
    val listIssueDateTime: Instant,
    /**
     * Next update date and time
     */
    val nextUpdate: Instant,
    /**
     * Distribution points (URIs where this LoTE can be obtained)
     */
    val distributionPoints: List<String> = emptyList(),
    /**
     * Scheme type/community rules (multilingual URIs)
     */
    val schemeTypeCommunityRules: List<MultiLangURI> = emptyList(),
    /**
     * Policy or legal notice (multilingual)
     */
    val policyOrLegalNotice: List<MultiLangString> = emptyList(),
    /**
     * Historical information period in days
     */
    val historicalInformationPeriod: Int? = null,
    /**
     * Trusted entities in this list (was trustServiceProviders)
     */
    val trustedEntities: List<ETSITrustedEntity>,
    /**
     * Signature value (Base64-encoded)
     */
    val signature: String? = null,
    /**
     * Pointers to other LoTEs (was pointersToOtherTSL)
     */
    val pointersToOtherLoTE: List<ETSIOtherLoTEPointer> = emptyList(),
)

/**
 * Represents a Trusted Entity in an ETSI LoTE (was TrustServiceProvider).
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSITrustedEntity", exact = true)
data class ETSITrustedEntity(
    /**
     * Trusted entity information (was tspInformation)
     */
    val trustedEntityInformation: ETSITrustedEntityInformation,
    /**
     * Services provided by this trusted entity (was tspServices)
     */
    val trustedEntityServices: List<ETSITrustedEntityService>,
)

/**
 * Information about a Trusted Entity (was TSPInformation).
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSITrustedEntityInformation", exact = true)
data class ETSITrustedEntityInformation(
    /**
     * Trusted entity name (multilingual, was tspName)
     */
    val name: List<MultiLangString>,
    /**
     * Trusted entity trade name (multilingual, was tspTradeName)
     */
    val tradeName: List<MultiLangString> = emptyList(),
    /**
     * Trusted entity address (was tspAddress)
     */
    val address: ETSIOperatorAddress,
    /**
     * Trusted entity information URI (multilingual, was tspInformationURI)
     */
    val informationURI: List<MultiLangURI> = emptyList(),
    /**
     * Trusted entity identifier (was tspIdentifier)
     */
    val identifier: String? = null,
)

/**
 * Operator address containing postal and electronic addresses (was TSPAddress).
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSIOperatorAddress", exact = true)
data class ETSIOperatorAddress(
    /**
     * Postal addresses
     */
    val postalAddresses: List<PostalAddress>,
    /**
     * Electronic addresses (email, URIs)
     */
    val electronicAddresses: List<String> = emptyList(),
)

/**
 * Postal address.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("PostalAddress", exact = true)
data class PostalAddress(
    val streetAddress: String,
    val locality: String,
    val stateOrProvince: String? = null,
    val postalCode: String? = null,
    val countryName: String,
)

/**
 * Represents a service provided by a Trusted Entity (was TSPService).
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSITrustedEntityService", exact = true)
data class ETSITrustedEntityService(
    /**
     * Service information
     */
    val serviceInformation: ETSIServiceInformation,
    /**
     * Service history (status changes over time)
     */
    val serviceHistory: List<ETSIServiceInformation> = emptyList(),
)

/**
 * Information about a trust service (was ServiceInformation).
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSIServiceInformation", exact = true)
data class ETSIServiceInformation(
    /**
     * Service type identifier (URI)
     */
    val serviceTypeIdentifier: String,
    /**
     * Service name (multilingual)
     */
    val serviceName: List<MultiLangString>,
    /**
     * Digital identity (X.509 certificates, was singular)
     */
    val serviceDigitalIdentity: ETSIServiceDigitalIdentity,
    /**
     * Current status of the service
     */
    val serviceStatus: String,
    /**
     * Status starting time
     */
    val statusStartingTime: Instant,
    /**
     * Service supply points with optional type attributes
     */
    val serviceSupplyPoints: List<ServiceSupplyPoint> = emptyList(),
    /**
     * Scheme service definition URI (multilingual)
     */
    val schemeServiceDefinitionURI: List<MultiLangURI> = emptyList(),
    /**
     * TSP service definition URI (multilingual)
     */
    val tspServiceDefinitionURI: List<MultiLangURI> = emptyList(),
    /**
     * Additional service information elements
     */
    val additionalServiceInformation: List<AdditionalServiceInformation> = emptyList(),
    /**
     * Expired certificates revocation information date/time
     */
    val expiredCertsRevocationInfo: Instant? = null,
)

/**
 * Service supply point with optional type attribute.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ServiceSupplyPoint", exact = true)
data class ServiceSupplyPoint(
    /**
     * URI of the service supply point
     */
    val uri: String,
    /**
     * Optional type attribute specifying the kind of service at this URI
     */
    val type: String? = null,
)

/**
 * Additional service information element.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("AdditionalServiceInformation", exact = true)
data class AdditionalServiceInformation(
    /**
     * URI providing additional information (multilingual)
     */
    val uri: List<MultiLangURI>,
    /**
     * Optional information value associated with the URI
     */
    val informationValue: String? = null,
    /**
     * Other arbitrary information (multilingual)
     */
    val otherInformation: List<MultiLangString> = emptyList(),
)

/**
 * Digital identity of a service (was ServiceDigitalIdentity).
 * Now supports plural certificates and X.509 SKIs per 602 terminology.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSIServiceDigitalIdentity", exact = true)
data class ETSIServiceDigitalIdentity(
    /**
     * X.509 certificates (DER-encoded, Base64). Was singular x509Certificate.
     */
    val x509Certificates: List<String> = emptyList(),
    /**
     * Subject name
     */
    val subjectName: String? = null,
    /**
     * X.509 Subject Key Identifiers (Base64-encoded). Was singular x509SKI.
     */
    val x509SKIs: List<String> = emptyList(),
)

/**
 * Pointer to another LoTE (was OtherTSLPointer).
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSIOtherLoTEPointer", exact = true)
data class ETSIOtherLoTEPointer(
    /**
     * Scheme operator name (multilingual)
     */
    val schemeOperatorName: List<MultiLangString>,
    /**
     * Scheme territory
     */
    val schemeTerritory: String,
    /**
     * LoTE location URI (was tslLocation)
     */
    val location: String,
    /**
     * Service digital identities for the pointed LoTE
     */
    val serviceDigitalIdentities: List<ETSIServiceDigitalIdentity> = emptyList(),
    /**
     * Additional information
     */
    val additionalInformation: ETSIAdditionalInformation? = null,
)

/**
 * Additional information for OtherLoTEPointer (was AdditionalInformation).
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSIAdditionalInformation", exact = true)
data class ETSIAdditionalInformation(
    /**
     * Textual information (multilingual)
     */
    val textualInformation: List<MultiLangString> = emptyList(),
    /**
     * Other information
     */
    val otherInformation: List<String> = emptyList(),
    /**
     * MIME type for digital identity data
     */
    val mimeType: String? = null,
)

/**
 * ETSI TSL service types (TS 119 612 + 602).
 */
object ETSIServiceType {
    // 612 types
    const val CA_QC = "http://uri.etsi.org/TrstSvc/Svctype/CA/QC"
    const val CA_PKC = "http://uri.etsi.org/TrstSvc/Svctype/CA/PKC"
    const val OCSP = "http://uri.etsi.org/TrstSvc/Svctype/Certstatus/OCSP"
    const val OCSP_QC = "http://uri.etsi.org/TrstSvc/Svctype/Certstatus/OCSP/QC"
    const val CRL = "http://uri.etsi.org/TrstSvc/Svctype/Certstatus/CRL"
    const val CRL_QC = "http://uri.etsi.org/TrstSvc/Svctype/Certstatus/CRL/QC"
    const val TSA = "http://uri.etsi.org/TrstSvc/Svctype/TSA"
    const val TSA_QTST = "http://uri.etsi.org/TrstSvc/Svctype/TSA/QTST"

    // 602 types
    const val PID_ISSUANCE = "http://uri.etsi.org/19602/SvcType/PID/Issuance"
    const val PID_REVOCATION = "http://uri.etsi.org/19602/SvcType/PID/Revocation"
    const val WALLET_ISSUANCE = "http://uri.etsi.org/19602/SvcType/WalletSolution/Issuance"
    const val WALLET_REVOCATION = "http://uri.etsi.org/19602/SvcType/WalletSolution/Revocation"
}

/**
 * ETSI TSL service status values (612 + 602).
 */
object ETSIServiceStatus {
    // 612 statuses
    const val GRANTED = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted"
    const val WITHDRAWN = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/withdrawn"
    const val SET_BY_NATIONAL_LAW = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/setbynationallaw"
    const val DEPRECATED_NATIONAL_LEVEL = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/deprecatedatnationallevel"
    const val RECOGNISED_NATIONAL_LEVEL = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/recognisedatnationallevel"
    const val DEPRECATED_EU_LEVEL = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/deprecatedateulevelandremovedFromEUTL"
    const val SUSPENDED = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/suspended"
    const val REVOKED = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/revoked"

    // 602 statuses
    const val NOTIFIED = "http://uri.etsi.org/19602/PubEAAProvidersList/SvcStatus/notified"
    const val WITHDRAWN_602 = "http://uri.etsi.org/19602/PubEAAProvidersList/SvcStatus/withdrawn"
}
