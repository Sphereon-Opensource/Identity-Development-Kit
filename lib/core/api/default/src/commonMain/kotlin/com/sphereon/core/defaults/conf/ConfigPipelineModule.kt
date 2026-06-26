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

package com.sphereon.core.defaults.conf

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.DefaultPropertyInterpolator
import com.sphereon.core.api.conf.DefaultSecretAddressResolver
import com.sphereon.core.api.conf.EnvOnlySecretProviderSelectionResolver
import com.sphereon.core.api.conf.PartitionStrategy
import com.sphereon.core.api.conf.PropertyInterpolator
import com.sphereon.core.api.conf.SecretAddressResolver
import com.sphereon.core.api.conf.SecretProviderRegistry
import com.sphereon.core.api.conf.SecretProviderSelectionResolver
import com.sphereon.core.api.conf.SecretResolver
import com.sphereon.core.api.conf.toSecretResolver
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

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
 * - `${secret:<logical.key>}` - Cascade secret (tenant-selected -> app-selected -> env)
 * - `${secret:@env:VAR_NAME}` - Secret pinned to the environment provider
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
    @Named("appSecretProviderRegistry")
    fun provideAppSecretProviderRegistry(): SecretProviderRegistry = SecretProviderRegistry()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSecretProviderRegistry(
        @Named("appSecretProviderRegistry") registry: SecretProviderRegistry,
    ): SecretProviderRegistry = registry

    /**
     * Provide the secret resolver using the registry and the per-scope provider selection.
     *
     * The [SecretProviderSelectionResolver] is resolved from the DI graph. The IDK default is
     * the env-only floor ([DefaultSecretProviderSelectionResolver]); EDK/VDX contribute a
     * config-backed implementation that selects per-tenant providers via
     * `@ContributesBinding(AppScope::class, replaces = [DefaultSecretProviderSelectionResolver::class])`.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideSecretResolver(
        registry: SecretProviderRegistry,
        selectionResolverProvider: Provider<SecretProviderSelectionResolver>,
        addressResolver: SecretAddressResolver,
    ): SecretResolver = registry.toSecretResolver(lazySelectionResolver(selectionResolverProvider), addressResolver)

    /**
     * Provide the default property interpolator with secret resolution support.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providePropertyInterpolator(secretResolver: SecretResolver): PropertyInterpolator = DefaultPropertyInterpolator(secretResolver = secretResolver)

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

@ContributesTo(UserScope::class)
interface UserConfigPipelineModule {
    /**
     * User-scope registry starts with the app-scope providers, then user-scope
     * bootstrapping can replace providers with principal-config-aware instances.
     */
    @Provides
    @SingleIn(UserScope::class)
    fun provideUserSecretProviderRegistry(
        @Named("appSecretProviderRegistry") appRegistry: SecretProviderRegistry,
    ): SecretProviderRegistry {
        val registry = SecretProviderRegistry()
        appRegistry.getAll().forEach { registry.register(it) }
        return registry
    }

    @Provides
    @SingleIn(UserScope::class)
    fun provideUserSecretResolver(
        registry: SecretProviderRegistry,
        selectionResolverProvider: Provider<SecretProviderSelectionResolver>,
        addressResolver: SecretAddressResolver,
    ): SecretResolver = registry.toSecretResolver(lazySelectionResolver(selectionResolverProvider), addressResolver)

    @Provides
    @SingleIn(UserScope::class)
    fun provideUserPropertyInterpolator(secretResolver: SecretResolver): PropertyInterpolator = DefaultPropertyInterpolator(secretResolver = secretResolver)

    @Provides
    fun provideNullableUserPropertyInterpolator(interpolator: PropertyInterpolator): PropertyInterpolator? = interpolator

    @Provides
    fun provideNullableUserSecretResolver(resolver: SecretResolver): SecretResolver? = resolver
}

/**
 * Wrap a [Provider] of [SecretProviderSelectionResolver] in a lazily-resolving adapter.
 *
 * The [SecretResolver] only needs the selection resolver at runtime (during `resolve()`), but a
 * config-backed selection resolver transitively depends on the config services, which in turn depend
 * on the `SecretResolver?` binding — a construction-time DI cycle. Injecting the selection resolver
 * as a [Provider] and resolving it lazily on first use breaks that cycle without changing behaviour.
 */
private fun lazySelectionResolver(provider: Provider<SecretProviderSelectionResolver>,): SecretProviderSelectionResolver =
    object : SecretProviderSelectionResolver {
        override suspend fun selectedProvider(
            scope: ConfigLevel,
            scopeIdentifier: String?,
        ): String? = provider().selectedProvider(scope, scopeIdentifier)

        // Forward the RICHER selection too, otherwise the read path would lose the partition
        // strategy + instance id the writer used, breaking read/write address symmetry whenever a
        // backend descriptor pins them.
        override suspend fun selectedProviderSelection(
            scope: ConfigLevel,
            scopeIdentifier: String?,
        ) = provider().selectedProviderSelection(scope, scopeIdentifier)
    }

/**
 * IDK default [SecretProviderSelectionResolver] contributed at app scope: the env-only floor,
 * delegating to [EnvOnlySecretProviderSelectionResolver] (no per-scope selection).
 *
 * Contributed (rather than `@Provides`d) so EDK/VDX can override it with a config-backed,
 * per-tenant implementation via
 * `@ContributesBinding(AppScope::class, replaces = [DefaultSecretProviderSelectionResolver::class])`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SecretProviderSelectionResolver>())
class DefaultSecretProviderSelectionResolver : SecretProviderSelectionResolver {
    private val delegate = EnvOnlySecretProviderSelectionResolver()

    override suspend fun selectedProvider(
        scope: ConfigLevel,
        scopeIdentifier: String?,
    ): String? = delegate.selectedProvider(scope, scopeIdentifier)
}

/**
 * IDK default [SecretProviderSelectionResolver] contributed at USER scope: the env-only floor.
 *
 * Mirrors the app-scope [DefaultSecretProviderSelectionResolver] so EDK/VDX can replace the
 * selection at their OWN user scope (via `replaces = [DefaultUserSecretProviderSelectionResolver::class]`)
 * without a cross-scope replace. Without this, a UserScope graph would have no default to override.
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<SecretProviderSelectionResolver>())
class DefaultUserSecretProviderSelectionResolver : SecretProviderSelectionResolver {
    private val delegate = EnvOnlySecretProviderSelectionResolver()

    override suspend fun selectedProvider(
        scope: ConfigLevel,
        scopeIdentifier: String?,
    ): String? = delegate.selectedProvider(scope, scopeIdentifier)
}

/**
 * IDK default [SecretAddressResolver] contributed at app AND user scope: the standard
 * logical->physical mapping ([DefaultSecretAddressResolver]). Contributed (rather than `@Provides`d)
 * so a deployment may override it, though no override currently exists.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SecretAddressResolver>())
@ContributesBinding(UserScope::class, binding = binding<SecretAddressResolver>())
class DefaultSecretAddressResolverBinding : SecretAddressResolver {
    private val delegate = DefaultSecretAddressResolver()

    override fun physicalAddress(
        logicalKey: String,
        providerType: String,
        scope: ConfigLevel,
        scopeIdentifier: String?,
        instanceId: String?,
        strategy: PartitionStrategy,
    ): String = delegate.physicalAddress(logicalKey, providerType, scope, scopeIdentifier, instanceId, strategy)
}
