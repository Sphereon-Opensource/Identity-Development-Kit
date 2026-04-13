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

package com.sphereon.oauth2.server.resource.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.oauth2.common.model.AuthenticationScheme
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Result of successful resource request verification
 *
 * Contains the verified token payload and metadata about the authentication.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifiedResourceRequest", exact = true)
@JsExportCompat
data class VerifiedResourceRequest(
    /**
     * The verified token payload (either JWT or introspection result)
     */
    val tokenPayload: TokenPayload,
    /**
     * Authentication scheme used (Bearer or DPoP)
     */
    val scheme: AuthenticationScheme,
    /**
     * The access token string
     */
    val accessToken: String,
    /**
     * The authorization server that issued the token
     */
    val authorizationServer: String,
    /**
     * DPoP verification result (present only for DPoP-bound tokens)
     */
    val dpop: DpopVerificationResult? = null,
)

/**
 * Token payload - either from JWT verification or token introspection
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TokenPayload", exact = true)
// @JsExportCompat
sealed interface TokenPayload {
    /**
     * Subject (user ID)
     */
    val sub: String

    /**
     * Issuer (Authorization Server)
     */
    val iss: String

    /**
     * Audience (Resource Server)
     */
    val aud: List<String>?

    /**
     * Expiration time
     */
    val exp: Instant

    /**
     * Issued at time
     */
    val iat: Instant

    /**
     * Scope
     */
    val scope: String?

    /**
     * Client ID
     */
    val clientId: String?

    /**
     * DPoP binding (JWK thumbprint)
     */
    val dpopJkt: String?

    /**
     * JWT payload from RFC 9068 access token
     */
    @Serializable
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("JwtTokenPayload", exact = true)
//    @JsExportCompat
    data class Jwt(
        override val sub: String,
        override val iss: String,
        override val aud: List<String>?,
        override val exp: Instant,
        override val iat: Instant,
        override val scope: String?,
        override val clientId: String?,
        override val dpopJkt: String?,
        val jti: String?,
        val additionalClaims: Map<String, String> = emptyMap(),
    ) : TokenPayload

    /**
     * Token introspection response payload (RFC 7662)
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("IntrospectionTokenPayload", exact = true)
//    @JsExportCompat
    data class Introspection(
        val response: TokenIntrospectionResponse,
    ) : TokenPayload {
        override val sub: String get() = response.sub ?: ""
        override val iss: String get() = response.iss ?: ""
        override val aud: List<String>? get() = response.aud
        override val exp: Instant get() = response.exp?.let { Instant.fromEpochSeconds(it) } ?: Instant.DISTANT_FUTURE
        override val iat: Instant get() = response.iat?.let { Instant.fromEpochSeconds(it) } ?: Instant.DISTANT_PAST
        override val scope: String? get() = response.scope
        override val clientId: String? get() = response.clientId
        override val dpopJkt: String? get() = response.cnf?.jkt
    }
}

/**
 * DPoP verification result
 *
 * Contains metadata about the verified DPoP proof.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("DpopVerificationResult", exact = true)
@JsExportCompat
data class DpopVerificationResult(
    /**
     * The JWK from the DPoP proof
     */
    val jwk: Jwk,
    /**
     * JWK thumbprint (computed from JWK)
     * Must match cnf.jkt claim in access token
     */
    val jkt: String,
    /**
     * DPoP proof unique ID (jti claim)
     * Should be tracked for replay protection
     */
    val jti: String,
)
