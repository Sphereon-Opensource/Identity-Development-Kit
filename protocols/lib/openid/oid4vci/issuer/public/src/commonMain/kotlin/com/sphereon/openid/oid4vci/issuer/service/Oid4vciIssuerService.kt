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

package com.sphereon.openid.oid4vci.issuer.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferCommand
import com.sphereon.openid.oid4vci.issuer.command.CreatedCredentialOffer
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationCommand
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceArgs
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceCommand

/**
 * OID4VCI Issuer Service
 *
 * Main service interface for credential issuance operations.
 * Provides access to all issuer commands.
 */
interface Oid4vciIssuerService {
    suspend fun createCredentialOffer(args: CreateCredentialOfferArgs): IdkResult<CreatedCredentialOffer, IdkError>

    suspend fun buildIssuerMetadata(args: BuildIssuerMetadataArgs): IdkResult<CredentialIssuerMetadata, IdkError>

    suspend fun issueNonce(args: IssueNonceArgs): IdkResult<NonceResponse, IdkError>

    suspend fun handleCredentialRequest(args: HandleCredentialRequestArgs): IdkResult<CredentialResponse, IdkError>

    suspend fun handleDeferredCredentialRequest(args: HandleDeferredCredentialRequestArgs): IdkResult<CredentialResponse, IdkError>

    suspend fun handleNotification(args: HandleNotificationArgs): IdkResult<Unit, IdkError>

    suspend fun buildSignedIssuerMetadata(args: BuildSignedIssuerMetadataArgs): IdkResult<JwtCompactResult, IdkError>

    val commands: Commands

    interface Commands {
        val createCredentialOffer: CreateCredentialOfferCommand
        val buildIssuerMetadata: BuildIssuerMetadataCommand
        val issueNonce: IssueNonceCommand
        val handleCredentialRequest: HandleCredentialRequestCommand
        val handleDeferredCredentialRequest: HandleDeferredCredentialRequestCommand
        val handleNotification: HandleNotificationCommand
        val buildSignedIssuerMetadata: BuildSignedIssuerMetadataCommand
    }
}
