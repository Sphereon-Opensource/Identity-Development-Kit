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

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.oauth2.common.config.AuthorizationServerMode
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildServerMetadataCommandImplTest {
    private val ctx = OAuth2ServerTestContext("discovery-test", this)

    @Test
    fun testMetadataReflectsConfig() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    issuer = "https://auth.example.com",
                    baseUrl = "https://auth.example.com",
                    grantTypesEnabled = setOf("authorization_code", "client_credentials"),
                    responseTypesSupported = setOf("code"),
                    tokenEndpointAuthMethodsSupported = setOf("client_secret_basic", "client_secret_post"),
                    pkce = FeaturePolicy.REQUIRED,
                    introspection = FeaturePolicy.SUPPORTED,
                    revocation = FeaturePolicy.SUPPORTED,
                    par = FeaturePolicy.DISABLED,
                    dpop = FeaturePolicy.DISABLED,
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            val metadata = result.value
            assertEquals("https://auth.example.com", metadata.issuer)
            assertEquals("https://auth.example.com/token", metadata.tokenEndpoint)
            assertEquals("https://auth.example.com/authorize", metadata.authorizationEndpoint)
            assertTrue(metadata.grantTypesSupported!!.contains("authorization_code"))
            assertTrue(metadata.grantTypesSupported!!.contains("client_credentials"))
            assertEquals(listOf("code"), metadata.responseTypesSupported)
            assertEquals(listOf("client_secret_basic", "client_secret_post"), metadata.tokenEndpointAuthMethodsSupported)
        }

    @Test
    fun testDisabledFeaturesOmitEndpoints() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    introspection = FeaturePolicy.DISABLED,
                    revocation = FeaturePolicy.DISABLED,
                    par = FeaturePolicy.DISABLED,
                    dpop = FeaturePolicy.DISABLED,
                    pkce = FeaturePolicy.DISABLED,
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            val metadata = result.value
            assertNull(metadata.introspectionEndpoint)
            assertNull(metadata.revocationEndpoint)
            assertNull(metadata.pushedAuthorizationRequestEndpoint)
            assertNull(metadata.dpopSigningAlgValuesSupported)
            assertNull(metadata.codeChallengeMethodsSupported)
        }

    @Test
    fun testEnabledFeaturesIncludeEndpoints() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    introspection = FeaturePolicy.SUPPORTED,
                    revocation = FeaturePolicy.SUPPORTED,
                    par = FeaturePolicy.SUPPORTED,
                    pkce = FeaturePolicy.SUPPORTED,
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            val metadata = result.value
            assertNotNull(metadata.introspectionEndpoint)
            assertNotNull(metadata.revocationEndpoint)
            assertNotNull(metadata.pushedAuthorizationRequestEndpoint)
            assertNotNull(metadata.codeChallengeMethodsSupported)
            assertEquals("https://auth.example.com/introspect", metadata.introspectionEndpoint)
            assertEquals("https://auth.example.com/revoke", metadata.revocationEndpoint)
            assertEquals("https://auth.example.com/par", metadata.pushedAuthorizationRequestEndpoint)
        }

    @Test
    fun testParRequiredSetsFlag() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    par = FeaturePolicy.REQUIRED,
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            assertEquals(true, result.value.requirePushedAuthorizationRequests)
        }

    @Test
    fun testExternalServerReturnsError() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    mode = AuthorizationServerMode.EXTERNAL,
                    baseUrl = "https://external-as.example.com",
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isErr)
        }

    @Test
    fun testNamedServerLookup() =
        runTest {
            val primary =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://primary.example.com",
                    revocation = FeaturePolicy.SUPPORTED,
                )
            val secondary =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://secondary.example.com",
                    revocation = FeaturePolicy.DISABLED,
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("primary" to primary, "secondary" to secondary)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result1 = command.execute(BuildServerMetadataArgs(serverId = "primary"))
            assertTrue(result1.isOk)
            assertNotNull(result1.value.revocationEndpoint)

            val result2 = command.execute(BuildServerMetadataArgs(serverId = "secondary"))
            assertTrue(result2.isOk)
            assertNull(result2.value.revocationEndpoint)
        }

    @Test
    fun testUnknownServerReturnsError() =
        runTest {
            val configProvider = TestOAuth2ServersConfigProvider()
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs(serverId = "nonexistent"))

            assertTrue(result.isErr)
        }

    @Test
    fun testBaseUrlOverride() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://internal.example.com",
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result =
                command.execute(
                    BuildServerMetadataArgs(baseUrlOverride = "https://public.example.com"),
                )

            assertTrue(result.isOk)
            assertEquals("https://public.example.com", result.value.issuer)
            assertEquals("https://public.example.com/token", result.value.tokenEndpoint)
        }
}
