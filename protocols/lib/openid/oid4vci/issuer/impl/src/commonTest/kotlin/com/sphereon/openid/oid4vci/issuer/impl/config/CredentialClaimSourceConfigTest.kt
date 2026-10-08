/*
 * © 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.openid.oid4vci.issuer.config.CredentialClaimSource
import com.sphereon.openid.oid4vci.issuer.config.CredentialClaimSourceKind
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Claim sources are read from the credential configuration's own claim definitions. */
class CredentialClaimSourceConfigTest {
    private val baseProperties =
        mapOf<String, Any>(
            "oid4vci.issuer.identifier" to "https://issuer.example.com",
            "oid4vci.issuer.credentialConfigurationIds" to "EuPid",
            "oid4vci.issuer.credentials.[EuPid].format" to "dc+sd-jwt",
            "oid4vci.issuer.credentials.[EuPid].claims.[family_name].mandatory" to "true",
        )

    private fun provider(properties: Map<String, Any>) =
        ConfigDrivenOid4vciIssuerConfigProvider(TestSessionExecution(TestPrincipalConfigService(properties)))

    @Test
    fun onlyClaimDefinitionsWithASourceAreReturned() {
        val provider =
            provider(
                baseProperties +
                    mapOf("oid4vci.issuer.credentials.[EuPid].claims.[given_name].source" to "federation.userinfo.given_name"),
            )

        val sources = provider.credentialClaimSources("EuPid")

        assertTrue(sources.isOk)
        assertEquals(
            mapOf("given_name" to CredentialClaimSource(CredentialClaimSourceKind.FEDERATION_USERINFO, "given_name")),
            sources.value,
        )
        val configuration = provider.credentialConfigurations.getValue("EuPid")
        val claimPaths =
            configuration.credentialMetadata?.claims.orEmpty().map { claim -> claim.path.map { it.jsonPrimitive.content } } +
                configuration.claims.orEmpty().map { it.path }
        assertTrue(
            listOf("given_name") in claimPaths,
            "a claim that only declares a source is still part of the credential configuration: $claimPaths",
        )
    }

    @Test
    fun noSourcesConfiguredMeansNoClaimSources() {
        val sources = provider(baseProperties).credentialClaimSources("EuPid")

        assertTrue(sources.isOk)
        assertTrue(sources.value.isEmpty())
    }

    @Test
    fun unknownSourceFailsTheConfiguration() {
        val sources =
            provider(
                baseProperties +
                    mapOf("oid4vci.issuer.credentials.[EuPid].claims.[given_name].source" to "userinfo.given_name"),
            ).credentialClaimSources("EuPid")

        assertTrue(sources.isErr)
        assertEquals("invalid_credential_configuration", sources.error.code)
    }
}
