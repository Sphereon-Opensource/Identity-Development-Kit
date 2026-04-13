package com.sphereon.did.manager.impl.testutil

import com.sphereon.did.manager.impl.createJsDidManagerTestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createDidManagerTestAppComponent(testInstance: Any): AppComponent {
    return createJsDidManagerTestAppComponent(testInstance)
}
