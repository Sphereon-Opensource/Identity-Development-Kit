/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.crypto.core.kms

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for provider and resolver selection to ensure deterministic behavior.
 */
class ProviderSelectionTest {

    @Test
    fun testProviderQueryReturnsMatchingProvider() = runTest {
        val mock = TestKmsMock()
        val provider = TestKmsProviderMock()
        mock.registerProvider(provider, makeDefaultKms = true)

        val result = mock.queryProvider(kmsQuery {
            signatureAlgorithm = com.sphereon.crypto.core.generic.SignatureAlgorithm.ECDSA_SHA256
        })

        assertTrue(result.isOk, "Expected Ok result")
        assertEquals(provider.id, result.value.match?.providerId)
    }

    @Test
    fun testProviderQueryReturnsErrorWhenNoMatch() = runTest {
        val mock = TestKmsMock()
        // Don't register any providers

        val result = mock.queryProvider(kmsQuery {
            signatureAlgorithm = com.sphereon.crypto.core.generic.SignatureAlgorithm.ECDSA_SHA256
        })

        assertTrue(result.isErr, "Expected Err result when no provider matches")
    }

    @Test
    fun testQueryProvidersReturnsAllMatching() = runTest {
        val mock = TestKmsMock()
        val provider = TestKmsProviderMock()
        mock.registerProvider(provider, makeDefaultKms = true)

        val result = mock.queryProviders(kmsQuery {
            // Empty query matches all
        })

        assertTrue(result.isOk)
        assertEquals(1, result.value.matchCount)
    }

    @Test
    fun testGetAllCapabilities() = runTest {
        val mock = TestKmsMock()
        val provider = TestKmsProviderMock()
        mock.registerProvider(provider, makeDefaultKms = true)

        val result = mock.getAllCapabilities(includeDisabled = false)

        assertTrue(result.isOk)
        assertTrue(result.value.capabilities.containsKey(provider.id))
    }

    @Test
    fun testResolverRegistration() {
        val mock = TestKmsMock()
        val resolver = TestKeyResolver()
        mock.registerResolver(resolver, makeDefaultResolver = true)

        assertEquals(resolver.getId(), mock.defaultResolverId())
        assertEquals(resolver, mock.getResolverById(resolver.getId()))
    }

    @Test
    fun testProviderRegistration() {
        val mock = TestKmsMock()
        val provider = TestKmsProviderMock()
        mock.registerProvider(provider, makeDefaultKms = true)

        assertEquals(provider.id, mock.defaultProviderId())
        assertEquals(provider, mock.getProviderById(provider.id))
        assertTrue(mock.getProviderIds().contains(provider.id))
    }
}
