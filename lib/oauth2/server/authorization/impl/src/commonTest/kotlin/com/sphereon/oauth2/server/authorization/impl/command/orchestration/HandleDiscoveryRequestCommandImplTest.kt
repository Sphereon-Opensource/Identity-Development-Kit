/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.orchestration

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestArgs
import com.sphereon.oauth2.server.authorization.impl.command.discovery.HandleDiscoveryRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandleDiscoveryRequestCommandImplTest {
    private val ctx = OAuth2ServerTestContext("handle-discovery-test", this)

    @Test
    fun forwardsServerIdAndBaseUrl() =
        runTest {
            var seenServerId: String? = null
            var seenBaseUrl: String? = null
            val expected =
                AuthorizationServerMetadata(
                    issuer = "https://issuer.example.com",
                    authorizationEndpoint = "https://issuer.example.com/authorize",
                    tokenEndpoint = "https://issuer.example.com/token",
                )
            val buildServerMetadata =
                stubBuildServerMetadata { args ->
                    seenServerId = args.serverId
                    seenBaseUrl = args.baseUrlOverride
                    Ok(expected)
                }
            val command = HandleDiscoveryRequestCommandImpl(ctx.execution, buildServerMetadata)

            val result =
                command.execute(
                    HandleDiscoveryRequestArgs(
                        serverId = "tenant-a",
                        baseUrlOverride = "https://issuer.example.com",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals("tenant-a", seenServerId)
            assertEquals("https://issuer.example.com", seenBaseUrl)
            assertEquals("https://issuer.example.com", result.value.issuer)
        }

    @Test
    fun propagatesBuildMetadataError() =
        runTest {
            val buildServerMetadata =
                stubBuildServerMetadata {
                    Err(IdkError.fromString(code = "server_error", message = "config broken"))
                }
            val command = HandleDiscoveryRequestCommandImpl(ctx.execution, buildServerMetadata)

            val result = command.execute(HandleDiscoveryRequestArgs())

            assertTrue(result.isErr)
            assertEquals("server_error", result.error.code)
        }
}
