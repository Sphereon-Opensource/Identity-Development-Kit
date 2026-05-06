/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.testutil

import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider

/**
 * Test config provider for unit tests.
 * Allows overriding config per test.
 */
class TestOAuth2ServersConfigProvider(
    private val config: OAuth2ServersConfig = OAuth2ServersConfig(),
) : OAuth2ServersConfigProvider {
    override fun getConfig(): OAuth2ServersConfig = config

    override fun getServer(id: String): OAuth2ServerInstanceConfig? = config.getServer(id)

    override fun getDefaultServer(): OAuth2ServerInstanceConfig = config.getDefaultServer()

    override fun resolveIssuer(
        serverId: String,
        tenantId: String,
    ): String {
        val server =
            config.getServer(serverId)
                ?: error("OAuth2 server '$serverId' not found in configuration")
        val issuer = server.issuer
        val template = server.issuerTemplate
        return when {
            issuer != null -> {
                issuer
            }

            template != null -> {
                template.replace("{tenant-id}", tenantId)
            }

            else -> {
                error(
                    "OAuth2 server '$serverId' has no issuer configured; " +
                        "set issuer or issuerTemplate on OAuth2ServerInstanceConfig",
                )
            }
        }
    }
}
