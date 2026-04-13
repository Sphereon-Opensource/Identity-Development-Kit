package com.sphereon.core.api.testutil

import com.sphereon.core.api.createTestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createCoreApiTestAppComponent(
    testInstance: Any,
    appId: String,
    profile: String,
    version: String
): AppComponent {
    return createTestAppComponent(testInstance, appId, profile, version)
}

