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

package com.sphereon.crypto.kms.rest.server.command.describe

import com.sphereon.core.api.service.ServiceCommandGroupDescription
import com.sphereon.core.api.service.ServiceCommandGroupDescriptorProvider
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.command.StoreKeyServiceCommand
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * App-scoped descriptor provider for the KMS Keys service command group.
 *
 * Provides metadata about the key management commands (CRUD operations)
 * for discoverability by the [com.sphereon.core.api.service.ServiceCommandGroupCatalog].
 *
 * Analogous to [com.sphereon.crypto.kms.rest.server.adapter.describe.KeysHttpAdapterDescriptorProvider]
 * which describes the same commands for the HTTP adapter catalog.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ServiceCommandGroupDescriptorProvider>())
class KmsKeysServiceCommandGroupDescriptorProvider : ServiceCommandGroupDescriptorProvider {
    override val groupId: String = GROUP_ID

    override fun describe(): ServiceCommandGroupDescription = ServiceCommandGroupDescription(
        groupId = GROUP_ID,
        module = "kms",
        service = "keys",
        displayName = "KMS Key Management",
        commandIds = listOf(
            GetKeyServiceCommand.COMMAND_ID,
            ListKeysServiceCommand.COMMAND_ID,
            StoreKeyServiceCommand.COMMAND_ID,
            GenerateKeyServiceCommand.COMMAND_ID,
            DeleteKeyServiceCommand.COMMAND_ID
        )
    )

    companion object {
        const val GROUP_ID = "kms.keys"
    }
}
