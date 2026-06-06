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

package com.sphereon.openid.wallet

import com.sphereon.crypto.jose.jws.JwsIdentifierMode
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
    val credentialIssuer: String,
    val credentialConfigurationId: String,
    val accessToken: String,
    val cNonce: String? = null,
    val holderKeyAlias: String,
    val signingAlgorithm: String = "ES256",
    val count: Int = 1,
    /** How the holder public key is referenced in the proof JWT header. Defaults to JWK (inline key). */
    val keyInclusionMode: JwsIdentifierMode = JwsIdentifierMode.JWK,
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
    val clientId: String,
    val redirectUri: String,
)
