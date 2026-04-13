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
 *
 */

package com.sphereon.mdoc.data.server

import com.sphereon.cbor.cddl_tstr
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * 8.3.2.2.2.1 Server retrieval mdoc request
 * The server retrieval mdoc request shall be JSON encoded and formatted as follows:
 */

@OptIn(ExperimentalObjCName::class)
@ObjCName("ServerRequest", exact = true)
data class ServerRequest(
    /**
     * version is the version for the ServerRequest structure: in the current version of this document its value
     * shall be “1.0”.
     */
    val version: cddl_tstr = "1.0",
    /**
     * token shall contain the server retrieval token (see 8.2.1.2) which identifies the mdoc.
     */
    val token: cddl_tstr,
    /**
     * docRequests contains an array of all requested documents.
     */
    val docRequests: Array<ServerItemsRequest>,
)
