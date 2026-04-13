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
 */

package com.sphereon.openid.oid4vci.common.dsl

import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValueObjectsTest {
    // -----------------------------------------------------------------------
    // CredentialTarget
    // -----------------------------------------------------------------------

    @Test
    fun credentialTargetByConfigurationId() {
        val target = CredentialTarget.ByConfigurationId("UniversityDegree")
        assertEquals("UniversityDegree", target.credentialConfigurationId)
        assertTrue(target is CredentialTarget.ByConfigurationId)
    }

    @Test
    fun credentialTargetByIdentifier() {
        val target = CredentialTarget.ByIdentifier("cred-identifier-abc")
        assertEquals("cred-identifier-abc", target.credentialIdentifier)
        assertTrue(target is CredentialTarget.ByIdentifier)
    }

    @Test
    fun credentialTargetSealedDispatches() {
        val targets: List<CredentialTarget> =
            listOf(
                CredentialTarget.ByConfigurationId("UniversityDegree"),
                CredentialTarget.ByIdentifier("cred-id-xyz"),
            )

        val configIds = targets.filterIsInstance<CredentialTarget.ByConfigurationId>().map { it.credentialConfigurationId }
        val identifiers = targets.filterIsInstance<CredentialTarget.ByIdentifier>().map { it.credentialIdentifier }

        assertEquals(listOf("UniversityDegree"), configIds)
        assertEquals(listOf("cred-id-xyz"), identifiers)
    }

    // -----------------------------------------------------------------------
    // IaeInteractionType.fromUrn
    // -----------------------------------------------------------------------

    @Test
    fun iaeInteractionTypeFromUrnVpPresentation() {
        val type = IaeInteractionType.fromUrn("urn:openid:dcp:iae:openid4vp_presentation")
        assertNotNull(type)
        assertEquals(IaeInteractionType.OPENID4VP_PRESENTATION, type)
        assertEquals("urn:openid:dcp:iae:openid4vp_presentation", type.urn)
    }

    @Test
    fun iaeInteractionTypeFromUrnRedirectToWeb() {
        val type = IaeInteractionType.fromUrn("urn:openid:dcp:iae:redirect_to_web")
        assertNotNull(type)
        assertEquals(IaeInteractionType.REDIRECT_TO_WEB, type)
        assertEquals("urn:openid:dcp:iae:redirect_to_web", type.urn)
    }

    @Test
    fun iaeInteractionTypeFromUrnInvalidReturnsNull() {
        val type = IaeInteractionType.fromUrn("urn:unknown:interaction_type")
        assertNull(type)
    }

    @Test
    fun iaeInteractionTypeFromUrnEmptyReturnsNull() {
        val type = IaeInteractionType.fromUrn("")
        assertNull(type)
    }

    @Test
    fun iaeInteractionTypeAllEntriesHaveUniqueUrns() {
        val urns = IaeInteractionType.entries.map { it.urn }
        assertEquals(urns.size, urns.toSet().size, "All IaeInteractionType entries must have unique URNs")
    }

    @Test
    fun iaeInteractionTypeFromUrnRoundTrip() {
        for (type in IaeInteractionType.entries) {
            val resolved = IaeInteractionType.fromUrn(type.urn)
            assertEquals(type, resolved, "fromUrn(${type.urn}) must return $type")
        }
    }

    // -----------------------------------------------------------------------
    // AuthenticatedEndpoint
    // -----------------------------------------------------------------------

    @Test
    fun authenticatedEndpointConstruction() {
        val endpoint =
            AuthenticatedEndpoint(
                url = "https://issuer.example.com/credential",
                accessToken = "Bearer abc123",
            )

        assertEquals("https://issuer.example.com/credential", endpoint.url)
        assertEquals("Bearer abc123", endpoint.accessToken)
    }

    @Test
    fun authenticatedEndpointEquality() {
        val ep1 = AuthenticatedEndpoint("https://example.com", "tok-1")
        val ep2 = AuthenticatedEndpoint("https://example.com", "tok-1")
        val ep3 = AuthenticatedEndpoint("https://example.com", "tok-2")

        assertEquals(ep1, ep2)
        assertTrue(ep1 != ep3)
    }

    // -----------------------------------------------------------------------
    // PkceChallenge defaults
    // -----------------------------------------------------------------------

    @Test
    fun pkceChallengeDefaultMethod() {
        val pkce = PkceChallenge(codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkce.codeChallenge)
        assertEquals("S256", pkce.codeChallengeMethod)
    }

    @Test
    fun pkceChallengeCustomMethod() {
        val pkce = PkceChallenge(codeChallenge = "challenge", codeChallengeMethod = "plain")
        assertEquals("plain", pkce.codeChallengeMethod)
    }

    @Test
    fun pkceBuilderProducesCorrectChallenge() {
        val pkce =
            PkceBuilder()
                .apply {
                    codeChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                    codeChallengeMethod("S256")
                }.build()

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkce.codeChallenge)
        assertEquals("S256", pkce.codeChallengeMethod)
    }

    @Test
    fun pkceBuilderDefaultMethod() {
        val pkce =
            PkceBuilder()
                .apply {
                    codeChallenge("some-challenge")
                }.build()

        assertEquals("S256", pkce.codeChallengeMethod)
    }

    @Test
    fun pkceBuilderMissingChallengeFails() {
        assertFailsWith<IllegalArgumentException> {
            PkceBuilder().build()
        }
    }

    // -----------------------------------------------------------------------
    // RetryPolicy defaults
    // -----------------------------------------------------------------------

    @Test
    fun retryPolicyDefaults() {
        val policy = RetryPolicy()
        assertEquals(3, policy.maxAttempts)
        assertEquals(5, policy.initialIntervalSeconds)
        assertEquals(2.0, policy.backoffMultiplier)
    }

    @Test
    fun retryPolicyCustomValues() {
        val policy = RetryPolicy(maxAttempts = 10, initialIntervalSeconds = 30, backoffMultiplier = 1.5)
        assertEquals(10, policy.maxAttempts)
        assertEquals(30, policy.initialIntervalSeconds)
        assertEquals(1.5, policy.backoffMultiplier)
    }

    @Test
    fun retryPolicyEquality() {
        val p1 = RetryPolicy(maxAttempts = 5, initialIntervalSeconds = 10, backoffMultiplier = 2.0)
        val p2 = RetryPolicy(maxAttempts = 5, initialIntervalSeconds = 10, backoffMultiplier = 2.0)
        assertEquals(p1, p2)
    }

    // -----------------------------------------------------------------------
    // AuthorizationDetailsBuilder
    // -----------------------------------------------------------------------

    @Test
    fun authorizationDetailsBuilderWithSingleCredential() {
        val details =
            AuthorizationDetailsBuilder()
                .apply {
                    openidCredential("UniversityDegree")
                }.build()

        assertEquals(1, details.size)
    }

    @Test
    fun authorizationDetailsBuilderWithMultipleCredentials() {
        val details =
            AuthorizationDetailsBuilder()
                .apply {
                    openidCredential("UniversityDegree")
                    openidCredential("MembershipCard")
                }.build()

        assertEquals(2, details.size)
    }

    @Test
    fun authorizationDetailsBuilderEmptyProducesEmptyList() {
        val details = AuthorizationDetailsBuilder().build()
        assertTrue(details.isEmpty())
    }
}
