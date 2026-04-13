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

package com.sphereon.openid.oid4vci.issuer.bridge

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.JsonObject

/**
 * Bridge interface abstracting the AS topology from the OID4VCI issuer.
 *
 * Scoped to what differs by AS topology. Nonce lifecycle is NOT in the bridge —
 * it is issuer-owned protocol state regardless of AS configuration.
 */
interface Oid4vciAuthorizationServerBridge {
    /** Register a pre-authorized code in the AS, returning the code string. */
    suspend fun registerPreAuthorizedCode(args: RegisterPreAuthCodeArgs): IdkResult<RegisteredPreAuthCode, IdkError>

    /** Atomically consume a pre-authorized code, returning the linked session. */
    suspend fun consumePreAuthorizedCode(args: ConsumePreAuthCodeArgs): IdkResult<ConsumedPreAuthCode, IdkError>

    /** Create an authorization context linked to issuer_state. */
    suspend fun createAuthorizationContext(args: CreateAuthContextArgs): IdkResult<AuthorizationContextRef, IdkError>

    /** Validate an access token and return the associated grant context. */
    suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError>

    /** Contribute OID4VCI-specific fields to AS metadata. */
    suspend fun augmentAsMetadata(args: AugmentAsMetadataArgs): IdkResult<JsonObject, IdkError>
}

data class RegisterPreAuthCodeArgs(
    val sessionId: String,
    val credentialConfigurationIds: List<String>,
    val txCodeRequired: Boolean,
    val issuerIdentifier: String? = null,
    val useCredentialIdentifiers: Boolean = true,
)

data class RegisteredPreAuthCode(
    val code: String,
    val txCode: String?,
)

data class ConsumePreAuthCodeArgs(
    val code: String,
    val txCode: String?,
    val clientId: String,
)

data class ConsumedPreAuthCode(
    val sessionId: String,
    val subject: String?,
    val credentialConfigurationIds: List<String>,
    val credentialIdentifiers: List<String>? = null,
)

data class CreateAuthContextArgs(
    val issuerState: String,
    val credentialConfigurationIds: List<String>,
    val authorizationDetails: List<com.sphereon.openid.oid4vci.common.model.Oid4vciAuthorizationDetail>? = null,
)

data class AuthorizationContextRef(
    val issuerState: String,
    val sessionId: String,
)

data class ValidateAccessTokenArgs(
    val accessToken: String,
    val dpopProof: String? = null,
)

data class ValidatedTokenContext(
    val subject: String,
    val clientId: String,
    val scope: String?,
    val credentialConfigurationIds: List<String>,
    val credentialIdentifiers: List<String>? = null,
)

data class AugmentAsMetadataArgs(
    val baseMetadata: JsonObject,
)
