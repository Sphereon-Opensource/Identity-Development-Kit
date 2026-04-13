package com.sphereon.identity.reconciliation.impl.testutil

import com.sphereon.identity.reconciliation.impl.createJvmReconciliationTestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createReconciliationTestAppComponent(testInstance: Any): AppComponent {
    return createJvmReconciliationTestAppComponent(testInstance)
}
