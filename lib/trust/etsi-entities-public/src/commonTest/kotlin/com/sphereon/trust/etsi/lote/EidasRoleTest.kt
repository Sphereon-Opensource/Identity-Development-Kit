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
import com.sphereon.trust.etsi.lote.model.LoTEServiceType
import com.sphereon.trust.etsi.lote.model.LoTEType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EidasRoleTest {
    @Test
    fun allRolesMapToValidLoTETypeConstants() {
        assertEquals(LoTEType.EU_PID_PROVIDERS, EidasRole.PID_ISSUER.loTEType)
        assertEquals(LoTEType.EU_WALLET_PROVIDERS, EidasRole.WALLET_PROVIDER.loTEType)
        assertEquals(LoTEType.EU_PUB_EAA_PROVIDERS, EidasRole.QEAA_ISSUER.loTEType)
        assertEquals(LoTEType.EU_WRPAC_PROVIDERS, EidasRole.RELYING_PARTY.loTEType)
        assertEquals(LoTEType.EU_REGISTRARS, EidasRole.REGISTRAR.loTEType)
    }

    @Test
    fun allRolesMapToValidIssuanceServiceTypes() {
        assertEquals(LoTEServiceType.PID_ISSUANCE, EidasRole.PID_ISSUER.issuanceServiceType)
        assertEquals(LoTEServiceType.WALLET_ISSUANCE, EidasRole.WALLET_PROVIDER.issuanceServiceType)
        assertEquals(LoTEServiceType.PUB_EAA_ISSUANCE, EidasRole.QEAA_ISSUER.issuanceServiceType)
        assertEquals(LoTEServiceType.WRPAC_ISSUANCE, EidasRole.RELYING_PARTY.issuanceServiceType)
        assertEquals(LoTEServiceType.REGISTER, EidasRole.REGISTRAR.issuanceServiceType)
    }

    @Test
    fun revocationServiceTypesAreCorrect() {
        assertEquals(LoTEServiceType.PID_REVOCATION, EidasRole.PID_ISSUER.revocationServiceType)
        assertEquals(LoTEServiceType.WALLET_REVOCATION, EidasRole.WALLET_PROVIDER.revocationServiceType)
        assertEquals(LoTEServiceType.PUB_EAA_REVOCATION, EidasRole.QEAA_ISSUER.revocationServiceType)
        assertEquals(LoTEServiceType.WRPAC_REVOCATION, EidasRole.RELYING_PARTY.revocationServiceType)
        assertNull(EidasRole.REGISTRAR.revocationServiceType)
    }

    @Test
    fun allLoTETypesAreDistinct() {
        val loTETypes = EidasRole.entries.map { it.loTEType }.toSet()
        assertEquals(EidasRole.entries.size, loTETypes.size, "All LoTEType URIs should be distinct")
    }

    @Test
    fun fromLoTETypeReturnsCorrectRole() {
        assertEquals(EidasRole.PID_ISSUER, EidasRole.fromLoTEType(LoTEType.EU_PID_PROVIDERS))
        assertEquals(EidasRole.WALLET_PROVIDER, EidasRole.fromLoTEType(LoTEType.EU_WALLET_PROVIDERS))
        assertEquals(EidasRole.QEAA_ISSUER, EidasRole.fromLoTEType(LoTEType.EU_PUB_EAA_PROVIDERS))
        assertEquals(EidasRole.RELYING_PARTY, EidasRole.fromLoTEType(LoTEType.EU_WRPAC_PROVIDERS))
        assertEquals(EidasRole.REGISTRAR, EidasRole.fromLoTEType(LoTEType.EU_REGISTRARS))
    }

    @Test
    fun fromLoTETypeReturnsNullForUnknownType() {
        assertNull(EidasRole.fromLoTEType("http://unknown/type"))
    }

    @Test
    fun fromServiceTypeFindsIssuanceTypes() {
        assertEquals(EidasRole.PID_ISSUER, EidasRole.fromServiceType(LoTEServiceType.PID_ISSUANCE))
        assertEquals(EidasRole.WALLET_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.WALLET_ISSUANCE))
        assertEquals(EidasRole.QEAA_ISSUER, EidasRole.fromServiceType(LoTEServiceType.PUB_EAA_ISSUANCE))
        assertEquals(EidasRole.RELYING_PARTY, EidasRole.fromServiceType(LoTEServiceType.WRPAC_ISSUANCE))
        assertEquals(EidasRole.REGISTRAR, EidasRole.fromServiceType(LoTEServiceType.REGISTER))
    }

    @Test
    fun fromServiceTypeFindsRevocationTypes() {
        assertEquals(EidasRole.PID_ISSUER, EidasRole.fromServiceType(LoTEServiceType.PID_REVOCATION))
        assertEquals(EidasRole.WALLET_PROVIDER, EidasRole.fromServiceType(LoTEServiceType.WALLET_REVOCATION))
        assertEquals(EidasRole.QEAA_ISSUER, EidasRole.fromServiceType(LoTEServiceType.PUB_EAA_REVOCATION))
        assertEquals(EidasRole.RELYING_PARTY, EidasRole.fromServiceType(LoTEServiceType.WRPAC_REVOCATION))
    }

    @Test
    fun fromServiceTypeFindsLegacyTypes() {
        // QEAA_ISSUER has legacy CA_QC mapping
        val result = EidasRole.fromServiceType("http://uri.etsi.org/TrstSvc/Svctype/CA/QC")
        assertEquals(EidasRole.QEAA_ISSUER, result)
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
        val registrarTypes = EidasRole.REGISTRAR.allServiceTypes()
        assertEquals(1, registrarTypes.size)
        assertTrue(registrarTypes.contains(LoTEServiceType.REGISTER))
    }

    @Test
    fun qeaaIssuerHasLegacyServiceTypes() {
        assertTrue(EidasRole.QEAA_ISSUER.legacyServiceTypes.isNotEmpty())
        assertTrue(EidasRole.QEAA_ISSUER.legacyServiceTypes.contains("http://uri.etsi.org/TrstSvc/Svctype/CA/QC"))
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
        assertEquals(5, EidasRole.entries.size)
    }
}
