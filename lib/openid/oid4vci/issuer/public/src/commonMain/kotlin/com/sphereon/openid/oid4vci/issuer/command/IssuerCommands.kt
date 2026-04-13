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

package com.sphereon.openid.oid4vci.issuer.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotification
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.DeferredCredentialRequest
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialRequestEncryption
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import kotlinx.serialization.json.JsonElement

// ============================================================================
// CreateCredentialOfferCommand
// ============================================================================

data class CreateCredentialOfferArgs(
    val issuerId: String,
    val credentialConfigurationIds: List<String>,
    val preAuthorizedCodeGrant: Boolean = false,
    val authorizationCodeGrant: Boolean = false,
    val txCodeRequired: Boolean = false,
    val preSeededAttributes: Map<String, JsonElement>? = null,
    val offerTtlSeconds: Long = 600,
)

data class CreatedCredentialOffer(
    val offerId: String,
    val sessionId: String,
    val offer: CredentialOffer,
    val offerUri: String,
    val txCode: String? = null,
)

interface CreateCredentialOfferCommand : ServiceCommand<CreateCredentialOfferArgs, CreatedCredentialOffer> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.createoffer"
    }
}

// ============================================================================
// BuildIssuerMetadataCommand
// ============================================================================

data class BuildIssuerMetadataArgs(
    val issuerIdentifier: String,
    val baseUrl: String,
    val authorizationServers: List<String>? = null,
    val credentialConfigurations: Map<String, CredentialConfigurationSupported>,
    val display: List<com.sphereon.openid.oid4vc.common.DisplayProperties>? = null,
    val credentialResponseEncryption: MetadataCredentialResponseEncryption? = null,
    val credentialRequestEncryption: MetadataCredentialRequestEncryption? = null,
    val batchCredentialIssuance: BatchCredentialIssuance? = null,
)

interface BuildIssuerMetadataCommand : ServiceCommand<BuildIssuerMetadataArgs, CredentialIssuerMetadata> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.metadata"
    }
}

// ============================================================================
// IssueNonceCommand
// ============================================================================

data class IssueNonceArgs(
    val ttlSeconds: Long = 300,
)

interface IssueNonceCommand : ServiceCommand<IssueNonceArgs, NonceResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.nonce"
    }
}

// ============================================================================
// HandleCredentialRequestCommand
// ============================================================================

data class HandleCredentialRequestArgs(
    val accessToken: String,
    val dpopProof: String? = null,
    val credentialRequest: CredentialRequest,
    val issuerIdentifier: String? = null,
    val credentialConfigurations: Map<String, CredentialConfigurationSupported> = emptyMap(),
)

interface HandleCredentialRequestCommand : ServiceCommand<HandleCredentialRequestArgs, CredentialResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.credential"
    }
}

// ============================================================================
// HandleDeferredCredentialRequestCommand
// ============================================================================

data class HandleDeferredCredentialRequestArgs(
    val accessToken: String,
    val dpopProof: String? = null,
    val deferredRequest: DeferredCredentialRequest,
)

interface HandleDeferredCredentialRequestCommand : ServiceCommand<HandleDeferredCredentialRequestArgs, CredentialResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.deferred"
    }
}

// ============================================================================
// HandleNotificationCommand
// ============================================================================

data class HandleNotificationArgs(
    val accessToken: String,
    val notification: CredentialNotification,
)

interface HandleNotificationCommand : ServiceCommand<HandleNotificationArgs, Unit> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.notification"
    }
}

// ============================================================================
// BuildSignedIssuerMetadataCommand
// ============================================================================

data class BuildSignedIssuerMetadataArgs(
    val metadata: CredentialIssuerMetadata,
    val signingKey: ManagedIdentifierOptsOrResult,
    val identifierMode: JwsIdentifierMode = JwsIdentifierMode.AUTO,
)

interface BuildSignedIssuerMetadataCommand : ServiceCommand<BuildSignedIssuerMetadataArgs, JwtCompactResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.signedmetadata"
    }
}
