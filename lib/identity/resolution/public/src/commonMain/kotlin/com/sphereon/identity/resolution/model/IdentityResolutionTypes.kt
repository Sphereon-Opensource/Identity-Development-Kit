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

import com.sphereon.identity.matching.model.IdentifierType
import kotlinx.serialization.Serializable

@Serializable
data class ResolveIdentityArgs(
    val identifier: String,
    val tenantId: String,
    val config: IdentityResolutionConfig? = null,
)

@Serializable
data class ResolveMatchingIdentityArgs(
    val identifier: String,
    val tenantId: String,
    val kmsKeyId: String,
    val identifierType: IdentifierType = IdentifierType.SUBJECT_ID,
    val macAlgorithm: String = "HMAC_SHA256",
)

@Serializable
data class IdentityResolutionResult(
    val resolved: Boolean = false,
    val internalIdentityId: String? = null,
    val resolverId: String? = null,
    val identifierType: IdentifierType? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object {
        val NOT_FOUND = IdentityResolutionResult(resolved = false)

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

@Serializable
data class IdentityResolutionConfig(
    val enabled: Boolean = false,
    val resolvers: Map<String, ResolverConfig> = emptyMap(),
)

@Serializable
data class ResolverConfig(
    val enabled: Boolean = true,
    val priority: Int = 0,
    val properties: Map<String, String> = emptyMap(),
)
