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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service interface for OID4VCI Client (Wallet/Holder) operations.
 *
 * This is the command-free surface that exposes client actions only as services,
 * keeping command objects internal to implementations.
 *
 * Reference: OpenID for Verifiable Credential Issuance 1.0
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vciHolderService", exact = true)
interface Oid4vciHolderService {
    suspend fun parseCredentialOffer(rawOffer: String): IdkResult<CredentialOffer, IdkError>

    suspend fun resolveCredentialOffer(offer: CredentialOffer): IdkResult<ResolvedCredentialOffer, IdkError>

    suspend fun resolveIssuerMetadata(issuerUrl: String): IdkResult<CredentialIssuerMetadata, IdkError>

    suspend fun selectAuthorizationServer(
        issuerMetadata: CredentialIssuerMetadata,
        preferredAuthorizationServer: String? = null,
    ): IdkResult<ResolvedAuthorizationServer, IdkError>

    suspend fun requestNonce(nonceEndpoint: String): IdkResult<NonceResponse, IdkError>

    suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String? = null,
        clientId: String? = null,
        redirectUri: String? = null,
    ): IdkResult<TokenResponseWithContext, IdkError>

    suspend fun createCredentialRequestProof(
        issuerUrl: String,
        cNonce: String? = null,
        signingKeyId: String,
        signingAlgorithm: String = "ES256",
        clientId: String? = null,
        count: Int = 1,
    ): IdkResult<CreatedProof, IdkError>

    suspend fun requestCredential(
        credentialEndpoint: String,
        accessToken: String,
        credentialConfigurationId: String? = null,
        credentialIdentifier: String? = null,
        proofs: CredentialRequestProofs? = null,
        credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
        requestEncryptionJwk: JsonObject? = null,
        requestEncryptionAlg: String? = null,
        requestEncryptionEnc: String? = null,
        decryptionKeyId: String? = null,
    ): IdkResult<CredentialResponse, IdkError>

    suspend fun requestDeferredCredential(
        deferredCredentialEndpoint: String,
        accessToken: String,
        transactionId: String,
        credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
        requestEncryptionJwk: JsonObject? = null,
        requestEncryptionAlg: String? = null,
        requestEncryptionEnc: String? = null,
        decryptionKeyId: String? = null,
    ): IdkResult<CredentialResponse, IdkError>

    suspend fun sendNotification(
        notificationEndpoint: String,
        accessToken: String,
        notificationId: String,
        event: CredentialNotificationEvent,
        eventDescription: String? = null,
    ): IdkResult<Unit, IdkError>

    suspend fun followUpIae(args: FollowUpIaeArgs): IdkResult<IaeHolderResult, IdkError>

    suspend fun initiateIae(args: InitiateIaeArgs): IdkResult<IaeHolderResult, IdkError>

    suspend fun buildAuthorizationRequest(
        authorizationEndpoint: String,
        clientId: String,
        redirectUri: String,
        credentialConfigurationIds: List<String>,
        scope: String? = null,
        issuerState: String? = null,
        usePar: Boolean = false,
        parEndpoint: String? = null,
        credentialIdentifiers: Map<String, List<String>>? = null,
        locations: List<String>? = null,
    ): IdkResult<AuthorizationRequestResult, IdkError>

    suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String? = null,
    ): IdkResult<TokenResponseWithContext, IdkError>
}

/**
 * Adapter interface for OID4VCI Client operations without command exposure.
 *
 * This keeps a lightweight surface for integrations that only need the client
 * operations and do not require access to the command objects.
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vciHolderAdapter", exact = true)
interface Oid4vciHolderAdapter : Oid4vciHolderService

/**
 * Main interface for OID4VCI Client (Wallet/Holder) operations.
 *
 * A Client is responsible for:
 * 1. Parsing and resolving credential offers from issuers
 * 2. Resolving issuer metadata and selecting authorization servers
 * 3. Exchanging pre-authorized codes or authorization codes for access tokens
 * 4. Creating credential request proofs (key binding)
 * 5. Requesting credentials from the credential endpoint
 * 6. Polling for deferred credentials
 * 7. Sending notifications back to the issuer
 *
 * Reference: OpenID for Verifiable Credential Issuance 1.0
 * - Section 4: Credential Offer
 * - Section 6: Credential Endpoint
 * - Section 8: Token Request
 * - Section 9: Deferred Credential Endpoint
 * - Section 10: Notification Endpoint
 *
 * @see ResolvedCredentialOffer
 * @see TokenResponseWithContext
 * @see CreatedProof
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vciHolder", exact = true)
interface Oid4vciHolder : Oid4vciHolderAdapter {
    val commands: Commands

    @JsExportIgnoreCompat
    interface Commands {
        val parseCredentialOffer: ParseCredentialOfferCommand
        val resolveCredentialOffer: ResolveCredentialOfferCommand
        val resolveIssuerMetadata: ResolveIssuerMetadataCommand
        val selectAuthorizationServer: SelectAuthorizationServerCommand
        val requestNonce: RequestNonceCommand
        val exchangePreAuthorizedCode: ExchangePreAuthorizedCodeCommand
        val createCredentialRequestProof: CreateCredentialRequestProofCommand
        val requestCredential: RequestCredentialCommand
        val requestDeferredCredential: RequestDeferredCredentialCommand
        val sendNotification: SendNotificationCommand
        val followUpIae: FollowUpIaeCommand
        val initiateIae: InitiateIaeCommand
        val buildAuthorizationRequest: BuildAuthorizationRequestCommand
        val exchangeAuthorizationCode: ExchangeAuthorizationCodeCommand
    }
}
