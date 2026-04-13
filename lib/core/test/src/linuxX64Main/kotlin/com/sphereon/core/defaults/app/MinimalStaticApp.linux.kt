package com.sphereon.core.defaults.app

import com.sphereon.di.app.AbstractAppComponent

actual fun staticMinimalTestAppComponent(application: Any, appId: String, profile: String, version: String): AbstractAppComponent {
    return createMinimalStaticAppComponent(application, appId, profile, version)
}
