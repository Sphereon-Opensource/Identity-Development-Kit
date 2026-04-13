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

package com.sphereon.core.api.service

import kotlinx.serialization.Serializable

/**
 * Describes a group of related ServiceCommands that form a logical service.
 *
 * Analogous to [com.sphereon.core.api.http.describe.HttpAdapterDescription] for external HTTP APIs.
 * Used for discoverability, admin UIs, health checks, and transport server validation.
 *
 * Example: the "kms.keys" group contains commands for key CRUD operations:
 * `kms.keys.get`, `kms.keys.list`, `kms.keys.store`, `kms.keys.generate`, `kms.keys.delete`.
 */
@Serializable
data class ServiceCommandGroupDescription(
    /** Unique group identifier, derived from module + service. E.g. "kms.keys" */
    val groupId: String,
    /** Module this group belongs to. E.g. "kms" */
    val module: String,
    /** Service within the module. E.g. "keys" */
    val service: String,
    /** Human-readable display name. E.g. "KMS Key Management" */
    val displayName: String,
    /** Command IDs belonging to this group */
    val commandIds: List<String>,
    /** Whether this entire group is enabled */
    val isEnabled: Boolean = true,
)

/**
 * Provides metadata about a service command group at app startup time.
 *
 * App-scoped — only provides metadata, no runtime command instances.
 * Analogous to [com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider].
 *
 * Implementations contribute via multibinding:
 * ```kotlin
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesIntoSet(AppScope::class, binding = binding<ServiceCommandGroupDescriptorProvider>())
 * class MyGroupDescriptorProvider : ServiceCommandGroupDescriptorProvider { ... }
 * ```
 */
interface ServiceCommandGroupDescriptorProvider {
    /** Unique group identifier. E.g. "kms.keys" */
    val groupId: String

    /** Builds the full group description with command list and metadata. */
    fun describe(): ServiceCommandGroupDescription
}

/**
 * Catalog of all service command groups discovered via DI.
 *
 * App-scoped — collects metadata from all [ServiceCommandGroupDescriptorProvider] instances.
 * Analogous to [com.sphereon.core.api.http.dispatch.HttpAdapterCatalog].
 *
 * Used by transport servers, admin endpoints, and health checks to discover
 * available service groups and their commands.
 */
interface ServiceCommandGroupCatalog {
    /** All discovered group descriptions, sorted by groupId. */
    val groups: List<ServiceCommandGroupDescription>

    /** Find all groups belonging to a specific module. */
    fun findByModule(module: String): List<ServiceCommandGroupDescription>

    /** Find a specific group by its groupId. */
    fun findByGroupId(groupId: String): ServiceCommandGroupDescription?

    /** Check if the group containing a command is enabled. Returns true if command is not in any group. */
    fun isCommandEnabled(commandId: String): Boolean
}
