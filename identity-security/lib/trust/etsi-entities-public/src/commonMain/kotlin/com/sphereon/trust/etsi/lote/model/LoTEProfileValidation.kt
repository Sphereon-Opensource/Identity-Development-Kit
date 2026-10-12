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

package com.sphereon.trust.etsi.lote.model

import com.sphereon.trust.etsi.lote.serialization.LoTEJson
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/** Supported TS 119 602 V1.1.1 profile gate, separate from signatures and admission. */
object LoTEProfileValidation {
    private data class Scheme(val status: String, val rules: String)
    private const val URI_BASE = "http://uri.etsi.org/19602/"
    private val schemes = mapOf(
        EidasRole.PID_PROVIDER to Scheme("PIDProvidersList", "PIDProviders"),
        EidasRole.WALLET_PROVIDER to Scheme("WalletProvidersList", "WalletProvidersList"),
        EidasRole.ACCESS_CA to Scheme("WRPACProvidersList", "WRPACProvidersList"),
        // V1.1.1 C.2.2 and G.1 both print this spelling; do not silently normalize it.
        EidasRole.REGISTRATION_CERTIFICATE_PROVIDER to Scheme("WRPRCrovidersList", "WRPRCProvidersList"),
        EidasRole.PUB_EAA_PROVIDER to Scheme("PubEAAProvidersList", "PubEAAProvidersList"),
        EidasRole.REGISTRAR_AND_REGISTER to Scheme("RegistrarsAndRegistersList", "RegistrarsAndRegistersList"),
    )
    fun validate(list: LoTE, expectedType: String, at: Instant? = null) {
        LoTEJson.encode(list)
        val info = list.listAndSchemeInformation
        require(info.type == expectedType) { "Unexpected LoTE profile" }
        val role = requireNotNull(EidasRole.fromLoTEType(expectedType)) { "Unsupported LoTE profile" }
        val scheme = requireNotNull(schemes[role]) { "Not a published TS 119 602 profile" }
        require(info.versionIdentifier == 1 && info.sequenceNumber > 0) { "Unsupported version or nonpositive sequence" }
        require(info.nextUpdate > info.listIssueDateTime) { "NextUpdate must follow ListIssueDateTime" }
        require(info.nextUpdate <= info.listIssueDateTime.plus(6, DateTimeUnit.MONTH, TimeZone.UTC)) { "Validity exceeds six calendar months" }
        if (at != null) require(info.listIssueDateTime <= at && at < info.nextUpdate) { "LoTE is not current" }
        require(info.schemeTerritory == "EU") { "Published EU profile requires EU scheme territory" }
        require(info.schemeOperatorAddress != null && info.schemeName.isNotEmpty() && info.schemeInformationURI.isNotEmpty() && info.policyOrLegalNotice.isNotEmpty()) { "Explicit scheme information is incomplete" }
        require(info.statusDeterminationApproach == URI_BASE + scheme.status + "/StatusDetn/EU") { "Incorrect published status determination URI" }
        require(info.schemeTypeCommunityRules.isNotEmpty() && info.schemeTypeCommunityRules.all { it.uriValue == URI_BASE + scheme.rules + "/schemerules/EU" }) { "Incorrect published scheme rules URI" }
        extensions(info.schemeExtensions)
        info.pointersToOtherLoTE.forEach { pointer -> pointer.serviceDigitalIdentities.forEach(::identity) }
        val pubEaa = role == EidasRole.PUB_EAA_PROVIDER
        if (pubEaa) {
            require(info.historicalInformationPeriod == 65535) { "Pub-EAA requires permanent history" }
            require(info.pointersToOtherLoTE.isEmpty()) { "Pub-EAA prohibits LoTE pointers" }
        } else {
            require(info.historicalInformationPeriod == null) { "Membership profiles prohibit historical information period" }
            // Structural validation requires nonempty qualifiers with type, operator name and MIME.
            // Territory and community rules are optional. The crypto consumer additionally binds
            // the digital identity to the authenticated signer; a mirror may have a different URL.
            require(info.pointersToOtherLoTE.any { pointer ->
                absoluteUri(pointer.location) && pointer.serviceDigitalIdentities.isNotEmpty() &&
                    pointer.qualifiers.all { qualifier ->
                        (qualifier.loTEType == null || qualifier.loTEType == expectedType) &&
                            (qualifier.schemeTerritory == null || qualifier.schemeTerritory == "EU") &&
                            (qualifier.schemeOperatorName.isEmpty() || qualifier.schemeOperatorName == info.schemeOperatorName) &&
                            (qualifier.schemeTypeCommunityRules.isEmpty() || qualifier.schemeTypeCommunityRules == info.schemeTypeCommunityRules)
                    }
            }) { "Membership profile requires an identified self-pointer" }
        }
        val types = setOfNotNull(role.issuanceServiceType, role.revocationServiceType)
        val walletServiceIdentifiers = mutableSetOf<String>()
        list.trustedEntitiesList.forEach { entity ->
            extensions(entity.trustedEntityInformation.informationExtensions)
            if (pubEaa) require(entity.trustedEntityInformation.tradeName.any {
                Regex("^OJ:[A-Z]{2}.+$").matches(it.value) && absoluteUri(it.value)
            }) { "Pub-EAA requires an OJ legal-basis URI in TETradeName" }
            entity.trustedEntityServices.forEach { service ->
                val current = service.serviceInformation
                require(current.serviceTypeIdentifier in types) { "Service type is outside the LoTE profile" }
                validateServiceExtensions(current.serviceInformationExtensions, role == EidasRole.WALLET_PROVIDER)
                if (role == EidasRole.WALLET_PROVIDER) {
                    require(walletServiceIdentifiers.add(requireNotNull(serviceUniqueIdentifier(current.serviceInformationExtensions.single())))) { "Wallet service identifier is not unique" }
                }
                identity(current.serviceDigitalIdentity)
                if (!pubEaa) {
                    require(current.serviceStatus == null && current.statusStartingTime == null && service.serviceHistory.isEmpty()) {
                        "Membership profiles prohibit service status, starting time and history"
                    }
                } else {
                    status(current.serviceStatus)
                    val start = requireNotNull(current.statusStartingTime) { "Pub-EAA status time is required" }
                    // Continuing status may predate reissuance. New-status backdating requires
                    // authenticated prior state, not a universal single-document inequality.
                    val historyTimes = service.serviceHistory.map { historic ->
                        status(historic.serviceStatus)
                        require(historic.statusStartingTime < start) { "History must precede the current status" }
                        require(historic.serviceTypeIdentifier == null || historic.serviceTypeIdentifier in types) { "Historical service type is outside the profile" }
                        extensions(historic.serviceInformationExtensions)
                        require(historic.serviceDigitalIdentity.x509SKIs.isNotEmpty() && historic.serviceDigitalIdentity.x509Certificates.isEmpty()) { "Pub-EAA history requires X509SKI and prohibits certificates" }
                        historic.statusStartingTime
                    }
                    require(historyTimes.zipWithNext().all { (newer, older) -> newer > older }) { "History must be strictly descending" }
                }
            }
        }
    }

    private fun status(value: String?) {
        require(value == LoTEServiceStatus.NOTIFIED || value == LoTEServiceStatus.WITHDRAWN) { "Unsupported Pub-EAA status" }
    }

    private fun identity(value: LoTEServiceDigitalIdentity) {
        // The current certificate matcher implements the default DER/base64 form.
        // Explicit representations need a verified decoder before they can authorize.
        require(value.x509Certificates.all { it.encoding == null && it.specRef == null }) {
            "Unsupported explicit certificate encoding or specification reference"
        }
    }

    /** The caller must supply the corresponding service from a previously authenticated list. */
    fun validateStatusTransition(previous: LoTEServiceInformation, current: LoTEServiceInformation, issueTime: Instant) {
        status(previous.serviceStatus)
        status(current.serviceStatus)
        val start = requireNotNull(current.statusStartingTime)
        requireNotNull(previous.statusStartingTime)
        if (previous.serviceStatus != current.serviceStatus || previous.statusStartingTime != start) {
            require(start >= issueTime) { "New Pub-EAA status must not be backdated before its introducing publication" }
        }
    }

    /** Runtime eligibility, separate from structural validation and historical inspection. */
    fun isStatusEffective(service: LoTEServiceInformation, at: Instant): Boolean = service.statusStartingTime?.let { it <= at } ?: false

    /**
     * Narrow supported JSON binding of the pinned 1960201_json_schema_sie.json named definition.
     * That schema does not define its array-item encoding or a criticality envelope. This exact
     * one-key form is our supported interpretation, not a universal interoperability claim.
     */
    fun serviceUniqueIdentifier(value: JsonElement): String? {
        val objectValue = value as? JsonObject ?: return null
        if (objectValue.keys != setOf("ServiceUniqueIdentifier")) return null
        val identifier = objectValue["ServiceUniqueIdentifier"] as? JsonPrimitive ?: return null
        return identifier.content.takeIf { identifier.isString && absoluteUri(it) }
    }

    fun validateServiceExtensions(values: List<JsonElement>, walletProfile: Boolean) {
        if (walletProfile) {
            require(values.size == 1 && serviceUniqueIdentifier(values.single()) != null) { "Wallet profile requires one supported ServiceUniqueIdentifier extension" }
        } else extensions(values)
    }

    private fun extensions(values: List<JsonElement>) {
        require(values.isEmpty()) { "Unsupported LoTE extension; no recognized criticality envelope" }
    }

    private fun absoluteUri(value: String): Boolean =
        Regex("^[A-Za-z][A-Za-z0-9+.-]*:[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$").matches(value) &&
            !Regex("%(?![A-Fa-f0-9]{2})").containsMatchIn(value)
}
