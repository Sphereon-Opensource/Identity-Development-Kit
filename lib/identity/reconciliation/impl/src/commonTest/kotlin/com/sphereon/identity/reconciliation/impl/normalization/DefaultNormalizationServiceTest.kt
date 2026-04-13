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

package com.sphereon.identity.reconciliation.impl.normalization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultNormalizationServiceTest {
    private val service = DefaultNormalizationService()

    @Test
    fun lowercaseTrimProfile() {
        assertEquals("alice", service.normalize("  Alice  ", "lowercase-trim"))
        assertEquals("bob", service.normalize("BOB", "lowercase-trim"))
    }

    @Test
    fun exactProfile() {
        assertEquals("  Alice  ", service.normalize("  Alice  ", "exact"))
    }

    @Test
    fun humanNameBirthStripsAccents() {
        assertEquals("francois", service.normalize("François", "human-name-birth-v1"))
        assertEquals("muller", service.normalize("Müller", "human-name-birth-v1"))
        assertEquals("strauss", service.normalize("Strauß", "human-name-birth-v1"))
    }

    @Test
    fun humanNameBirthTrimsAndLowercases() {
        assertEquals("alice", service.normalize("  ALICE  ", "human-name-birth-v1"))
    }

    @Test
    fun humanNameBirthHandlesPlainAscii() {
        assertEquals("john smith", service.normalize("John Smith", "human-name-birth-v1"))
    }

    @Test
    fun emailProfile() {
        assertEquals("user@example.com", service.normalize("  User@Example.COM  ", "email-lowercase-v1"))
    }

    @Test
    fun unknownProfileThrows() {
        assertFailsWith<IllegalArgumentException> {
            service.normalize("test", "nonexistent-profile")
        }
    }

    @Test
    fun supportedProfilesReturnsAll() {
        val profiles = service.supportedProfiles()
        assertTrue(profiles.contains("lowercase-trim"))
        assertTrue(profiles.contains("exact"))
        assertTrue(profiles.contains("human-name-birth-v1"))
        assertTrue(profiles.contains("email-lowercase-v1"))
    }
}
