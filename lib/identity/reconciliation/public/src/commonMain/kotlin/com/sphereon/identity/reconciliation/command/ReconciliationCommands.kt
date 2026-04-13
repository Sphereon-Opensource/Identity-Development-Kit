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

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.identity.reconciliation.model.CancelReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.CompleteReconciliationArgs
import com.sphereon.identity.reconciliation.model.CompleteReconciliationResult
import com.sphereon.identity.reconciliation.model.CreateReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.CreateReconciliationSessionResult
import com.sphereon.identity.reconciliation.model.GetReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.ReconciliationSession

interface CreateReconciliationSessionCommand : ServiceCommand<CreateReconciliationSessionArgs, CreateReconciliationSessionResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "identity.reconciliation.create"
    }
}

interface CompleteReconciliationCommand : ServiceCommand<CompleteReconciliationArgs, CompleteReconciliationResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "identity.reconciliation.complete"
    }
}

interface GetReconciliationSessionCommand : ServiceCommand<GetReconciliationSessionArgs, ReconciliationSession> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "identity.reconciliation.get"
    }
}

interface CancelReconciliationSessionCommand : ServiceCommand<CancelReconciliationSessionArgs, ReconciliationSession> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "identity.reconciliation.cancel"
    }
}
