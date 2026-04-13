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
 * Resolution and caching capabilities.
 *
 * Describes how DID resolution and caching should behave for this method.
 *
 * @property allowsCaching Whether resolution results can be cached
 * @property allowsLocalRecord Whether local records can be stored for this method
 * @property cacheTtlSeconds Recommended cache TTL in seconds (null = no caching)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidResolutionCapabilities", exact = true)
@JsExportCompat
@Serializable
data class ResolutionCapabilities(
    val allowsCaching: Boolean = false,
    val allowsLocalRecord: Boolean = true,
    val cacheTtlSeconds: Long? = null
) {
    companion object {
        /**
         * No caching (for computed DIDs like did:key).
         */
        val NO_CACHING: ResolutionCapabilities = ResolutionCapabilities(
            allowsCaching = false,
            allowsLocalRecord = true,
            cacheTtlSeconds = null
        )

        /**
         * Standard web caching (5 minutes TTL).
         */
        val WEB_CACHING: ResolutionCapabilities = ResolutionCapabilities(
            allowsCaching = true,
            allowsLocalRecord = true,
            cacheTtlSeconds = 300  // 5 minutes
        )
    }
}
