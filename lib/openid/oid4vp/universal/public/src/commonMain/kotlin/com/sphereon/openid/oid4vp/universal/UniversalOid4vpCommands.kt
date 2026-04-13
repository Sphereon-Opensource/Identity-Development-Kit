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

package com.sphereon.openid.oid4vp.universal

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * HTTP endpoint command for creating authorization requests.
 *
 * POST /oid4vp/backend/auth/requests
 *
 * Creates a new OID4VP authorization session and returns the request URI
 * for wallet initiation along with a QR code data URI.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthRequestEndpointCommand", exact = true)
interface CreateAuthRequestEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.universal.create"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/backend/auth/requests",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "createAuthorizationRequest",
                commandId = COMMAND_ID,
                tags = setOf("universal-oid4vp", "auth-requests"),
                summary = "Create a new OID4VP authorization request session",
            )
    }
}

/**
 * HTTP endpoint command for getting authorization request status.
 *
 * GET /oid4vp/backend/auth/requests/{correlation_id}
 *
 * Returns the current status of an authorization session, including
 * verified credential data when verification is complete.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetAuthRequestStatusEndpointCommand", exact = true)
interface GetAuthRequestStatusEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.universal.status"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/backend/auth/requests/{correlation_id}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getAuthorizationRequestStatus",
                commandId = COMMAND_ID,
                tags = setOf("universal-oid4vp", "auth-requests"),
                summary = "Get the status of an OID4VP authorization request",
            )
    }
}

/**
 * HTTP endpoint command for deleting authorization requests.
 *
 * DELETE /oid4vp/backend/auth/requests/{correlation_id}
 *
 * Deletes an authorization session, cleaning up all associated state.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteAuthRequestEndpointCommand", exact = true)
interface DeleteAuthRequestEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.universal.delete"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/backend/auth/requests/{correlation_id}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "deleteAuthorizationRequest",
                commandId = COMMAND_ID,
                tags = setOf("universal-oid4vp", "auth-requests"),
                summary = "Delete an OID4VP authorization request session",
            )
    }
}
