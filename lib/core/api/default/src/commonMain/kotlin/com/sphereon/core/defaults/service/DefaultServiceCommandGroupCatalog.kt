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

package com.sphereon.core.defaults.service

import com.sphereon.core.api.service.ServiceCommandGroupCatalog
import com.sphereon.core.api.service.ServiceCommandGroupDescription
import com.sphereon.core.api.service.ServiceCommandGroupDescriptorProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default catalog that collects all [ServiceCommandGroupDescriptorProvider] instances via multibinding.
 *
 * Analogous to DefaultHttpAdapterCatalog for HTTP adapters.
 * Builds an indexed catalog at startup.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServiceCommandGroupCatalog>())
class DefaultServiceCommandGroupCatalog(
    descriptorProviders: Set<ServiceCommandGroupDescriptorProvider>,
) : ServiceCommandGroupCatalog {
    override val groups: List<ServiceCommandGroupDescription> =
        descriptorProviders
            .map { it.describe() }
            .sortedBy { it.groupId }

    private val groupsByModule: Map<String, List<ServiceCommandGroupDescription>> =
        groups.groupBy { it.module }

    private val groupsById: Map<String, ServiceCommandGroupDescription> =
        groups.associateBy { it.groupId }

    private val commandToGroup: Map<String, ServiceCommandGroupDescription> =
        groups.flatMap { group -> group.commandIds.map { it to group } }.toMap()

    override fun findByModule(module: String): List<ServiceCommandGroupDescription> = groupsByModule[module] ?: emptyList()

    override fun findByGroupId(groupId: String): ServiceCommandGroupDescription? = groupsById[groupId]

    override fun isCommandEnabled(commandId: String): Boolean {
        val group = commandToGroup[commandId]
        return group?.isEnabled != false
    }
}
