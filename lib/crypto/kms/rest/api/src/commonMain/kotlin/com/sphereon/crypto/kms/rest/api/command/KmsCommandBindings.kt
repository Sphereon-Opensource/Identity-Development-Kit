/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms.rest.api.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(SessionScope::class)
interface KmsCommandBindings {
    @Provides
    fun kmsGetKey(registry: SessionScopedCommandRegistry): GetKeyServiceCommand =
        registry.get(GetKeyServiceCommand.COMMAND_ID) as? GetKeyServiceCommand
            ?: error("No binding for ${GetKeyServiceCommand.COMMAND_ID}")

    @Provides
    fun kmsListKeys(registry: SessionScopedCommandRegistry): ListKeysServiceCommand =
        registry.get(ListKeysServiceCommand.COMMAND_ID) as? ListKeysServiceCommand
            ?: error("No binding for ${ListKeysServiceCommand.COMMAND_ID}")

    @Provides
    fun kmsStoreKey(registry: SessionScopedCommandRegistry): StoreKeyServiceCommand =
        registry.get(StoreKeyServiceCommand.COMMAND_ID) as? StoreKeyServiceCommand
            ?: error("No binding for ${StoreKeyServiceCommand.COMMAND_ID}")

    @Provides
    fun kmsGenerateKey(registry: SessionScopedCommandRegistry): GenerateKeyServiceCommand =
        registry.get(GenerateKeyServiceCommand.COMMAND_ID) as? GenerateKeyServiceCommand
            ?: error("No binding for ${GenerateKeyServiceCommand.COMMAND_ID}")

    @Provides
    fun kmsDeleteKey(registry: SessionScopedCommandRegistry): DeleteKeyServiceCommand =
        registry.get(DeleteKeyServiceCommand.COMMAND_ID) as? DeleteKeyServiceCommand
            ?: error("No binding for ${DeleteKeyServiceCommand.COMMAND_ID}")
}
