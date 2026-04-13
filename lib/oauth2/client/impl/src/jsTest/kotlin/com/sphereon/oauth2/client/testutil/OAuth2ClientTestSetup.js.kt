package com.sphereon.oauth2.client.testutil

import com.sphereon.oauth2.client.createJsOauth2TestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createOAuth2ClientTestAppComponent(testInstance: Any): AppComponent {
    return createJsOauth2TestAppComponent(testInstance)
}
