package com.sphereon.oauth2.server.authorization.impl.testutil

import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider

/**
 * Test config provider for unit tests.
 * Allows overriding config per test.
 */
class TestOAuth2ServersConfigProvider(
    private val config: OAuth2ServersConfig = OAuth2ServersConfig()
) : OAuth2ServersConfigProvider {

    override fun getConfig(): OAuth2ServersConfig = config

    override fun getServer(id: String): OAuth2ServerInstanceConfig? = config.getServer(id)

    override fun getDefaultServer(): OAuth2ServerInstanceConfig = config.getDefaultServer()

    override fun resolveIssuer(serverId: String, tenantId: String): String {
        val server = config.getServer(serverId) ?: return "http://localhost:8080"
        val issuer = server.issuer
        val template = server.issuerTemplate
        return when {
            issuer != null -> issuer
            template != null -> template.replace("{tenant-id}", tenantId)
            else -> server.baseUrl
        }
    }
}
