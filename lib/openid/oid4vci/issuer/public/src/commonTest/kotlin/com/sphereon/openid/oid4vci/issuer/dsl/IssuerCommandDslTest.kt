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

package com.sphereon.openid.oid4vci.issuer.dsl

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuerCommandDslTest {
    // -----------------------------------------------------------------------
    // createOfferArgs — pre-authorized code grant with tx code
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsWithPreAuthGrantAndTxCode() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credentials("UniversityDegree")
                preAuthorizedCodeGrant(txCodeRequired = true)
            }

        assertEquals("https://issuer.example.com", args.issuerId)
        assertEquals(listOf("UniversityDegree"), args.credentialConfigurationIds)
        assertEquals(true, args.preAuthorizedCodeGrant)
        assertEquals(false, args.authorizationCodeGrant)
        assertEquals(true, args.txCodeRequired)
        assertNull(args.preSeededAttributes)
        assertEquals(600L, args.offerTtlSeconds)
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — pre-authorized code grant without tx code
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsWithPreAuthGrantWithoutTxCode() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credentials("MembershipCard")
                preAuthorizedCodeGrant()
            }

        assertEquals(true, args.preAuthorizedCodeGrant)
        assertEquals(false, args.txCodeRequired)
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — authorization code grant only
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsWithAuthCodeGrantOnly() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credentials("EmployeeID")
                authorizationCodeGrant()
            }

        assertEquals(false, args.preAuthorizedCodeGrant)
        assertEquals(true, args.authorizationCodeGrant)
        assertEquals(false, args.txCodeRequired)
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — both grants enabled
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsWithBothGrants() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credentials("UniversityDegree")
                preAuthorizedCodeGrant(txCodeRequired = false)
                authorizationCodeGrant()
            }

        assertEquals(true, args.preAuthorizedCodeGrant)
        assertEquals(true, args.authorizationCodeGrant)
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — with pre-seeded attributes
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsWithPreSeededAttributes() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credentials("UniversityDegree")
                preAuthorizedCodeGrant()
                attributes {
                    put("given_name", JsonPrimitive("Jane"))
                    put("family_name", JsonPrimitive("Doe"))
                    put("degree", JsonPrimitive("Bachelor of Science"))
                }
            }

        val attrs = args.preSeededAttributes
        assertNotNull(attrs)
        assertEquals(3, attrs.size)
        assertEquals(JsonPrimitive("Jane"), attrs["given_name"])
        assertEquals(JsonPrimitive("Doe"), attrs["family_name"])
        assertEquals(JsonPrimitive("Bachelor of Science"), attrs["degree"])
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — without attributes gives null
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsWithoutAttributesGivesNull() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credentials("UniversityDegree")
                preAuthorizedCodeGrant()
            }

        assertNull(args.preSeededAttributes)
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — multiple credential configurations
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsWithMultipleCredentialConfigurations() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credentials("UniversityDegree", "MembershipCard", "EmployeeID")
                preAuthorizedCodeGrant()
            }

        assertEquals(3, args.credentialConfigurationIds.size)
        assertTrue(args.credentialConfigurationIds.contains("UniversityDegree"))
        assertTrue(args.credentialConfigurationIds.contains("MembershipCard"))
        assertTrue(args.credentialConfigurationIds.contains("EmployeeID"))
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — single credential via credential() function
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsWithSingleCredentialFunction() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credential("PID")
                preAuthorizedCodeGrant()
            }

        assertEquals(listOf("PID"), args.credentialConfigurationIds)
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — mixing credential() and credentials() accumulates
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsMixingCredentialAndCredentials() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credential("PID")
                credentials("UniversityDegree", "MembershipCard")
                preAuthorizedCodeGrant()
            }

        assertEquals(3, args.credentialConfigurationIds.size)
        assertEquals("PID", args.credentialConfigurationIds[0])
        assertEquals("UniversityDegree", args.credentialConfigurationIds[1])
        assertEquals("MembershipCard", args.credentialConfigurationIds[2])
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — custom TTL
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsWithCustomTtl() {
        val args =
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credentials("UniversityDegree")
                preAuthorizedCodeGrant()
                offerTtlSeconds = 300L
            }

        assertEquals(300L, args.offerTtlSeconds)
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — missing issuerId must fail
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsMissingIssuerIdFails() {
        assertFailsWith<IllegalArgumentException> {
            createOfferArgs {
                credentials("UniversityDegree")
                preAuthorizedCodeGrant()
            }
        }
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — missing credentials must fail
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsMissingCredentialsFails() {
        assertFailsWith<IllegalArgumentException> {
            createOfferArgs {
                issuerId("https://issuer.example.com")
                preAuthorizedCodeGrant()
            }
        }
    }

    // -----------------------------------------------------------------------
    // createOfferArgs — missing grant type must fail
    // -----------------------------------------------------------------------

    @Test
    fun createOfferArgsMissingGrantTypeFails() {
        assertFailsWith<IllegalArgumentException> {
            createOfferArgs {
                issuerId("https://issuer.example.com")
                credentials("UniversityDegree")
                // no preAuthorizedCodeGrant() or authorizationCodeGrant()
            }
        }
    }
}
