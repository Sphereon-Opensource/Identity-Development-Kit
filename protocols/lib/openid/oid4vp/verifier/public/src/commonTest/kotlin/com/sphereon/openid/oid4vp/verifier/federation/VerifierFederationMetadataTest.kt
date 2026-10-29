package com.sphereon.openid.oid4vp.verifier.federation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VerifierFederationMetadataTest {
    @Test
    fun standingFormatsAreOnlyOpenId4vpAppendixBIdentifiers() {
        assertEquals(setOf("dc+sd-jwt", "mso_mdoc", "jwt_vc_json"), VerifierFederationMetadata.VP_FORMATS_SUPPORTED.keys)
    }

    @Test
    fun requestAdvertisesOnlyTheFormatsItsQueryAsksFor() {
        assertEquals(setOf("dc+sd-jwt"), VerifierFederationMetadata.vpFormatsFor(listOf("dc+sd-jwt", "dc+sd-jwt")).keys)
        assertEquals(setOf("dc+sd-jwt", "mso_mdoc"), VerifierFederationMetadata.vpFormatsFor(listOf("dc+sd-jwt", "mso_mdoc")).keys)
        assertFalse("jwt_vc_json-ld" in VerifierFederationMetadata.vpFormatsFor(listOf("dc+sd-jwt")))
    }

    @Test
    fun formatsOutsideOpenId4vpAreNeverAdvertised() {
        assertFalse("jwt_vc_json-ld" in VerifierFederationMetadata.vpFormatsFor(listOf("jwt_vc_json-ld", "dc+sd-jwt")))
        assertEquals(VerifierFederationMetadata.VP_FORMATS_SUPPORTED, VerifierFederationMetadata.vpFormatsFor(listOf("jwt_vc_json-ld")))
    }

    @Test
    fun unknownFormatsFallBackToTheStandingFormats() {
        assertEquals(VerifierFederationMetadata.VP_FORMATS_SUPPORTED, VerifierFederationMetadata.vpFormatsFor(listOf("x-unknown")))
        assertEquals(VerifierFederationMetadata.VP_FORMATS_SUPPORTED, VerifierFederationMetadata.vpFormatsFor(emptyList()))
    }
}
