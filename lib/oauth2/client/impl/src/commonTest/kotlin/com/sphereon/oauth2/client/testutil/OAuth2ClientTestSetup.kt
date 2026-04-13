package com.sphereon.oauth2.client.testutil

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppComponent
import com.sphereon.di.session.SessionInstance
import dev.whyoleg.cryptography.CryptographyProvider

expect fun createOAuth2ClientTestAppComponent(testInstance: Any): AppComponent

class OAuth2ClientTestContext(sessionId: String, testInstance: Any) {
    val app: AppComponent = createOAuth2ClientTestAppComponent(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)
    val execution = session.asCoreApiServiceComponent().serviceExecution
    val keyManagerService: KeyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService

    init {
        val config = SoftwareKmsProviderConfig(
            id = "$sessionId-software-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        val softwareKmsProvider = (app as SoftwareKmsProviderFactoryImpl.Component)
            .softwareKmsProvider
            .create(config, execution)
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)
    }
}
