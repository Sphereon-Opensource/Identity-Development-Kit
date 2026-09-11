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

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.testutil.fixedSigningIdentifierResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Frozen" metadata test capturing the exact shape that the IDK OP advertises under
 * the OIDF Basic/Config OP profile. Any future drift (a newly advertised field, a removed one,
 * a changed default) fails the test so the change is forced to propagate through the runbook.
 *
 * Configuration matches the WP4 OIDF-deployment settings:
 *   - OIDC enabled
 *   - logout / PAR / DPoP / attestation / IAE all disabled (WP5 profiles)
 *   - PKCE required, S256 only
 *   - response_types_supported = ["code"]
 *   - subject_types_supported = ["public"]
 */
class BuildServerMetadataGoldenTest {
    private val ctx = OAuth2ServerTestContext("discovery-golden-test", this)

    private fun oidfBasicConfig(): OAuth2ServerInstanceConfig =
        OAuth2ServerInstanceConfig(
            issuer = "https://oidf-op.example.com",
            oidc = FeaturePolicy.SUPPORTED,
            // Everything below is either explicitly disabled (WP5 scope) or set to the
            // OIDF Basic OP default.
            par = FeaturePolicy.DISABLED,
            dpop = FeaturePolicy.DISABLED,
            attestation = FeaturePolicy.DISABLED,
            iae = FeaturePolicy.DISABLED,
            logout = FeaturePolicy.DISABLED,
            pkce = FeaturePolicy.REQUIRED,
            pkceMethodsSupported = setOf("S256"),
            responseTypesSupported = setOf("code"),
            subjectTypesSupported = listOf("public"),
            idTokenSigningAlgValuesSupported = setOf("ES256"),
            tokenEndpointAuthMethodsSupported = setOf("client_secret_basic", "client_secret_post"),
            introspectionEndpointAuthMethodsSupported = setOf("client_secret_basic"),
            revocationEndpointAuthMethodsSupported = setOf("client_secret_basic", "client_secret_post"),
        )

    @Test
    fun basicOidfOpMetadataMatchesGolden() =
        runTest {
            val provider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig(servers = mapOf("default" to oidfBasicConfig())))
            val command =
                BuildServerMetadataCommandImpl(
                    execution = ctx.execution,
                    configProvider = provider,
                    signingIdentifierResolver = fixedSigningIdentifierResolver(),
                    identifierService = ctx.identifierService,
            grantHandlers = emptyMap(),
                    kmsProviderRegistry = ctx.kmsProviderRegistry,
                    buildSignedMetadata =
                        com.sphereon.oauth2.server.authorization.impl.testutil
                            .StubBuildSignedAuthorizationServerMetadataCommand(ctx.execution),
                )

            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(result.isOk)
            val m: AuthorizationServerMetadata = result.value

            // ── MUST be present ────────────────────────────────────────────
            assertEquals("https://oidf-op.example.com", m.issuer)
            assertEquals("https://oidf-op.example.com/token", m.tokenEndpoint)
            assertEquals("https://oidf-op.example.com/authorize", m.authorizationEndpoint)
            assertEquals("https://oidf-op.example.com/.well-known/jwks.json", m.jwksUri)
            assertEquals("https://oidf-op.example.com/userinfo", m.userinfoEndpoint)
            assertEquals("https://oidf-op.example.com/introspect", m.introspectionEndpoint)
            assertEquals("https://oidf-op.example.com/revoke", m.revocationEndpoint)
            assertEquals(listOf("code"), m.responseTypesSupported)
            assertEquals(listOf("public"), m.subjectTypesSupported)
            assertEquals(listOf("ES256"), m.idTokenSigningAlgValuesSupported)
            assertEquals(listOf("S256"), m.codeChallengeMethodsSupported)
            // token-endpoint auth methods (stored as Set in config, serialised as List)
            assertEquals(
                setOf("client_secret_basic", "client_secret_post"),
                m.tokenEndpointAuthMethodsSupported?.toSet(),
            )

            // ── MUST be absent in the OIDF Basic profile ──────────────────
            assertNull(m.pushedAuthorizationRequestEndpoint, "PAR advertised — review config")
            assertNull(m.requirePushedAuthorizationRequests)
            assertNull(m.dpopSigningAlgValuesSupported)
            assertNull(m.endSessionEndpoint, "end_session_endpoint leaked — logout must stay disabled for OIDF Basic")
            assertNull(m.backchannelLogoutSupported)
            assertNull(m.backchannelLogoutSessionSupported)
            assertNull(m.frontchannelLogoutSupported)
            assertNull(m.challengeEndpoint, "attestation challenge endpoint leaked")
            assertNull(m.clientAttestationSigningAlgValuesSupported)
            assertNull(m.clientAttestationPopSigningAlgValuesSupported)
            assertNull(m.interactiveAuthorizationEndpoint, "IAE endpoint leaked")

            // ── pre-authorized_code anonymous grant: config-derived ──────
            assertFalse(
                m.preAuthorizedGrantAnonymousAccessSupported ?: false,
                "grant-types default does not include pre-authorized_code; advertisement must be false",
            )
        }

    @Test
    fun unsupportedSigningAlgInConfigFailsMetadataBuild() =
        runTest {
            val bad =
                oidfBasicConfig().copy(idTokenSigningAlgValuesSupported = setOf("EdDSA"))
            val provider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig(servers = mapOf("default" to bad)))
            val command =
                BuildServerMetadataCommandImpl(
                    execution = ctx.execution,
                    configProvider = provider,
                    signingIdentifierResolver = fixedSigningIdentifierResolver(),
                    identifierService = ctx.identifierService,
            grantHandlers = emptyMap(),
                    kmsProviderRegistry = ctx.kmsProviderRegistry,
                    buildSignedMetadata =
                        com.sphereon.oauth2.server.authorization.impl.testutil
                            .StubBuildSignedAuthorizationServerMetadataCommand(ctx.execution),
                )

            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(result.isErr, "metadata build must fail-fast on unsupported alg advertisement")
        }

    @Test
    fun pairwiseSubjectTypeInConfigFailsMetadataBuild() =
        runTest {
            val bad =
                oidfBasicConfig().copy(subjectTypesSupported = listOf("public", "pairwise"))
            val provider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig(servers = mapOf("default" to bad)))
            val command =
                BuildServerMetadataCommandImpl(
                    execution = ctx.execution,
                    configProvider = provider,
                    signingIdentifierResolver = fixedSigningIdentifierResolver(),
                    identifierService = ctx.identifierService,
            grantHandlers = emptyMap(),
                    kmsProviderRegistry = ctx.kmsProviderRegistry,
                    buildSignedMetadata =
                        com.sphereon.oauth2.server.authorization.impl.testutil
                            .StubBuildSignedAuthorizationServerMetadataCommand(ctx.execution),
                )

            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(result.isErr, "metadata build must fail-fast on pairwise advertisement until subject hashing is implemented")
        }

    /**
     * OIDF Basic OP shape with discovery driven by a real RSA signing key. Verifies that:
     *  - `id_token_signing_alg_values_supported` is derived from the resolved KMS key alg
     *    when `idTokenSigningAlgValuesSupported` is not pinned in config (the new behaviour),
     *  - the rest of the OIDF Basic metadata shape (PKCE / OIDC / no PAR / no logout / no DPoP)
     *    still holds, and
     *  - all advertised endpoints stay HTTPS when `issuer` is HTTPS.
     *
     * This is the OIDF Config baseline test: real deployments fronted by RSA KMS keys must
     * advertise `RS256` automatically, not the historical `ES256` hardcoded fallback.
     */
    @Test
    fun rsaSigningKeyDrivesIdTokenSigningAlgValuesSupported() =
        runTest {
            val alias = "oidf-rsa-2048"
            val genResult =
                ctx.keyManagerService.generateKeyResult(
                    alias = alias,
                    use = JwkUse.sig,
                    alg = SignatureAlgorithm.RSA_SHA256,
                )
            assertTrue(
                genResult.isOk,
                "RSA test key must be provisioned: ${if (genResult.isErr) genResult.error.message.defaultMessage else ""}",
            )

            val rsaConfig =
                oidfBasicConfig().copy(
                    // `null` so the derivation path is exercised — the historical bug returned
                    // ES256 here regardless of the actual key. The actual signing key is now
                    // resolved through the SigningKeyStore (P0-K4) instead of via per-server
                    // config; the test seeds the store at fixture-construction time.
                    idTokenSigningAlgValuesSupported = null,
                )
            val provider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig(servers = mapOf("default" to rsaConfig)))
            val command =
                BuildServerMetadataCommandImpl(
                    execution = ctx.execution,
                    configProvider = provider,
                    signingIdentifierResolver = fixedSigningIdentifierResolver(ManagedOptsAlias(identifier = alias)),
                    identifierService = ctx.identifierService,
            grantHandlers = emptyMap(),
                    kmsProviderRegistry = ctx.kmsProviderRegistry,
                    buildSignedMetadata =
                        com.sphereon.oauth2.server.authorization.impl.testutil
                            .StubBuildSignedAuthorizationServerMetadataCommand(ctx.execution),
                )

            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(
                result.isOk,
                "metadata build must succeed for RSA-backed OIDF Basic shape: " +
                    if (result.isErr) result.error.message.defaultMessage ?: "" else "",
            )
            val m: AuthorizationServerMetadata = result.value

            assertEquals(
                listOf("RS256"),
                m.idTokenSigningAlgValuesSupported,
                "id_token_signing_alg_values_supported must be derived from the resolved RSA key, not the historical ES256 fallback",
            )

            // OIDF Basic shape (no logout / no PAR / no DPoP) still holds.
            assertEquals(listOf("S256"), m.codeChallengeMethodsSupported)
            assertEquals(listOf("public"), m.subjectTypesSupported)
            assertEquals(listOf("code"), m.responseTypesSupported)
            assertNull(m.pushedAuthorizationRequestEndpoint)
            assertNull(m.dpopSigningAlgValuesSupported)
            assertNull(m.endSessionEndpoint)

            // All advertised endpoints stay HTTPS when issuer is HTTPS.
            val httpsEndpoints =
                listOfNotNull(
                    m.issuer,
                    m.tokenEndpoint,
                    m.authorizationEndpoint,
                    m.jwksUri,
                    m.userinfoEndpoint,
                    m.introspectionEndpoint,
                    m.revocationEndpoint,
                )
            assertTrue(httpsEndpoints.isNotEmpty())
            httpsEndpoints.forEach {
                assertTrue(it.startsWith("https://"), "endpoint must be HTTPS when issuer is HTTPS: $it")
            }
        }

    @Test
    fun tenantProviderSigningKeyDrivesOidcMetadataAlgValuesSupported() =
        runTest {
            // Fresh tenant provisioning stores the AS signing key in a KMS service that is absent
            // from the tenant AS process. Discovery must use the persisted algorithm descriptor
            // and must not attempt to resolve key material merely to build public metadata.
            val providerId = "provider-deliberately-absent-from-local-registry"
            val kid = "oauth2-as-phase-discovery"

            val config =
                oidfBasicConfig().copy(
                    idTokenSigningAlgValuesSupported = null,
                )
            val provider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig(servers = mapOf("default" to config)))
            val command =
                BuildServerMetadataCommandImpl(
                    execution = ctx.execution,
                    configProvider = provider,
                    signingIdentifierResolver =
                        fixedSigningIdentifierResolver(
                            ManagedOptsKeyInfo(
                                identifier =
                                    KeyInfo<KeyType>(
                                        kid = kid,
                                        alias = kid,
                                        providerId = providerId,
                                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                    ),
                            ),
                        ),
                    identifierService = ctx.identifierService,
            grantHandlers = emptyMap(),
                    kmsProviderRegistry = ctx.kmsProviderRegistry,
                    buildSignedMetadata =
                        com.sphereon.oauth2.server.authorization.impl.testutil
                            .StubBuildSignedAuthorizationServerMetadataCommand(ctx.execution),
                )

            val result = command.execute(BuildServerMetadataArgs())

            assertTrue(
                result.isOk,
                "metadata build must resolve the tenant-provider signing key: " +
                    if (result.isErr) result.error.message.defaultMessage ?: "" else "",
            )
            assertEquals(listOf("ES256"), result.value.idTokenSigningAlgValuesSupported)
        }
}
