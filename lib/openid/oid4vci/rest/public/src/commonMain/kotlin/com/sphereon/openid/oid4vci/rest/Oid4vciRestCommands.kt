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

package com.sphereon.openid.oid4vci.rest

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * HTTP endpoint command for creating credential offers.
 *
 * POST /oid4vci/backend/credential/offers
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateCredentialOfferEndpointCommand", exact = true)
interface CreateCredentialOfferEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.rest.create"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/backend/credential/offers",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "createCredentialOffer",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-rest", "credential-offers"),
                summary = "Create a new OID4VCI credential offer session",
            )
    }
}

/**
 * HTTP endpoint command for getting credential offer status.
 *
 * GET /oid4vci/backend/credential/offers/{correlation_id}
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetCredentialOfferStatusEndpointCommand", exact = true)
interface GetCredentialOfferStatusEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.rest.status"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/backend/credential/offers/{correlation_id}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getCredentialOfferStatus",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-rest", "credential-offers"),
                summary = "Get the status of an OID4VCI credential offer session",
            )
    }
}

/**
 * HTTP endpoint command for deleting credential offers.
 *
 * DELETE /oid4vci/backend/credential/offers/{correlation_id}
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteCredentialOfferEndpointCommand", exact = true)
interface DeleteCredentialOfferEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.rest.delete"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/backend/credential/offers/{correlation_id}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "deleteCredentialOffer",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-rest", "credential-offers"),
                summary = "Delete an OID4VCI credential offer session",
            )
    }
}
