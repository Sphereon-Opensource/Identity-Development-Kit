package com.sphereon.did.resolver.impl.testutil

import com.sphereon.did.resolver.impl.createJsDidResolverTestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createDidResolverTestAppComponent(testInstance: Any): AppComponent {
    return createJsDidResolverTestAppComponent(testInstance)
}
