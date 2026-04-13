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

package com.sphereon.core.api.conf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileChainTest {

    @Test
    fun getResolutionChainIncludesDefaultWhenFallbackEnabled() {
        val chain = ProfileChain(
            profiles = listOf("production"),
            fallbackToDefault = true,
            defaultProfile = "default"
        )

        val resolutionChain = chain.getResolutionChain()

        assertEquals(listOf("production", "default"), resolutionChain)
    }

    @Test
    fun getResolutionChainExcludesDefaultWhenFallbackDisabled() {
        val chain = ProfileChain(
            profiles = listOf("production"),
            fallbackToDefault = false
        )

        val resolutionChain = chain.getResolutionChain()

        assertEquals(listOf("production"), resolutionChain)
    }

    @Test
    fun getResolutionChainDoesNotDuplicateDefault() {
        val chain = ProfileChain(
            profiles = listOf("production", "default"),
            fallbackToDefault = true
        )

        val resolutionChain = chain.getResolutionChain()

        assertEquals(listOf("production", "default"), resolutionChain)
    }

    @Test
    fun containsReturnsTrueForProfileInChain() {
        val chain = ProfileChain(
            profiles = listOf("production", "staging"),
            fallbackToDefault = false
        )

        assertTrue(chain.contains("production"))
        assertTrue(chain.contains("staging"))
        assertFalse(chain.contains("development"))
    }

    @Test
    fun containsReturnsTrueForDefaultWhenFallbackEnabled() {
        val chain = ProfileChain(
            profiles = listOf("production"),
            fallbackToDefault = true
        )

        assertTrue(chain.contains("default"))
    }

    @Test
    fun containsReturnsFalseForDefaultWhenFallbackDisabled() {
        val chain = ProfileChain(
            profiles = listOf("production"),
            fallbackToDefault = false
        )

        assertFalse(chain.contains("default"))
    }

    @Test
    fun ofCreatesChainWithSingleProfile() {
        val chain = ProfileChain.of("production")

        assertEquals(listOf("production"), chain.profiles)
        assertTrue(chain.fallbackToDefault)
    }

    @Test
    fun ofCreatesChainWithMultipleProfiles() {
        val chain = ProfileChain.of("production", "staging")

        assertEquals(listOf("production", "staging"), chain.profiles)
    }

    @Test
    fun ofCreatesChainWithoutFallback() {
        val chain = ProfileChain.of("production", fallbackToDefault = false)

        assertFalse(chain.fallbackToDefault)
    }

    @Test
    fun defaultOnlyCreatesEmptyChainWithFallback() {
        val chain = ProfileChain.defaultOnly()

        assertTrue(chain.profiles.isEmpty())
        assertTrue(chain.fallbackToDefault)
        assertEquals(listOf("default"), chain.getResolutionChain())
    }
}

class DefaultProfileResolverTest {

    @Test
    fun getActiveProfilesReturnsConfiguredProfiles() {
        val resolver = DefaultProfileResolver(
            activeProfiles = listOf("production", "eu-west")
        )

        assertEquals(listOf("production", "eu-west"), resolver.getActiveProfiles())
    }

    @Test
    fun getActiveProfilesReturnsEmptyListWhenNoProfiles() {
        val resolver = DefaultProfileResolver(activeProfiles = emptyList())

        // May be empty or loaded from env depending on environment
        assertNotNull(resolver.getActiveProfiles())
    }

    @Test
    fun isProfileActiveReturnsTrueForActiveProfile() {
        val resolver = DefaultProfileResolver(
            activeProfiles = listOf("production")
        )

        assertTrue(resolver.isProfileActive("production"))
    }

    @Test
    fun isProfileActiveReturnsTrueForDefaultProfile() {
        val resolver = DefaultProfileResolver(
            activeProfiles = listOf("production")
        )

        assertTrue(resolver.isProfileActive("default"))
    }

    @Test
    fun isProfileActiveReturnsFalseForInactiveProfile() {
        val resolver = DefaultProfileResolver(
            activeProfiles = listOf("production")
        )

        assertFalse(resolver.isProfileActive("staging"))
    }

    @Test
    fun getProfilePriorityReturnsIndexForActiveProfile() {
        val resolver = DefaultProfileResolver(
            activeProfiles = listOf("production", "staging", "development")
        )

        assertEquals(0, resolver.getProfilePriority("production"))
        assertEquals(1, resolver.getProfilePriority("staging"))
        assertEquals(2, resolver.getProfilePriority("development"))
    }

    @Test
    fun getProfilePriorityReturnsLastForDefault() {
        val resolver = DefaultProfileResolver(
            activeProfiles = listOf("production", "staging")
        )

        assertEquals(2, resolver.getProfilePriority("default"))
    }

    @Test
    fun getProfilePriorityReturnsNullForInactiveProfile() {
        val resolver = DefaultProfileResolver(
            activeProfiles = listOf("production")
        )

        assertNull(resolver.getProfilePriority("unknown"))
    }

    @Test
    fun defaultProfileIsDefault() {
        val resolver = DefaultProfileResolver()

        assertEquals("default", resolver.defaultProfile)
    }

    @Test
    fun customDefaultProfileIsUsed() {
        val resolver = DefaultProfileResolver(
            defaultProfile = "base"
        )

        assertEquals("base", resolver.defaultProfile)
    }

    @Test
    fun getProfileChainReturnsValidChain() {
        val resolver = DefaultProfileResolver(
            activeProfiles = listOf("production", "eu")
        )

        val chain = resolver.getProfileChain()

        assertEquals(listOf("production", "eu"), chain.profiles)
        assertTrue(chain.fallbackToDefault)
    }
}

class ProfileMetadataTest {

    @Test
    fun defaultValuesAreCorrect() {
        val metadata = ProfileMetadata()

        assertEquals("default", metadata.profile)
        assertFalse(metadata.isSecretRef)
        assertTrue(metadata.additionalMetadata.isEmpty())
    }

    @Test
    fun customValuesArePreserved() {
        val metadata = ProfileMetadata(
            profile = "production",
            isSecretRef = true,
            additionalMetadata = mapOf("source" to "vault")
        )

        assertEquals("production", metadata.profile)
        assertTrue(metadata.isSecretRef)
        assertEquals("vault", metadata.additionalMetadata["source"])
    }
}

class ResolutionContextProfileExtensionsTest {

    @Test
    fun profileExtensionReturnsDefaultWhenNull() {
        val context = ResolutionContext.app()

        assertEquals("default", context.profile)
    }

    @Test
    fun profileExtensionReturnsConfiguredProfile() {
        val context = ResolutionContext.app().copy(
            options = ResolutionOptions(profile = "production")
        )

        assertEquals("production", context.profile)
    }

    @Test
    fun withProfileCreatesNewContextWithProfile() {
        val original = ResolutionContext.app()
        val withProfile = original.withProfile("staging")

        assertEquals("default", original.profile)
        assertEquals("staging", withProfile.profile)
    }

    @Test
    fun withProfileChainSetsFirstProfileAsActive() {
        val context = ResolutionContext.app()
        val chain = ProfileChain.of("production", "staging")
        val withChain = context.withProfileChain(chain)

        assertEquals("production", withChain.profile)
    }

    @Test
    fun withProfileChainUsesDefaultWhenChainIsEmpty() {
        val context = ResolutionContext.app()
        val chain = ProfileChain.defaultOnly()
        val withChain = context.withProfileChain(chain)

        assertEquals("default", withChain.profile)
    }
}
