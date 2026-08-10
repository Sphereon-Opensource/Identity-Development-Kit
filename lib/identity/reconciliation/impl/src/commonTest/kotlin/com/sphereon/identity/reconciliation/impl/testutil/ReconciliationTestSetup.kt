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

package com.sphereon.identity.reconciliation.impl.testutil

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance
import com.sphereon.identity.reconciliation.command.CancelReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.CompleteReconciliationCommand
import com.sphereon.identity.reconciliation.command.CreateReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.GetReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.asReconciliationGraph
import com.sphereon.identity.reconciliation.store.ReconciliationProviderStore
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore

expect fun createReconciliationTestAppGraph(testInstance: Any): AppGraph

class ReconciliationTestContext(
    sessionId: String,
    testInstance: Any,
) {
    val app: AppGraph = createReconciliationTestAppGraph(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId, principalType = com.sphereon.di.context.PrincipalType.USER)
    val execution = session.asCoreApiServiceGraph().serviceExecution

    private val reconciliation = session.asReconciliationGraph()

    val createSessionCommand: CreateReconciliationSessionCommand = reconciliation.createReconciliationSessionCommand
    val completeCommand: CompleteReconciliationCommand = reconciliation.completeReconciliationCommand
    val getSessionCommand: GetReconciliationSessionCommand = reconciliation.getReconciliationSessionCommand
    val cancelSessionCommand: CancelReconciliationSessionCommand = reconciliation.cancelReconciliationSessionCommand

    val sessionStore: ReconciliationSessionStore = reconciliation.reconciliationSessionStore
    val providerStore: ReconciliationProviderStore = reconciliation.reconciliationProviderStore
}
