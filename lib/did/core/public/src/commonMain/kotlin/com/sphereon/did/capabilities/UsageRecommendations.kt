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
 * Usage recommendations for a DID method.
 *
 * These are guidance recommendations, not enforcement. They help consumers
 * choose appropriate DID methods for their use cases.
 *
 * @property naturalPersons Whether this method is suitable for natural persons
 * @property organizations Whether this method is suitable for organizations
 * @property ephemeral Whether this method is suitable for ephemeral/temporary identifiers
 * @property longLived Whether this method is suitable for long-lived identifiers
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidUsageRecommendations", exact = true)
@JsExportCompat
@Serializable
data class UsageRecommendations
    @JvmOverloads
    constructor(
        val naturalPersons: Boolean = false,
        val organizations: Boolean = false,
        val ephemeral: Boolean = false,
        val longLived: Boolean = false,
    ) {
        companion object {
            /**
             * Recommended for ephemeral/personal use (did:key, did:jwk).
             */
            @JvmStatic
            val EPHEMERAL_PERSONAL: UsageRecommendations =
                UsageRecommendations(
                    naturalPersons = true,
                    organizations = false,
                    ephemeral = true,
                    longLived = false,
                )

            /**
             * Recommended for organizational use (did:web).
             */
            @JvmStatic
            val ORGANIZATIONAL: UsageRecommendations =
                UsageRecommendations(
                    naturalPersons = false,
                    organizations = true,
                    ephemeral = false,
                    longLived = true,
                )

            /**
             * Suitable for any use case.
             */
            @JvmStatic
            val GENERAL: UsageRecommendations =
                UsageRecommendations(
                    naturalPersons = true,
                    organizations = true,
                    ephemeral = true,
                    longLived = true,
                )
        }
    }
