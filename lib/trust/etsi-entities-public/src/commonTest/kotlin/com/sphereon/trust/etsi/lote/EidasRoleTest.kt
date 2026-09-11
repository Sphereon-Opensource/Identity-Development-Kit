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

import com.sphereon.trust.etsi.lote.model.EidasRole
import com.sphereon.trust.etsi.lote.model.EidasRole.Companion.allServiceTypes
import com.sphereon.trust.etsi.lote.model.EtsiTrustListProfile
import com.sphereon.trust.etsi.lote.model.LoTLServiceType
import com.sphereon.trust.etsi.lote.model.LoTEServiceType
import com.sphereon.trust.etsi.lote.model.LoTEType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EidasRoleTest {
    @Test
    fun registrarsAndRegistersIsNotRegistrationCertificateProvider() {
        assertNull(EidasRole.fromLoTEType(LoTEType.EU_REGISTRARS))
        assertEquals("Access Certificate Authority", EidasRole.ACCESS_CA.description)
    }

    @Test
    fun task1SeparatesQeaaFromLoTEProfiles() {
        assertNull(EidasRole.QEAA_PROVIDER.loTEType)
        assertEquals("http://uri.etsi.org/TrstSvc/Svctype/EAA/Q", EidasRole.QEAA_PROVIDER.issuanceServiceType)
        assertEquals(6, EidasRole.entries.size)
    }

    @Test
    fun allRolesMapToValidLoTETypeConstants() {
        assertEquals(LoTEType.EU_PID_PROVIDERS, EidasRole.PID_PROVIDER.loTEType)
        assertEquals(LoTEType.EU_WALLET_PROVIDERS, EidasRole.WALLET_PROVIDER.loTEType)
        assertNull(EidasRole.QEAA_PROVIDER.loTEType)
        assertEquals(LoTEType.EU_PUB_EAA_PROVIDERS, EidasRole.PUB_EAA_PROVIDER.loTEType)
        assertEquals(LoTEType.EU_WRPAC_PROVIDERS, EidasRole.ACCESS_CA.loTEType)
        assertEquals(LoTEType.EU_WRPRC_PROVIDERS, EidasRole.REGISTRATION_CERTIFICATE_PROVIDER.loTEType)
    }

    @Test
    fun allRolesMapToValidIssuanceServiceTypes() {
        assertEquals(LoTEServiceType.PID_ISSUANCE, EidasRole.PID_PROVIDER.issuanceServiceType)
        assertEquals(LoTEServiceType.WALLET_ISSUANCE, EidasRole.WALLET_PROVIDER.issuanceServiceType)
        assertEquals(LoTLServiceType.QEAA_ISSUANCE, EidasRole.QEAA_PROVIDER.issuanceServiceType)
        assertEquals(LoTEServiceType.PUB_EAA_ISSUANCE, EidasRole.PUB_EAA_PROVIDER.issuanceServiceType)
        assertEquals(LoTEServiceType.WRPAC_ISSUANCE, EidasRole.ACCESS_CA.issuanceServiceType)
        assertEquals(LoTEServiceType.WRPRC_ISSUANCE, EidasRole.REGISTRATION_CERTIFICATE_PROVIDER.issuanceServiceType)
    }

    @Test
    fun revocationServiceTypesAreCorrect() {
        assertEquals(LoTEServiceType.PID_REVOCATION, EidasRole.PID_PROVIDER.revocationServiceType)
        assertEquals(LoTEServiceType.WALLET_REVOCATION, EidasRole.WALLET_PROVIDER.revocationServiceType)
        assertNull(EidasRole.QEAA_PROVIDER.revocationServiceType)
        assertEquals(LoTEServiceType.PUB_EAA_REVOCATION, EidasRole.PUB_EAA_PROVIDER.revocationServiceType)
        assertEquals(LoTEServiceType.WRPAC_REVOCATION, EidasRole.ACCESS_CA.revocationServiceType)
        assertEquals(LoTEServiceType.WRPRC_REVOCATION, EidasRole.REGISTRATION_CERTIFICATE_PROVIDER.revocationServiceType)
    }

    @Test
    fun allLoTETypesAreDistinct() {
        val loTETypes = EidasRole.entries.mapNotNull { it.loTEType }.toSet()
        assertEquals(EidasRole.entries.size - 1, loTETypes.size, "LoTEType URIs should be distinct for TS 119 602 roles")
    }

    @Test
    fun fromLoTETypeReturnsCorrectRole() {
        assertEquals(EidasRole.PID_PROVIDER, EidasRole.fromLoTEType(LoTEType.EU_PID_PROVIDERS))
        assertEquals(EidasRole.WALLET_PROVIDER, EidasRole.fromLoTEType(LoTEType.EU_WALLET_PROVIDERS))
        assertEquals(EidasRole.PUB_EAA_PROVIDER, EidasRole.fromLoTEType(LoTEType.EU_PUB_EAA_PROVIDERS))
        assertEquals(EidasRole.ACCESS_CA, EidasRole.fromLoTEType(LoTEType.EU_WRPAC_PROVIDERS))
        assertEquals(EidasRole.REGISTRATION_CERTIFICATE_PROVIDER, EidasRole.fromLoTEType(LoTEType.EU_WRPRC_PROVIDERS))
        assertNull(EidasRole.fromLoTEType(LoTEType.EU_REGISTRARS))
    }

    @Test
    fun fromLoTETypeReturnsNullForUnknownType() {
        assertNull(EidasRole.fromLoTEType("http://unknown/type"))
    }

    @Test
    fun fromServiceTypeFindsIssuanceTypes() {
        assertEquals(EidasRole.PID_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.PID_ISSUANCE))
        assertEquals(EidasRole.WALLET_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.WALLET_ISSUANCE))
        assertEquals(EidasRole.PUB_EAA_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.PUB_EAA_ISSUANCE))
        assertEquals(EidasRole.ACCESS_CA, EidasRole.fromServiceType(LoTEServiceType.WRPAC_ISSUANCE))
        assertEquals(EidasRole.REGISTRATION_CERTIFICATE_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.WRPRC_ISSUANCE))
        assertEquals(EidasRole.QEAA_PROVIDER, EidasRole.fromServiceType(LoTLServiceType.QEAA_ISSUANCE))
    }

    @Test
    fun fromServiceTypeFindsRevocationTypes() {
        assertEquals(EidasRole.PID_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.PID_REVOCATION))
        assertEquals(EidasRole.WALLET_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.WALLET_REVOCATION))
        assertEquals(EidasRole.PUB_EAA_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.PUB_EAA_REVOCATION))
        assertEquals(EidasRole.ACCESS_CA, EidasRole.fromServiceType(LoTEServiceType.WRPAC_REVOCATION))
        assertEquals(EidasRole.REGISTRATION_CERTIFICATE_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.WRPRC_REVOCATION))
    }

    @Test
    fun fromServiceTypeFindsLegacyTypes() {
        assertNull(EidasRole.fromServiceType("http://uri.etsi.org/TrstSvc/Svctype/CA/QC"))
    }

    @Test
    fun fromServiceTypeReturnsNullForUnknown() {
        assertNull(EidasRole.fromServiceType("http://unknown/service"))
    }

    @Test
    fun allServiceTypesReturnsIssuanceAndRevocation() {
        val pidTypes = EidasRole.PID_ISSUER.allServiceTypes()
        assertEquals(2, pidTypes.size)
        assertTrue(pidTypes.contains(LoTEServiceType.PID_ISSUANCE))
        assertTrue(pidTypes.contains(LoTEServiceType.PID_REVOCATION))
    }

    @Test
    fun allServiceTypesExcludesNullRevocation() {
        val registrarTypes = EidasRole.REGISTRATION_CERTIFICATE_PROVIDER.allServiceTypes()
        assertEquals(2, registrarTypes.size)
        assertTrue(registrarTypes.contains(LoTEServiceType.WRPRC_ISSUANCE))
        assertTrue(registrarTypes.contains(LoTEServiceType.WRPRC_REVOCATION))
    }

    @Test
    fun qeaaUsesMemberStateProfileOnly() {
        assertEquals(EtsiTrustListProfile.TS_119_612_MEMBER_STATE, EidasRole.QEAA_PROVIDER.trustListProfile)
        assertTrue(EidasRole.QEAA_PROVIDER.legacyServiceTypes.isEmpty())
    }

    @Test
    fun nonQeaaRolesHaveNoLegacyTypes() {
        for (role in EidasRole.entries) {
            if (role != EidasRole.QEAA_ISSUER) {
                assertTrue(role.legacyServiceTypes.isEmpty(), "${role.name} should have no legacy service types")
            }
        }
    }

    @Test
    fun allRolesHaveDescriptions() {
        for (role in EidasRole.entries) {
            assertNotNull(role.description)
            assertTrue(role.description.isNotEmpty(), "${role.name} should have a non-empty description")
        }
    }

    @Test
    fun allEntriesAreEnumerable() {
        assertEquals(6, EidasRole.entries.size)
    }
}
