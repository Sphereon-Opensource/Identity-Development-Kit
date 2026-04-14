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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.data.DocTypeAlias
import com.sphereon.mdoc.data.RequestInfoAlias
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ServerItemsRequest", exact = true)
data class ServerItemsRequest(
    /**
     * docType is the requested document type
     */
    val docType: DocTypeAlias,
    /**
     * NameSpaces contains the requested data elements and the namespace they belong to.
     */
    val nameSpaces: MutableMap<String, MutableMap<String, Boolean>>,
    /**
     * requestInfo may be used by the mdoc reader to provide additional information. This document does
     * not define any key-value pairs for use in requestInfo. An IA infrastructure shall ignore any key-value
     * pairs that it is not able to interpret.
     */
    val requestInfo: RequestInfoAlias?,
)
