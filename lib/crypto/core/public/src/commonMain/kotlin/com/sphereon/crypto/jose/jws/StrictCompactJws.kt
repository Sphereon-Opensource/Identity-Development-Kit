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

package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import kotlinx.serialization.json.JsonObject

data class ParsedCompactJws(
    val original: JwsCompact,
    val protectedHeader: JsonObject,
    val payload: JsonObject,
    val signature: ByteArray,
    val protectedSegment: String,
    val payloadSegment: String,
    val signatureSegment: String,
)

sealed interface StrictCompactJwsError : IdkErrorType {
    override val severity: IdkError.Severity
        get() = IdkError.Severity.ERROR

    override val category: ErrorCategory
        get() = ErrorCategory.VALIDATION

    override val exception: Throwable?
        get() = null

    override val causes: List<IdkErrorType>
        get() = emptyList()

    data class InvalidSegmentCount(
        val actualCount: Int,
    ) : StrictCompactJwsError {
        override val code: String = "JWS_COMPACT_INVALID_SEGMENT_COUNT"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.crypto.jose.jws.invalid-segment-count",
                defaultMessage = "Compact JWS must contain exactly three segments",
            )
        override val meta: Map<String, Any?> =
            mapOf("segment" to "compact", "actualCount" to actualCount)
    }

    data class EmptySegment(
        val segment: String,
    ) : StrictCompactJwsError {
        override val code: String = "JWS_COMPACT_EMPTY_SEGMENT"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.crypto.jose.jws.empty-segment",
                defaultMessage = "Compact JWS segment '$segment' must not be empty",
            )
        override val meta: Map<String, Any?> = mapOf("segment" to segment)
    }

    data class InvalidBase64Url(
        val segment: String,
    ) : StrictCompactJwsError {
        override val code: String = "JWS_COMPACT_INVALID_BASE64URL"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.crypto.jose.jws.invalid-base64url",
                defaultMessage = "Compact JWS segment '$segment' is not valid base64url",
            )
        override val meta: Map<String, Any?> = mapOf("segment" to segment)
    }

    data class InvalidJsonObject(
        val segment: String,
        val reason: String,
    ) : StrictCompactJwsError {
        override val code: String = "JWS_COMPACT_INVALID_JSON_OBJECT"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.crypto.jose.jws.invalid-json-object",
                defaultMessage = "Compact JWS segment '$segment' must decode to a JSON object",
            )
        override val meta: Map<String, Any?> = mapOf("segment" to segment, "reason" to reason)
    }
}

object StrictCompactJws {
    fun parse(value: String): IdkResult<ParsedCompactJws, IdkError> {
        val segments = value.split('.')
        if (segments.size != 3) {
            return error(StrictCompactJwsError.InvalidSegmentCount(segments.size))
        }

        val protectedSegment = segments[0]
        val payloadSegment = segments[1]
        val signatureSegment = segments[2]

        listOf(
            "protected" to protectedSegment,
            "payload" to payloadSegment,
            "signature" to signatureSegment,
        ).forEach { (segmentName, segment) ->
            if (segment.isEmpty()) {
                return error(StrictCompactJwsError.EmptySegment(segmentName))
            }
            if (!isCanonicalBase64Url(segment)) {
                return error(StrictCompactJwsError.InvalidBase64Url(segmentName))
            }
        }

        val protectedHeader = decodeJsonObject("protected", protectedSegment).getOrElse { return Err(it) }
        val payload = decodeJsonObject("payload", payloadSegment).getOrElse { return Err(it) }
        val signature =
            try {
                JwsUtils.decodeBase64UrlToBytes(signatureSegment)
            } catch (_: Exception) {
                return error(StrictCompactJwsError.InvalidBase64Url("signature"))
            }

        return Ok(
            ParsedCompactJws(
                original = JwsCompact(value),
                protectedHeader = protectedHeader,
                payload = payload,
                signature = signature,
                protectedSegment = protectedSegment,
                payloadSegment = payloadSegment,
                signatureSegment = signatureSegment,
            ),
        )
    }

    private fun isCanonicalBase64Url(segment: String): Boolean {
        if (!base64UrlPattern.matches(segment) || segment.length % 4 == 1) {
            return false
        }
        return try {
            JwsUtils.encodeBytesToBase64Url(JwsUtils.decodeBase64UrlToBytes(segment)) == segment
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeJsonObject(
        segmentName: String,
        segment: String,
    ): IdkResult<JsonObject, IdkError> {
        try {
            JwsUtils.decodeBase64UrlToBytes(segment)
        } catch (_: Exception) {
            return error(StrictCompactJwsError.InvalidBase64Url(segmentName))
        }
        return try {
            Ok(JwsUtils.decodeBase64UrlToJson(segment))
        } catch (expected: Exception) {
            error(StrictCompactJwsError.InvalidJsonObject(segmentName, expected::class.simpleName ?: "json_error"))
        }
    }

    private val base64UrlPattern = Regex("^[A-Za-z0-9_-]+$")

    private fun error(value: StrictCompactJwsError): IdkResult<Nothing, IdkError> =
        Err(IdkError.fromDTO(value))
}
