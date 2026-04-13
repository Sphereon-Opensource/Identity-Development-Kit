/*
 * Copyright 2025 Sphereon International B.V.
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

import com.sphereon.core.api.service.ServiceCommand
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Typed service command for getting authorization request status.
 *
 * GET /oid4vp/backend/auth/requests/{correlationId}
 *
 * This command uses the Binary API v5 pattern with typed input/output
 * and transport metadata for automatic HTTP binding.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetAuthRequestStatusServiceCommand", exact = true)
interface GetAuthRequestStatusServiceCommand :
    ServiceCommand<GetAuthRequestStatusInput, GetAuthorizationRequestStatusOutput> {

    companion object {
        const val COMMAND_ID = "oid4vp.universal.status"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Typed service command for creating authorization requests.
 *
 * POST /oid4vp/backend/auth/requests
 *
 * This command uses the Binary API v5 pattern with typed input/output
 * and transport metadata for automatic HTTP binding.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthRequestServiceCommand", exact = true)
interface CreateAuthRequestServiceCommand :
    ServiceCommand<CreateAuthorizationRequestInput, CreateAuthorizationRequestOutput> {

    companion object {
        const val COMMAND_ID = "oid4vp.universal.create"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Typed service command for deleting authorization requests.
 *
 * DELETE /oid4vp/backend/auth/requests/{correlationId}
 *
 * This command uses the Binary API v5 pattern with typed input/output
 * and transport metadata for automatic HTTP binding.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteAuthRequestServiceCommand", exact = true)
interface DeleteAuthRequestServiceCommand :
    ServiceCommand<GetAuthRequestStatusInput, DeleteAuthRequestOutput> {

    companion object {
        const val COMMAND_ID = "oid4vp.universal.delete"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Output for delete authorization request command.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteAuthRequestOutput", exact = true)
@kotlinx.serialization.Serializable
data class DeleteAuthRequestOutput(
    val correlationId: String,
    val deleted: Boolean = true
)
