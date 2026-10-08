package com.sphereon.openid.oid4vc.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresentationFormatTest {
    @Test
    fun jwtVpJsonIsAPresentationFormatAndNotACredentialFormat() {
        assertEquals(PresentationFormat.JWT_VP_JSON, PresentationFormat.fromValue("jwt_vp_json"))
        assertEquals(PresentationFormat.JWT_VP_JSON, PresentationFormat.fromValueLenient("application/vp+jwt"))
        assertNull(PresentationFormat.fromValueLenient("application/jwt"))
        assertNull(PresentationFormat.fromValueLenient("application/ld+json"))
        assertTrue(PresentationFormat.JWT_VP_JSON.isJwt)
        assertTrue(PresentationFormat.LDP_VP.isDataIntegrity)
        assertNull(CredentialFormat.fromValue("jwt_vp_json"))
        assertNull(CredentialFormat.fromValueLenient("jwt_vp_json"))
    }
}
