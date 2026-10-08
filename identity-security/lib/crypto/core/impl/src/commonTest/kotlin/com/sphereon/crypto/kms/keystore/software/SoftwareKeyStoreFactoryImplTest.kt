package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.SecuredTenantContextDetails
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlin.test.Test
import kotlin.test.assertEquals

class SoftwareKeyStoreFactoryImplTest {
    @Test
    fun anonymousResolvedTenantFallsBackToSessionTenantForKeystorePath() {
        val execution =
            TestExecution(
                resolvedTenantId = IdentityConstants.ANONYMOUS_TENANT_ID,
                sessionTenantId = "platform",
            )

        val tenantId = tenantIdForKeystoreResolution(execution)
        val path =
            TenantKeyStorePathResolver.resolvePath(
                Pkcs12KeyStoreConfig(id = "software", password = "test-password"),
                tenantId,
            )

        assertEquals("platform", tenantId)
        assertEquals("/keystore/platform/software.p12", path)
    }

    @Test
    fun concreteResolvedTenantStillWinsOverSessionTenantForKeystorePath() {
        val execution =
            TestExecution(
                resolvedTenantId = "child-tenant",
                sessionTenantId = "platform",
            )

        val tenantId = tenantIdForKeystoreResolution(execution)
        val path =
            TenantKeyStorePathResolver.resolvePath(
                Pkcs12KeyStoreConfig(id = "software", password = "test-password"),
                tenantId,
            )

        assertEquals("child-tenant", tenantId)
        assertEquals("/keystore/child-tenant/software.p12", path)
    }
}

private class TestExecution(
    private val resolvedTenantId: String,
    sessionTenantId: String,
) : SessionExecution {
    override val sessionContext: SessionContext = TestSessionContext(sessionTenantId)
    override val tenantId: String get() = resolvedTenantId
    override val sessionContextManager: SessionContextManager
        get() = throw NotImplementedError("Not needed for this test")
    override val log: SessionLogService
        get() = throw NotImplementedError("Not needed for this test")
    override val conf: ContextConfig = TestContextConfig
}

private class TestSessionContext(
    tenantId: String,
) : SessionContext {
    override val context: UserContext = TestUserContext(tenantId)
    override val sessionId: String = "test-session"
    override val correlationId: String = "test-correlation"
}

private class TestUserContext(
    tenantId: String,
) : UserContext {
    override val id: String = "test-user"
    override val tenant: TenantContextData =
        object : TenantContextData {
            override val tenantId: String = tenantId
        }
    override val principal: Any = "test-principal"
    override val secureDetails: SecuredTenantContextDetails? = null
    override val principalType: PrincipalType = PrincipalType.USER
}

private object TestContextConfig : ContextConfig {
    override val app: AppConfigService
        get() = throw NotImplementedError("Not needed for this test")
    override val tenant: TenantConfigService
        get() = throw NotImplementedError("Not needed for this test")
    override val principal: PrincipalConfigService
        get() = throw NotImplementedError("Not needed for this test")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for this test")
}
