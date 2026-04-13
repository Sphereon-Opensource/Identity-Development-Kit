/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc.transport.restapi

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Handle representing a REST API connection.
 *
 * REST API is stateless, so the handle just contains the URI.
 *
 * @param uri The HTTPS URI where DeviceResponse should be POSTed
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RestApiHandle", exact = true)
data class RestApiHandle(
    val uri: String,
)

/**
 * REST API-specific errors.
 */
sealed class RestApiError(
    val errorMessage: String,
    val errorCause: Throwable? = null,
) : Exception(errorMessage, errorCause) {
    abstract fun toIdkError(): IdkErrorType

    /**
     * URI does not use HTTPS.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("InsecureUri", exact = true)
    data class InsecureUri(
        val uri: String,
    ) : RestApiError(
            "REST API URI must use HTTPS, got: $uri",
        ) {
        override fun toIdkError() =
            IdkError(
                code = "REST_API_INSECURE_URI",
                severity = IdkError.Severity.ERROR,
                message =
                    IdkError.Message(
                        i18nKey = "com.sphereon.mdoc.transport.restapi.insecure-uri",
                        defaultMessage = errorMessage,
                    ),
                meta = mapOf("uri" to uri),
            )
    }

    /**
     * Failed to connect to server.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ConnectionFailed", exact = true)
    data class ConnectionFailed(
        val uri: String,
        val throwable: Throwable,
    ) : RestApiError(
            "Failed to connect to $uri",
            throwable,
        ) {
        override fun toIdkError() =
            IdkError(
                code = "REST_API_CONNECTION_FAILED",
                severity = IdkError.Severity.ERROR,
                message =
                    IdkError.Message(
                        i18nKey = "com.sphereon.mdoc.transport.restapi.connection-failed",
                        defaultMessage = errorMessage,
                    ),
                exception = throwable,
                meta = mapOf("uri" to uri),
            )
    }

    /**
     * Network error during transfer.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("NetworkError", exact = true)
    data class NetworkError(
        val uri: String,
        val throwable: Throwable,
    ) : RestApiError(
            "Network error communicating with $uri",
            throwable,
        ) {
        override fun toIdkError() =
            IdkError(
                code = "REST_API_NETWORK_ERROR",
                severity = IdkError.Severity.ERROR,
                message =
                    IdkError.Message(
                        i18nKey = "com.sphereon.mdoc.transport.restapi.network-error",
                        defaultMessage = errorMessage,
                    ),
                exception = throwable,
                meta = mapOf("uri" to uri),
            )
    }

    /**
     * Server returned error status code.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ServerError", exact = true)
    data class ServerError(
        val uri: String,
        val statusCode: Int,
        val serverMessage: String,
    ) : RestApiError(serverMessage) {
        override fun toIdkError() =
            IdkError(
                code = "REST_API_SERVER_ERROR",
                severity = IdkError.Severity.ERROR,
                message =
                    IdkError.Message(
                        i18nKey = "com.sphereon.mdoc.transport.restapi.server-error",
                        defaultMessage = serverMessage,
                    ),
                meta = mapOf("uri" to uri, "statusCode" to statusCode),
            )
    }

    /**
     * Missing engagement data required for REST API session establishment.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("MissingEngagementData", exact = true)
    data class MissingEngagementData(
        val errorMsg: String,
    ) : RestApiError(errorMsg) {
        override fun toIdkError() =
            IdkError(
                code = "REST_API_MISSING_ENGAGEMENT_DATA",
                severity = IdkError.Severity.ERROR,
                message =
                    IdkError.Message(
                        i18nKey = "com.sphereon.mdoc.transport.restapi.missing-engagement-data",
                        defaultMessage = errorMsg,
                    ),
            )
    }
}
