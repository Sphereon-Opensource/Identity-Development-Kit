package com.sphereon.mdoc.core.testutil

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.CoseCryptoServiceImpl
import com.sphereon.di.app.AppComponent
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.MdocSignServiceImpl
import com.sphereon.mdoc.core.component.createTestMdocCoreAppComponent

actual fun createMdocTestAppComponent(testInstance: Any): AppComponent {
    return createTestMdocCoreAppComponent(
        application = testInstance,
        appId = "test-mdoc-core",
        profile = "test",
        version = "1.0.0-test"
    )
}

actual fun createMdocSignService(execution: SessionExecution): MdocSignService {
    return MdocSignServiceImpl(
        coseCryptoService = CoseCryptoServiceImpl(),
        execution = execution
    )
}
