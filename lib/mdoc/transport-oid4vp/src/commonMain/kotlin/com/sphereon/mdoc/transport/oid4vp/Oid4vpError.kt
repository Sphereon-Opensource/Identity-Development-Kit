/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.mdoc.transport.oid4vp

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat

/**
 * Error types specific to OID4VP transport (ISO 18013-7 Annex B).
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpError", exact = true)
sealed class Oid4vpError(
    val errorMessage: String,
    val errorCause: Throwable? = null
) : Exception(errorMessage, errorCause) {

    abstract fun toIdkError(): IdkErrorType

    /**
     * Failed to establish connection to OID4VP endpoint.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ConnectionFailed", exact = true)
    data class ConnectionFailed(
        val uri: String,
        val throwable: Throwable
    ) : Oid4vpError(
        "Failed to connect to OID4VP endpoint: $uri",
        throwable
    ) {
        override fun toIdkError() = IdkError(
            code = "OID4VP_CONNECTION_FAILED",
            severity = IdkError.Severity.ERROR,
            message = IdkError.Message(
                i18nKey = "com.sphereon.mdoc.oid4vp.error.connection-failed",
                defaultMessage = errorMessage
            ),
            exception = throwable,
            meta = mapOf("uri" to uri)
        )
    }

    /**
     * Network error during OID4VP communication.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("NetworkError", exact = true)
    data class NetworkError(
        val uri: String,
        val throwable: Throwable
    ) : Oid4vpError(
        "Network error during OID4VP communication with $uri",
        throwable
    ) {
        override fun toIdkError() = IdkError(
            code = "OID4VP_NETWORK_ERROR",
            severity = IdkError.Severity.ERROR,
            message = IdkError.Message(
                i18nKey = "com.sphereon.mdoc.oid4vp.error.network-error",
                defaultMessage = errorMessage
            ),
            exception = throwable,
            meta = mapOf("uri" to uri)
        )
    }

    /**
     * Server returned an error HTTP status code.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ServerError", exact = true)
    data class ServerError(
        val uri: String,
        val statusCode: Int,
        val serverMessage: String
    ) : Oid4vpError(serverMessage) {
        override fun toIdkError() = IdkError(
            code = "OID4VP_SERVER_ERROR",
            severity = IdkError.Severity.ERROR,
            message = IdkError.Message(
                i18nKey = "com.sphereon.mdoc.oid4vp.error.server-error",
                defaultMessage = serverMessage
            ),
            meta = mapOf("uri" to uri, "statusCode" to statusCode)
        )
    }

    /**
     * Invalid Authorization Request format or content.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("InvalidRequest", exact = true)
    data class InvalidRequest(
        val reason: String
    ) : Oid4vpError("Invalid OID4VP Authorization Request: $reason") {
        override fun toIdkError() = IdkError(
            code = "OID4VP_INVALID_REQUEST",
            severity = IdkError.Severity.ERROR,
            message = IdkError.Message(
                i18nKey = "com.sphereon.mdoc.oid4vp.error.invalid-request",
                defaultMessage = errorMessage
            ),
            meta = mapOf("reason" to reason)
        )
    }

    /**
     * Invalid JWT format or signature.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("InvalidJwt", exact = true)
    data class InvalidJwt(
        val reason: String
    ) : Oid4vpError("Invalid OID4VP JWT: $reason") {
        override fun toIdkError() = IdkError(
            code = "OID4VP_INVALID_JWT",
            severity = IdkError.Severity.ERROR,
            message = IdkError.Message(
                i18nKey = "com.sphereon.mdoc.oid4vp.error.invalid-jwt",
                defaultMessage = errorMessage
            ),
            meta = mapOf("reason" to reason)
        )
    }

    /**
     * Presentation Definition validation failed.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("InvalidPresentationDefinition", exact = true)
    data class InvalidPresentationDefinition(
        val reason: String
    ) : Oid4vpError("Invalid OID4VP Presentation Definition: $reason") {
        override fun toIdkError() = IdkError(
            code = "OID4VP_INVALID_PRESENTATION_DEFINITION",
            severity = IdkError.Severity.ERROR,
            message = IdkError.Message(
                i18nKey = "com.sphereon.mdoc.oid4vp.error.invalid-presentation-definition",
                defaultMessage = errorMessage
            ),
            meta = mapOf("reason" to reason)
        )
    }

    /**
     * Failed to sign documents for OID4VP response.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("SigningFailed", exact = true)
    data class SigningFailed(
        val throwable: Throwable
    ) : Oid4vpError(
        "Failed to sign documents for OID4VP response",
        throwable
    ) {
        override fun toIdkError() = IdkError(
            code = "OID4VP_SIGNING_FAILED",
            severity = IdkError.Severity.ERROR,
            message = IdkError.Message(
                i18nKey = "com.sphereon.mdoc.oid4vp.error.signing-failed",
                defaultMessage = errorMessage
            ),
            exception = throwable
        )
    }

    /**
     * Insecure URI (non-HTTPS) used for OID4VP.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("InsecureUri", exact = true)
    data class InsecureUri(
        val uri: String
    ) : Oid4vpError("OID4VP URIs must use HTTPS per ISO 18013-7: $uri") {
        override fun toIdkError() = IdkError(
            code = "OID4VP_INSECURE_URI",
            severity = IdkError.Severity.ERROR,
            message = IdkError.Message(
                i18nKey = "com.sphereon.mdoc.oid4vp.error.insecure-uri",
                defaultMessage = errorMessage
            ),
            meta = mapOf("uri" to uri)
        )
    }

    /**
     * Missing required engagement data.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("MissingEngagementData", exact = true)
    data class MissingEngagementData(
        val reason: String
    ) : Oid4vpError("Missing OID4VP engagement data: $reason") {
        override fun toIdkError() = IdkError(
            code = "OID4VP_MISSING_ENGAGEMENT_DATA",
            severity = IdkError.Severity.ERROR,
            message = IdkError.Message(
                i18nKey = "com.sphereon.mdoc.oid4vp.error.missing-engagement-data",
                defaultMessage = errorMessage
            ),
            meta = mapOf("reason" to reason)
        )
    }
}
