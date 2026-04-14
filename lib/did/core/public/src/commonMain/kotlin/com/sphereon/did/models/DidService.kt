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

package com.sphereon.did.models

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Represents a service endpoint in a DID Document.
 *
 * Services are used to express ways of communicating with the DID subject or
 * associated entities. A service can be any type of service the DID subject
 * wants to advertise.
 *
 * @property id The service ID. This is typically a DID URL fragment (e.g., "did:example:123#service-1")
 * @property type The type of service (e.g., "LinkedDomains", "CredentialRegistry")
 * @property serviceEndpoint The service endpoint URL or object. For Obj-C/JS compatibility,
 *           this is a simple string. Complex endpoints should be JSON-encoded.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidService", exact = true)
@JsExportCompat
@Serializable
data class DidService(
    val id: String,
    val type: String,
    val serviceEndpoint: String,
) {
    /**
     * Gets the service ID (fragment) from the full service ID.
     * For example, if id is "did:example:123#service-1", this returns "service-1".
     *
     * @return The fragment portion of the ID, or the full ID if no fragment is present
     */
    fun getServiceId(): String {
        val fragmentIndex = id.indexOf('#')
        return if (fragmentIndex >= 0) {
            id.substring(fragmentIndex + 1)
        } else {
            id
        }
    }

    /**
     * Gets the DID portion from the service ID.
     * For example, if id is "did:example:123#service-1", this returns "did:example:123".
     *
     * @return The DID portion of the ID, or the full ID if no fragment is present
     */
    fun getDid(): String {
        val fragmentIndex = id.indexOf('#')
        return if (fragmentIndex >= 0) {
            id.substring(0, fragmentIndex)
        } else {
            id
        }
    }
}
