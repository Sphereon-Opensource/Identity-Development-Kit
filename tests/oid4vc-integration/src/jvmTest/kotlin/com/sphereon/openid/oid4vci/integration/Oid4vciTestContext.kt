package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance

class Oid4vciTestContext(
    testInstance: Any,
) {
    val app: AppGraph = createOid4vciTestAppGraph(application = testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId("oid4vci-e2e-test")
    val execution = session.asCoreApiServiceGraph().serviceExecution

    init {
        // Register software KMS provider for crypto operations in tests
        val config = SoftwareKmsProviderConfig(id = "oid4vci-test-kms")
        val factory = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
        val provider = factory.create(config, execution)
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        kms.registerProvider(provider, makeDefaultKms = true)
    }
}
