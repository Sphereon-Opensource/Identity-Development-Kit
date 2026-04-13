package com.sphereon.did.resolver.impl.testutil

import com.sphereon.di.app.AppComponent

actual fun createDidResolverTestAppComponent(testInstance: Any): AppComponent {
    return com.sphereon.did.resolver.impl.createDidResolverTestAppComponent(testInstance)
}
