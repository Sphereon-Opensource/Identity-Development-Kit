/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.core.api.conf

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InterpolationPolicyTest {
    @Test
    fun cacheIdentityIsStableAndChangesWithExactAuthority() {
        val first =
            DefaultInterpolationPolicyProvider(
                mapOf(
                    "feature.name" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                    "feature.path" to InterpolationPolicy.APP_ENVIRONMENT,
                ),
            )
        val reordered =
            DefaultInterpolationPolicyProvider(
                mapOf(
                    "feature.path" to InterpolationPolicy.APP_ENVIRONMENT,
                    "feature.name" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                ),
            )
        val tightened =
            DefaultInterpolationPolicyProvider(
                mapOf(
                    "feature.name" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                    "feature.path" to InterpolationPolicy.DENY,
                ),
            )

        assertEquals(first.cacheIdentity, reordered.cacheIdentity)
        assertNotEquals(first.cacheIdentity, tightened.cacheIdentity)
    }

    @Test
    fun unlistedAppEnvironmentIsDeniedAndExactAllowlistedKeyIsAllowed() {
        val app =
            ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                addProperty("deployment.path", "\${env:PATH}")
            }
        val sources = DefaultPropertySources(mutableListOf(app))
        val denied =
            PropertyResolverFactory.withInterpolation(
                propertySources = sources,
                resolverLevel = ConfigLevel.APP,
            )
        val allowed =
            PropertyResolverFactory.withInterpolation(
                propertySources = sources,
                resolverLevel = ConfigLevel.APP,
                interpolationPolicyProvider =
                    DefaultInterpolationPolicyProvider(
                        mapOf("deployment.path" to InterpolationPolicy.APP_ENVIRONMENT),
                    ),
            )

        assertFailsWith<IllegalStateException> {
            denied.getPropertyAsString("deployment.path")
        }
        assertTrue(allowed.getRequiredPropertyAsString("deployment.path").isNotEmpty())
    }

    @Test
    fun exactAppEnvironmentPolicyDoesNotAuthorizeTenantOrPrincipalEnvironmentReads() =
        runTest {
            val app =
                ProtectedMutableMapPropertySource("app", ConfigLevel.APP).apply {
                    addProperty("deployment.path", "\${env:PATH}")
                }
            val sources = DefaultPropertySources(mutableListOf(app))
            val policy =
                DefaultInterpolationPolicyProvider(
                    mapOf("deployment.path" to InterpolationPolicy.APP_ENVIRONMENT),
                )
            val interpolator = DefaultPropertyInterpolator()

            listOf(ConfigLevel.TENANT, ConfigLevel.PRINCIPAL).forEach { level ->
                assertEquals(
                    InterpolationPolicy.DENY,
                    policy.policyFor("deployment.path", level),
                )
                val resolver = ProtectedPropertySourcesResolver(sources, level)
                val result =
                    interpolator.interpolateWithProvenance(
                        value = "\${env:PATH}",
                        resolver = resolver,
                        requestingScope = level,
                        maxDepth = null,
                        policy = policy.policyFor("deployment.path", level),
                        sourceProvenance = ResolutionProvenance.known(level),
                    )
                assertTrue(result.isErr)
                assertFalse(result.error.message.defaultMessage.contains("PATH"))
            }
        }

    @Test
    fun sensitiveKeysDefaultDenyAndRequireExactExplicitPolicy() {
        val provider = DefaultInterpolationPolicyProvider()
        val explicit =
            DefaultInterpolationPolicyProvider(
                mapOf("oauth.client-id" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
            )

        assertEquals(InterpolationPolicy.DENY, provider.policyFor("oauth.client-id", ConfigLevel.APP))
        assertEquals(InterpolationPolicy.DENY, provider.policyFor("smtp.password", ConfigLevel.APP))
        assertEquals(
            InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
            explicit.policyFor("oauth.client-id", ConfigLevel.APP),
        )
    }

    @Test
    fun patternPoliciesSupportIndexedKeysAndRemainAppOnly() {
        val provider =
            DefaultInterpolationPolicyProvider(
                InterpolationPolicyCatalog(
                    patternPolicies =
                        listOf(
                            InterpolationPolicyPattern(
                                normalizedKeyRegex = "^oauth2\\.clients\\.\\[\\d+]\\.audience$",
                                policy = InterpolationPolicy.APP_ENVIRONMENT,
                            ),
                        ),
                ),
            )

        assertEquals(
            InterpolationPolicy.APP_ENVIRONMENT,
            provider.policyFor("oauth2.clients.[17].audience", ConfigLevel.APP),
        )
        assertEquals(
            InterpolationPolicy.DENY,
            provider.policyFor("oauth2.clients.[17].audience", ConfigLevel.TENANT),
        )
        assertEquals(
            InterpolationPolicy.DENY,
            provider.policyFor("oauth2.clients.[17].secret", ConfigLevel.APP),
        )
    }

    @Test
    fun exactPoliciesWinAndPatternOrderChangesCacheIdentity() {
        val allow =
            InterpolationPolicyPattern(
                normalizedKeyRegex = "^oauth2\\.clients\\.\\[\\d+]\\.audience$",
                policy = InterpolationPolicy.APP_ENVIRONMENT,
            )
        val deny =
            InterpolationPolicyPattern(
                normalizedKeyRegex = "^oauth2\\.clients\\.\\[\\d+]\\..+$",
                policy = InterpolationPolicy.DENY,
            )
        val allowThenDeny =
            DefaultInterpolationPolicyProvider(
                InterpolationPolicyCatalog(
                    exactPolicies = mapOf("oauth2.clients.[7].audience" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                    patternPolicies = listOf(allow, deny),
                ),
            )
        val denyThenAllow =
            DefaultInterpolationPolicyProvider(
                InterpolationPolicyCatalog(
                    exactPolicies = mapOf("oauth2.clients.[7].audience" to InterpolationPolicy.PROPERTY_REFERENCES_ONLY),
                    patternPolicies = listOf(deny, allow),
                ),
            )

        assertEquals(
            InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
            allowThenDeny.policyFor("oauth2.clients.[7].audience", ConfigLevel.APP),
        )
        assertNotEquals(allowThenDeny.cacheIdentity, denyThenAllow.cacheIdentity)
    }
}
