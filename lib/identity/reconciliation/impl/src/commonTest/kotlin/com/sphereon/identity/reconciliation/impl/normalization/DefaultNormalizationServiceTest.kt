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
