/*
 * © 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.config

import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialClaimSourceTest {
    private val federatedToken =
        token(
            upstreamIssuer = "https://idp.example.test/realms/acme",
            upstreamSubject = "idp-user-42",
            userinfo =
                mapOf(
                    "given_name" to JsonPrimitive("Ada"),
                    "family_name" to JsonPrimitive("Lovelace"),
                    "email" to JsonPrimitive("ada@example.test"),
                ),
        )

    @Test
    fun mappedFederatedClaimIsIncludedUnderTheCredentialClaimName() {
        val resolved =
            resolveCredentialClaimSources(
                sources =
                    mapOf(
                        "given_name" to source("federation.userinfo.given_name"),
                        "contact_email" to source("federation.userinfo.email"),
                        "idp_subject" to source("federation.subject"),
                        "idp" to source("federation.issuer"),
                    ),
                token = federatedToken,
            )

        assertEquals(
            mapOf(
                "given_name" to JsonPrimitive("Ada"),
                "contact_email" to JsonPrimitive("ada@example.test"),
                "idp_subject" to JsonPrimitive("idp-user-42"),
                "idp" to JsonPrimitive("https://idp.example.test/realms/acme"),
            ),
            resolved,
        )
    }

    @Test
    fun unmappedFederatedClaimsAreExcluded() {
        val resolved =
            resolveCredentialClaimSources(
                sources = mapOf("given_name" to source("federation.userinfo.given_name")),
                token = federatedToken,
            )

        assertEquals(setOf("given_name"), resolved.keys)
    }

    @Test
    fun noClaimSourcesMeansNoFederatedClaims() {
        assertTrue(resolveCredentialClaimSources(emptyMap(), federatedToken).isEmpty())
    }

    @Test
    fun claimTheIdpDidNotReleaseIsOmitted() {
        val resolved =
            resolveCredentialClaimSources(
                sources = mapOf("birth_date" to source("federation.userinfo.birthdate")),
                token = federatedToken,
            )

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun withoutFederationTheSourceIsEmpty() {
        val localToken =
            token(
                upstreamIssuer = null,
                upstreamSubject = null,
                userinfo = mapOf("given_name" to JsonPrimitive("Local")),
            )
        val resolved =
            resolveCredentialClaimSources(
                sources =
                    mapOf(
                        "given_name" to source("federation.userinfo.given_name"),
                        "idp_subject" to source("federation.subject"),
                        "idp" to source("federation.issuer"),
                    ),
                token = localToken,
            )

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun parseRejectsUnknownOrIncompleteReferences() {
        assertNull(CredentialClaimSource.parse("userinfo.given_name"))
        assertNull(CredentialClaimSource.parse("federation.userinfo."))
        assertNull(CredentialClaimSource.parse("federation.userinfo.given name"))
        assertNull(CredentialClaimSource.parse("federation"))
        assertEquals(
            CredentialClaimSource(CredentialClaimSourceKind.FEDERATION_USERINFO, "given_name"),
            CredentialClaimSource.parse(" federation.userinfo.given_name "),
        )
    }

    private fun source(value: String): CredentialClaimSource = requireNotNull(CredentialClaimSource.parse(value))

    private fun token(
        upstreamIssuer: String?,
        upstreamSubject: String?,
        userinfo: Map<String, JsonElement>?,
    ) = ValidatedTokenContext(
        subject = "local-subject",
        clientId = "wallet",
        scope = "openid",
        credentialConfigurationIds = listOf("EuPid"),
        userinfoClaims = userinfo,
        upstreamSubject = upstreamSubject,
        upstreamIssuer = upstreamIssuer,
    )
}
