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

package com.sphereon.openid.oid4vci.holder.impl.http.command

import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal val holderJson =
    Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
internal val JSON_HEADERS = mapOf("Content-Type" to "application/json")

internal fun oid4vciErrorResponse(
    statusCode: Int,
    errorCode: String,
    description: String,
) = GenericHttpResponse(
    statusCode = statusCode,
    headers = JSON_HEADERS,
    body = holderJson.encodeToString(Oid4vciErrorResponse.serializer(), Oid4vciErrorResponse(error = errorCode, errorDescription = description)),
)

internal fun sessionNotFound(sessionId: String) =
    GenericHttpResponse(
        statusCode = 404,
        headers = JSON_HEADERS,
        body = holderJson.encodeToString(Oid4vciErrorResponse.serializer(), Oid4vciErrorResponse(error = "not_found", errorDescription = "Session $sessionId not found")),
    )

internal fun buildAuthorizationDetailsFromConfigIds(credentialConfigurationIds: List<String>): List<JsonElement> =
    credentialConfigurationIds.map { configId ->
        buildJsonObject {
            put("type", JsonPrimitive("openid_credential"))
            put("credential_configuration_id", JsonPrimitive(configId))
        }
    }
