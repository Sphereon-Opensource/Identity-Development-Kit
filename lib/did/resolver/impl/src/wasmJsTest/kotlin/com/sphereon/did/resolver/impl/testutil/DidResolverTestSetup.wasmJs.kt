package com.sphereon.did.resolver.impl.testutil

import com.sphereon.did.resolver.impl.createWasmJsDidResolverTestAppComponent
import com.sphereon.di.app.AppComponent

actual fun createDidResolverTestAppComponent(testInstance: Any): AppComponent {
    return createWasmJsDidResolverTestAppComponent(testInstance)
}
