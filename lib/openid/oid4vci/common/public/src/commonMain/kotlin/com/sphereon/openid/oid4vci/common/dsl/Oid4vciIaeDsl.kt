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

package com.sphereon.openid.oid4vci.common.dsl

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// ---------------------------------------------------------------------------
// PkceBuilder
// ---------------------------------------------------------------------------

/**
 * Builds a [PkceChallenge] inside an IAE DSL block.
 *
 * ```kotlin
 * pkce {
 *     codeChallenge("E9Melhoa2...")
 *     codeChallengeMethod("S256")
 * }
 * ```
 */
@Oid4vciDsl
class PkceBuilder {
    private var challenge: String? = null
    private var method: String = "S256"

    /** Set the PKCE code_challenge value. */
    fun codeChallenge(value: String) {
        challenge = value
    }

    /** Set the PKCE code_challenge_method (default: "S256"). */
    fun codeChallengeMethod(value: String) {
        method = value
    }

    fun build(): PkceChallenge {
        val c = requireNotNull(challenge) { "pkce { codeChallenge(...) } must be set" }
        return PkceChallenge(codeChallenge = c, codeChallengeMethod = method)
    }
}

// ---------------------------------------------------------------------------
// AuthorizationDetailsBuilder
// ---------------------------------------------------------------------------

/**
 * Builds a list of RFC 9396 authorization_details JSON elements.
 *
 * Shared by both holder-side and AS-side IAE DSL builders.
 *
 * ```kotlin
 * authorizationDetails {
 *     openidCredential("UniversityDegree")
 *     openidCredential("MembershipCard", claims = listOf("given_name", "family_name"))
 * }
 * ```
 */
@Oid4vciDsl
class AuthorizationDetailsBuilder {
    private val details = mutableListOf<JsonElement>()

    /**
     * Add an `openid_credential` authorization_detail entry.
     *
     * @param credentialConfigurationId The credential_configuration_id to request.
     * @param claims Optional list of claim names to restrict the credential to.
     */
    fun openidCredential(
        credentialConfigurationId: String,
        claims: List<String>? = null,
    ) {
        details.add(
            buildJsonObject {
                put("type", "openid_credential")
                put("credential_configuration_id", credentialConfigurationId)
                if (!claims.isNullOrEmpty()) {
                    putJsonArray("claims") {
                        claims.forEach { add(JsonPrimitive(it)) }
                    }
                }
            },
        )
    }

    /**
     * Add a raw [JsonElement] authorization_detail entry for cases not covered by typed helpers.
     */
    fun raw(element: JsonElement) {
        details.add(element)
    }

    fun build(): List<JsonElement> = details.toList()
}
