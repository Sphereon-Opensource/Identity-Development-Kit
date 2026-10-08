/* Copyright 2026 Sphereon International B.V. Licensed under Apache-2.0. */
package com.sphereon.openid.oid4vci.issuer.impl.http.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssuerMetadataDescriptorRoutingTest {
    @Test
    fun `dynamic issuer metadata descriptors divide suffix and prefix route ownership`() {
        val spec = GetIssuerMetadataEndpointCommand.specDescriptorFor("").pathPatterns
        val prefix = GetIssuerMetadataEndpointCommand.legacyPrefixDescriptorFor("").pathPatterns

        assertEquals(
            listOf(
                GetIssuerMetadataEndpointCommand.BARE_PATH,
                "${GetIssuerMetadataEndpointCommand.BARE_PATH}/{issuerPath...}",
            ),
            spec,
        )
        assertEquals(
            listOf(
                "/{issuerPath}${GetIssuerMetadataEndpointCommand.BARE_PATH}",
                "/{issuerPathParent}/{issuerPathChild}${GetIssuerMetadataEndpointCommand.BARE_PATH}",
            ),
            prefix,
        )
        assertTrue(spec.toSet().intersect(prefix.toSet()).isEmpty())
    }

    @Test
    fun `configured issuer metadata descriptors remain exact and disjoint`() {
        val issuer = "https://issuer.example/tenant/issuer"
        val spec = GetIssuerMetadataEndpointCommand.specDescriptorFor(issuer).pathPatterns
        val prefix = GetIssuerMetadataEndpointCommand.legacyPrefixDescriptorFor(issuer).pathPatterns

        assertEquals(listOf("/.well-known/openid-credential-issuer/tenant/issuer"), spec)
        assertEquals(listOf("/tenant/issuer/.well-known/openid-credential-issuer"), prefix)
        assertTrue(spec.toSet().intersect(prefix.toSet()).isEmpty())
    }

    @Test
    fun `runtime descriptor retains catalog aliases alongside configured issuer paths`() {
        val runtime =
            GetIssuerMetadataEndpointCommand
                .descriptorFor("https://issuer.example/tenant/issuer")
                .pathPatterns

        assertTrue(GetIssuerMetadataEndpointCommand.BARE_PATH in runtime)
        assertTrue("${GetIssuerMetadataEndpointCommand.BARE_PATH}/{issuerPath...}" in runtime)
        assertTrue("/{issuerPath}${GetIssuerMetadataEndpointCommand.BARE_PATH}" in runtime)
        assertTrue("/{issuerPathParent}/{issuerPathChild}${GetIssuerMetadataEndpointCommand.BARE_PATH}" in runtime)
        assertTrue("${GetIssuerMetadataEndpointCommand.BARE_PATH}/tenant/issuer" in runtime)
        assertTrue("/tenant/issuer${GetIssuerMetadataEndpointCommand.BARE_PATH}" in runtime)
    }
}
