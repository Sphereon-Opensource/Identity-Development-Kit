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

package com.sphereon.core.api.json

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlin.js.JsName

@JsExportCompat
@JsName("jsonSerializer")
val jsonSerializer: Json
    get() = JsonSupport.serializer

/**
 * Central JSON serialization support for the Identity Development Kit.
 *
 * This object provides a pluggable serializer module registration system that allows
 * libraries to contribute their own polymorphic serializers at runtime. Libraries
 * register their serializers via [register], and the combined [module] is used
 * to create a [Json] [serializer] that can handle all registered types.
 *
 * Example usage:
 * ```
 * JsonSupport.register("my-module") {
 *     polymorphic(MyBaseClass::class) {
 *         subclass(MyImpl::class, MyImpl.serializer())
 *     }
 * }
 *
 * val json = JsonSupport.serializer.encodeToString(myObject)
 * ```
 *
 * For DI-based automatic registration, implement [SerializerRegistration] and
 * contribute it to the AppScope.
 */
@JsExportCompat
@JsName("JsonSupport")
object JsonSupport {
    /**
     * Called by each library to add its own registrations.
     * Libraries should call this method from their SerializerRegistration.onEnterScope() implementation.
     */
    private val registrars = mutableListOf<SerializersModuleBuilder.() -> Unit>()

    /**
     * Track which registration IDs have been loaded to prevent duplicate registrations.
     */
    private val loadedRegistrationIds = mutableSetOf<String>()

    /**
     * The combined serializers module containing all registered serializers.
     * This is built lazily each time it's accessed to include any new registrations.
     */
    val module: SerializersModule
        get() =
            SerializersModule {
                // Apply all registered serializer modules
                registrars.forEach { it(this) }
            }

    /**
     * A Json instance configured with the combined [module].
     * Use this for serializing/deserializing objects that may be polymorphic types
     * registered by various libraries.
     */
    val serializer: Json
        get() = Json { serializersModule = module }

    /**
     * Register a serializer module with an optional unique ID.
     * If an ID is provided and already registered, the registration is skipped.
     *
     * @param registrationId Optional unique identifier for this registration (e.g., "memory-keystore")
     * @param block The serializer module builder block
     */
    fun register(
        registrationId: String? = null,
        block: SerializersModuleBuilder.() -> Unit,
    ) {
        if (registrationId != null) {
            if (!loadedRegistrationIds.add(registrationId)) {
                // Already registered, skip
                return
            }
        }
        registrars += block
    }
}
