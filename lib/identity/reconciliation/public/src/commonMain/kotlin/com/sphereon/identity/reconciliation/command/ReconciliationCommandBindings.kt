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

package com.sphereon.identity.reconciliation.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface ReconciliationCommandBindings {
    @Provides
    fun createReconciliationSession(registry: SessionScopedCommandRegistry): CreateReconciliationSessionCommand =
        registry.get(CreateReconciliationSessionCommand.COMMAND_ID) as? CreateReconciliationSessionCommand
            ?: error("No binding for ${CreateReconciliationSessionCommand.COMMAND_ID}")

    @Provides
    fun completeReconciliation(registry: SessionScopedCommandRegistry): CompleteReconciliationCommand =
        registry.get(CompleteReconciliationCommand.COMMAND_ID) as? CompleteReconciliationCommand
            ?: error("No binding for ${CompleteReconciliationCommand.COMMAND_ID}")

    @Provides
    fun getReconciliationSession(registry: SessionScopedCommandRegistry): GetReconciliationSessionCommand =
        registry.get(GetReconciliationSessionCommand.COMMAND_ID) as? GetReconciliationSessionCommand
            ?: error("No binding for ${GetReconciliationSessionCommand.COMMAND_ID}")

    @Provides
    fun cancelReconciliationSession(registry: SessionScopedCommandRegistry): CancelReconciliationSessionCommand =
        registry.get(CancelReconciliationSessionCommand.COMMAND_ID) as? CancelReconciliationSessionCommand
            ?: error("No binding for ${CancelReconciliationSessionCommand.COMMAND_ID}")
}
