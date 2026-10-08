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

package com.sphereon.identity.reconciliation.api

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmOverloads

/**
 * Protocol-free OIDC operations used by reconciliation session create/complete.
 *
 * Identity-security publishes this SPI without depending on protocols. The OAuth2-backed
 * adapter lives in `lib-identity-reconciliation-oidc` (protocols pack).
 */
interface ReconciliationOidcClient {
    suspend fun createPkce(): IdkResult<ReconciliationPkceMaterial, IdkError>

    suspend fun discoverEndpoints(issuer: String): ReconciliationOidcEndpoints?

    suspend fun exchangeAuthorizationCode(
        request: ReconciliationTokenExchangeRequest,
    ): IdkResult<ReconciliationTokenExchangeResult, IdkError>

    suspend fun fetchUserInfo(
        accessToken: String,
        userinfoEndpoint: String,
    ): IdkResult<ReconciliationUserInfoResult, IdkError>

    fun extractIdTokenClaims(idToken: String): Map<String, JsonElement>
}

@JsExportCompat
@Serializable
data class ReconciliationPkceMaterial
    @JvmOverloads
    constructor(
        val codeVerifier: String,
        val codeChallenge: String,
        val codeChallengeMethod: String = "S256",
    )

@JsExportCompat
@Serializable
data class ReconciliationOidcEndpoints
    @JvmOverloads
    constructor(
        val authorizationEndpoint: String? = null,
        val tokenEndpoint: String? = null,
        val userinfoEndpoint: String? = null,
    )

@JsExportCompat
@Serializable
data class ReconciliationTokenExchangeRequest
    @JvmOverloads
    constructor(
        val tokenEndpoint: String,
        val authorizationCode: String,
        val redirectUri: String? = null,
        val codeVerifier: String? = null,
        val clientId: String,
        val clientSecret: String? = null,
    )

@JsExportCompat
@Serializable
data class ReconciliationTokenExchangeResult
    @JvmOverloads
    constructor(
        val accessToken: String,
        val idToken: String? = null,
    )

@JsExportCompat
@Serializable
data class ReconciliationUserInfoResult
    @JvmOverloads
    constructor(
        val sub: String,
        val claims: Map<String, JsonElement> = emptyMap(),
    )
