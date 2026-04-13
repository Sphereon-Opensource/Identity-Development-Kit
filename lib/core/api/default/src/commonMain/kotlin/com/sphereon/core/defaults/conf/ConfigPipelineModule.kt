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

package com.sphereon.core.defaults.conf

import com.sphereon.core.api.conf.DefaultPropertyInterpolator
import com.sphereon.core.api.conf.PropertyInterpolator
import com.sphereon.core.api.conf.SecretProviderRegistry
import com.sphereon.core.api.conf.SecretResolver
import com.sphereon.core.api.conf.toSecretResolver
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * DI module that provides configuration pipeline components.
 *
 * This module provides:
 * - [SecretProviderRegistry] - Registry for secret providers (env provider included by default)
 * - [SecretResolver] - Resolver for secret references in configuration values
 * - [PropertyInterpolator] - Interpolator for ${...} placeholders including secrets
 *
 * These components enable property interpolation and secret resolution in ConfigEnvironments.
 *
 * ## Usage
 *
 * Once this module is active, ConfigEnvironments will automatically resolve:
 * - `${property.name}` - References to other properties
 * - `${property.name:default}` - With default values
 * - `${env:VAR_NAME}` - Environment variables
 * - `${secret:env:VAR_NAME}` - Secret from environment
 *
 * ## Extending with Cloud Providers
 *
 * EDK and VDX can contribute additional secret providers (Vault, Azure Key Vault, etc.)
 * by injecting the [SecretProviderRegistry] and calling `register()`.
 */
@ContributesTo(AppScope::class)
interface ConfigPipelineModule {

    /**
     * Provide the secret provider registry.
     * The registry comes pre-configured with the EnvSecretProvider.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideSecretProviderRegistry(): SecretProviderRegistry = SecretProviderRegistry()

    /**
     * Provide the secret resolver using the registry.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideSecretResolver(registry: SecretProviderRegistry): SecretResolver = registry.toSecretResolver()

    /**
     * Provide the default property interpolator with secret resolution support.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providePropertyInterpolator(secretResolver: SecretResolver): PropertyInterpolator =
        DefaultPropertyInterpolator(secretResolver = secretResolver)

    /**
     * Provide nullable PropertyInterpolator binding for ConfigEnvironment injection.
     * This allows ConfigEnvironments to receive the interpolator via constructor injection.
     */
    @Provides
    fun provideNullablePropertyInterpolator(interpolator: PropertyInterpolator): PropertyInterpolator? = interpolator

    /**
     * Provide nullable SecretResolver binding for ConfigEnvironment injection.
     * This allows ConfigEnvironments to receive the secret resolver via constructor injection.
     */
    @Provides
    fun provideNullableSecretResolver(resolver: SecretResolver): SecretResolver? = resolver
}
