package com.sphereon.did.manager.impl.testutil

import com.sphereon.did.manager.impl.createWasmJsDidManagerTestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createDidManagerTestAppComponent(testInstance: Any): AppComponent {
    return createWasmJsDidManagerTestAppComponent(testInstance)
}
