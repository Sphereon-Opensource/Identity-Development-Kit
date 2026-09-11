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

package com.sphereon.openid.oid4vp.universal.impl.command

import com.sphereon.openid.oid4vp.universal.VerifiedClaimsValue
import com.sphereon.openid.oid4vp.universal.VerifiedData
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val verifiedDataJson = Json { ignoreUnknownKeys = true }

/**
 * Claims that structurally bind or describe a credential rather than convey disclosable
 * attributes. They MUST NOT surface as user-selectable DCQL paths or as rows in a
 * verification UI — callers still see them in the raw VP token if needed.
 *
 * Coverage:
 *   - JWT registered claims (RFC 7519 §4.1): iss, sub, aud, exp, nbf, iat, jti
 *   - SD-JWT VC registered claims (draft-ietf-oauth-sd-jwt-vc §4): cnf, vct, status
 *   - SD-JWT internals (RFC 9901): _sd, _sd_alg, `...` (decoy element)
 *
 * `sub` is explicitly included — when the holder is DID-bound, `sub` holds the DID and
 * is a binding artefact, not a user-facing identity claim.
 */
private val PROTECTED_CLAIMS =
    setOf(
        // JWT registered
        "iss",
        "sub",
        "aud",
        "exp",
        "nbf",
        "iat",
        "jti",
        // SD-JWT VC registered
        "cnf",
        "vct",
        "status",
        // SD-JWT internals
        "_sd",
        "_sd_alg",
        "...",
    )

internal fun buildVerifiedData(session: AuthorizationSession): VerifiedData? {
    val validationResult = session.validationResult ?: return null
    if (!validationResult.valid) {
        return null
    }

    val claims =
        validationResult.matchedCredentials.map { matched ->
            val claimsMap =
                matched.disclosedClaims
                    .filterKeys { it !in PROTECTED_CLAIMS }
                    .mapValues { (_, value) ->
                        when (value) {
                            is String -> JsonPrimitive(value)
                            is Number -> JsonPrimitive(value)
                            is Boolean -> JsonPrimitive(value)
                            null -> JsonPrimitive(null as String?)
                            else -> JsonPrimitive(value.toString())
                        }
                    }

            VerifiedClaimsValue(
                id = matched.credentialQueryId,
                type = matched.credentialFormat.value,
                claims = claimsMap,
                presentation = matched.presentation,
                verificationEvidence = matched.verificationEvidence?.let { evidence ->
                    evidence.copy(trust = evidence.trust?.copy(details = null, diagnostics = emptyList()))
                },
            )
        }

    return VerifiedData(
        credentialClaims = claims,
        authorizationResponse = buildAuthorizationResponse(session),
    )
}

private fun buildAuthorizationResponse(session: AuthorizationSession): JsonObject? {
    val parsedResponse = session.parsedResponse
    val vpTokenElement = parsedResponse?.rawVpToken?.let(::parseVpTokenJson)
    val state = parsedResponse?.state

    if (vpTokenElement == null && state == null) {
        return null
    }

    return buildJsonObject {
        if (vpTokenElement != null) {
            put("vp_token", vpTokenElement)
        }
        if (state != null) {
            put("state", JsonPrimitive(state))
        }
    }
}

private fun parseVpTokenJson(rawVpToken: String): JsonObject? =
    runCatching {
        verifiedDataJson.parseToJsonElement(rawVpToken) as? JsonObject
    }.getOrNull()
