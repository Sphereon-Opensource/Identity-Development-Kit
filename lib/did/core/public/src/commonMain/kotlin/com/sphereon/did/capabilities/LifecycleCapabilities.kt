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

package com.sphereon.did.capabilities

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * DID lifecycle operation capabilities.
 *
 * Describes which lifecycle operations are supported by a DID method.
 *
 * @property create Whether new DIDs can be created
 * @property update Whether existing DIDs can be updated (e.g., add/remove keys)
 * @property deactivate Whether DIDs can be deactivated
 * @property delete Whether DIDs can be deleted (removed from storage)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidLifecycleCapabilities", exact = true)
@JsExportCompat
@Serializable
data class LifecycleCapabilities(
    val create: Boolean = true,
    val update: Boolean = false,
    val deactivate: Boolean = false,
    val delete: Boolean = false
) {
    companion object {
        /**
         * Capabilities for immutable DID methods (did:key, did:jwk).
         */
        val IMMUTABLE: LifecycleCapabilities = LifecycleCapabilities(
            create = true,
            update = false,
            deactivate = false,
            delete = false
        )

        /**
         * Capabilities for fully mutable DID methods (did:web).
         */
        val FULL: LifecycleCapabilities = LifecycleCapabilities(
            create = true,
            update = true,
            deactivate = true,
            delete = true
        )
    }

    /**
     * Checks if the method supports any modification operations.
     */
    fun isMutable(): Boolean = update || deactivate || delete
}
