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
 */

package com.sphereon.core.defaults.service

import com.sphereon.core.api.service.ServiceCommandGroupDescription
import com.sphereon.core.api.service.ServiceCommandGroupDescriptorProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * NoOp stub to ensure the `Set<ServiceCommandGroupDescriptorProvider>` multibinding
 * always has at least one entry. Without this, kotlin-inject-anvil would fail
 * at compile time when no real descriptor providers are on the classpath.
 *
 * Filtered out by [DefaultServiceCommandGroupCatalog].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ServiceCommandGroupDescriptorProvider>())
class NoOpServiceCommandGroupDescriptorProvider : ServiceCommandGroupDescriptorProvider {
    override val groupId: String = ID

    override fun describe(): ServiceCommandGroupDescription = ServiceCommandGroupDescription(
        groupId = ID,
        module = "",
        service = "",
        displayName = "NoOp",
        commandIds = emptyList(),
        isEnabled = false
    )

    companion object {
        const val ID = "__NO_OP_GROUP__"
    }
}
