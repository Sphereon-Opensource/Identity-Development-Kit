package com.sphereon.did.manager.impl.testutil

import com.sphereon.di.app.AppComponent

actual fun createDidManagerTestAppComponent(testInstance: Any): AppComponent {
    return com.sphereon.did.manager.impl.createDidManagerTestAppComponent(testInstance)
}
