/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.oauth2.common.model.CreateDpopProofOptions
import com.sphereon.oauth2.common.model.DpopProofResult
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.common.model.VerifyDpopProofResult

/**
 * Arguments for creating a DPoP proof JWT
 *
 * @property options Options for creating the DPoP proof (includes issuer/key for signing)
 * @property publicJwk The public key (JWK) to embed in the DPoP proof header
 */
@JsExportCompat
data class CreateDpopProofArgs(
    val options: CreateDpopProofOptions,
    val publicJwk: Jwk,
)

/**
 * Command for creating DPoP proof JWTs (RFC 9449)
 *
 * DPoP (Demonstrating Proof-of-Possession) is a mechanism to bind tokens to a specific client
 * by requiring the client to prove possession of a private key.
 *
 * The DPoP proof is a JWT signed with the client's private key that includes:
 * - jti: Unique identifier
 * - htm: HTTP method
 * - htu: HTTP URL (without query and fragment)
 * - iat: Issued at timestamp
 * - ath: Hash of access token (when presenting with access token)
 * - nonce: Server-provided nonce (when required)
 */
@JsExportCompat
interface CreateDpopProofCommand : ServiceCommand<CreateDpopProofArgs, DpopProofResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.dpop.create"
    }
}

/**
 * Command for verifying DPoP proof JWTs (RFC 9449)
 *
 * Verifies that a DPoP proof is valid by checking:
 * - JWT signature with embedded public key
 * - typ header is "dpop+jwt"
 * - All required claims are present
 * - htm and htu match the request
 * - ath matches the access token (if provided)
 * - nonce matches expected value (if provided)
 * - JWK thumbprint matches expected value (if provided)
 */
@JsExportCompat
interface VerifyDpopProofCommand : ServiceCommand<VerifyDpopProofOptions, VerifyDpopProofResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.dpop.verify"
    }
}
