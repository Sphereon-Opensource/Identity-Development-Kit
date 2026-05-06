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

/**
 * Extract the access token from the `Authorization` header, accepting both `Bearer` (RFC 6750)
 * and `DPoP` (RFC 9449 §7.1) schemes. RFC 9449 mandates that DPoP-bound access tokens MUST be
 * sent using the `DPoP` scheme — refusing it here breaks any wallet that obtained a
 * sender-constrained token at the AS, which the OID4VCI HAIP profile requires.
 *
 * Header lookup is case-insensitive per RFC 9110 §5.1; the scheme keyword match is also
 * case-insensitive.
 */
internal fun extractAccessToken(req: GenericHttpRequest): String? {
    val authHeader = req.headers["Authorization"] ?: req.headers["authorization"] ?: return null
    val trimmed = authHeader.trim()
    val space = trimmed.indexOf(' ')
    if (space < 0) return null
    val scheme = trimmed.substring(0, space)
    if (!scheme.equals("Bearer", ignoreCase = true) && !scheme.equals("DPoP", ignoreCase = true)) {
        return null
    }
    return trimmed.substring(space + 1).trim().ifEmpty { null }
}

/**
 * Decrypts the request body if Content-Type is application/jwt (JWE compact).
 *
 * Per OID4VCI 1.0 §10: holders MAY encrypt credential-request bodies using the issuer's public
 * key advertised in `credential_request_encryption.jwks`. When Content-Type is application/jwt,
 * the body is a JWE compact serialization that must be decrypted before parsing as JSON.
 *
 * The `decryptor` is the `Oid4vciIssuerConfigProvider.credentialRequestDecryptionKey` opts
 * (typically a `ManagedOptsAlias` pointing at the KMS-managed ECDH-ES private key whose public
 * half was published in metadata). The kid in the JWE protected header should match the kid
 * the metadata builder pinned to the published JWK; `MultiManagedIdentifierService.resolve`
 * inside `DecryptJweCommand` does the alias→private-key lookup.
 *
 * @return The (possibly decrypted) request body as a string, or null if decryption fails or
 *   was needed but no `decryptor` was configured (issuer doesn't advertise request encryption).
 */
internal suspend fun decryptRequestIfNeeded(
    req: GenericHttpRequest,
    decryptJweCommand: DecryptJweCommand,
    decryptor: com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult? = null,
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

    if (decryptor == null) {
        println("[$LOG_TAG] WARN: JWE request body received but no credential_request_encryption decryption key is configured")
        return null
    }

    val decryptResult =
        decryptJweCommand.execute(
            DecryptJweArgs(jwe = jwe, decryptor = decryptor),
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

    // OID4VCI 1.0 §8.3.1: credential-error responses are HTTP 400 with the error code in the
    // body — `unknown_credential_configuration` and `unknown_credential_identifier` included.
    // The only exceptions are auth-related: 401 for `invalid_token` (RFC 6750).
    val statusCode =
        when (oid4vciErrorCode) {
            Oid4vciErrors.INVALID_TOKEN -> 401
            else -> 400
        }

    return oid4vciErrorResponse(statusCode, oid4vciErrorCode, msg)
}
