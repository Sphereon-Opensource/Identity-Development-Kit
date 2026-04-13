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

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.ProofType
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Oid4vciMetadataDslTest {
    private val json = Json { ignoreUnknownKeys = true }

    // -----------------------------------------------------------------------
    // 1. Complete issuer metadata with multiple credential configurations
    // -----------------------------------------------------------------------

    @Test
    fun buildCompleteIssuerMetadataWithMultipleConfigs() {
        val metadata =
            issuerMetadata("https://issuer.example.com") {
                authorizationServer("https://auth.example.com")
                nonceEndpoint = "https://issuer.example.com/nonce"

                credentialConfiguration("UniversityDegree", CredentialFormat.SD_JWT_DC) {
                    vct = "https://credentials.example.com/university_degree"
                    scope = "UniversityDegree"
                    bindingMethod("did:key")
                    signingAlg(JwaAlgorithm.ES256)
                    proofType(ProofType.JWT, JwaAlgorithm.ES256)
                    display {
                        name = "University Degree"
                        locale = "en-US"
                        logo("https://university.example.edu/logo.png", "University Logo")
                        backgroundColor = "#12107c"
                        textColor = "#FFFFFF"
                    }
                }

                credentialConfiguration("EmployeeID", CredentialFormat.JWT_VC_JSON) {
                    scope = "EmployeeID"
                    credentialDefinition {
                        type("VerifiableCredential", "EmployeeIDCredential")
                    }
                }

                display {
                    name = "Example Issuer"
                    locale = "en-US"
                }
            }

        assertEquals("https://issuer.example.com", metadata.credentialIssuer)
        assertEquals("https://issuer.example.com/credential", metadata.credentialEndpoint)
        assertEquals("https://issuer.example.com/nonce", metadata.nonceEndpoint)
        assertEquals(listOf("https://auth.example.com"), metadata.authorizationServers)
        assertEquals(2, metadata.credentialConfigurationsSupported.size)

        val uniDegree = metadata.credentialConfigurationsSupported["UniversityDegree"]
        assertNotNull(uniDegree)
        assertEquals("dc+sd-jwt", uniDegree.format)
        assertEquals("https://credentials.example.com/university_degree", uniDegree.vct)
        assertEquals("UniversityDegree", uniDegree.scope)
        assertEquals(listOf("did:key"), uniDegree.cryptographicBindingMethodsSupported)
        assertEquals(listOf("ES256"), uniDegree.credentialSigningAlgValuesSupported)
        assertEquals(listOf("ES256"), uniDegree.proofTypesSupported?.get("jwt")?.proofSigningAlgValuesSupported)

        val uniDisplay = uniDegree.display?.firstOrNull()
        assertNotNull(uniDisplay)
        assertEquals("University Degree", uniDisplay.name)
        assertEquals("en-US", uniDisplay.locale)
        assertEquals("https://university.example.edu/logo.png", uniDisplay.logo?.uri)
        assertEquals("University Logo", uniDisplay.logo?.altText)
        assertEquals("#12107c", uniDisplay.backgroundColor)
        assertEquals("#FFFFFF", uniDisplay.textColor)

        val employeeId = metadata.credentialConfigurationsSupported["EmployeeID"]
        assertNotNull(employeeId)
        assertEquals("jwt_vc_json", employeeId.format)
        assertEquals("EmployeeID", employeeId.scope)
        assertEquals(listOf("VerifiableCredential", "EmployeeIDCredential"), employeeId.credentialDefinition?.type)

        assertEquals(1, metadata.display?.size)
        assertEquals("Example Issuer", metadata.display?.firstOrNull()?.name)
    }

    // -----------------------------------------------------------------------
    // 2. SD-JWT DC config with VCT
    // -----------------------------------------------------------------------

    @Test
    fun buildSdJwtDcConfigWithVct() {
        val config =
            credentialConfiguration(CredentialFormat.SD_JWT_DC) {
                vct = "https://credentials.example.com/identity_credential"
                scope = "IdentityCredential"
                bindingMethods("did:key", "did:jwk")
                signingAlgs(JwaAlgorithm.ES256, JwaAlgorithm.ES384)
                proofType(ProofType.JWT, JwaAlgorithm.ES256, JwaAlgorithm.ES384)
            }

        assertEquals("dc+sd-jwt", config.format)
        assertEquals("https://credentials.example.com/identity_credential", config.vct)
        assertEquals("IdentityCredential", config.scope)
        assertEquals(listOf("did:key", "did:jwk"), config.cryptographicBindingMethodsSupported)
        assertEquals(listOf("ES256", "ES384"), config.credentialSigningAlgValuesSupported)

        val proof = config.proofTypesSupported?.get("jwt")
        assertNotNull(proof)
        assertEquals(listOf("ES256", "ES384"), proof.proofSigningAlgValuesSupported)

        assertNull(config.doctype)
        assertNull(config.credentialDefinition)
    }

    // -----------------------------------------------------------------------
    // 3. mDOC config with doctype
    // -----------------------------------------------------------------------

    @Test
    fun buildMdocConfigWithDoctype() {
        val config =
            credentialConfiguration(CredentialFormat.MSO_MDOC) {
                doctype = "org.iso.18013.5.1.mDL"
                bindingMethod("cose_key")
                signingAlg(JwaAlgorithm.ES256)
                proofType(ProofType.JWT, JwaAlgorithm.ES256)
                display {
                    name = "Mobile Driving Licence"
                    locale = "en-GB"
                    backgroundColor = "#FFFFFF"
                }
            }

        assertEquals("mso_mdoc", config.format)
        assertEquals("org.iso.18013.5.1.mDL", config.doctype)
        assertEquals(listOf("cose_key"), config.cryptographicBindingMethodsSupported)
        assertEquals(listOf("ES256"), config.credentialSigningAlgValuesSupported)

        val display = config.display?.firstOrNull()
        assertNotNull(display)
        assertEquals("Mobile Driving Licence", display.name)
        assertEquals("en-GB", display.locale)
        assertEquals("#FFFFFF", display.backgroundColor)

        assertNull(config.vct)
        assertNull(config.credentialDefinition)
    }

    // -----------------------------------------------------------------------
    // 4. JWT VC JSON config with credential definition
    // -----------------------------------------------------------------------

    @Test
    fun buildJwtVcJsonConfigWithCredentialDefinition() {
        val config =
            credentialConfiguration(CredentialFormat.JWT_VC_JSON) {
                scope = "UniversityDegree"
                credentialDefinition {
                    context(
                        "https://www.w3.org/2018/credentials/v1",
                        "https://www.w3.org/2018/credentials/examples/v1",
                    )
                    type("VerifiableCredential", "UniversityDegreeCredential")
                    claim("degree") {
                        mandatory = true
                        valueType = "string"
                        display("Degree", "en-US")
                    }
                }
            }

        assertEquals("jwt_vc_json", config.format)
        assertEquals("UniversityDegree", config.scope)

        val def = config.credentialDefinition
        assertNotNull(def)
        assertEquals(listOf("VerifiableCredential", "UniversityDegreeCredential"), def.type)
        assertEquals(2, def.context?.size)
        assertTrue(def.context!!.contains("https://www.w3.org/2018/credentials/v1"))

        val degreeClaim = def.credentialSubject?.get("degree")
        assertNotNull(degreeClaim)
        assertEquals(true, degreeClaim.mandatory)
        assertEquals("string", degreeClaim.valueType)
        assertEquals("Degree", degreeClaim.display?.firstOrNull()?.name)
        assertEquals("en-US", degreeClaim.display?.firstOrNull()?.locale)
    }

    // -----------------------------------------------------------------------
    // 5. Config with display, claims, and proof types
    // -----------------------------------------------------------------------

    @Test
    fun buildConfigWithDisplayClaimsAndProofTypes() {
        val config =
            credentialConfiguration(CredentialFormat.SD_JWT_VC) {
                vct = "https://credentials.example.com/pid"
                bindingMethods("did:key", "jwk")
                signingAlg(JwaAlgorithm.ES256)
                proofType(ProofType.JWT, JwaAlgorithm.ES256)

                display {
                    name = "Personal ID"
                    locale = "en-US"
                    description = "Government-issued personal identity document"
                    logo("https://gov.example.com/logo.png", "Government Logo")
                    backgroundImage("https://gov.example.com/bg.png")
                    backgroundColor = "#003087"
                    textColor = "#FFFFFF"
                }

                claim("given_name") {
                    mandatory = true
                    valueType = "string"
                    display("Given Name", "en-US")
                }
                claim("family_name") {
                    mandatory = true
                    valueType = "string"
                    display("Family Name", "en-US")
                }
                claim("birth_date") {
                    mandatory = false
                    valueType = "string"
                }
            }

        assertEquals("vc+sd-jwt", config.format)

        val display = config.display?.firstOrNull()
        assertNotNull(display)
        assertEquals("Personal ID", display.name)
        assertEquals("en-US", display.locale)
        assertEquals("Government-issued personal identity document", display.description)
        assertEquals("https://gov.example.com/logo.png", display.logo?.uri)
        assertEquals("Government Logo", display.logo?.altText)
        assertEquals("https://gov.example.com/bg.png", display.backgroundImage?.uri)
        assertEquals("#003087", display.backgroundColor)
        assertEquals("#FFFFFF", display.textColor)

        assertNotNull(config.claims)
        assertEquals(3, config.claims?.size)
        assertEquals(true, config.claims?.get("given_name")?.mandatory)
        assertEquals("string", config.claims?.get("given_name")?.valueType)
        assertEquals(
            "Given Name",
            config.claims
                ?.get("given_name")
                ?.display
                ?.firstOrNull()
                ?.name,
        )
        assertEquals(false, config.claims?.get("birth_date")?.mandatory)

        assertNotNull(config.proofTypesSupported?.get("jwt"))
    }

    // -----------------------------------------------------------------------
    // 6. Config with multiple locales
    // -----------------------------------------------------------------------

    @Test
    fun buildConfigWithMultipleLocales() {
        val config =
            credentialConfiguration(CredentialFormat.SD_JWT_DC) {
                vct = "https://credentials.example.com/diploma"

                display {
                    name = "University Diploma"
                    locale = "en-US"
                    backgroundColor = "#1a237e"
                    textColor = "#FFFFFF"
                }
                display {
                    name = "Universitätsdiplom"
                    locale = "de-DE"
                    backgroundColor = "#1a237e"
                    textColor = "#FFFFFF"
                }
                display {
                    name = "Diplôme universitaire"
                    locale = "fr-FR"
                    backgroundColor = "#1a237e"
                    textColor = "#FFFFFF"
                }
            }

        assertEquals(3, config.display?.size)
        assertEquals("University Diploma", config.display?.get(0)?.name)
        assertEquals("en-US", config.display?.get(0)?.locale)
        assertEquals("Universitätsdiplom", config.display?.get(1)?.name)
        assertEquals("de-DE", config.display?.get(1)?.locale)
        assertEquals("Diplôme universitaire", config.display?.get(2)?.name)
        assertEquals("fr-FR", config.display?.get(2)?.locale)
    }

    // -----------------------------------------------------------------------
    // 7. OID4VCI 1.1 credential_metadata with path-based claims
    // -----------------------------------------------------------------------

    @Test
    fun buildConfigWithOid4vci11CredentialMetadata() {
        val config =
            credentialConfiguration(CredentialFormat.SD_JWT_DC) {
                vct = "https://credentials.example.com/identity_credential"

                credentialMetadata {
                    display {
                        name = "Identity Credential"
                        locale = "en-US"
                    }

                    claim("given_name") {
                        mandatory = true
                        display("Given Name", "en-US")
                        display("Vorname", "de-DE")
                    }

                    claim("address", "street_address") {
                        mandatory = false
                    }

                    claim("nationalities", 0)
                }
            }

        val meta = config.credentialMetadata
        assertNotNull(meta)

        assertEquals(1, meta.display?.size)
        assertEquals("Identity Credential", meta.display?.firstOrNull()?.name)

        assertNotNull(meta.claims)
        assertEquals(3, meta.claims?.size)

        val claim0 = meta.claims!![0]
        assertEquals(1, claim0.path.size)
        assertEquals("given_name", claim0.path[0].jsonPrimitive.content)
        assertEquals(true, claim0.mandatory)
        assertEquals(2, claim0.display?.size)
        assertEquals("Given Name", claim0.display?.get(0)?.name)
        assertEquals("en-US", claim0.display?.get(0)?.locale)
        assertEquals("Vorname", claim0.display?.get(1)?.name)
        assertEquals("de-DE", claim0.display?.get(1)?.locale)

        val claim1 = meta.claims!![1]
        assertEquals(2, claim1.path.size)
        assertEquals("address", claim1.path[0].jsonPrimitive.content)
        assertEquals("street_address", claim1.path[1].jsonPrimitive.content)
        assertEquals(false, claim1.mandatory)

        val claim2 = meta.claims!![2]
        assertEquals(2, claim2.path.size)
        assertEquals("nationalities", claim2.path[0].jsonPrimitive.content)
        assertEquals(
            0,
            claim2.path[1]
                .jsonPrimitive.content
                .toInt(),
        )
    }

    // -----------------------------------------------------------------------
    // 8. DSL output round-trips through JSON serialization
    // -----------------------------------------------------------------------

    @Test
    fun dslOutputRoundTripsViaJson() {
        val metadata =
            issuerMetadata("https://issuer.example.com") {
                authorizationServer("https://auth.example.com")
                nonceEndpoint = "https://issuer.example.com/nonce"
                batchCredentialIssuance(10)

                credentialConfiguration("IdentityCredential", CredentialFormat.SD_JWT_DC) {
                    vct = "https://credentials.example.com/identity"
                    bindingMethod("did:key")
                    signingAlg(JwaAlgorithm.ES256)
                    proofType(ProofType.JWT, JwaAlgorithm.ES256)
                    display {
                        name = "Identity Credential"
                        locale = "en-US"
                    }
                }
            }

        val encoded = json.encodeToString(metadata)
        val decoded = json.decodeFromString<CredentialIssuerMetadata>(encoded)

        assertEquals(metadata.credentialIssuer, decoded.credentialIssuer)
        assertEquals(metadata.credentialEndpoint, decoded.credentialEndpoint)
        assertEquals(metadata.nonceEndpoint, decoded.nonceEndpoint)
        assertEquals(metadata.authorizationServers, decoded.authorizationServers)
        assertEquals(metadata.batchCredentialIssuance?.batchSize, decoded.batchCredentialIssuance?.batchSize)

        val origConfig = metadata.credentialConfigurationsSupported["IdentityCredential"]!!
        val decodedConfig = decoded.credentialConfigurationsSupported["IdentityCredential"]
        assertNotNull(decodedConfig)
        assertEquals(origConfig.format, decodedConfig.format)
        assertEquals(origConfig.vct, decodedConfig.vct)
        assertEquals(origConfig.cryptographicBindingMethodsSupported, decodedConfig.cryptographicBindingMethodsSupported)
        assertEquals(origConfig.credentialSigningAlgValuesSupported, decodedConfig.credentialSigningAlgValuesSupported)
        assertEquals(origConfig.display?.firstOrNull()?.name, decodedConfig.display?.firstOrNull()?.name)
    }

    // -----------------------------------------------------------------------
    // 9. Default endpoint derivation from issuer URL
    // -----------------------------------------------------------------------

    @Test
    fun defaultEndpointsAreDerivedFromIssuerUrl() {
        val metadata =
            issuerMetadata("https://issuer.example.com") {
                credentialConfiguration("Test", CredentialFormat.JWT_VC_JSON) {
                    credentialDefinition { type("VerifiableCredential") }
                }
            }

        assertEquals("https://issuer.example.com/credential", metadata.credentialEndpoint)
        assertNull(metadata.nonceEndpoint)
        assertNull(metadata.authorizationServers)
        assertNull(metadata.batchCredentialEndpoint)
        assertNull(metadata.deferredCredentialEndpoint)
        assertNull(metadata.notificationEndpoint)
    }

    // -----------------------------------------------------------------------
    // 10. Batch credential issuance and response encryption metadata
    // -----------------------------------------------------------------------

    @Test
    fun buildIssuerMetadataWithEncryptionAndBatch() {
        val metadata =
            issuerMetadata("https://issuer.example.com") {
                batchCredentialIssuance(5)

                credentialResponseEncryption {
                    alg("ECDH-ES", "RSA-OAEP")
                    enc("A256GCM")
                    zip("DEF")
                    encryptionRequired = true
                }

                credentialConfiguration("Test", CredentialFormat.SD_JWT_DC) {
                    vct = "https://credentials.example.com/test"
                }
            }

        assertEquals(5, metadata.batchCredentialIssuance?.batchSize)

        val enc = metadata.credentialResponseEncryption
        assertNotNull(enc)
        assertEquals(listOf("ECDH-ES", "RSA-OAEP"), enc.algValuesSupported)
        assertEquals(listOf("A256GCM"), enc.encValuesSupported)
        assertEquals(listOf("DEF"), enc.zipValuesSupported)
        assertTrue(enc.encryptionRequired)
    }
}
