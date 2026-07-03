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

import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.IssuanceSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Carries the in-flight state of an authorization code flow initiation. */
@Serializable
data class AuthCodeStart(
    val authorizationUrl: String,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val tokenEndpoint: String,
    val clientId: String,
)

/** A set of OAuth2 tokens returned after a successful token exchange. */
@Serializable
data class TokenSet(
    val accessToken: String,
    val cNonce: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
)

/** Parameters needed to request one or more credential instances from an issuer. */
@Serializable
data class ObtainCredentialRequest(
    val walletInstanceId: String,
    val credentialIssuer: String,
    val authorizationServerUrl: String? = null,
    val credentialConfigurationId: String,
    val credentialIdentifier: String? = null,
    val accessToken: String,
    val cNonce: String? = null,
    val holderKeyAlias: String,
    val signingAlgorithm: String = "ES256",
    val count: Int = 1,
    /** How the holder public key is referenced in the proof JWT header. Defaults to JWK (inline key). */
    val keyInclusionMode: JwsIdentifierMode = JwsIdentifierMode.JWK,
) {
    init {
        require(walletInstanceId.isNotBlank()) { "ObtainCredentialRequest.walletInstanceId must not be blank" }
        require(credentialConfigurationId.isNotBlank()) { "ObtainCredentialRequest.credentialConfigurationId must not be blank" }
    }
}

/** Result of an OID4VCI credential request. */
@Serializable
sealed class ObtainCredentialResult {
    @Serializable
    @SerialName("stored")
    data class Stored(
        val record: CredentialRecord,
    ) : ObtainCredentialResult()

    @Serializable
    @SerialName("deferred")
    data class Deferred(
        val session: IssuanceSession,
    ) : ObtainCredentialResult()
}

/** Parameters for polling a persisted deferred issuance session. */
@Serializable
data class ResumeDeferredIssuanceRequest(
    val walletInstanceId: String,
    val issuanceSessionId: String,
) {
    init {
        require(walletInstanceId.isNotBlank()) { "ResumeDeferredIssuanceRequest.walletInstanceId must not be blank" }
        require(issuanceSessionId.isNotBlank()) { "ResumeDeferredIssuanceRequest.issuanceSessionId must not be blank" }
    }
}

/** Parameters for refreshing/reissuing an existing stored credential record. */
@Serializable
data class RefreshCredentialRequest(
    val walletInstanceId: String,
    val credentialRecordId: String,
    val credentialIssuer: String? = null,
    val authorizationServerUrl: String? = null,
    val credentialConfigurationId: String? = null,
    val credentialIdentifier: String? = null,
    val accessToken: String,
    val cNonce: String? = null,
    val holderKeyAlias: String? = null,
    val signingAlgorithm: String = "ES256",
    val keyInclusionMode: JwsIdentifierMode = JwsIdentifierMode.JWK,
) {
    init {
        require(walletInstanceId.isNotBlank()) { "RefreshCredentialRequest.walletInstanceId must not be blank" }
        require(credentialRecordId.isNotBlank()) { "RefreshCredentialRequest.credentialRecordId must not be blank" }
        require(accessToken.isNotBlank()) { "RefreshCredentialRequest.accessToken must not be blank" }
    }
}

/** Result of a credential refresh/reissuance operation. */
@Serializable
data class RefreshCredentialResult(
    val record: CredentialRecord,
    val refreshedInstanceIds: List<String>,
)

/** Result of a presentation exchange. */
@Serializable
data class PresentationResult(
    val submitted: Boolean,
    val redirectUri: String? = null,
)

/** Wallet-level client configuration shared across flows. */
@Serializable
data class WalletConfig(
    val walletInstanceId: String,
    val clientId: String,
    val redirectUri: String,
) {
    init {
        require(walletInstanceId.isNotBlank()) { "WalletConfig.walletInstanceId must not be blank" }
    }
}
