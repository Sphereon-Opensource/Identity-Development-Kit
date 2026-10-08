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
 *
 */

package com.sphereon.core.api.conf

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Interface for resolving active profiles in the configuration system.
 * Profiles allow environment-specific configuration (e.g., "development", "staging", "production").
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProfileResolver", exact = true)
interface ProfileResolver {
    /**
     * Get the default profile name.
     */
    val defaultProfile: String

    /**
     * Get the list of active profiles in priority order (highest priority first).
     */
    fun getActiveProfiles(): List<String>

    /**
     * Check if a specific profile is active.
     */
    fun isProfileActive(profile: String): Boolean

    /**
     * Get the priority of a profile (lower number = higher priority).
     * Returns null if the profile is not active.
     */
    fun getProfilePriority(profile: String): Int?
}

/**
 * Profile chain for configuration resolution.
 * Defines the order of profiles to check when resolving configuration.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProfileChain", exact = true)
@CoverageExcludedDataClass
data class ProfileChain(
    /**
     * Ordered list of profiles to check (highest priority first).
     */
    val profiles: List<String>,
    /**
     * Whether to fall back to default profile if key not found in active profiles.
     */
    val fallbackToDefault: Boolean = true,
    /**
     * The default profile name.
     */
    val defaultProfile: String = "default",
) {
    /**
     * Get the full resolution chain including default if fallback is enabled.
     */
    fun getResolutionChain(): List<String> =
        if (fallbackToDefault && !profiles.contains(defaultProfile)) {
            profiles + defaultProfile
        } else {
            profiles
        }

    /**
     * Check if a profile is in the chain.
     */
    fun contains(profile: String): Boolean =
        profiles.contains(profile) ||
            (fallbackToDefault && profile == defaultProfile)

    companion object {
        /**
         * Create a profile chain with a single profile.
         */
        @JvmStatic
        fun of(
            profile: String,
            fallbackToDefault: Boolean = true,
        ): ProfileChain = ProfileChain(listOf(profile), fallbackToDefault)

        /**
         * Create a profile chain with multiple profiles.
         */
        @JvmStatic
        fun of(
            vararg profiles: String,
            fallbackToDefault: Boolean = true,
        ): ProfileChain = ProfileChain(profiles.toList(), fallbackToDefault)

        /**
         * Create a default-only profile chain.
         */
        @JvmStatic
        fun defaultOnly(): ProfileChain = ProfileChain(emptyList(), fallbackToDefault = true)
    }
}

/**
 * Default profile resolver that uses environment variables or system properties.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultProfileResolver", exact = true)
class DefaultProfileResolver(
    /**
     * Active profiles. If empty, will be populated from environment.
     */
    activeProfiles: List<String> = emptyList(),
    /**
     * Environment variable name for active profiles.
     */
    private val envVarName: String = "SPHEREON_PROFILES_ACTIVE",
    /**
     * Profile separator in environment variable.
     */
    private val profileSeparator: String = ",",
    /**
     * Default profile name.
     */
    override val defaultProfile: String = "default",
) : ProfileResolver {
    private val profiles: List<String> =
        activeProfiles.ifEmpty {
            loadProfilesFromEnvironment()
        }

    private fun loadProfilesFromEnvironment(): List<String> {
        val envValue = Env.get(envVarName)
        return if (envValue.isNullOrBlank()) {
            emptyList()
        } else {
            envValue
                .split(profileSeparator)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }

    override fun getActiveProfiles(): List<String> = profiles

    override fun isProfileActive(profile: String): Boolean = profiles.contains(profile) || profile == defaultProfile

    override fun getProfilePriority(profile: String): Int? {
        val index = profiles.indexOf(profile)
        return when {
            index >= 0 -> index
            profile == defaultProfile -> profiles.size
            else -> null
        }
    }

    /**
     * Get the profile chain for resolution.
     */
    fun getProfileChain(): ProfileChain =
        ProfileChain(
            profiles = profiles,
            fallbackToDefault = true,
            defaultProfile = defaultProfile,
        )
}

/**
 * Profile-aware property resolution support.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProfileAwareResolution", exact = true)
interface ProfileAwareResolution {
    /**
     * Get the profile resolver.
     */
    val profileResolver: ProfileResolver

    /**
     * Get a property with profile-aware resolution.
     * Checks profiles in order, falling back to default if configured.
     */
    fun getPropertyWithProfile(key: String): String?

    /**
     * Get all properties for a specific profile.
     */
    fun getPropertiesForProfile(profile: String): Map<String, String>
}

/**
 * Profile metadata for configuration settings.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProfileMetadata", exact = true)
@CoverageExcludedDataClass
data class ProfileMetadata(
    /**
     * The profile this setting belongs to.
     */
    val profile: String = "default",
    /**
     * Additional metadata for the setting.
     */
    val additionalMetadata: Map<String, String> = emptyMap(),
)

/**
 * Extension to get profile from ResolutionContext.
 */
val ResolutionContext.profile: String
    get() = options.profile ?: "default"

/**
 * Extension to create a context with a specific profile.
 */
fun ResolutionContext.withProfile(profile: String): ResolutionContext =
    copy(
        options = options.copy(profile = profile),
    )

/**
 * Extension to create a context with multiple profiles (chain).
 */
fun ResolutionContext.withProfileChain(chain: ProfileChain): ResolutionContext =
    copy(
        options =
            options.copy(
                profile = chain.profiles.firstOrNull() ?: chain.defaultProfile,
                additionalOptions =
                    options.additionalOptions +
                        mapOf(
                            "profileChain" to chain.profiles.joinToString(","),
                            "fallbackToDefault" to chain.fallbackToDefault.toString(),
                        ),
            ),
    )
