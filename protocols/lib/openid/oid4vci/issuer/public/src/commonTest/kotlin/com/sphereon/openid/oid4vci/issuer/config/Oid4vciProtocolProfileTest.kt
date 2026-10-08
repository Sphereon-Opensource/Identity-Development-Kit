package com.sphereon.openid.oid4vci.issuer.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Oid4vciProtocolProfileTest {
    @Test
    fun pinnedPreviewProfileHasExplicitWireMapping() {
        val profile = Oid4vciIssuerSpecProfile.OID4VCI_1_1_DRAFT_2A1F0513

        assertEquals(Oid4vciSpecVersion.V1_1, profile.version)
        assertEquals("openid-4-verifiable-credential-issuance-1_1-2a1f0513", profile.oid4vciSpec)
        assertEquals(SdJwtVcSpecProfile.DRAFT_11, profile.sdJwtVcSpec)
        assertEquals(false, profile.credentialResponseEncryptionAlgorithmRequired)
        assertEquals(true, profile.credentialResponseEncryptionCompressionAllowed)
        assertEquals(true, Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL.credentialResponseEncryptionAlgorithmRequired)
        assertEquals(false, Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL.credentialResponseEncryptionCompressionAllowed)
    }

    @Test
    fun profileParsingAcceptsOnlyGovernedProfileNames() {
        assertEquals(
            Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL,
            Oid4vciIssuerSpecProfile.parse("OID4VCI_1_0_FINAL"),
        )
        assertEquals(
            Oid4vciIssuerSpecProfile.OID4VCI_1_1_DRAFT_2A1F0513,
            Oid4vciIssuerSpecProfile.parse("OID4VCI_1_1_DRAFT_2A1F0513"),
        )
        assertFailsWith<IllegalArgumentException> {
            Oid4vciIssuerSpecProfile.parse("1.1")
        }
    }
}
