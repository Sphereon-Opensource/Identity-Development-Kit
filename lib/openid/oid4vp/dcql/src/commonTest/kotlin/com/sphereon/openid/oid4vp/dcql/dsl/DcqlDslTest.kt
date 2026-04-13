/*
 * Copyright (c) 2025 Sphereon B.V.
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
package com.sphereon.openid.oid4vp.dcql.dsl

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.openid.oid4vp.dcql.DcqlTrustedAuthority
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DcqlDslTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun buildSimpleCredentialQuery() {
        val query = dcqlQuery {
            credential("identity") {
                sdJwtVc {
                    vctValues("https://credentials.example.com/identity")
                }
                claim("given_name")
                claim("family_name")
            }
        }

        assertNotNull(query.credentials)
        assertEquals(1, query.credentials!!.size)

        val credential = query.credentials!!.first()
        assertEquals("identity", credential.id)
        assertEquals("dc+sd-jwt", credential.format)
        assertNotNull(credential.meta)
        assertNotNull(credential.claims)
        assertEquals(2, credential.claims!!.size)
        assertEquals(listOf("given_name"), credential.claims!![0].path)
        assertEquals(listOf("family_name"), credential.claims!![1].path)
    }

    @Test
    fun buildQueryWithSdJwtVcMetadata() {
        val query = dcqlQuery {
            credential("test") {
                sdJwtVc {
                    vctValues("https://credentials.example.com/vc1", "https://credentials.example.com/vc2")
                    sdJwtAlgorithms("ES256", "ES384")
                    kbJwtAlgorithms("ES256")
                }
            }
        }

        val credential = query.credentials!!.first()
        assertEquals("dc+sd-jwt", credential.format)
        assertNotNull(credential.meta)

        // Verify meta contains expected values
        val meta = credential.meta!!
        assertNotNull(meta["vct_values"])
        assertNotNull(meta["sd_jwt_alg_values"])
        assertNotNull(meta["kb_jwt_alg_values"])
    }

    @Test
    fun buildQueryWithMdocFormat() {
        val query = dcqlQuery {
            credential("mdl") {
                mDoc {
                    mDL()
                    namespaces("org.iso.18013.5.1", "org.iso.18013.5.1.aamva")
                }
                claim("family_name")
                claim("given_name")
                claim("portrait")
            }
        }

        val credential = query.credentials!!.first()
        assertEquals("mso_mdoc", credential.format)
        assertNotNull(credential.meta)

        val meta = credential.meta!!
        assertEquals("org.iso.18013.5.1.mDL", meta["doctype_value"]?.toString()?.trim('"'))
    }

    @Test
    fun buildQueryWithNestedClaimPaths() {
        val query = dcqlQuery {
            credential("test") {
                claim("address" then "street_address")
                claim("address" then "locality")
                claim("address" then "postal_code")
            }
        }

        val claims = query.credentials!!.first().claims!!
        assertEquals(listOf("address", "street_address"), claims[0].path)
        assertEquals(listOf("address", "locality"), claims[1].path)
        assertEquals(listOf("address", "postal_code"), claims[2].path)
    }

    @Test
    fun buildQueryWithClaimValueConstraints() {
        val query = dcqlQuery {
            credential("age_verification") {
                claim(listOf("over_18")) {
                    values(true)
                }
                claim(listOf("nationality")) {
                    values("US", "CA", "GB")
                }
            }
        }

        val claims = query.credentials!!.first().claims!!
        assertEquals(listOf("over_18"), claims[0].path)
        assertNotNull(claims[0].values)
        assertEquals(listOf("nationality"), claims[1].path)
        assertNotNull(claims[1].values)
        assertEquals(3, claims[1].values!!.size)
    }

    @Test
    fun buildQueryWithIntentToRetain() {
        val query = dcqlQuery {
            credential("contact") {
                claim(listOf("email")) {
                    intentToRetain()
                }
            }
        }

        val claim = query.credentials!!.first().claims!!.first()
        assertEquals(true, claim.intent_to_retain)
    }

    @Test
    fun buildQueryWithTrustedAuthorities() {
        val query = dcqlQuery {
            credential("identity") {
                sdJwtVc {
                    vctValues("https://credentials.example.com/identity")
                }
                trustedAuthorities {
                    openIdFederation("https://federation.example.com")
                    etsiTrustedList("https://eidas.europa.eu/TL/EN_TL.xml")
                    authorityKeyIdentifier("base64-encoded-aki")
                }
            }
        }

        val authorities = query.credentials!!.first().trusted_authorities
        assertNotNull(authorities)
        assertEquals(3, authorities.size)

        assertEquals(DcqlTrustedAuthority.TYPE_OPENID_FEDERATION, authorities[0].type)
        assertEquals(DcqlTrustedAuthority.TYPE_ETSI_TRUSTED_LIST, authorities[1].type)
        assertEquals(DcqlTrustedAuthority.TYPE_AUTHORITY_KEY_IDENTIFIER, authorities[2].type)
    }

    @Test
    fun buildQueryWithHolderBindingOptions() {
        val query = dcqlQuery {
            credential("test") {
                requireHolderBinding(false)
                allowMultiple(true)
            }
        }

        val credential = query.credentials!!.first()
        assertEquals(false, credential.require_cryptographic_holder_binding)
        assertEquals(true, credential.multiple)
    }

    @Test
    fun buildQueryWithCredentialSets() {
        val query = dcqlQuery {
            credentialSet {
                required()
                option("passport")
                option("drivers_license")
                option("national_id")
            }
        }

        assertNotNull(query.credential_sets)
        assertEquals(1, query.credential_sets!!.size)

        val credentialSet = query.credential_sets!!.first()
        assertEquals(true, credentialSet.required)
        assertEquals(3, credentialSet.options.size)
        assertEquals(listOf("passport"), credentialSet.options[0].credential_ids)
    }

    @Test
    fun buildQueryWithMultipleCredentialIdsPerOption() {
        val query = dcqlQuery {
            credentialSet {
                option("university_id", "transcript")
                option("diploma")
            }
        }

        val options = query.credential_sets!!.first().options
        assertEquals(listOf("university_id", "transcript"), options[0].credential_ids)
        assertEquals(listOf("diploma"), options[1].credential_ids)
    }

    @Test
    fun buildQueryWithBothCredentialsAndCredentialSets() {
        val query = dcqlQuery {
            credential("identity") {
                claim("name")
            }
            credentialSet {
                option("passport")
                option("drivers_license")
            }
        }

        assertNotNull(query.credentials)
        assertNotNull(query.credential_sets)
        assertEquals(1, query.credentials!!.size)
        assertEquals(1, query.credential_sets!!.size)
    }

    @Test
    fun buildQueryWithClaimSets() {
        val query = dcqlQuery {
            credential("identity") {
                claimSet("basic_identity", listOf("first_name", "last_name", "birth_date"))
                claimSet("contact_info", listOf("email", "phone"))
            }
        }

        val claimSets = query.credentials!!.first().claim_sets
        assertNotNull(claimSets)
        assertEquals(2, claimSets.size)
        assertEquals("basic_identity", claimSets[0].id)
        assertEquals(listOf("first_name", "last_name", "birth_date"), claimSets[0].claims)
    }

    @Test
    fun buildQueryWithJwtVcJsonFormat() {
        val query = dcqlQuery {
            credential("degree") {
                jwtVcJson {
                    types("VerifiableCredential", "UniversityDegreeCredential")
                    algorithms("ES256", "ES384")
                }
            }
        }

        val credential = query.credentials!!.first()
        assertEquals("jwt_vc_json", credential.format)
        assertNotNull(credential.meta)
    }

    @Test
    fun buildQueryWithLdpVcFormat() {
        val query = dcqlQuery {
            credential("degree") {
                ldpVc {
                    types("VerifiableCredential", "UniversityDegreeCredential")
                    proofTypes("Ed25519Signature2020", "JsonWebSignature2020")
                }
            }
        }

        val credential = query.credentials!!.first()
        assertEquals("ldp_vc", credential.format)
        assertNotNull(credential.meta)
    }

    @Test
    fun queryIsValidatedOnBuild() {
        // Empty credentials array should fail validation
        assertFailsWith<IllegalArgumentException> {
            dcqlQuery {
                // No credentials added - this should fail
            }
        }
    }

    @Test
    fun dcqlQueryResultReturnsIdkResultOnValidationFailure() {
        val result = dcqlQueryResult {
            // No credentials added - this should return an error
        }

        assertTrue(result is Err)
    }

    @Test
    fun dcqlQueryResultReturnsOkOnValidQuery() {
        val result = dcqlQueryResult {
            credential("test") {
                claim("name")
            }
        }

        assertTrue(result is Ok)
        assertEquals("test", (result as Ok).value.credentials!!.first().id)
    }

    @Test
    fun serializeQueryBuiltWithDsl() {
        val query = dcqlQuery {
            credential("identity_credential") {
                sdJwtVc {
                    vctValues("https://credentials.example.com/identity_credential")
                }
                claim("given_name")
                claim("family_name")
                claim("birthdate")
            }
        }

        // Serialize and deserialize to verify structure
        val jsonString = json.encodeToString(com.sphereon.openid.oid4vp.dcql.DcqlQuery.serializer(), query)
        val deserializedQuery = json.decodeFromString<com.sphereon.openid.oid4vp.dcql.DcqlQuery>(jsonString)

        assertEquals(query, deserializedQuery)
    }

    @Test
    fun infixThenBuildsCorrectPaths() {
        val path1 = "address" then "street"
        assertEquals(listOf("address", "street"), path1)

        val path2 = path1 then "line1"
        assertEquals(listOf("address", "street", "line1"), path2)
    }

    @Test
    fun sdJwtVcWithoutMetadata() {
        val query = dcqlQuery {
            credential("test") {
                sdJwtVc()
                claim("name")
            }
        }

        val credential = query.credentials!!.first()
        assertEquals("dc+sd-jwt", credential.format)
        assertNull(credential.meta)
    }
}
