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

package com.sphereon.trust.etsi.lote

import com.sphereon.trust.etsi.lote.model.*
import com.sphereon.trust.etsi.lote.serialization.LoTEJson
import kotlinx.serialization.json.*
import kotlin.test.*

/** Independent wire literal; provenance is recorded in commonTest/resources/lote/README.md. */
class LoTEPublishedBindingTest {
    @Test fun sequenceRangeIsExplicitAndNeverTruncated() {
        val boundary = LoTEJson.parse(pidFixture.replace("\"LoTESequenceNumber\": 7", "\"LoTESequenceNumber\": 2147483647"))
        assertEquals(Int.MAX_VALUE, boundary.listAndSchemeInformation.sequenceNumber)
        val error = assertFailsWith<IllegalArgumentException> {
            LoTEJson.parse(pidFixture.replace("\"LoTESequenceNumber\": 7", "\"LoTESequenceNumber\": 2147483648"))
        }
        assertTrue(error.message.orEmpty().contains("supported 32-bit range"))
    }

    @Test fun acceptsPublishedWireShapeWithoutInventingStatus() {
        val list = LoTEJson.parse(pidFixture)
        val service = list.trustedEntitiesList.single().trustedEntityServices.single().serviceInformation
        assertNull(service.serviceStatus)
        assertNull(service.statusStartingTime)
        assertNull(list.trustedEntitiesList.single().trustedEntityInformation.address!!.postalAddresses.single().locality)
        assertEquals("urn:example:der", service.serviceDigitalIdentity.x509Certificates.single().encoding)
        assertEquals("urn:example:openid4vci", service.serviceSupplyPoints.single().serviceType)
        val encoded = LoTEJson.encode(list)
        assertEquals(Json.parseToJsonElement(pidFixture), Json.parseToJsonElement(encoded))
    }

    @Test fun acceptsAbsentStatusAcrossPublishedMembershipProfiles() {
        val profiles = listOf("EUPIDProvidersList", "EUWalletProvidersList", "EUWRPACProvidersList", "EUWRPRCProvidersList", "EURegistrarsAndRegistersList")
        for (profile in profiles) {
            val parsed = LoTEJson.parse(pidFixture.replace("EUPIDProvidersList", profile))
            assertNull(parsed.trustedEntitiesList.single().trustedEntityServices.single().serviceInformation.serviceStatus)
        }
    }

    @Test fun rejectsLegacyEnvelopeAndFieldNames() {
        assertFailsWith<IllegalArgumentException> { LoTEJson.parse(Json.parseToJsonElement(pidFixture).jsonObject.getValue("LoTE").toString()) }
        for (pair in listOf("val" to "pkiOb", "Country" to "CountryName", "ServiceDefinitionURI" to "TEServiceDefinitionURI", "TEPostalAddress" to "PostalAddresses")) {
            assertFailsWith<IllegalArgumentException> { LoTEJson.parse(pidFixture.replace("\"${pair.first}\"", "\"${pair.second}\"")) }
        }
        assertFailsWith<IllegalArgumentException> { LoTEJson.parse(pidFixture.replace("\"lang\":\"en\",\"StreetAddress\"", "\"StreetAddress\"")) }
    }

    @Test fun rejectsMissingMandatoryEntityMetadataAndEmptyMembers() {
        val original = Json.parseToJsonElement(pidFixture).jsonObject
        val list = original.getValue("LoTE").jsonObject
        val entity = list.getValue("TrustedEntitiesList").jsonArray.single().jsonObject
        for (field in listOf("TEAddress", "TEInformationURI", "TEName")) {
            val information = entity.getValue("TrustedEntityInformation").jsonObject
            val invalidEntity = JsonObject(entity + ("TrustedEntityInformation" to JsonObject(information - field)))
            val invalidList = JsonObject(list + ("TrustedEntitiesList" to JsonArray(listOf(invalidEntity))))
            assertFailsWith<IllegalArgumentException> { LoTEJson.parse(JsonObject(mapOf("LoTE" to invalidList)).toString()) }
        }
        assertFailsWith<IllegalArgumentException> { LoTEJson.parse(JsonObject(mapOf("LoTE" to JsonObject(list + ("TrustedEntitiesList" to JsonArray(emptyList()))))).toString()) }
        val emptyList = LoTEJson.parse(JsonObject(mapOf("LoTE" to JsonObject(list - "TrustedEntitiesList"))).toString())
        assertTrue(emptyList.trustedEntitiesList.isEmpty())
        assertFalse(LoTEJson.encode(emptyList).contains("TrustedEntitiesList"))
    }

    @Test fun preservesPubEaaHistoryAndRequiresItsStatusAndTime() {
        val parsed = LoTEJson.parse(pidFixture.replace("EUPIDProvidersList", "EUPubEAAProvidersList"))
        val original = parsed.trustedEntitiesList.single().trustedEntityServices.single()
        val info = original.serviceInformation.copy(serviceStatus = LoTEServiceStatus.WITHDRAWN, statusStartingTime = kotlin.time.Instant.parse("2026-10-01T00:00:00Z"))
        val service = original.copy(serviceInformation = info, serviceHistory = listOf(info.copy(serviceStatus = LoTEServiceStatus.NOTIFIED).toHistoryInstance()))
        val list = parsed.copy(trustedEntitiesList = listOf(parsed.trustedEntitiesList.single().copy(trustedEntityServices = listOf(service))))
        val encoded = LoTEJson.encode(list)
        assertEquals(list, LoTEJson.parse(encoded))
        val missingTime = encoded.replace("\"StatusStartingTime\":\"2026-10-01T00:00:00Z\",", "")
        assertFailsWith<IllegalArgumentException> { LoTEJson.parse(missingTime) }
    }

    @Test fun retainsExtensionPayloadsForSeparateCriticalityValidation() {
        val parsed = LoTEJson.parse(pidFixture)
        val extension = buildJsonObject { put("critical", true); put("extensionType", "urn:example:unknown") }
        val list = parsed.copy(listAndSchemeInformation = parsed.listAndSchemeInformation.copy(schemeExtensions = listOf(extension)))
        assertEquals(list, LoTEJson.parse(LoTEJson.encode(list)))
    }

    companion object {
        val pidFixture = """
{
  "LoTE": {
    "ListAndSchemeInformation": {
      "LoTEVersionIdentifier": 1,
      "LoTESequenceNumber": 7,
      "LoTEType": "http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList",
      "SchemeOperatorName": [{"lang":"en","value":"Independent fixture operator"}],
      "SchemeOperatorAddress": {
        "SchemeOperatorPostalAddress": [{"lang":"en","StreetAddress":"1 Example Street","Country":"NL"}],
        "SchemeOperatorElectronicAddress": [{"lang":"en","uriValue":"mailto:operator@example.com"}]
      },
      "ListIssueDateTime": "2026-10-01T00:00:00Z",
      "NextUpdate": "2026-11-01T00:00:00Z"
    },
    "TrustedEntitiesList": [{
      "TrustedEntityInformation": {
        "TEName": [{"lang":"en","value":"Example PID provider"}],
        "TEAddress": {
          "TEPostalAddress": [{"lang":"en","StreetAddress":"2 Example Street","Country":"NL"}],
          "TEElectronicAddress": [{"lang":"en","uriValue":"mailto:provider@example.com"}]
        },
        "TEInformationURI": [{"lang":"en","uriValue":"https://example.com/provider"}]
      },
      "TrustedEntityServices": [{
        "ServiceInformation": {
          "ServiceTypeIdentifier": "http://uri.etsi.org/19602/SvcType/PID/Issuance",
          "ServiceName": [{"lang":"en","value":"PID issuance"}],
          "ServiceDigitalIdentity": {"X509Certificates":[{"val":"Y2VydGlmaWNhdGU=","encoding":"urn:example:der","specRef":"X.509"}]},
          "ServiceSupplyPoints": [{"uriValue":"https://example.com/issuance","ServiceType":"urn:example:openid4vci"}],
          "ServiceDefinitionURI": [{"lang":"en","uriValue":"https://example.com/service"}]
        }
      }]
    }]
  }
}
        """.trimIndent()
    }
}
