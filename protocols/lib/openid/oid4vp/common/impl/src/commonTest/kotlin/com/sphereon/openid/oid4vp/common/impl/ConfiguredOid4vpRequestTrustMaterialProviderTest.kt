/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.openid.oid4vp.common.impl

import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.MutableMapPropertySource
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.conf.PropertySourcesPropertyResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfiguredOid4vpRequestTrustMaterialProviderTest {
    private val rootA = pem("AAAA")
    private val rootB = pem("BBBB")

    @Test
    fun failsClosedWithoutResolver() =
        runTest {
            val result = ConfiguredOid4vpRequestTrustMaterialProvider(null).resolve()
            assertTrue(result.isErr)
            assertEquals("X5C_TRUST_MATERIAL_UNAVAILABLE", result.error.code)
        }

    @Test
    fun failsClosedWhenNothingIsConfigured() =
        runTest {
            val result = ConfiguredOid4vpRequestTrustMaterialProvider(resolver("other.key" to "x")).resolve()
            assertTrue(result.isErr)
            assertEquals("X5C_TRUST_MATERIAL_UNAVAILABLE", result.error.code)
        }

    @Test
    fun readsSingleValueAndIndexedValuesInOrder() =
        runTest {
            val result =
                ConfiguredOid4vpRequestTrustMaterialProvider(
                    resolver(
                        "${ConfiguredOid4vpRequestTrustMaterialProvider.TRUST_ANCHORS_KEY}.1" to rootB,
                        "${ConfiguredOid4vpRequestTrustMaterialProvider.TRUST_ANCHORS_KEY}.0" to rootA,
                    ),
                ).resolve()
            assertTrue(result.isOk)
            assertEquals(listOf(rootA, rootB), result.value.x509.map { it.certificatePem })
        }

    @Test
    fun splitsConcatenatedPemAndDeduplicates() =
        runTest {
            val result =
                ConfiguredOid4vpRequestTrustMaterialProvider(
                    resolver(ConfiguredOid4vpRequestTrustMaterialProvider.TRUST_ANCHORS_KEY to "$rootA\n$rootB\n$rootA"),
                ).resolve()
            assertTrue(result.isOk)
            assertEquals(listOf(rootA, rootB), result.value.x509.map { it.certificatePem })
        }

    @Test
    fun blankValuesAreIgnored() =
        runTest {
            val result =
                ConfiguredOid4vpRequestTrustMaterialProvider(
                    resolver(ConfiguredOid4vpRequestTrustMaterialProvider.TRUST_ANCHORS_KEY to "   "),
                ).resolve()
            assertTrue(result.isErr)
        }

    private fun resolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    private fun pem(body: String) = "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----"
}
