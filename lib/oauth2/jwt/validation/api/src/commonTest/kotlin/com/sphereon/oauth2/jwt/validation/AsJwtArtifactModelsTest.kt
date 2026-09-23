/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.oauth2.jwt.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsJwtArtifactModelsTest {
    @Test
    fun `scope is exact and closed over the four AS protocol artifacts`() {
        assertTrue(setOf(AsJwtArtifactScope.ACCESS_TOKEN).admits(JwtArtifactContext.ACCESS_TOKEN))
        assertFalse(setOf(AsJwtArtifactScope.ACCESS_TOKEN).admits(JwtArtifactContext.ID_TOKEN))
        assertTrue(setOf(AsJwtArtifactScope.ID_TOKEN).admits(JwtArtifactContext.ID_TOKEN))
        assertTrue(setOf(AsJwtArtifactScope.JARM_RESPONSE).admits(JwtArtifactContext.JARM_RESPONSE))
        assertTrue(setOf(AsJwtArtifactScope.LOGOUT_TOKEN).admits(JwtArtifactContext.LOGOUT_TOKEN))
        assertFalse(emptySet<AsJwtArtifactScope>().admits(JwtArtifactContext.ACCESS_TOKEN))
        assertFalse(AsJwtArtifactScope.entries.any { it.name in setOf("CREDENTIAL_JWT", "CLIENT_ASSERTION", "DPOP", "REQUEST_OBJECT") })
    }
}
