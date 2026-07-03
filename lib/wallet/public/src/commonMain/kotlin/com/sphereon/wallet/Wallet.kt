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

package com.sphereon.wallet

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.IssuanceSession
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletIssuanceSessionStore

/**
 * Top-level wallet facade. Orchestrates OID4VCI issuance flows, OID4VP presentations,
 * key management, and credential storage.
 *
 * Implementations live in lib-wallet-impl; this interface is the stable public API.
 */
interface Wallet {
    /** Access to persisted credential records and metadata sidecars. */
    val credentials: WalletCredentialStore

    /** Access to pending/resumable OID4VCI issuance sessions. */
    val issuanceSessions: WalletIssuanceSessionStore

    /**
     * Creates (or retrieves) a holder key scoped to [walletInstanceId], returning the
     * managed key alias. If [alias] is null a wallet-scoped alias is generated automatically.
     */
    suspend fun createHolderKey(
        walletInstanceId: String,
        alias: String? = null,
    ): IdkResult<String, IdkError>

    /**
     * Initiates an authorization code flow for the given issuer, returning the data
     * needed to redirect the user to the authorization endpoint.
     */
    suspend fun startAuthorizationCodeFlow(
        credentialIssuer: String,
        config: WalletConfig,
        scope: String? = null,
    ): IdkResult<AuthCodeStart, IdkError>

    /**
     * Completes an authorization code flow by exchanging the authorization code for tokens.
     */
    suspend fun completeAuthorizationCodeFlow(
        start: AuthCodeStart,
        code: String,
    ): IdkResult<TokenSet, IdkError>

    /**
     * Exchanges a pre-authorized code (from a credential offer) for tokens.
     */
    suspend fun exchangePreAuthorizedCode(
        credentialIssuer: String,
        preAuthorizedCode: String,
        txCode: String? = null,
        config: WalletConfig? = null,
    ): IdkResult<TokenSet, IdkError>

    /**
     * Obtains one or more credential instances from the issuer. Synchronous issuance stores a
     * [CredentialRecord]; deferred issuance stores an [IssuanceSession] without creating a
     * credential record until polling returns an actual credential artifact.
     */
    suspend fun obtainCredential(request: ObtainCredentialRequest): IdkResult<ObtainCredentialResult, IdkError>

    /**
     * Polls a persisted deferred issuance session and stores the resulting credential only after
     * the deferred endpoint returns the credential artifact.
     */
    suspend fun resumeDeferredIssuance(request: ResumeDeferredIssuanceRequest): IdkResult<ObtainCredentialResult, IdkError>

    /**
     * Refreshes or reissues an existing credential record as a new credential instance.
     * The prior credential body is never overwritten.
     */
    suspend fun refreshCredential(request: RefreshCredentialRequest): IdkResult<RefreshCredentialResult, IdkError>

    /**
     * Responds to a verifier's authorization request URI, performing a presentation exchange.
     */
    suspend fun present(
        requestUri: String,
        config: WalletConfig,
    ): IdkResult<PresentationResult, IdkError>
}
