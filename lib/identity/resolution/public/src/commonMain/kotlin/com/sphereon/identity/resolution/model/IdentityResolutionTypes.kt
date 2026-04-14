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
 */

package com.sphereon.identity.resolution.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.identity.matching.model.IdentifierType
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@JsExportCompat
@Serializable
data class ResolveIdentityArgs
    @JvmOverloads
    constructor(
        val identifier: String,
        val tenantId: String,
        val config: IdentityResolutionConfig? = null,
    )

@JsExportCompat
@Serializable
data class ResolveMatchingIdentityArgs
    @JvmOverloads
    constructor(
        val identifier: String,
        val tenantId: String,
        val kmsKeyId: String,
        val identifierType: IdentifierType = IdentifierType.SUBJECT_ID,
        val macAlgorithm: String = "HMAC_SHA256",
    )

@JsExportCompat
@Serializable
data class IdentityResolutionResult
    @JvmOverloads
    constructor(
        val resolved: Boolean = false,
        val internalIdentityId: String? = null,
        val resolverId: String? = null,
        val identifierType: IdentifierType? = null,
        @JsExportIgnoreCompat
        val metadata: Map<String, String> = emptyMap(),
    ) {
        companion object {
            val NOT_FOUND = IdentityResolutionResult(resolved = false)

            @JvmStatic
            @JvmOverloads
            fun found(
                internalIdentityId: String,
                resolverId: String,
                identifierType: IdentifierType? = null,
                metadata: Map<String, String> = emptyMap(),
            ) = IdentityResolutionResult(
                resolved = true,
                internalIdentityId = internalIdentityId,
                resolverId = resolverId,
                identifierType = identifierType,
                metadata = metadata,
            )
        }
    }

@JsExportCompat
@Serializable
data class IdentityResolutionConfig
    @JvmOverloads
    constructor(
        val enabled: Boolean = false,
        @JsExportIgnoreCompat
        val resolvers: Map<String, ResolverConfig> = emptyMap(),
    )

@JsExportCompat
@Serializable
data class ResolverConfig
    @JvmOverloads
    constructor(
        val enabled: Boolean = true,
        val priority: Int = 0,
        @JsExportIgnoreCompat
        val properties: Map<String, String> = emptyMap(),
    )
