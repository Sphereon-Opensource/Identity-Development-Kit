package com.sphereon.identity.reconciliation.impl.testutil

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.di.app.AppComponent
import com.sphereon.di.session.SessionInstance
import com.sphereon.identity.reconciliation.command.CancelReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.CompleteReconciliationCommand
import com.sphereon.identity.reconciliation.command.CreateReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.GetReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.asReconciliationComponent
import com.sphereon.identity.reconciliation.store.ReconciliationProviderStore
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore

expect fun createReconciliationTestAppComponent(testInstance: Any): AppComponent

class ReconciliationTestContext(sessionId: String, testInstance: Any) {
    val app: AppComponent = createReconciliationTestAppComponent(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)
    val execution = session.asCoreApiServiceComponent().serviceExecution

    private val reconciliation = session.asReconciliationComponent()

    val createSessionCommand: CreateReconciliationSessionCommand = reconciliation.createReconciliationSessionCommand
    val completeCommand: CompleteReconciliationCommand = reconciliation.completeReconciliationCommand
    val getSessionCommand: GetReconciliationSessionCommand = reconciliation.getReconciliationSessionCommand
    val cancelSessionCommand: CancelReconciliationSessionCommand = reconciliation.cancelReconciliationSessionCommand

    val sessionStore: ReconciliationSessionStore = reconciliation.reconciliationSessionStore
    val providerStore: ReconciliationProviderStore = reconciliation.reconciliationProviderStore
}
