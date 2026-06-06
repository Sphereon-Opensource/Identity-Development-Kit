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

import com.sphereon.core.api.json.StringOrStringListSerializer
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.did.serializers.DidServiceWithExtensionsSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Represents a service endpoint in a DID Document.
 *
 * Services are used to express ways of communicating with the DID subject or
 * associated entities. A service can be any type of service the DID subject
 * wants to advertise.
 *
 * W3C DID 1.1 allows `type` to be either a string or an array of strings, and
 * `serviceEndpoint` to be a string, a JSON object, or an array of strings/objects.
 * The model mirrors that polymorphism directly; [com.sphereon.did.serializers.StringOrStringListSerializer]
 * collapses single-element `type` lists back to a bare string on output.
 *
 * @property id The service ID — typically a DID URL fragment (e.g. `did:example:123#service-1`).
 * @property type One or more service type identifiers. On the wire this appears as a bare
 *           string when there is exactly one type and as a JSON array otherwise (handled by
 *           [com.sphereon.did.serializers.StringOrStringListSerializer]).
 * @property serviceEndpoint Endpoint descriptor — a JSON string, object, or array of
 *           strings/objects per DID 1.1. Kept as a [kotlinx.serialization.json.JsonElement]
 *           so all three shapes round-trip without lossy flattening; use
 *           [serviceEndpointAsStringOrNull] when you only accept the string form.
 * @property extensions Unknown JSON properties on this service, captured verbatim for
 *           lossless round-trip. Must not hold keys defined by the W3C DID Core schema.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidService", exact = true)
@JsExportCompat
@Serializable(with = DidServiceWithExtensionsSerializer::class)
data class DidService(
    val id: String,
    @Serializable(with = StringOrStringListSerializer::class)
    val type: List<String>,
    val serviceEndpoint: JsonElement,
    val extensions: Map<String, JsonElement> = emptyMap(),
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

    /**
     * True when this service advertises the given type.
     */
    fun hasType(candidate: String): Boolean = type.contains(candidate)

    /**
     * If `serviceEndpoint` is a simple JSON string, return its unquoted value.
     * Returns null when the endpoint is an object or array — callers must handle
     * those shapes explicitly via the `serviceEndpoint` field.
     */
    fun serviceEndpointAsStringOrNull(): String? = (serviceEndpoint as? JsonPrimitive)?.takeIf { it.isString }?.content
}
