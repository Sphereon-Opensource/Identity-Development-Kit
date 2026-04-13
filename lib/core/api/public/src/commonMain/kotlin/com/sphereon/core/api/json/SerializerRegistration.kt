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

import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.Scoped

/**
 * Interface for automatic JSON serializer registration.
 *
 * Implementations of this interface are automatically discovered and initialized
 * when contributed to the AppScope using @ContributesBinding. The `onEnterScope`
 * method is called automatically, triggering serializer registration.
 *
 * Example usage:
 * ```
 * @Inject
 * @ContributesIntoSet(AppScope::class, binding = binding<SerializerRegistration>())
 * @ContributesIntoSet(AppScope::class, binding = binding<Scoped>())
 * @SingleIn(AppScope::class)
 * class MySerializerRegistration : SerializerRegistration {
 *     override fun onEnterScope(scope: Scope) {
 *         JsonSupport.register("my-module") {
 *             polymorphic(MyBaseClass::class) {
 *                 subclass(MyImpl::class, MyImpl.serializer())
 *             }
 *         }
 *     }
 * }
 * ```
 */
interface SerializerRegistration : Scoped {
    /**
     * Called automatically when entering the AppScope.
     * Implementations should register their serializers with [JsonSupport] here.
     *
     * @param scope The scope being entered
     */
    override fun onEnterScope(scope: Scope)

    override fun onExitScope() {
        // Nothing to clean up for serializer registrations
    }
}
