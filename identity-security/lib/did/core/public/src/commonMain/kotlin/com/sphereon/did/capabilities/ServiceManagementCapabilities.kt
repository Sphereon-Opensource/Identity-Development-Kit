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

package com.sphereon.did.capabilities

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Service endpoint management capabilities.
 *
 * Describes which service management operations are supported by a DID method.
 *
 * @property addition Whether new services can be added to existing DIDs
 * @property replacement Whether existing services can be replaced
 * @property removal Whether services can be removed from DIDs
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidServiceManagementCapabilities", exact = true)
@JsExportCompat
@Serializable
data class ServiceManagementCapabilities
    @JvmOverloads
    constructor(
        val addition: Boolean = false,
        val replacement: Boolean = false,
        val removal: Boolean = false,
    ) {
        companion object {
            /**
             * No service management support.
             */
            @JvmStatic
            val NONE: ServiceManagementCapabilities = ServiceManagementCapabilities()

            /**
             * Full service management support.
             */
            @JvmStatic
            val FULL: ServiceManagementCapabilities =
                ServiceManagementCapabilities(
                    addition = true,
                    replacement = true,
                    removal = true,
                )
        }

        /**
         * Checks if any service management operations are supported.
         */
        fun supportsServiceManagement(): Boolean = addition || replacement || removal
    }
