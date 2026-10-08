/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.credential

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CredentialFormatTest {
    @Test
    fun mapsVcdm20WireIdentifierWithoutCollapsingVcdm11JwtVcJson() {
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, CredentialFormat.fromValueLenient("jwt_vc_json-ld"))
        assertEquals(CredentialFormat.LDP_VC, CredentialFormat.fromValueLenient("ldp_vc"))
        assertNull(CredentialFormat.fromValueLenient("vc+ld+json+jwt"))
        assertNull(CredentialFormat.fromValueLenient("ldp_vp"))
        assertEquals(CredentialFormat.JWT_VC_JSON, CredentialFormat.fromValueLenient("jwt_vc_json"))
        assertNull(CredentialFormat.fromValueLenient("vc+ld+json+jwt-extra"))
        assertNull(CredentialFormat.fromValueLenient("application/vc+ld+json+jwt"))
    }
}
