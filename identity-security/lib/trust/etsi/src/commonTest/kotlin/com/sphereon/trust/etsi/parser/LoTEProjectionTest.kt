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

package com.sphereon.trust.etsi.parser

import com.sphereon.trust.etsi.lote.serialization.LoTEJson
import com.sphereon.trust.etsi.lote.model.*
import com.sphereon.trust.etsi.model.LoTE612Converter.toETSILoTE
import com.sphereon.trust.etsi.model.LoTE612Converter.toLoTE
import com.sphereon.trust.etsi.model.GenericTrustAnchorExportPolicy
import com.sphereon.trust.etsi.resolution.LoTERoleTrustListStatusPolicy
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

class LoTEProjectionTest {
    private val at = kotlin.time.Instant.parse("2026-10-09T00:00:00Z")

    private fun trusted(list: LoTE, role: EidasRole = EidasRole.PID_PROVIDER): Boolean {
        val projected = list.toETSILoTE()
        val service = projected.trustedEntities.single().trustedEntityServices.single().serviceInformation
        return LoTERoleTrustListStatusPolicy.evaluate(service, role, projected, at).first
    }

    @Test fun matchingCertificateCannotAuthorizeUnsupportedRepresentation() {
        val list = LoTEJson.parse(fixture)
        val entity = list.trustedEntitiesList.single()
        val service = entity.trustedEntityServices.single()
        val identity = service.serviceInformation.serviceDigitalIdentity
        val certificate = identity.x509Certificates.single()
        assertTrue(trusted(list))
        for (unsupported in listOf(certificate.copy(encoding = "urn:unsupported:encoding"), certificate.copy(specRef = "unsupported specification"))) {
            // Keep identical certificate bytes; only its declared representation changes.
            assertEquals(certificate.value, unsupported.value)
            val changed = service.copy(serviceInformation = service.serviceInformation.copy(serviceDigitalIdentity = identity.copy(x509Certificates = listOf(unsupported))))
            assertFalse(trusted(list.copy(trustedEntitiesList = listOf(entity.copy(trustedEntityServices = listOf(changed))))))
        }
    }

    @Test fun roleAuthorizationRejectsUnsupportedExtensionsAtEveryLevel() {
        val list = LoTEJson.parse(fixture)
        val entity = list.trustedEntitiesList.single()
        val service = entity.trustedEntityServices.single()
        assertTrue(trusted(list))
        // The pinned JSON binding defines no criticality envelope: a caller-supplied
        // critical=false cannot make an unknown extension safe to authorize.
        for (critical in listOf(true, false)) {
            val extension = buildJsonObject { put("critical", critical); put("type", "urn:unsupported") }
            assertFalse(trusted(list.copy(listAndSchemeInformation = list.listAndSchemeInformation.copy(schemeExtensions = listOf(extension)))))
            assertFalse(trusted(list.copy(trustedEntitiesList = listOf(entity.copy(trustedEntityInformation = entity.trustedEntityInformation.copy(informationExtensions = listOf(extension)))))))
            assertFalse(trusted(list.copy(trustedEntitiesList = listOf(entity.copy(trustedEntityServices = listOf(service.copy(serviceInformation = service.serviceInformation.copy(serviceInformationExtensions = listOf(extension)))))))))
        }
    }

    @Test fun membershipAuthorizationRejectsForbiddenHistory() {
        val list = LoTEJson.parse(fixture)
        val entity = list.trustedEntitiesList.single()
        val service = entity.trustedEntityServices.single()
        val history = service.serviceInformation.copy(serviceStatus = LoTEServiceStatus.NOTIFIED, statusStartingTime = at).toHistoryInstance()
        assertFalse(trusted(list.copy(trustedEntitiesList = listOf(entity.copy(trustedEntityServices = listOf(service.copy(serviceHistory = listOf(history))))))))
    }

    @Test fun pubEaaAuthorizationChecksHistoryPeriodChronologyAndCriticality() {
        val list = LoTEJson.parse(fixture)
        val entity = list.trustedEntitiesList.single()
        val original = entity.trustedEntityServices.single()
        val current = original.serviceInformation.copy(serviceTypeIdentifier = LoTEServiceType.PUB_EAA_ISSUANCE,
            serviceStatus = LoTEServiceStatus.NOTIFIED, statusStartingTime = list.listAndSchemeInformation.listIssueDateTime)
        val historical = current.copy(
            statusStartingTime = kotlin.time.Instant.parse("2026-09-01T00:00:00Z"),
            serviceDigitalIdentity = LoTEServiceDigitalIdentity(x509SKIs = listOf("c3ludGhldGljLXNraQ==")),
        ).toHistoryInstance()
        val pubEntity = entity.copy(trustedEntityInformation = entity.trustedEntityInformation.copy(
            tradeName = listOf(MultiLangString("en", "OJ:EU:synthetic-test-law")),
        ))
        val valid = list.copy(listAndSchemeInformation = list.listAndSchemeInformation.copy(
            type = LoTEType.EU_PUB_EAA_PROVIDERS, historicalInformationPeriod = 65535,
            statusDeterminationApproach = "http://uri.etsi.org/19602/PubEAAProvidersList/StatusDetn/EU",
            schemeTypeCommunityRules = listOf(MultiLangURI("en", "http://uri.etsi.org/19602/PubEAAProvidersList/schemerules/EU")),
            pointersToOtherLoTE = emptyList(),
        ), trustedEntitiesList = listOf(pubEntity.copy(trustedEntityServices = listOf(original.copy(serviceInformation = current, serviceHistory = listOf(historical))))))
        assertTrue(trusted(valid, EidasRole.PUB_EAA_PROVIDER))
        assertFalse(trusted(valid.copy(listAndSchemeInformation = valid.listAndSchemeInformation.copy(historicalInformationPeriod = null)), EidasRole.PUB_EAA_PROVIDER))
        fun withHistory(history: List<ServiceHistoryInstance>): LoTE = valid.copy(trustedEntitiesList = listOf(pubEntity.copy(trustedEntityServices = listOf(original.copy(serviceInformation = current, serviceHistory = history)))))
        assertFalse(trusted(withHistory(listOf(historical.copy(statusStartingTime = at))), EidasRole.PUB_EAA_PROVIDER))
        assertFalse(trusted(withHistory(listOf(historical, historical)), EidasRole.PUB_EAA_PROVIDER))
        val critical = buildJsonObject { put("critical", true) }
        assertFalse(trusted(withHistory(listOf(historical.copy(serviceInformationExtensions = listOf(critical)))), EidasRole.PUB_EAA_PROVIDER))
    }

    @Test fun genericAnchorExportGateRejectsEvery602PurposeAndWithdrawnList() {
        val list = LoTEJson.parse(fixture)
        for (type in listOf(LoTEType.EU_PID_PROVIDERS, LoTEType.EU_WRPAC_PROVIDERS, LoTEType.EU_WRPRC_PROVIDERS, LoTEType.EU_REGISTRARS, LoTEType.EU_PUB_EAA_PROVIDERS)) {
            val projected = list.copy(listAndSchemeInformation = list.listAndSchemeInformation.copy(type = type)).toETSILoTE()
            assertFalse(GenericTrustAnchorExportPolicy.permits(projected))
            assertFalse(GenericTrustAnchorExportPolicy.permits(projected.copy(sourceLoTE = null)))
        }
        val entity = list.trustedEntitiesList.single()
        val service = entity.trustedEntityServices.single()
        val withdrawn = list.copy(trustedEntitiesList = listOf(entity.copy(trustedEntityServices = listOf(service.copy(serviceInformation = service.serviceInformation.copy(serviceStatus = LoTEServiceStatus.WITHDRAWN))))))
        assertFalse(GenericTrustAnchorExportPolicy.permits(withdrawn.toETSILoTE()))
    }

    @Test fun preservesExplicitAbsenceAndTypedEndpointsAcrossProjection() {
        val list = LoTEJson.parse(fixture)
        val projected = list.toETSILoTE()
        val info = projected.trustedEntities.single().trustedEntityServices.single().serviceInformation
        assertNull(info.sourceLoTEInformation!!.serviceStatus)
        assertNull(info.sourceLoTEInformation!!.statusStartingTime)
        assertEquals("urn:example:openid4vci", info.serviceSupplyPoints.single().type)
        assertEquals(list, projected.toLoTE())
        assertFalse(LoTERoleTrustListStatusPolicy.evaluate(info.serviceStatus).first)
        assertTrue(LoTERoleTrustListStatusPolicy.evaluate(info, EidasRole.PID_PROVIDER, projected, kotlin.time.Instant.parse("2026-10-09T00:00:00Z")).first)
        assertFalse(LoTERoleTrustListStatusPolicy.evaluate(info, EidasRole.QEAA_PROVIDER, projected, kotlin.time.Instant.parse("2026-10-09T00:00:00Z")).first)
        assertFalse(LoTERoleTrustListStatusPolicy.evaluate(info, EidasRole.ACCESS_CA, projected, kotlin.time.Instant.parse("2026-10-09T00:00:00Z")).first)
    }

    @Test fun rejectsEditsInsteadOfSilentlyReturningStaleSource() {
        val projected = LoTEJson.parse(fixture).toETSILoTE()
        assertFailsWith<IllegalArgumentException> { projected.copy(sequenceNumber = 8).toLoTE() }
        val entity = projected.trustedEntities.single()
        val service = entity.trustedEntityServices.single()
        val modified = projected.copy(trustedEntities = listOf(entity.copy(trustedEntityServices = listOf(service.copy(serviceInformation = service.serviceInformation.copy(serviceStatus = "urn:modified"))))))
        assertFailsWith<IllegalArgumentException> { modified.toLoTE() }
    }

    @Test fun preservesHistoryAndAbsentOptionalHistoryType() {
        val list = LoTEJson.parse(fixture)
        val entity = list.trustedEntitiesList.single()
        val service = entity.trustedEntityServices.single()
        val history = ServiceHistoryInstance(serviceName = service.serviceInformation.serviceName,
            serviceDigitalIdentity = service.serviceInformation.serviceDigitalIdentity,
            serviceStatus = LoTEServiceStatus.NOTIFIED,
            statusStartingTime = kotlin.time.Instant.parse("2026-09-01T00:00:00Z"))
        val historic = list.copy(trustedEntitiesList = listOf(entity.copy(trustedEntityServices = listOf(service.copy(serviceHistory = listOf(history))))))
        assertEquals(historic, historic.toETSILoTE().toLoTE())
    }

    // Hand-authored PID profile fixture with explicit Annex scheme information;
    // dummy certificate bytes deliberately do not establish signature validity.
    private val fixture = """
{
  "LoTE": {
    "ListAndSchemeInformation": {
      "LoTEVersionIdentifier": 1,
      "LoTESequenceNumber": 7,
      "LoTEType": "http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList",
      "SchemeTerritory": "EU",
      "SchemeName": [{"lang":"en","value":"Synthetic PID profile"}],
      "SchemeInformationURI": [{"lang":"en","uriValue":"https://example.com/information"}],
      "StatusDeterminationApproach": "http://uri.etsi.org/19602/PIDProvidersList/StatusDetn/EU",
      "SchemeTypeCommunityRules": [{"lang":"en","uriValue":"http://uri.etsi.org/19602/PIDProviders/schemerules/EU"}],
      "PolicyOrLegalNotice": [{"LoTELegalNotice":"Synthetic test; not an official EU list"}],
      "SchemeOperatorName": [{"lang":"en","value":"Independent fixture operator"}],
      "SchemeOperatorAddress": {
        "SchemeOperatorPostalAddress": [{"lang":"en","StreetAddress":"1 Example Street","Country":"NL"}],
        "SchemeOperatorElectronicAddress": [{"lang":"en","uriValue":"mailto:operator@example.com"}]
      },
      "PointersToOtherLoTE": [{
        "ServiceDigitalIdentities": [{"X509Certificates":[{"val":"Y2VydGlmaWNhdGU="}]}],
        "LoTELocation": "https://example.com/list",
        "LoTEQualifiers": [{
          "LoTEType": "http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList",
          "SchemeOperatorName": [{"lang":"en","value":"Independent fixture operator"}],
          "MimeType": "application/json"
        }]
      }],
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
          "ServiceDigitalIdentity": {"X509Certificates":[{"val":"Y2VydGlmaWNhdGU="}]},
          "ServiceSupplyPoints": [{"uriValue":"https://example.com/issuance","ServiceType":"urn:example:openid4vci"}],
          "ServiceDefinitionURI": [{"lang":"en","uriValue":"https://example.com/service"}]
        }
      }]
    }]
  }
}

    """.trimIndent()
}
