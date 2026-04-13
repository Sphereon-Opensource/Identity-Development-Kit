/*
 * (c) 2026 Sphereon International B.V.
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests verifying that AS metadata reflects only real capabilities.
 *
 * The metadata document (RFC 8414) must accurately advertise what the AS supports.
 * Disabled features must not appear in metadata, and enabled features must be present.
 * This prevents clients from attempting unsupported flows.
 */
class MetadataCapabilityTest {
    private val ctx = OAuth2ServerTestContext("metadata-capability-test", this)

    // ========================================================================
    // IAE (Interactive Authorization Endpoint) — OID4VCI 1.1 Section 13.3
    // ========================================================================

    @Test
    fun iaeDisabledOmitsEndpoint() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    iae = FeaturePolicy.DISABLED,
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            assertNull(
                result.value.interactiveAuthorizationEndpoint,
                "IAE endpoint must be absent when iae=DISABLED",
            )
        }

    @Test
    fun iaeEnabledIncludesEndpoint() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    iae = FeaturePolicy.SUPPORTED,
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            assertEquals(
                "https://auth.example.com/iae",
                result.value.interactiveAuthorizationEndpoint,
                "IAE endpoint must be present when iae=SUPPORTED",
            )
        }

    // ========================================================================
    // Pre-authorized grant anonymous access
    // ========================================================================

    @Test
    fun preAuthGrantAdvertisedOnlyWhenEnabled() =
        runTest {
            // Config WITHOUT the pre-authorized_code grant type
            val configWithout =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    grantTypesEnabled = setOf("authorization_code", "client_credentials"),
                )
            val providerWithout =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to configWithout)),
                )
            val cmdWithout = BuildServerMetadataCommandImpl(ctx.execution, providerWithout)
            val resultWithout = cmdWithout.execute(BuildServerMetadataArgs())

            assertTrue(resultWithout.isOk)
            assertFalse(
                resultWithout.value.preAuthorizedGrantAnonymousAccessSupported == true,
                "pre-authorized_grant_anonymous_access_supported must not be true when grant type is not enabled",
            )

            // Config WITH the pre-authorized_code grant type
            val configWith =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    grantTypesEnabled =
                        setOf(
                            "authorization_code",
                            "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                        ),
                )
            val providerWith =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to configWith)),
                )
            val cmdWith = BuildServerMetadataCommandImpl(ctx.execution, providerWith)
            val resultWith = cmdWith.execute(BuildServerMetadataArgs())

            assertTrue(resultWith.isOk)
            assertEquals(
                true,
                resultWith.value.preAuthorizedGrantAnonymousAccessSupported,
                "pre-authorized_grant_anonymous_access_supported must be true when grant type is enabled",
            )
        }

    // ========================================================================
    // Minimal metadata — all features disabled
    // ========================================================================

    @Test
    fun allFeaturesDisabledProducesMinimalMetadata() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    introspection = FeaturePolicy.DISABLED,
                    revocation = FeaturePolicy.DISABLED,
                    par = FeaturePolicy.DISABLED,
                    dpop = FeaturePolicy.DISABLED,
                    pkce = FeaturePolicy.DISABLED,
                    iae = FeaturePolicy.DISABLED,
                    oidc = FeaturePolicy.DISABLED,
                    attestation = FeaturePolicy.DISABLED,
                    tokenExchange = FeaturePolicy.DISABLED,
                    grantTypesEnabled = setOf("authorization_code"),
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            val metadata = result.value

            // Required fields must still be present
            assertEquals("https://auth.example.com", metadata.issuer)
            assertNotNull(metadata.tokenEndpoint)
            assertNotNull(metadata.authorizationEndpoint)
            assertNotNull(metadata.jwksUri)

            // All optional feature endpoints must be absent
            assertNull(metadata.introspectionEndpoint, "introspection endpoint should be absent")
            assertNull(metadata.revocationEndpoint, "revocation endpoint should be absent")
            assertNull(metadata.pushedAuthorizationRequestEndpoint, "PAR endpoint should be absent")
            assertNull(metadata.dpopSigningAlgValuesSupported, "DPoP alg values should be absent")
            assertNull(metadata.codeChallengeMethodsSupported, "PKCE methods should be absent")
            assertNull(metadata.interactiveAuthorizationEndpoint, "IAE endpoint should be absent")
            assertNull(metadata.userinfoEndpoint, "userinfo endpoint should be absent")
            assertNull(metadata.subjectTypesSupported, "subject types should be absent")
            assertNull(metadata.idTokenSigningAlgValuesSupported, "id_token signing alg values should be absent")
            assertNull(metadata.claimsSupported, "claims should be absent")
            assertNull(metadata.challengeEndpoint, "attestation challenge endpoint should be absent")
            assertNull(metadata.clientAttestationSigningAlgValuesSupported, "attestation signing alg should be absent")
        }

    // ========================================================================
    // Full metadata — all features enabled
    // ========================================================================

    @Test
    fun allFeaturesEnabledProducesFullMetadata() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    baseUrl = "https://auth.example.com",
                    introspection = FeaturePolicy.SUPPORTED,
                    revocation = FeaturePolicy.SUPPORTED,
                    par = FeaturePolicy.SUPPORTED,
                    dpop = FeaturePolicy.SUPPORTED,
                    pkce = FeaturePolicy.SUPPORTED,
                    iae = FeaturePolicy.SUPPORTED,
                    oidc = FeaturePolicy.SUPPORTED,
                    attestation = FeaturePolicy.SUPPORTED,
                    attestationChallengeRequired = true,
                    dpopSigningAlgValuesSupported = setOf("ES256"),
                    clientAttestationSigningAlgValuesSupported = setOf("ES256"),
                    clientAttestationPopSigningAlgValuesSupported = setOf("ES256"),
                    grantTypesEnabled =
                        setOf(
                            "authorization_code",
                            "client_credentials",
                            "refresh_token",
                            "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                        ),
                )
            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to config)),
                )
            val command = BuildServerMetadataCommandImpl(ctx.execution, configProvider)

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(result.isOk)
            val metadata = result.value

            // Required core fields
            assertEquals("https://auth.example.com", metadata.issuer)
            assertEquals("https://auth.example.com/token", metadata.tokenEndpoint)
            assertEquals("https://auth.example.com/authorize", metadata.authorizationEndpoint)

            // Feature endpoints
            assertEquals("https://auth.example.com/introspect", metadata.introspectionEndpoint)
            assertEquals("https://auth.example.com/revoke", metadata.revocationEndpoint)
            assertEquals("https://auth.example.com/par", metadata.pushedAuthorizationRequestEndpoint)
            assertEquals("https://auth.example.com/iae", metadata.interactiveAuthorizationEndpoint)
            assertEquals("https://auth.example.com/userinfo", metadata.userinfoEndpoint)
            assertEquals("https://auth.example.com/attestation-challenge", metadata.challengeEndpoint)

            // Feature values
            assertNotNull(metadata.dpopSigningAlgValuesSupported)
            assertTrue(metadata.dpopSigningAlgValuesSupported!!.contains("ES256"))
            assertNotNull(metadata.codeChallengeMethodsSupported)
            assertNotNull(metadata.subjectTypesSupported)
            assertNotNull(metadata.idTokenSigningAlgValuesSupported)
            assertNotNull(metadata.claimsSupported)
            assertNotNull(metadata.clientAttestationSigningAlgValuesSupported)
            assertNotNull(metadata.clientAttestationPopSigningAlgValuesSupported)

            // Pre-auth grant
            assertEquals(true, metadata.preAuthorizedGrantAnonymousAccessSupported)
        }
}
