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

package com.sphereon.trust.etsi.lote.serialization

import kotlinx.serialization.json.*
import kotlin.time.Instant

/** Structural binding of TS 119 602 V1.1.1; profile policy and signatures are separate.
 * Source: https://forge.etsi.org/rep/esi/x19_60201_lists_of_trusted_entities/-/blob/e84f427f0cde99513b574ef4b5a155ac4a38eab6/1960201_json_schema/1960201_json_schema.json
 * The misplaced additionalProperties under ServiceDigitalIdentity.properties is treated
 * as a keyword, not a field. PublicKeyValue remains an extensible JWK object.
 */
internal object LoTEJsonStructure {
    private data class Rule(
        val type: String,
        val fields: Map<String, String> = emptyMap(),
        val required: Set<String> = emptySet(),
        val item: String? = null,
        val min: Int = 0,
        val max: Int = Int.MAX_VALUE,
        val format: String = "",
    )
    private val rules = mapOf(
        "Document" to Rule("object", fields = mapOf("LoTE" to "LoTE"), required = setOf("LoTE")),
        "LoTE" to Rule("object", fields = mapOf("ListAndSchemeInformation" to "ListAndSchemeInformation", "TrustedEntitiesList" to "TrustedEntitiesList"), required = setOf("ListAndSchemeInformation")),
        "ListAndSchemeInformation" to Rule("object", fields = mapOf("LoTEVersionIdentifier" to "LoTEVersionIdentifier", "LoTESequenceNumber" to "LoTESequenceNumber", "LoTEType" to "LoTEType", "SchemeOperatorName" to "SchemeOperatorName", "SchemeOperatorAddress" to "SchemeOperatorAddress", "SchemeName" to "SchemeName", "SchemeInformationURI" to "SchemeInformationURI", "StatusDeterminationApproach" to "StatusDeterminationApproach", "SchemeTypeCommunityRules" to "SchemeTypeCommunityRules", "SchemeTerritory" to "SchemeTerritory", "PolicyOrLegalNotice" to "PolicyOrLegalNotice", "HistoricalInformationPeriod" to "HistoricalInformationPeriod", "PointersToOtherLoTE" to "PointersToOtherLoTE", "ListIssueDateTime" to "ListIssueDateTime", "NextUpdate" to "NextUpdate", "DistributionPoints" to "DistributionPoints", "SchemeExtensions" to "SchemeExtensions"), required = setOf("LoTEVersionIdentifier", "LoTESequenceNumber", "SchemeOperatorName", "ListIssueDateTime", "NextUpdate")),
        "LoTEVersionIdentifier" to Rule("integer"),
        "LoTESequenceNumber" to Rule("integer"),
        "LoTEType" to Rule("string", format = "uri"),
        "multiLangString_lang" to Rule("string", min = 0, format = ""),
        "multiLangString_value" to Rule("string", min = 0, format = ""),
        "multiLangString" to Rule("object", fields = mapOf("lang" to "multiLangString_lang", "value" to "multiLangString_value"), required = setOf("lang", "value")),
        "NonEmptyMultiLangURI_lang" to Rule("string", min = 0, format = ""),
        "NonEmptyMultiLangURI_uriValue" to Rule("string", min = 1, format = "uri"),
        "NonEmptyMultiLangURI" to Rule("object", fields = mapOf("lang" to "NonEmptyMultiLangURI_lang", "uriValue" to "NonEmptyMultiLangURI_uriValue"), required = setOf("lang", "uriValue")),
        "SchemeOperatorName" to Rule("array", item = "multiLangString", min = 1),
        "SchemeOperatorAddress" to Rule("object", fields = mapOf("SchemeOperatorPostalAddress" to "PostalAddresses", "SchemeOperatorElectronicAddress" to "ElectronicAddress"), required = setOf("SchemeOperatorPostalAddress", "SchemeOperatorElectronicAddress")),
        "PostalAddresses" to Rule("array", item = "PostalAddress", min = 1),
        "PostalAddress_lang" to Rule("string", min = 0, format = ""),
        "PostalAddress_StreetAddress" to Rule("string", min = 0, format = ""),
        "PostalAddress_Locality" to Rule("string", min = 0, format = ""),
        "PostalAddress_StateOrProvince" to Rule("string", min = 0, format = ""),
        "PostalAddress_PostalCode" to Rule("string", min = 0, format = ""),
        "PostalAddress_Country" to Rule("string", min = 0, format = ""),
        "PostalAddress" to Rule("object", fields = mapOf("lang" to "PostalAddress_lang", "StreetAddress" to "PostalAddress_StreetAddress", "Locality" to "PostalAddress_Locality", "StateOrProvince" to "PostalAddress_StateOrProvince", "PostalCode" to "PostalAddress_PostalCode", "Country" to "PostalAddress_Country"), required = setOf("lang", "StreetAddress", "Country")),
        "ElectronicAddress" to Rule("array", item = "NonEmptyMultiLangURI", min = 1),
        "SchemeName" to Rule("array", item = "multiLangString", min = 1),
        "SchemeInformationURI" to Rule("array", item = "NonEmptyMultiLangURI", min = 1),
        "StatusDeterminationApproach" to Rule("string", format = "uri"),
        "SchemeTypeCommunityRules" to Rule("array", item = "NonEmptyMultiLangURI", min = 1),
        "SchemeTerritory" to Rule("string", min = 2, max = 2),
        "PolicyOrLegalNotice" to Rule("array", min = 1),
        "HistoricalInformationPeriod" to Rule("integer"),
        "PointersToOtherLoTE" to Rule("array", item = "OtherLoTEPointer", min = 1),
        "OtherLoTEPointer_LoTELocation" to Rule("string", min = 0, format = "uri"),
        "OtherLoTEPointer_ServiceDigitalIdentities" to Rule("array", item = "ServiceDigitalIdentity", min = 1),
        "OtherLoTEPointer_LoTEQualifiers" to Rule("array", item = "LoTEQualifier", min = 1),
        "OtherLoTEPointer" to Rule("object", fields = mapOf("LoTELocation" to "OtherLoTEPointer_LoTELocation", "ServiceDigitalIdentities" to "OtherLoTEPointer_ServiceDigitalIdentities", "LoTEQualifiers" to "OtherLoTEPointer_LoTEQualifiers"), required = setOf("LoTELocation", "ServiceDigitalIdentities", "LoTEQualifiers")),
        "ServiceDigitalIdentity_X509Certificates" to Rule("array", item = "pkiOb", min = 1),
        "ServiceDigitalIdentity_X509SubjectNames" to Rule("array", item = "X509SubjectName", min = 1),
        "ServiceDigitalIdentity_PublicKeyValues" to Rule("array", item = "PublicKeyValue", min = 1),
        "ServiceDigitalIdentity_X509SKIs" to Rule("array", item = "X509SKI", min = 1),
        "ServiceDigitalIdentity_OtherIds" to Rule("array", item = "OtherId", min = 1),
        "ServiceDigitalIdentity" to Rule("object", fields = mapOf("X509Certificates" to "ServiceDigitalIdentity_X509Certificates", "X509SubjectNames" to "ServiceDigitalIdentity_X509SubjectNames", "PublicKeyValues" to "ServiceDigitalIdentity_PublicKeyValues", "X509SKIs" to "ServiceDigitalIdentity_X509SKIs", "OtherIds" to "ServiceDigitalIdentity_OtherIds")),
        "pkiOb_encoding" to Rule("string", min = 0, format = "uri"),
        "pkiOb_specRef" to Rule("string", min = 0, format = ""),
        "pkiOb_val" to Rule("string", min = 0, format = ""),
        "pkiOb" to Rule("object", fields = mapOf("encoding" to "pkiOb_encoding", "specRef" to "pkiOb_specRef", "val" to "pkiOb_val"), required = setOf("val")),
        "X509SubjectName" to Rule("string"),
        "X509SKI" to Rule("string"),
        "OtherId" to Rule("string"),
        "LoTEQualifier" to Rule("object", fields = mapOf("LoTEType" to "LoTEType", "SchemeOperatorName" to "SchemeOperatorName", "SchemeTypeCommunityRules" to "SchemeTypeCommunityRules", "SchemeTerritory" to "SchemeTerritory", "MimeType" to "MimeType"), required = setOf("LoTEType", "SchemeOperatorName", "MimeType")),
        "MimeType" to Rule("string"),
        "ListIssueDateTime" to Rule("string", format = "date-time"),
        "NextUpdate" to Rule("string", format = "date-time"),
        "DistributionPoints" to Rule("array", min = 1),
        "SchemeExtensions" to Rule("array", min = 1),
        "TrustedEntitiesList" to Rule("array", item = "TrustedEntity", min = 1),
        "TrustedEntity" to Rule("object", fields = mapOf("TrustedEntityInformation" to "TrustedEntityInformation", "TrustedEntityServices" to "TrustedEntityServices"), required = setOf("TrustedEntityInformation", "TrustedEntityServices")),
        "TrustedEntityServices" to Rule("array", item = "TrustedEntityService", min = 1),
        "TrustedEntityService" to Rule("object", fields = mapOf("ServiceInformation" to "ServiceInformation", "ServiceHistory" to "ServiceHistory"), required = setOf("ServiceInformation")),
        "ServiceInformation" to Rule("object", fields = mapOf("ServiceName" to "ServiceName", "ServiceDigitalIdentity" to "ServiceDigitalIdentity", "ServiceTypeIdentifier" to "ServiceTypeIdentifier", "ServiceStatus" to "ServiceStatus", "StatusStartingTime" to "StatusStartingTime", "SchemeServiceDefinitionURI" to "SchemeServiceDefinitionURI", "ServiceSupplyPoints" to "ServiceSupplyPoints", "ServiceDefinitionURI" to "ServiceDefinitionURI", "ServiceInformationExtensions" to "ServiceInformationExtensions"), required = setOf("ServiceName", "ServiceDigitalIdentity")),
        "ServiceTypeIdentifier" to Rule("string", format = "uri"),
        "ServiceName" to Rule("array", item = "multiLangString", min = 1),
        "ServiceStatus" to Rule("string", format = "uri"),
        "StatusStartingTime" to Rule("string", format = "date-time"),
        "SchemeServiceDefinitionURI" to Rule("array", item = "NonEmptyMultiLangURI", min = 1),
        "ServiceSupplyPoints" to Rule("array", item = "ServiceSupplyPointURI", min = 1),
        "ServiceSupplyPointURI_ServiceType" to Rule("string", min = 1, format = "uri"),
        "ServiceSupplyPointURI_uriValue" to Rule("string", min = 1, format = "uri"),
        "ServiceSupplyPointURI" to Rule("object", fields = mapOf("ServiceType" to "ServiceSupplyPointURI_ServiceType", "uriValue" to "ServiceSupplyPointURI_uriValue"), required = setOf("uriValue")),
        "ServiceDefinitionURI" to Rule("array", item = "NonEmptyMultiLangURI", min = 1),
        "ServiceInformationExtensions" to Rule("array", min = 1),
        "ServiceHistory" to Rule("array", item = "ServiceHistoryInstance", min = 1),
        "ServiceHistoryInstance" to Rule("object", fields = mapOf("ServiceName" to "ServiceName", "ServiceDigitalIdentity" to "ServiceDigitalIdentity", "ServiceStatus" to "ServiceStatus", "StatusStartingTime" to "StatusStartingTime", "ServiceTypeIdentifier" to "ServiceTypeIdentifier", "ServiceInformationExtensions" to "ServiceInformationExtensions"), required = setOf("ServiceName", "ServiceDigitalIdentity", "ServiceStatus", "StatusStartingTime")),
        "TrustedEntityInformation" to Rule("object", fields = mapOf("TEName" to "TEName", "TETradeName" to "TETradeName", "TEAddress" to "TEAddress", "TEInformationURI" to "TEInformationURI", "TEInformationExtensions" to "TEInformationExtensions"), required = setOf("TEName", "TEAddress", "TEInformationURI")),
        "TEName" to Rule("array", item = "multiLangString", min = 1),
        "TETradeName" to Rule("array", item = "multiLangString", min = 1),
        "TEAddress" to Rule("object", fields = mapOf("TEPostalAddress" to "PostalAddresses", "TEElectronicAddress" to "ElectronicAddress"), required = setOf("TEPostalAddress", "TEElectronicAddress")),
        "TEInformationURI" to Rule("array", item = "NonEmptyMultiLangURI", min = 1),
        "TEInformationExtensions" to Rule("array", min = 1),
    )

    fun validate(document: JsonElement) = validate(document, "Document", "$")

    private fun validate(value: JsonElement, name: String, path: String) {
        if (name == "PublicKeyValue") {
            require(value is JsonObject && value["kty"] is JsonPrimitive) { "$path requires a JWK object with kty" }
            return
        }
        val rule = rules.getValue(name)
        when (rule.type) {
            "object" -> {
                require(value is JsonObject) { "$path must be an object" }
                require(value.keys.containsAll(rule.required)) { "$path missing required members: ${rule.required - value.keys}" }
                require(value.keys.all { it in rule.fields }) { "$path contains unknown members: ${value.keys - rule.fields.keys}" }
                value.forEach { (key, child) -> validate(child, rule.fields.getValue(key), "$path.$key") }
            }
            "array" -> {
                require(value is JsonArray && value.size >= rule.min) { "$path must contain at least ${rule.min} entries" }
                if (name == "PolicyOrLegalNotice") {
                    val policies = value.all { it is JsonObject && it.keys == setOf("LoTEPolicy") }
                    val notices = value.all { it is JsonObject && it.keys == setOf("LoTELegalNotice") }
                    require(policies != notices) { "$path must contain either policies or notices" }
                    value.forEachIndexed { index, entry ->
                        val obj = entry.jsonObject
                        if (policies) validate(obj.getValue("LoTEPolicy"), "NonEmptyMultiLangURI", "$path[$index]")
                        else require(obj["LoTELegalNotice"] is JsonPrimitive && obj.getValue("LoTELegalNotice").jsonPrimitive.isString)
                    }
                } else if (rule.item != null) value.forEachIndexed { index, child -> validate(child, rule.item, "$path[$index]") }
            }
            "integer" -> require(value is JsonPrimitive && !value.isString && value.intOrNull != null) { "$path must be an integer within the supported 32-bit range" }
            "string" -> {
                require(value is JsonPrimitive && value.isString && value.content.length in rule.min..rule.max) { "$path must be a string of the required length" }
                when (rule.format) {
                    "uri" -> require(Regex("[A-Za-z][A-Za-z0-9+.-]*:[^\\s]+").matches(value.content)) { "$path must be an absolute URI" }
                    "date-time" -> Instant.parse(value.content)
                }
            }
        }
    }
}
