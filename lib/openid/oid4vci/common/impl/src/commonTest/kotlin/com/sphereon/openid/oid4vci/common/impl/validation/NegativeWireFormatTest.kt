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

package com.sphereon.openid.oid4vci.common.impl.validation

import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.DeferredCredentialRequest
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.common.model.Oid4vciAuthorizationDetail
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import io.konform.validation.Invalid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Negative tests for malformed OID4VCI wire-format inputs.
 *
 * These tests verify that validators and serialization correctly reject
 * inputs that violate the OID4VCI specification.
 */
class NegativeWireFormatTest {
    private val json = Oid4vciJson.lenient

    // ========================================================================
    // Malformed Issuer Metadata
    // ========================================================================

    @Test
    fun malformedMetadataMissingCredentialIssuer() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = "",
                credentialEndpoint = "https://issuer.example.com/credential",
                credentialConfigurationsSupported =
                    mapOf(
                        "TestCredential" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                    ),
            )
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "Metadata with empty credential_issuer should fail validation")
    }

    @Test
    fun malformedMetadataMissingCredentialEndpoint() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example.com",
                credentialEndpoint = "",
                credentialConfigurationsSupported =
                    mapOf(
                        "TestCredential" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                    ),
            )
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "Metadata with empty credential_endpoint should fail validation")
    }

    @Test
    fun malformedMetadataEmptyConfigurations() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example.com",
                credentialEndpoint = "https://issuer.example.com/credential",
                credentialConfigurationsSupported = emptyMap(),
            )
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "Metadata with empty credential_configurations_supported should fail validation")
    }

    @Test
    fun malformedMetadataInvalidEncryptionMissingEnc() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example.com",
                credentialEndpoint = "https://issuer.example.com/credential",
                credentialConfigurationsSupported =
                    mapOf(
                        "TestCredential" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                    ),
                credentialResponseEncryption =
                    MetadataCredentialResponseEncryption(
                        algValuesSupported = listOf("ECDH-ES+A256KW"),
                        encValuesSupported = emptyList(),
                    ),
            )
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "Metadata with empty enc_values_supported in credential_response_encryption should fail validation")
    }

    @Test
    fun malformedMetadataNegativeBatchSize() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example.com",
                credentialEndpoint = "https://issuer.example.com/credential",
                credentialConfigurationsSupported =
                    mapOf(
                        "TestCredential" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                    ),
                batchCredentialIssuance = BatchCredentialIssuance(batchSize = 0),
            )
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "Metadata with batch_size=0 in batch_credential_issuance should fail validation")
    }

    // ========================================================================
    // Malformed Authorization Details
    // ========================================================================

    @Test
    fun malformedAuthDetailEmptyConfigId() {
        val detail = Oid4vciAuthorizationDetail(credentialConfigurationId = "")
        val result = authorizationDetailValidator(detail)
        assertTrue(result is Invalid, "Authorization detail with empty credential_configuration_id should fail validation")
    }

    @Test
    fun malformedAuthDetailWrongType() {
        val detail =
            Oid4vciAuthorizationDetail(
                type = "wrong",
                credentialConfigurationId = "UniversityDegree",
            )
        val result = authorizationDetailValidator(detail)
        assertTrue(result is Invalid, "Authorization detail with type != 'openid_credential' should fail validation")
    }

    // ========================================================================
    // Malformed Credential Request
    // ========================================================================

    @Test
    fun malformedRequestMissingIdentification() {
        val request = CredentialRequest(format = "jwt_vc_json")
        val result = credentialRequestValidator(request)
        assertTrue(result is Invalid, "Request with neither credential_configuration_id nor credential_identifier should fail validation")
    }

    @Test
    fun malformedRequestBothIdentifications() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "UniversityDegree",
                credentialIdentifier = "UniversityDegree_LDP_VC",
            )
        val result = credentialRequestValidator(request)
        assertTrue(result is Invalid, "Request with both credential_configuration_id AND credential_identifier should fail validation")
    }

    @Test
    fun malformedRequestEmptyProofValues() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "Degree",
                proofs = CredentialRequestProofs(proofType = "jwt", proofValues = emptyList()),
            )
        val result = credentialRequestValidator(request)
        assertTrue(result is Invalid, "Request with proofs containing empty proof values should fail validation")
    }

    @Test
    fun malformedRequestEmptyProofType() {
        val proofs = CredentialRequestProofs(proofType = "", proofValues = listOf(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.proof.sig")))
        val result = credentialRequestProofsValidator(proofs)
        assertTrue(result is Invalid, "Proofs with empty proof type key should fail validation")
    }

    // ========================================================================
    // Malformed Encryption
    // ========================================================================

    @Test
    fun malformedEncryptionEmptyEnc() {
        val encryption =
            RequestedCredentialResponseEncryption(
                jwk = JsonObject(mapOf("kty" to JsonPrimitive("EC"))),
                enc = "",
            )
        val result = credentialResponseEncryptionValidator(encryption)
        assertTrue(result is Invalid, "Encryption with empty enc should fail validation")
    }

    @Test
    fun malformedEncryptionEmptyJwk() {
        val encryption =
            RequestedCredentialResponseEncryption(
                jwk = JsonObject(emptyMap()),
                enc = "A256GCM",
            )
        val result = credentialResponseEncryptionValidator(encryption)
        assertTrue(result is Invalid, "Encryption with empty jwk should fail validation")
    }

    // ========================================================================
    // Malformed Deferred Request
    // ========================================================================

    @Test
    fun malformedDeferredEmptyTransactionId() {
        val request = DeferredCredentialRequest(transactionId = "")
        val result = deferredCredentialRequestValidator(request)
        assertTrue(result is Invalid, "Deferred request with empty transaction_id should fail validation")
    }
}
