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
import io.konform.validation.Validation
import io.konform.validation.constraints.minLength

// ============================================================================
// Credential Response Encryption
// ============================================================================

/**
 * Validates the encryption parameters in a credential response encryption request.
 *
 * Per OID4VCI 1.1 Section 9.2:
 * - [RequestedCredentialResponseEncryption.enc] is required and must not be empty
 * - [RequestedCredentialResponseEncryption.jwk] must not be empty (must contain key material)
 * - [RequestedCredentialResponseEncryption.alg] is optional in 1.1 (key agreement may come from the JWK itself)
 */
val credentialResponseEncryptionValidator =
    Validation<RequestedCredentialResponseEncryption> {
        RequestedCredentialResponseEncryption::enc {
            minLength(1) hint "enc is required for credential response encryption"
        }
        RequestedCredentialResponseEncryption::jwk {
            constrain("jwk must not be empty") { it.isNotEmpty() }
        }
    }

// ============================================================================
// Credential Request
// ============================================================================

val credentialRequestValidator =
    Validation<CredentialRequest> {
        constrain("'credential_configuration_id' or 'credential_identifier' must be provided") {
            it.credentialConfigurationId != null || it.credentialIdentifier != null
        }
        // OID4VCI 1.1 Section 9.2: credential_configuration_id and credential_identifier are mutually exclusive
        constrain("'credential_configuration_id' and 'credential_identifier' are mutually exclusive") {
            !(it.credentialConfigurationId != null && it.credentialIdentifier != null)
        }
        // Proofs consistency: proofValues must not be empty when proofs is present
        constrain("proofs.proof_values must not be empty") {
            val proofs = it.proofs ?: return@constrain true
            proofs.proofValues.isNotEmpty()
        }
        // Encryption parameter completeness
        constrain("credential_response_encryption must be valid when present") {
            val enc = it.credentialResponseEncryption ?: return@constrain true
            enc.enc.isNotEmpty() && enc.jwk.isNotEmpty()
        }
    }

// ============================================================================
// Proofs
// ============================================================================

/**
 * Validates a [CredentialRequestProofs] container.
 *
 * - proofType must be non-blank
 * - proofValues must not be empty
 */
val credentialRequestProofsValidator =
    Validation<CredentialRequestProofs> {
        CredentialRequestProofs::proofType {
            minLength(1) hint "proofs proof_type key is required"
        }
        CredentialRequestProofs::proofValues {
            constrain("proofs must contain at least one proof value") { it.isNotEmpty() }
        }
    }

// ============================================================================
// Deferred Credential Request
// ============================================================================

/**
 * Validates a [DeferredCredentialRequest].
 *
 * - transaction_id must be non-blank
 * - Encryption parameters (if present) must be valid
 */
val deferredCredentialRequestValidator =
    Validation<DeferredCredentialRequest> {
        DeferredCredentialRequest::transactionId {
            minLength(1) hint "transaction_id is required"
        }
        constrain("credential_response_encryption must be valid when present") {
            val enc = it.credentialResponseEncryption ?: return@constrain true
            enc.enc.isNotEmpty() && enc.jwk.isNotEmpty()
        }
    }

// ============================================================================
// Authorization Details
// ============================================================================

/**
 * Validates an [Oid4vciAuthorizationDetail] entry.
 *
 * - type must be "openid_credential"
 * - credential_configuration_id must be non-blank
 */
val authorizationDetailValidator =
    Validation<Oid4vciAuthorizationDetail> {
        Oid4vciAuthorizationDetail::type {
            constrain("authorization_details type must be 'openid_credential'") { it == "openid_credential" }
        }
        Oid4vciAuthorizationDetail::credentialConfigurationId {
            minLength(1) hint "credential_configuration_id is required in authorization_details"
        }
    }
