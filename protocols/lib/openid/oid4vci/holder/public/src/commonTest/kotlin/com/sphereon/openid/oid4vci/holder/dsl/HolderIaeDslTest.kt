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

package com.sphereon.openid.oid4vci.holder.dsl

import com.sphereon.openid.oid4vci.common.dsl.IaeInteractionType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HolderIaeDslTest {
    // -----------------------------------------------------------------------
    // initiateIaeArgs — VP presentation interaction type
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsWithVpPresentationInteractionType() {
        val args =
            initiateIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                clientId("wallet-app")
                redirectUri("https://wallet.example.com/callback")
                interactionTypes(IaeInteractionType.OPENID4VP_PRESENTATION)
            }

        assertEquals("https://as.example.com/iae", args.iaeEndpoint)
        assertEquals("wallet-app", args.clientId)
        assertEquals("https://wallet.example.com/callback", args.redirectUri)
        assertEquals(1, args.interactionTypesSupported.size)
        assertEquals("urn:openid:dcp:iae:openid4vp_presentation", args.interactionTypesSupported[0])
        assertNull(args.authorizationDetails)
        assertNull(args.scope)
        assertNull(args.codeChallenge)
        assertNull(args.codeChallengeMethod)
    }

    // -----------------------------------------------------------------------
    // initiateIaeArgs — multiple interaction types
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsWithMultipleInteractionTypes() {
        val args =
            initiateIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                clientId("wallet-app")
                redirectUri("https://wallet.example.com/callback")
                interactionTypes(IaeInteractionType.OPENID4VP_PRESENTATION, IaeInteractionType.REDIRECT_TO_WEB)
            }

        assertEquals(2, args.interactionTypesSupported.size)
        assertEquals("urn:openid:dcp:iae:openid4vp_presentation", args.interactionTypesSupported[0])
        assertEquals("urn:openid:dcp:iae:redirect_to_web", args.interactionTypesSupported[1])
    }

    // -----------------------------------------------------------------------
    // initiateIaeArgs — with PKCE block
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsWithPkceBlock() {
        val args =
            initiateIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                clientId("wallet-app")
                redirectUri("https://wallet.example.com/callback")
                interactionTypes(IaeInteractionType.REDIRECT_TO_WEB)
                pkce {
                    codeChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                    codeChallengeMethod("S256")
                }
            }

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", args.codeChallenge)
        assertEquals("S256", args.codeChallengeMethod)
    }

    // -----------------------------------------------------------------------
    // initiateIaeArgs — with PKCE inline shorthand
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsWithPkceInlineShorthand() {
        val args =
            initiateIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                clientId("wallet-app")
                redirectUri("https://wallet.example.com/callback")
                interactionTypes(IaeInteractionType.REDIRECT_TO_WEB)
                pkce("challenge-value-xyz", "S256")
            }

        assertEquals("challenge-value-xyz", args.codeChallenge)
        assertEquals("S256", args.codeChallengeMethod)
    }

    // -----------------------------------------------------------------------
    // initiateIaeArgs — with authorizationDetails builder
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsWithAuthorizationDetails() {
        val args =
            initiateIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                clientId("wallet-app")
                redirectUri("https://wallet.example.com/callback")
                interactionTypes(IaeInteractionType.OPENID4VP_PRESENTATION)
                authorizationDetails {
                    openidCredential("UniversityDegree")
                    openidCredential("MembershipCard", claims = listOf("given_name", "family_name"))
                }
            }

        val details = args.authorizationDetails
        assertNotNull(details)
        assertEquals(2, details.size)

        val first = details[0].jsonObject
        assertEquals("openid_credential", first["type"]?.jsonPrimitive?.content)
        assertEquals("UniversityDegree", first["credential_configuration_id"]?.jsonPrimitive?.content)

        val second = details[1].jsonObject
        assertEquals("MembershipCard", second["credential_configuration_id"]?.jsonPrimitive?.content)
        val claims = second["claims"]?.jsonArray
        assertNotNull(claims)
        assertEquals(2, claims.size)
        assertEquals("given_name", claims[0].jsonPrimitive.content)
        assertEquals("family_name", claims[1].jsonPrimitive.content)
    }

    // -----------------------------------------------------------------------
    // initiateIaeArgs — with scope
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsWithScope() {
        val args =
            initiateIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                clientId("wallet-app")
                redirectUri("https://wallet.example.com/callback")
                interactionTypes(IaeInteractionType.OPENID4VP_PRESENTATION)
                scope("openid UniversityDegree")
            }

        assertEquals("openid UniversityDegree", args.scope)
        assertNull(args.authorizationDetails)
    }

    // -----------------------------------------------------------------------
    // initiateIaeArgs — fails without interaction types
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsFailsWithoutInteractionTypes() {
        assertFailsWith<IllegalArgumentException> {
            initiateIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                clientId("wallet-app")
                redirectUri("https://wallet.example.com/callback")
                // no interactionTypes() call
            }
        }
    }

    // -----------------------------------------------------------------------
    // initiateIaeArgs — fails without iaeEndpoint
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsFailsWithoutIaeEndpoint() {
        assertFailsWith<IllegalArgumentException> {
            initiateIaeArgs {
                clientId("wallet-app")
                redirectUri("https://wallet.example.com/callback")
                interactionTypes(IaeInteractionType.OPENID4VP_PRESENTATION)
            }
        }
    }

    // -----------------------------------------------------------------------
    // initiateIaeArgs — fails without clientId
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsFailsWithoutClientId() {
        assertFailsWith<IllegalArgumentException> {
            initiateIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                redirectUri("https://wallet.example.com/callback")
                interactionTypes(IaeInteractionType.OPENID4VP_PRESENTATION)
            }
        }
    }

    // -----------------------------------------------------------------------
    // initiateIaeArgs — fails without redirectUri
    // -----------------------------------------------------------------------

    @Test
    fun initiateIaeArgsFailsWithoutRedirectUri() {
        assertFailsWith<IllegalArgumentException> {
            initiateIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                clientId("wallet-app")
                interactionTypes(IaeInteractionType.OPENID4VP_PRESENTATION)
            }
        }
    }

    // -----------------------------------------------------------------------
    // followUpIaeArgs — with VP response
    // -----------------------------------------------------------------------

    @Test
    fun followUpIaeArgsWithVpResponse() {
        val vpResponseJson: JsonObject =
            buildJsonObject {
                put("vp_token", JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.vp.sig"))
                put("presentation_submission", JsonPrimitive("{}"))
            }

        val args =
            followUpIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                authSession("wxroVrBY2MCq4dDNGXACS")
                vpResponse(vpResponseJson)
            }

        assertEquals("https://as.example.com/iae", args.iaeEndpoint)
        assertEquals("wxroVrBY2MCq4dDNGXACS", args.authSession)
        assertNotNull(args.openid4vpResponse)
        assertEquals("eyJhbGciOiJFUzI1NiJ9.vp.sig", args.openid4vpResponse!!["vp_token"]?.jsonPrimitive?.content)
        assertNull(args.codeVerifier)
    }

    // -----------------------------------------------------------------------
    // followUpIaeArgs — with code verifier (redirect-to-web)
    // -----------------------------------------------------------------------

    @Test
    fun followUpIaeArgsWithCodeVerifier() {
        val args =
            followUpIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                authSession("session-token-456")
                codeVerifier("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
            }

        assertEquals("session-token-456", args.authSession)
        assertEquals("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", args.codeVerifier)
        assertNull(args.openid4vpResponse)
    }

    // -----------------------------------------------------------------------
    // followUpIaeArgs — fails without authSession
    // -----------------------------------------------------------------------

    @Test
    fun followUpIaeArgsFailsWithoutAuthSession() {
        assertFailsWith<IllegalArgumentException> {
            followUpIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                // no authSession() call
            }
        }
    }

    // -----------------------------------------------------------------------
    // followUpIaeArgs — fails without iaeEndpoint
    // -----------------------------------------------------------------------

    @Test
    fun followUpIaeArgsFailsWithoutIaeEndpoint() {
        assertFailsWith<IllegalArgumentException> {
            followUpIaeArgs {
                authSession("session-token")
            }
        }
    }

    // -----------------------------------------------------------------------
    // followUpIaeArgs — both vpResponse and codeVerifier (spec allows)
    // -----------------------------------------------------------------------

    @Test
    fun followUpIaeArgsWithBothVpResponseAndCodeVerifier() {
        val vpResponseJson: JsonObject =
            buildJsonObject {
                put("vp_token", JsonPrimitive("some-vp-token"))
            }

        val args =
            followUpIaeArgs {
                iaeEndpoint("https://as.example.com/iae")
                authSession("session-xyz")
                vpResponse(vpResponseJson)
                codeVerifier("verifier-value")
            }

        assertNotNull(args.openid4vpResponse)
        assertEquals("verifier-value", args.codeVerifier)
    }
}
