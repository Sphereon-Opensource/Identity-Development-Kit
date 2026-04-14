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

package com.sphereon.crypto.core.kms

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.Command
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName
// ============================================================================
// Request/Response Types
// ============================================================================

/**
 * Arguments for querying a single KMS provider based on capability criteria.
 *
 * @property query The capability query criteria
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("QueryProviderArgs", exact = true)
@JsExportCompat
@Serializable
data class
QueryProviderArgs
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val query: KmsProviderQuery? = null,
    )

/**
 * Represents a provider match from a query.
 * Contains the provider ID and its capabilities - NOT the provider instance itself.
 * This design allows:
 * 1. Easy serialization for REST APIs
 * 2. Separation of query from provider retrieval
 * 3. User can inspect capabilities before choosing to use a provider
 *
 * @property providerId The unique ID of the matching provider
 * @property capabilities The provider's capabilities that matched the query
 * @property matchScore Optional score indicating how well this provider matches (0-100, higher is better)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProviderMatch", exact = true)
@JsExportCompat
@Serializable
data class
ProviderMatch
    @JvmOverloads
    constructor(
        val providerId: String,
        @kotlinx.serialization.Transient
        val capabilities: KmsProviderCapabilities? = null,
        val matchScore: Int = 100,
    )

/**
 * Result of querying for a single KMS provider.
 *
 * @property match The best matching provider (ID + capabilities)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("QueryProviderResult", exact = true)
@JsExportCompat
@Serializable
data class
QueryProviderResult
    @JvmOverloads
    constructor(
        val match: ProviderMatch? = null,
    )

/**
 * Arguments for querying multiple KMS providers based on capability criteria.
 *
 * @property query The capability query criteria
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("QueryProvidersArgs", exact = true)
@JsExportCompat
@Serializable
data class
QueryProvidersArgs
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val query: KmsProviderQuery? = null,
    )

/**
 * Result of querying for multiple KMS providers.
 *
 * @property matches All matching providers (ordered by match score, highest first)
 * @property totalProviders Total number of registered providers
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("QueryProvidersResult", exact = true)
@JsExportCompat
@Serializable
data class
QueryProvidersResult
    @JvmOverloads
    constructor(
        val matches: Array<ProviderMatch> = emptyArray(),
        val totalProviders: Int = 0,
    ) {
        val matchCount: Int get() = matches.size

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as QueryProvidersResult

            if (!matches.contentEquals(other.matches)) {
                return false
            }
            if (totalProviders != other.totalProviders) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = matches.contentHashCode()
            result = 31 * result + totalProviders
            return result
        }
    }

/**
 * Arguments for getting all provider capabilities.
 * No arguments needed - returns all providers' capabilities.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetAllCapabilitiesArgs", exact = true)
@JsExportCompat
@Serializable
data class
GetAllCapabilitiesArgs
    @JvmOverloads
    constructor(
        val includeDisabled: Boolean = false,
    )

/**
 * Result containing all provider capabilities.
 *
 * @property capabilities Map of provider ID to capabilities
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetAllCapabilitiesResult", exact = true)
@JsExportCompat
data class
GetAllCapabilitiesResult
    @JvmOverloads
    constructor(
        @kotlinx.serialization.Transient
        val capabilities: Map<String, KmsProviderCapabilities> = emptyMap(),
    )

// ============================================================================
// Command Interfaces
// ============================================================================

/**
 * Command interface for querying a single KMS provider based on capability criteria.
 *
 * This command follows the modern Command pattern with IdkResult return type.
 * It queries registered KMS providers and returns the provider ID and capabilities
 * of the first/best match. The user can then:
 * 1. Inspect the capabilities to confirm it meets their needs
 * 2. Call keyManagerService.getProviderById(providerId) to get the actual provider instance
 * 3. Use the provider for cryptographic operations
 *
 * Example usage:
 * ```kotlin
 * val args = QueryProviderArgs(
 *     query = kmsQuery {
 *         signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
 *         storageType = KeyStorageType.EPHEMERAL
 *     }
 * )
 * val result: IdkResult<QueryProviderResult, IdkError> = queryProviderCommand.execute(args)
 * if (result.isOk && result.value.match != null) {
 *     val providerId = result.value.match.providerId
 *     val capabilities = result.value.match.capabilities
 *
 *     // User decides if this provider is suitable, then gets it:
 *     val provider = keyManagerService.getProviderById(providerId)
 *     // Use provider...
 * }
 * ```
 */
@JsExportCompat
interface QueryProviderCommand : Command<QueryProviderArgs, QueryProviderResult, IdkError> {
    override val id: String
        get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.provider.query"
    }
}

/**
 * Command interface for querying multiple KMS providers based on capability criteria.
 *
 * This command follows the modern Command pattern with IdkResult return type.
 * It queries registered KMS providers and returns an ordered list of matches
 * (provider ID + capabilities), sorted by match score (highest first).
 *
 * This allows the user to:
 * 1. See all available options that meet their criteria
 * 2. Compare capabilities across providers
 * 3. Choose the best provider for their specific use case
 * 4. Get the actual provider instance using keyManagerService.getProviderById()
 *
 * Example usage:
 * ```kotlin
 * val args = QueryProvidersArgs(
 *     query = kmsQuery {
 *         operation = KmsProviderOperation.ENCRYPT
 *         contentEncryptionAlgorithm = ContentEncryptionAlgorithm.A256GCM
 *     }
 * )
 * val result: IdkResult<QueryProvidersResult, IdkError> = queryProvidersCommand.execute(args)
 * if (result.isOk) {
 *     println("Found ${result.value.matchCount} providers supporting A256GCM encryption:")
 *     result.value.matches.forEach { match ->
 *         println("- ${match.providerId} (score: ${match.matchScore})")
 *         println("  Type: ${match.capabilities?.providerType}")
 *     }
 *
 *     // User selects provider, then gets it:
 *     val bestMatch = result.value.matches.first()
 *     val provider = keyManagerService.getProviderById(bestMatch.providerId)
 * }
 * ```
 */
@JsExportCompat
interface QueryProvidersCommand : Command<QueryProvidersArgs, QueryProvidersResult, IdkError> {
    override val id: String
        get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.providers.query"
    }
}

/**
 * Command interface for getting all registered providers' capabilities.
 *
 * This command returns a snapshot of all provider capabilities, useful for:
 * - Introspection and diagnostics
 * - UI/Admin tools showing available capabilities
 * - Dynamic provider selection logic
 *
 * Example usage:
 * ```kotlin
 * val args = GetAllCapabilitiesArgs(includeDisabled = false)
 * val result: IdkResult<GetAllCapabilitiesResult, IdkError> = getAllCapabilitiesCommand.execute(args)
 * if (result.isOk) {
 *     result.value.capabilities.forEach { (providerId, caps) ->
 *         println("Provider $providerId supports: ${caps.operations.filter { it.supported }.map { it.operation }}")
 *     }
 * }
 * ```
 */
@JsExportCompat
interface GetAllCapabilitiesCommand : Command<GetAllCapabilitiesArgs, GetAllCapabilitiesResult, IdkError> {
    override val id: String
        get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "kms.capabilities.list"
    }
}
