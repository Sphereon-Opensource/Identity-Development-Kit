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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Input type for the GetAuthRequestStatus typed service command.
 *
 * This is the domain-level input that captures the path parameter
 * for fetching authorization request status.
 *
 * @property correlationId The unique session/correlation identifier (from path param)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetAuthRequestStatusInput", exact = true)
@JsExportCompat
@Serializable
data class GetAuthRequestStatusInput(
    // Serialized name MUST match the `{correlation_id}` placeholder in the
    // GET/DELETE `/backend/auth/requests/{correlation_id}` HttpEndpointDescriptor
    // pathPattern. The binary transport builds the command input JSON from the
    // extracted path params (BinaryCommandAdapter.buildInputFromParams), keyed by
    // the literal placeholder name. A camelCase `correlationId` here would leave the
    // field unset for a body-less GET and surface as "Request body required".
    @SerialName("correlation_id")
    val correlationId: String,
)
