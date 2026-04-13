package com.sphereon.mdoc.core.testutil

import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.DefaultCallbacks
import com.sphereon.crypto.core.cose.CoseCryptoProviderToCallbackAdapter
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.CertificateServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.app.AppComponent
import com.sphereon.di.session.SessionComponent
import com.sphereon.mdoc.MdocSignService

expect fun createMdocTestAppComponent(testInstance: Any): AppComponent

expect fun createMdocSignService(execution: SessionExecution): MdocSignService

class MdocTestContext(testInstance: Any) {
    val app: AppComponent = createMdocTestAppComponent(testInstance)
    val userContext = app.userContextManager.getAnonymous()
    val sessionContext = userContext.sessionContextManager.createOrGetFromId("mdoc-test-${Uuid.v4String()}")
    val sessionComponent: SessionComponent = sessionContext.component

    val kms: KeyManagerService = sessionComponent.asKeyManagerServiceComponent().keyManagerService
    val certificateService: CertificateService
    val mdocSignService: MdocSignService

    init {
        val softwareProviderFactory = (app as SoftwareKmsProviderFactoryImpl.Component).softwareKmsProvider
        val softwareConfig = SoftwareKmsProviderConfig(id = "test-software")
        val softwareProvider = softwareProviderFactory.create(softwareConfig, sessionContext.sessionExecution)
        kms.registerProvider(softwareProvider, makeDefaultKms = true)

        DefaultCallbacks.setCoseCryptoDefault(
            CoseCryptoProviderToCallbackAdapter(keyManagerServiceProvider = { kms })
        )

        certificateService = CertificateServiceImpl(keyManagerService = kms)
        mdocSignService = createMdocSignService(sessionContext.sessionExecution)
    }
}
