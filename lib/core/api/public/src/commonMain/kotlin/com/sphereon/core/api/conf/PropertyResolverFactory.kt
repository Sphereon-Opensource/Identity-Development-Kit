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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Factory for creating [PropertyResolver] instances with optional interpolation support.
 *
 * When an [interpolator] is provided, returns an [InterpolatingPropertySourcesPropertyResolver]
 * that resolves `${...}` placeholders inline after property lookup.
 * When no interpolator is provided, returns a plain [PropertySourcesPropertyResolver].
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertyResolverFactory", exact = true)
object PropertyResolverFactory {

    /**
     * Create a [PropertyResolver] from property sources with optional interpolation.
     *
     * @param propertySources The property sources to use
     * @param interpolator Optional interpolator for `${...}` placeholder substitution
     * @param redactionPolicy Policy for redacting sensitive values
     * @return A [PropertyResolver] instance
     */
    fun create(
        propertySources: PropertySources,
        interpolator: PropertyInterpolator? = null,
        redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy()
    ): PropertyResolver = if (interpolator != null) {
        InterpolatingPropertySourcesPropertyResolver(propertySources, interpolator, redactionPolicy)
    } else {
        PropertySourcesPropertyResolver(propertySources, redactionPolicy)
    }

    /**
     * Create a [PropertyResolver] with interpolation enabled.
     *
     * @param propertySources The property sources to use
     * @param maxInterpolationDepth Max nesting depth for recursive interpolation
     * @param secretResolver Optional resolver for secret references
     * @param redactionPolicy Policy for redacting sensitive values
     * @return A [PropertyResolver] with interpolation support
     */
    fun withInterpolation(
        propertySources: PropertySources,
        maxInterpolationDepth: Int = 10,
        secretResolver: SecretResolver? = null,
        redactionPolicy: SecretRedactionPolicy = DefaultSecretRedactionPolicy()
    ): PropertyResolver {
        val interpolator = DefaultPropertyInterpolator(
            maxDepth = maxInterpolationDepth,
            secretResolver = secretResolver
        )
        return create(propertySources, interpolator, redactionPolicy)
    }
}
