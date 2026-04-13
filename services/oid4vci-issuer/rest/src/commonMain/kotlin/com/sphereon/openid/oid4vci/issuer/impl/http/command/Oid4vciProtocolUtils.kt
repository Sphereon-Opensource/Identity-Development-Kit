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

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.crypto.jose.jwe.DecryptJweArgs
import com.sphereon.crypto.jose.jwe.DecryptJweCommand
import com.sphereon.crypto.jose.jwe.JweCompact
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import kotlinx.serialization.json.Json

internal val protocolJson =
    Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

internal val JSON_HEADERS = mapOf("Content-Type" to "application/json")
internal val JWT_HEADERS = mapOf("Content-Type" to "application/jwt")

internal const val ACCEPT_JWT = "application/jwt"
internal const val ACCEPT_ISSUER_METADATA_JWT = "application/openidvci-issuer-metadata+jwt"

private const val LOG_TAG = "OID4VCI_ISSUER"

internal fun extractBearerToken(req: GenericHttpRequest): String? {
    val authHeader = req.headers["Authorization"] ?: req.headers["authorization"] ?: return null
    if (!authHeader.startsWith("Bearer ", ignoreCase = true)) return null
    return authHeader.substringAfter(' ').trim()
}

/**
 * Decrypts the request body if Content-Type is application/jwt (JWE compact).
 *
 * Per OID4VCI 1.1: holders may encrypt request bodies using the issuer's public key.
 * When Content-Type is application/jwt, the body is a JWE compact serialization
 * that must be decrypted before parsing as JSON.
 *
 * @return The (possibly decrypted) request body as a string, or null if decryption fails.
 */
internal suspend fun decryptRequestIfNeeded(
    req: GenericHttpRequest,
    decryptJweCommand: DecryptJweCommand,
): String? {
    val contentType = req.headers["Content-Type"] ?: req.headers["content-type"] ?: ""
    val body = req.body ?: "{}"

    if (!contentType.contains("application/jwt", ignoreCase = true)) {
        return body
    }

    val trimmedBody = body.trim()
    if (!JweCompact.isValidCompactFormat(trimmedBody)) {
        println("[$LOG_TAG] WARN: Request body with Content-Type application/jwt is not valid JWE compact format")
        return null
    }

    val jwe =
        try {
            JweCompact.parse(trimmedBody)
        } catch (expected: Exception) {
            println("[$LOG_TAG] WARN: Failed to parse JWE compact request body: ${expected.message}")
            return null
        }

    val decryptResult =
        decryptJweCommand.execute(
            DecryptJweArgs(jwe = jwe),
        )

    return if (decryptResult.isOk) {
        decryptResult.value?.plaintext?.decodeToString()
    } else {
        println("[$LOG_TAG] WARN: JWE request decryption failed: ${decryptResult.error?.message}")
        null
    }
}

internal fun oid4vciErrorResponse(
    statusCode: Int,
    errorCode: String,
    description: String,
) = GenericHttpResponse(
    statusCode = statusCode,
    headers = JSON_HEADERS,
    body = protocolJson.encodeToString(Oid4vciErrorResponse.serializer(), Oid4vciErrorResponse(error = errorCode, errorDescription = description)),
)

internal fun mapOid4vciError(error: IdkError): GenericHttpResponse {
    val code = error.code ?: ""
    val msg = error.message.defaultMessage

    if (code == "AUTHENTICATION_ERROR" || code == "UNAUTHORIZED") {
        return oid4vciErrorResponse(401, Oid4vciErrors.INVALID_TOKEN, msg)
    }

    val oid4vciErrorCode =
        when (code) {
            Oid4vciErrors.INVALID_PROOF -> Oid4vciErrors.INVALID_PROOF
            Oid4vciErrors.INVALID_NONCE -> Oid4vciErrors.INVALID_NONCE
            Oid4vciErrors.UNKNOWN_CREDENTIAL_CONFIGURATION, "NOT_FOUND_ERROR" -> Oid4vciErrors.UNKNOWN_CREDENTIAL_CONFIGURATION
            Oid4vciErrors.UNKNOWN_CREDENTIAL_IDENTIFIER -> Oid4vciErrors.UNKNOWN_CREDENTIAL_IDENTIFIER
            Oid4vciErrors.INVALID_ENCRYPTION_PARAMETERS -> Oid4vciErrors.INVALID_ENCRYPTION_PARAMETERS
            Oid4vciErrors.UNSUPPORTED_CREDENTIAL_FORMAT -> Oid4vciErrors.UNSUPPORTED_CREDENTIAL_FORMAT
            Oid4vciErrors.INVALID_CREDENTIAL_REQUEST -> Oid4vciErrors.INVALID_CREDENTIAL_REQUEST
            "UNSUPPORTED_PROOF_TYPE" -> Oid4vciErrors.INVALID_PROOF
            else -> Oid4vciErrors.INVALID_CREDENTIAL_REQUEST
        }

    val statusCode =
        when (oid4vciErrorCode) {
            Oid4vciErrors.INVALID_TOKEN -> 401

            Oid4vciErrors.UNKNOWN_CREDENTIAL_CONFIGURATION,
            Oid4vciErrors.UNKNOWN_CREDENTIAL_IDENTIFIER,
            -> 404

            else -> 400
        }

    return oid4vciErrorResponse(statusCode, oid4vciErrorCode, msg)
}
