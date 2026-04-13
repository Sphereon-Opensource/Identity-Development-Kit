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

package com.sphereon.identity.reconciliation.impl.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.reconciliation.command.CancelReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.CompleteReconciliationCommand
import com.sphereon.identity.reconciliation.command.CreateReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.GetReconciliationSessionCommand
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface ReconciliationCommandDescriptors {
    @Provides @IntoMap
    @StringKey(CreateReconciliationSessionCommand.COMMAND_ID)
    fun createReconciliationSession(impl: CreateReconciliationSessionCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CompleteReconciliationCommand.COMMAND_ID)
    fun completeReconciliation(impl: CompleteReconciliationCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(GetReconciliationSessionCommand.COMMAND_ID)
    fun getReconciliationSession(impl: GetReconciliationSessionCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CancelReconciliationSessionCommand.COMMAND_ID)
    fun cancelReconciliationSession(impl: CancelReconciliationSessionCommandImpl): ServiceCommand<*, *> = impl
}
