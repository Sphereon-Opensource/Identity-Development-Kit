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

/**
 * Tests for OIDC discovery metadata — verifies that when oidc=SUPPORTED,
 * the discovery document includes all required OIDC fields, and when
 * oidc=DISABLED, OIDC-specific fields are omitted.
 */
class OidcDiscoveryMetadataTest {
    private val ctx = OAuth2ServerTestContext("oidc-discovery-test", this)

    @Test
    fun oidcEnabledIncludesOidcFields() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    oidc = FeaturePolicy.SUPPORTED,
                    introspection = FeaturePolicy.SUPPORTED,
                    revocation = FeaturePolicy.SUPPORTED,
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            val metadata = result.value

            // OIDC-required fields
            assertEquals("https://auth.example.com/userinfo", metadata.userinfoEndpoint)
            assertNotNull(metadata.subjectTypesSupported)
            assertTrue(metadata.subjectTypesSupported!!.contains("public"))
            assertNotNull(metadata.idTokenSigningAlgValuesSupported)
            assertTrue(metadata.idTokenSigningAlgValuesSupported!!.contains("ES256"))
            assertNotNull(metadata.claimsSupported)
            assertTrue(metadata.claimsSupported!!.contains("sub"))
            assertTrue(metadata.claimsSupported!!.contains("email"))
            assertTrue(metadata.claimsSupported!!.contains("name"))
        }

    @Test
    fun oidcDisabledOmitsOidcFields() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    oidc = FeaturePolicy.DISABLED,
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            val metadata = result.value

            // OIDC fields must be absent
            assertNull(metadata.userinfoEndpoint)
            assertNull(metadata.subjectTypesSupported)
            assertNull(metadata.idTokenSigningAlgValuesSupported)
            assertNull(metadata.claimsSupported)
        }

    @Test
    fun oidcDefaultIsDisabled() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    // oidc not set — defaults to DISABLED
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            val metadata = result.value

            assertNull(metadata.userinfoEndpoint, "OIDC disabled by default")
            assertNull(metadata.subjectTypesSupported)
        }

    @Test
    fun oidcEnabledWithBaseUrlOverride() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://internal.example.com",
                    oidc = FeaturePolicy.SUPPORTED,
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
            val metadata = result.value

            assertEquals("https://public.example.com/userinfo", metadata.userinfoEndpoint)
            assertEquals("https://public.example.com", metadata.issuer)
        }

    @Test
    fun oidcEnabledWithCustomClaimsSupported() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    oidc = FeaturePolicy.SUPPORTED,
                    claimsSupported = listOf("sub", "email", "custom_claim"),
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            assertEquals(listOf("sub", "email", "custom_claim"), result.value.claimsSupported)
        }
}
