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

import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.DeferredCredentialRequest
import com.sphereon.openid.oid4vci.common.model.Oid4vciAuthorizationDetail
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

class CredentialRequestValidatorTest {
    // ========================================================================
    // credentialRequestValidator
    // ========================================================================

    @Test
    fun validRequestWithCredentialConfigurationIdPasses() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "UniversityDegreeCredential",
            )
        val result = credentialRequestValidator(request)
        assertTrue(result is Valid, "A request with credentialConfigurationId should pass validation")
    }

    @Test
    fun validRequestWithCredentialIdentifierPasses() {
        val request =
            CredentialRequest(
                credentialIdentifier = "UniversityDegree_LDP_VC",
            )
        val result = credentialRequestValidator(request)
        assertTrue(result is Valid, "A request with credentialIdentifier should pass validation")
    }

    @Test
    fun neitherConfigIdNorIdentifierFails() {
        val request = CredentialRequest(format = "vc+sd-jwt")
        val result = credentialRequestValidator(request)
        assertTrue(result is Invalid, "A request with neither credentialConfigurationId nor credentialIdentifier should fail")
    }

    @Test
    fun validJwtProofsPasses() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "Degree",
                proofs = CredentialRequestProofs(proofType = "jwt", proofValues = listOf(JsonPrimitive("eyJhbGci..."))),
            )
        assertTrue(credentialRequestValidator(request) is Valid)
    }

    @Test
    fun emptyBatchProofsFails() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "Degree",
                proofs = CredentialRequestProofs(proofType = "jwt", proofValues = emptyList()),
            )
        assertTrue(credentialRequestValidator(request) is Invalid, "Batch proofs with empty values should fail")
    }

    @Test
    fun requestWithNoProofPasses() {
        val request = CredentialRequest(credentialConfigurationId = "Degree")
        assertTrue(credentialRequestValidator(request) is Valid, "Request without proof should pass (proof optional)")
    }

    @Test
    fun requestWithInvalidEncryptionFails() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "Degree",
                credentialResponseEncryption =
                    RequestedCredentialResponseEncryption(
                        jwk = JsonObject(emptyMap()),
                        enc = "A256GCM",
                    ),
            )
        assertTrue(credentialRequestValidator(request) is Invalid, "Request with empty jwk in encryption should fail")
    }

    // ========================================================================
    // credentialRequestProofsValidator
    // ========================================================================

    @Test
    fun proofsValidPasses() {
        val proofs = CredentialRequestProofs(proofType = "jwt", proofValues = listOf(JsonPrimitive("eyJ1..."), JsonPrimitive("eyJ2...")))
        assertTrue(credentialRequestProofsValidator(proofs) is Valid)
    }

    @Test
    fun proofsEmptyFails() {
        val proofs = CredentialRequestProofs(proofType = "jwt", proofValues = emptyList())
        assertTrue(credentialRequestProofsValidator(proofs) is Invalid)
    }

    @Test
    fun proofsEmptyTypeFails() {
        val proofs = CredentialRequestProofs(proofType = "", proofValues = listOf(JsonPrimitive("eyJ...")))
        assertTrue(credentialRequestProofsValidator(proofs) is Invalid)
    }

    // ========================================================================
    // deferredCredentialRequestValidator
    // ========================================================================

    @Test
    fun deferredRequestValidPasses() {
        val request = DeferredCredentialRequest(transactionId = "txn-123")
        assertTrue(deferredCredentialRequestValidator(request) is Valid)
    }

    @Test
    fun deferredRequestEmptyTransactionIdFails() {
        val request = DeferredCredentialRequest(transactionId = "")
        assertTrue(deferredCredentialRequestValidator(request) is Invalid)
    }

    @Test
    fun deferredRequestWithValidEncryptionPasses() {
        val jwk = JsonObject(mapOf("kty" to JsonPrimitive("EC")))
        val request =
            DeferredCredentialRequest(
                transactionId = "txn-123",
                credentialResponseEncryption = RequestedCredentialResponseEncryption(jwk = jwk, enc = "A256GCM"),
            )
        assertTrue(deferredCredentialRequestValidator(request) is Valid)
    }

    @Test
    fun deferredRequestWithInvalidEncryptionFails() {
        val request =
            DeferredCredentialRequest(
                transactionId = "txn-123",
                credentialResponseEncryption = RequestedCredentialResponseEncryption(jwk = JsonObject(emptyMap()), enc = "A256GCM"),
            )
        assertTrue(deferredCredentialRequestValidator(request) is Invalid)
    }

    // ========================================================================
    // authorizationDetailValidator
    // ========================================================================

    @Test
    fun authDetailValidPasses() {
        val detail = Oid4vciAuthorizationDetail(credentialConfigurationId = "UniversityDegree")
        assertTrue(authorizationDetailValidator(detail) is Valid)
    }

    @Test
    fun authDetailWrongTypeFails() {
        val detail = Oid4vciAuthorizationDetail(type = "not_openid_credential", credentialConfigurationId = "UniversityDegree")
        assertTrue(authorizationDetailValidator(detail) is Invalid)
    }

    @Test
    fun authDetailEmptyConfigIdFails() {
        val detail = Oid4vciAuthorizationDetail(credentialConfigurationId = "")
        assertTrue(authorizationDetailValidator(detail) is Invalid)
    }

    @Test
    fun authDetailWithOptionalFieldsPasses() {
        val detail =
            Oid4vciAuthorizationDetail(
                credentialConfigurationId = "UniversityDegree",
                credentialIdentifiers = listOf("id-1", "id-2"),
                locations = listOf("https://issuer.example.com"),
            )
        assertTrue(authorizationDetailValidator(detail) is Valid)
    }
}
