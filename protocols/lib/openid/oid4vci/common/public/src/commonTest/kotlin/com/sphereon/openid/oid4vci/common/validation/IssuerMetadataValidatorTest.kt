/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.common.validation

import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssuerMetadataValidatorTest {
    private fun validMetadata() =
        CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example.com",
            credentialEndpoint = "https://issuer.example.com/credential",
            credentialConfigurationsSupported =
                mapOf(
                    "IdentityCredential" to CredentialConfigurationSupported(
                        format = "dc+sd-jwt",
                        vct = "https://credentials.example/identity",
                        cryptographicBindingMethodsSupported = listOf("jwk"),
                        credentialSigningAlgValuesSupported = listOf(JsonPrimitive("ES256")),
                        proofTypesSupported = mapOf("jwt" to ProofTypeSupported(listOf("ES256"))),
                    ),
                ),
        )

    @Test
    fun validMetadataPasses() {
        val result = issuerMetadataValidator(validMetadata())
        assertTrue(result is Valid, "Valid issuer metadata should pass validation")
    }

    @Test
    fun emptyIssuerFails() {
        val metadata = validMetadata().copy(credentialIssuer = "")
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "An empty credentialIssuer should fail validation")
    }

    @Test
    fun emptyEndpointFails() {
        val metadata = validMetadata().copy(credentialEndpoint = "")
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "An empty credentialEndpoint should fail validation")
    }

    @Test
    fun emptyConfigsFails() {
        val metadata = validMetadata().copy(credentialConfigurationsSupported = emptyMap())
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "An empty credentialConfigurationsSupported map should fail validation")
    }

    @Test
    fun issuerIdentifierRequiresHttpsAbsoluteUrlWithoutQueryOrFragment() {
        assertTrue(issuerMetadataValidator(validMetadata()) is Valid)
        for (invalid in listOf(
            "http://issuer.example.com",
            "issuer.example.com",
            "/issuer",
            "https://issuer.example.com?mode=one",
            "https://issuer.example.com#fragment",
        )) {
            assertTrue(issuerMetadataValidator(validMetadata().copy(credentialIssuer = invalid)) is Invalid, invalid)
        }
    }

    @Test
    fun credentialEndpointRequiresHttpsAbsoluteUrl() {
        assertTrue(issuerMetadataValidator(validMetadata()) is Valid)
        for (invalid in listOf(
            "http://issuer.example.com/credential",
            "issuer.example.com/credential",
            "/credential",
        )) {
            assertTrue(issuerMetadataValidator(validMetadata().copy(credentialEndpoint = invalid)) is Invalid, invalid)
        }
    }

    @Test
    fun independentlyAdvertisedAuthorizationServerIdentifierIsAccepted() {
        assertTrue(issuerMetadataValidator(validMetadata()) is Valid)
        val metadata = validMetadata().copy(authorizationServers = listOf("https://authorization.example/tenant"))
        assertTrue(issuerMetadataValidator(metadata) is Valid)
    }

    @Test
    fun presentAuthorizationServerIdentifiersRequireHttpsAbsoluteIssuerUrls() {
        assertTrue(issuerMetadataValidator(validMetadata()) is Valid)
        for (invalid in listOf(
            "http://authorization.example",
            "authorization.example",
            "/authorization",
            "https://authorization.example?mode=one",
            "https://authorization.example#fragment",
        )) {
            val metadata = validMetadata().copy(authorizationServers = listOf("https://valid.example", invalid))
            assertTrue(issuerMetadataValidator(metadata) is Invalid, invalid)
        }
    }

    @Test
    fun issuerIdentifierAllowsNondefaultPortAndPreservesCaseSensitivePath() {
        val issuer = "https://issuer.example.com:8443/Tenant/CaseSensitive"
        val metadata = validMetadata().copy(credentialIssuer = issuer)

        assertTrue(issuerMetadataValidator(metadata) is Valid)
        assertEquals(issuer, metadata.credentialIssuer)
    }

    @Test
    fun credentialEndpointAllowsNondefaultPortAndQueryWithoutRewritingIt() {
        val endpoint = "https://issuer.example.com:9443/Credential/Issue?wallet=One%2FTwo"
        val metadata = validMetadata().copy(credentialEndpoint = endpoint)

        assertTrue(issuerMetadataValidator(metadata) is Valid)
        assertEquals(endpoint, metadata.credentialEndpoint)
    }

    @Test
    fun presentEmptyAuthorizationServersIsInvalidWhileAbsentAndNonemptyRemainValid() {
        val baseline = validMetadata()
        assertTrue(issuerMetadataValidator(baseline) is Valid)
        assertTrue(issuerMetadataValidator(baseline.copy(authorizationServers = listOf("https://authorization.example/tenant"))) is Valid)

        assertTrue(issuerMetadataValidator(baseline.copy(authorizationServers = emptyList())) is Invalid)
    }
}
