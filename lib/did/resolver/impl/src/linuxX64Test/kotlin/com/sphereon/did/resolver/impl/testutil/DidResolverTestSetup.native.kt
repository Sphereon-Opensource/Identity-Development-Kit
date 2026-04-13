package com.sphereon.did.resolver.impl.testutil

import com.sphereon.did.resolver.impl.createNativeDidResolverTestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createDidResolverTestAppComponent(testInstance: Any): AppComponent {
    return createNativeDidResolverTestAppComponent(testInstance)
}
