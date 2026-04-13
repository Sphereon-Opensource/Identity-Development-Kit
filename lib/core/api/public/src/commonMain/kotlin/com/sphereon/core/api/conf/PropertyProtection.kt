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

import com.sphereon.core.api.error.IdkError
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Reserved key prefixes for property protection.
 *
 * Properties can be protected from override and/or interpolation using these prefixes:
 * - `final.` prefix: Cannot be overridden at lower scope levels
 * - `protected.` prefix: Cannot be interpolated from lower scope levels
 * - `final.protected.` prefix: Both protections
 *
 * For environment variables, use underscore-separated prefixes (FINAL_, PROTECTED_, FINAL_PROTECTED_).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectionPrefixes", exact = true)
object ProtectionPrefixes {
    /** Prefix for properties that cannot be overridden at lower scopes */
    const val FINAL = "final."

    /** Prefix for properties that cannot be interpolated from lower scopes */
    const val PROTECTED = "protected."

    /** Combined prefix for both final and protected */
    const val FINAL_PROTECTED = "final.protected."

    /** Alternative order for combined prefix */
    const val PROTECTED_FINAL = "protected.final."

    // Environment variable prefixes (underscore-separated)

    /** Environment variable prefix for final properties */
    const val FINAL_ENV = "FINAL_"

    /** Environment variable prefix for protected properties */
    const val PROTECTED_ENV = "PROTECTED_"

    /** Environment variable prefix for final and protected properties */
    const val FINAL_PROTECTED_ENV = "FINAL_PROTECTED_"

    /** Alternative order for environment variable combined prefix */
    const val PROTECTED_FINAL_ENV = "PROTECTED_FINAL_"
}

/**
 * Protection modifiers for a property value.
 *
 * Protection is determined when loading configuration and enforced when:
 * - A lower scope attempts to override a FINAL property
 * - A lower scope attempts to interpolate a PROTECTED property via `${scope:key}`
 *
 * @property isFinal If true, property cannot be overridden at lower scopes
 * @property isInterpolationProtected If true, property cannot be interpolated from lower scopes
 * @property definedAt The scope level where this protection was defined
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertyProtection", exact = true)
@CoverageExcludedDataClass
data class PropertyProtection(
    val isFinal: Boolean = false,
    val isInterpolationProtected: Boolean = false,
    val definedAt: ConfigLevel? = null,
) {
    /**
     * Check if this protection has any restrictions.
     */
    val hasRestrictions: Boolean
        get() = isFinal || isInterpolationProtected

    /**
     * Create a copy with the scope level set.
     */
    fun withScope(level: ConfigLevel) = copy(definedAt = level)

    companion object {
        /** No protection - property can be overridden and interpolated from any scope */
        val NONE = PropertyProtection()

        /** Final only - property cannot be overridden at lower scopes */
        val FINAL = PropertyProtection(isFinal = true)

        /** Protected only - property cannot be interpolated from lower scopes */
        val PROTECTED = PropertyProtection(isInterpolationProtected = true)

        /** Both final and protected */
        val FINAL_AND_PROTECTED = PropertyProtection(isFinal = true, isInterpolationProtected = true)
    }
}

/**
 * Result of parsing a key with protection prefixes.
 *
 * @property canonicalKey The key with protection prefixes stripped
 * @property protection The protection metadata extracted from prefixes
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParsedProtectedKey", exact = true)
@CoverageExcludedDataClass
data class ParsedProtectedKey(
    val canonicalKey: String,
    val protection: PropertyProtection,
)

/**
 * A protected property value with its protection metadata.
 *
 * @property value The actual property value
 * @property protection The protection metadata for this value
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectedPropertyValue", exact = true)
@CoverageExcludedDataClass
data class ProtectedPropertyValue(
    val value: Any,
    val protection: PropertyProtection,
)

/**
 * Access policy errors for protection violations.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProtectionErrors", exact = true)
object ProtectionErrors {
    /**
     * Error when attempting to override a FINAL property from a lower scope.
     *
     * @param key The property key that was attempted to be overridden
     * @param definedAt The scope level where the property is defined as FINAL
     * @param attemptedAt The scope level that attempted the override
     */
    fun overrideNotAllowed(
        key: String,
        definedAt: ConfigLevel,
        attemptedAt: ConfigLevel,
    ): IdkError =
        IdkError.ILLEGAL_ARGUMENT_ERROR(
            message = "Property '$key' is marked FINAL at $definedAt scope and cannot be overridden at $attemptedAt scope",
        )

    /**
     * Error when attempting to interpolate a PROTECTED property from a lower scope.
     *
     * This error is raised BEFORE the value is read, ensuring the protected value
     * is never exposed even in error cases.
     *
     * @param key The property key that was attempted to be interpolated
     * @param definedAt The scope level where the property is defined as PROTECTED
     * @param requestedFrom The scope level that attempted the interpolation
     */
    fun interpolationNotAllowed(
        key: String,
        definedAt: ConfigLevel,
        requestedFrom: ConfigLevel,
    ): IdkError =
        IdkError.ILLEGAL_ARGUMENT_ERROR(
            message = "Property '$key' is marked PROTECTED at $definedAt scope and cannot be interpolated from $requestedFrom scope",
        )
}
