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

package com.sphereon.mdoc.data.server

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.cddl_tstr
import com.sphereon.mdoc.data.DeviceResponseDocumentErrorCborAlias
import com.sphereon.mdoc.data.JWT

/**
 * 8.3.2.2.2.2 Server retrieval mdoc response
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ServerResponse", exact = true)
data class ServerResponse(
    /**
     * version is the version for the ServerResponse structure: in the current version of this document its value
     * shall be “1.0”.
     */
    val version: cddl_tstr,

    /**
     * documents contains an array of all returned documents. Each document shall be returned as a JSON
     * Web Token (JWT), as specified in RFC 7519. The claims conveyed by each JWT are in com.sphereon.mdoc.dataelements.data.JWTClaimsSet.
     * Each JWT is protected using a JSON Web Signature (JWS) as specified in 9.2.2.
     */
    val documents: Array<JWT>?,

    /**
     * documentErrors can contain error codes for documents that are not returned.
     */
    // fixme: Does this need an array around the map?
    val documentErrors: Array<DeviceResponseDocumentErrorCborAlias>


) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServerResponse) return false

        if (version != other.version) return false
        if (documents != null) {
            if (other.documents == null) return false
            if (!documents.contentEquals(other.documents)) return false
        } else if (other.documents != null) return false
        if (!documentErrors.contentEquals(other.documentErrors)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + (documents?.contentHashCode() ?: 0)
        result = 31 * result + documentErrors.contentHashCode()
        return result
    }
}
