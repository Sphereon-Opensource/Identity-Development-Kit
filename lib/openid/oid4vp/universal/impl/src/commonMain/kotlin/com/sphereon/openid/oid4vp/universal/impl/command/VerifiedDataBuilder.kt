/*
 * Copyright 2026 Sphereon International B.V.
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
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val verifiedDataJson = Json { ignoreUnknownKeys = true }

internal fun buildVerifiedData(session: AuthorizationSession): VerifiedData? {
    val validationResult = session.validationResult ?: return null
    if (!validationResult.valid) return null

    val claims = validationResult.matchedCredentials.map { matched ->
        val claimsMap = matched.disclosedClaims.mapValues { (_, value) ->
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
            type = matched.format,
            claims = claimsMap,
            presentation = matched.presentation
        )
    }

    return VerifiedData(
        credentialClaims = claims,
        authorizationResponse = buildAuthorizationResponse(session)
    )
}

private fun buildAuthorizationResponse(session: AuthorizationSession): JsonObject? {
    val parsedResponse = session.parsedResponse
    val vpTokenElement = parsedResponse?.rawVpToken?.let(::parseVpTokenJson)
    val dcqlResponse = buildDcqlResponse(session)
    val state = parsedResponse?.state

    if (vpTokenElement == null && dcqlResponse == null && state == null) {
        return null
    }

    return buildJsonObject {
        if (vpTokenElement != null) {
            put("vp_token", vpTokenElement)
        }
        if (dcqlResponse != null) {
            put("dcql_response", dcqlResponse)
        }
        if (state != null) {
            put("state", JsonPrimitive(state))
        }
    }
}

private fun buildDcqlResponse(session: AuthorizationSession): JsonObject? {
    val validationResult = session.validationResult ?: return null
    val credentialMatches = buildCredentialMatches(validationResult)
    val credentialSetMatches = buildCredentialSetMatches(session)

    if (credentialMatches.isEmpty() && credentialSetMatches.isEmpty()) {
        return null
    }

    return buildJsonObject {
        if (credentialMatches.isNotEmpty()) {
            put("credential_matches", credentialMatches)
        }
        if (credentialSetMatches.isNotEmpty()) {
            put("credential_set_matches", credentialSetMatches)
        }
    }
}

private fun buildCredentialMatches(validationResult: ValidationResult): JsonArray {
    return buildJsonArray {
        for (matched in validationResult.matchedCredentials) {
            add(
                buildJsonObject {
                    put("credential_id", JsonPrimitive(matched.credentialQueryId))
                    if (matched.disclosedClaims.isNotEmpty()) {
                        put(
                            "claims_satisfied",
                            buildJsonArray {
                                for (claimPath in matched.disclosedClaims.keys) {
                                    add(JsonPrimitive(claimPath))
                                }
                            }
                        )
                    }
                }
            )
        }
    }
}

private fun buildCredentialSetMatches(session: AuthorizationSession): JsonArray {
    val matchedCredentialIds = session.validationResult
        ?.matchedCredentials
        ?.map { it.credentialQueryId }
        ?.toSet()
        .orEmpty()

    return buildJsonArray {
        session.dcqlQuery.credential_sets.orEmpty().forEachIndexed { index, credentialSet ->
            val selectedOption = credentialSet.options.firstOrNull { option ->
                option.credential_ids.all { credentialId -> credentialId in matchedCredentialIds }
            } ?: return@forEachIndexed

            for (credentialId in selectedOption.credential_ids) {
                add(
                    buildJsonObject {
                        put("credential_set_id", JsonPrimitive(index.toString()))
                        put("credential_id", JsonPrimitive(credentialId))
                    }
                )
            }
        }
    }
}

private fun parseVpTokenJson(rawVpToken: String): JsonObject? {
    return runCatching {
        verifiedDataJson.parseToJsonElement(rawVpToken) as? JsonObject
    }.getOrNull()
}
