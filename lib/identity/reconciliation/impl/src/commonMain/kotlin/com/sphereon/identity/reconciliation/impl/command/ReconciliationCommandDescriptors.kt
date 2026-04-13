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

package com.sphereon.identity.reconciliation.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.reconciliation.command.CancelReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.CompleteReconciliationCommand
import com.sphereon.identity.reconciliation.command.CreateReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.GetReconciliationSessionCommand
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface ReconciliationCommandDescriptors {

    @Provides @IntoSet
    fun createReconciliationSession(impl: Lazy<CreateReconciliationSessionCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateReconciliationSessionCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun completeReconciliation(impl: Lazy<CompleteReconciliationCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CompleteReconciliationCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun getReconciliationSession(impl: Lazy<GetReconciliationSessionCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GetReconciliationSessionCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun cancelReconciliationSession(impl: Lazy<CancelReconciliationSessionCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CancelReconciliationSessionCommand.COMMAND_ID) { impl.value }
}
