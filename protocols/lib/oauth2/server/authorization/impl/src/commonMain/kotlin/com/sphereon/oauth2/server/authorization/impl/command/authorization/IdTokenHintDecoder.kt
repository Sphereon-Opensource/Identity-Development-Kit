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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.decodeFromBase64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decode the `sub` claim out of an OIDC `id_token_hint` JWT payload without verifying the
 * signature. The hint exists so the OP can decide whether the active login session matches the
 * end-user the RP expects (OIDC Core 1.0 §3.1.2.1) — verifying the JWS would force the OP to
 * keep every key it ever signed an id_token under, which is impractical and explicitly *not*
 * required by the spec.
 *
 * The [expectedIssuer] check is the only integrity gate: the hint MUST come from this OP, so
 * only the `iss` claim is policed to keep an attacker from bouncing a hint minted elsewhere
 * past the session check. Returns `null` for any malformed input or claim mismatch — callers
 * treat absence as "no hint applies" rather than as a hard failure, matching OIDC Core's
 * SHOULD-handle wording for the parameter.
 */
internal fun decodeIdTokenHintSub(
    idTokenHint: String,
    expectedIssuer: String?
): String? = decodeIdTokenHintClaims(idTokenHint, expectedIssuer)?.sub

/**
 * Companion to [decodeIdTokenHintSub] returning the full set of claims the OIDC
 * RP-Initiated Logout / Front-Channel Logout / Back-Channel Logout flow needs:
 * `sub`, `aud`, `sid`. Same security stance as [decodeIdTokenHintSub] — the JWS
 * is not signature-verified, only the `iss` claim is policed to keep an attacker
 * from bouncing a hint minted elsewhere past the AS.
 *
 * Returns `null` on any malformed input. `aud` is exposed as the first audience
 * value (the AS only emits string-form `aud` and validators only need a single
 * client match).
 */
internal data class IdTokenHintClaims(
    val sub: String?,
    val aud: String?,
    val sid: String?,
)

internal fun decodeIdTokenHintClaims(
    idTokenHint: String,
    expectedIssuer: String?,
): IdTokenHintClaims? =
    runCatching {
        val parts = idTokenHint.split('.')
        if (parts.size != 3) return null
        val payloadJson = parts[1].decodeFromBase64Url().decodeToString()
        val payload = Json.parseToJsonElement(payloadJson).jsonObject
        val iss = payload["iss"]?.jsonPrimitive?.content
        if (expectedIssuer != null && iss != expectedIssuer) return null
        IdTokenHintClaims(
            sub = payload["sub"]?.jsonPrimitive?.content,
            aud =
                payload["aud"]?.let { audElement ->
                    when (audElement) {
                        is kotlinx.serialization.json.JsonArray -> audElement.firstOrNull()?.jsonPrimitive?.content
                        else -> audElement.jsonPrimitive.content
                    }
                },
            sid = payload["sid"]?.jsonPrimitive?.content,
        )
    }.getOrNull()
