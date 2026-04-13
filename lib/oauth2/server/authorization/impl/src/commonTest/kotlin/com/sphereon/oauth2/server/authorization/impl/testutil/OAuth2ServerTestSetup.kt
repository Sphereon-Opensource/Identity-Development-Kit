package com.sphereon.oauth2.server.authorization.impl.testutil

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppComponent
import com.sphereon.di.session.SessionInstance

expect fun createOAuth2ServerTestAppComponent(testInstance: Any): AppComponent

class OAuth2ServerTestContext(sessionId: String, testInstance: Any) {
    val app: AppComponent = createOAuth2ServerTestAppComponent(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)
    val execution = session.asCoreApiServiceComponent().serviceExecution

    init {
        // Register software KMS provider for crypto operations in tests
        val config = SoftwareKmsProviderConfig(id = "oauth2-test-software-kms")
        val factory = (app as SoftwareKmsProviderFactoryImpl.Component).softwareKmsProvider
        val provider = factory.create(config, execution)
        val kms = session.component.asKeyManagerServiceComponent().keyManagerService
        kms.registerProvider(provider, makeDefaultKms = true)
    }
}
