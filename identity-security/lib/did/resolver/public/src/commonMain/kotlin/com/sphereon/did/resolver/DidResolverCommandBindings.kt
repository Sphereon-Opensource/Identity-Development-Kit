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

package com.sphereon.did.resolver

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(SessionScope::class)
interface DidResolverCommandBindings {
    @Provides
    fun resolveDidCommand(registry: SessionScopedCommandRegistry): ResolveDidCommand =
        registry.get(ResolveDidCommand.COMMAND_ID) as? ResolveDidCommand
            ?: error("No binding for ${ResolveDidCommand.COMMAND_ID}")

    @Provides
    fun dereferenceDidCommand(registry: SessionScopedCommandRegistry): DereferenceDidCommand =
        registry.get(DereferenceDidCommand.COMMAND_ID) as? DereferenceDidCommand
            ?: error("No binding for ${DereferenceDidCommand.COMMAND_ID}")

    @Provides
    fun resolveVerificationMethodCommand(registry: SessionScopedCommandRegistry): ResolveVerificationMethodCommand =
        registry.get(ResolveVerificationMethodCommand.COMMAND_ID) as? ResolveVerificationMethodCommand
            ?: error("No binding for ${ResolveVerificationMethodCommand.COMMAND_ID}")

    @Provides
    fun resolveVerificationMethodsByPurposeCommand(registry: SessionScopedCommandRegistry): ResolveVerificationMethodsByPurposeCommand =
        registry.get(ResolveVerificationMethodsByPurposeCommand.COMMAND_ID) as? ResolveVerificationMethodsByPurposeCommand
            ?: error("No binding for ${ResolveVerificationMethodsByPurposeCommand.COMMAND_ID}")
}
