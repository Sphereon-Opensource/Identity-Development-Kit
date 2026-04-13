package com.sphereon.oauth2.server.authorization.impl.testutil

import com.sphereon.oauth2.server.authorization.impl.test.createNativeOAuth2ServerTestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createOAuth2ServerTestAppComponent(testInstance: Any): AppComponent {
    return createNativeOAuth2ServerTestAppComponent(testInstance)
}
