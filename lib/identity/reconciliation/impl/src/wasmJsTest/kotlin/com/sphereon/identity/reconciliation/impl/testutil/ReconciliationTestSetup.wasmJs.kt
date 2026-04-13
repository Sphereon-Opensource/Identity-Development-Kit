package com.sphereon.identity.reconciliation.impl.testutil

import com.sphereon.identity.reconciliation.impl.createWasmJsReconciliationTestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createReconciliationTestAppComponent(testInstance: Any): AppComponent {
    return createWasmJsReconciliationTestAppComponent(testInstance)
}
