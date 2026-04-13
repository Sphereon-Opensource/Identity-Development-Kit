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
 *
 */

package com.sphereon.crypto.key.persistence.impl

import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.crypto.core.kms.ManagedKeyStoreMode
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultManagedKeyStoreModeResolverTest {
    private val configService = mockk<PrincipalConfigService>()
    private val resolver = DefaultManagedKeyStoreModeResolver(configService)

    @Test
    fun returnsAutoWhenNoConfig() {
        every { configService.getPropertyAsString("sphereon.crypto.kms.managed-key-store.mode", any()) } returns null

        val result = resolver.resolve()

        assertEquals(ManagedKeyStoreMode.AUTO, result)
    }

    @Test
    fun returnsIteratingWhenConfigured() {
        every { configService.getPropertyAsString("sphereon.crypto.kms.managed-key-store.mode", any()) } returns "iterating"

        val result = resolver.resolve()

        assertEquals(ManagedKeyStoreMode.ITERATING, result)
    }

    @Test
    fun returnsPersistentWhenConfigured() {
        every { configService.getPropertyAsString("sphereon.crypto.kms.managed-key-store.mode", any()) } returns "persistent"

        val result = resolver.resolve()

        assertEquals(ManagedKeyStoreMode.PERSISTENT, result)
    }

    @Test
    fun returnsAutoForInvalidValue() {
        every { configService.getPropertyAsString("sphereon.crypto.kms.managed-key-store.mode", any()) } returns "invalid"

        val result = resolver.resolve()

        assertEquals(ManagedKeyStoreMode.AUTO, result)
    }

    @Test
    fun isCaseInsensitive() {
        every { configService.getPropertyAsString("sphereon.crypto.kms.managed-key-store.mode", any()) } returns "PERSISTENT"

        val result = resolver.resolve()

        assertEquals(ManagedKeyStoreMode.PERSISTENT, result)
    }
}
