/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.defaults.conf

import com.sphereon.core.api.Err
import com.sphereon.core.api.conf.DefaultPropertyInterpolator
import com.sphereon.core.api.conf.DefaultInterpolationPolicyProvider
import com.sphereon.core.api.conf.InterpolationPolicyProvider
import com.sphereon.core.api.conf.InterpolationPolicyCatalog
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.conf.PropertyInterpolator
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Provides ordinary property interpolation and a fail-closed opaque-secret resolver floor.
 *
 * Provider construction, selection, addressing, and tenant binding belong exclusively to the
 * normalized server-side secret-management runtime. Configuration interpolation cannot name or
 * select a provider.
 */
@ContributesTo(AppScope::class)
interface ConfigPipelineModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePropertyInterpolator(): PropertyInterpolator = DefaultPropertyInterpolator()

    @Provides
    fun provideNullablePropertyInterpolator(interpolator: PropertyInterpolator): PropertyInterpolator? = interpolator

    /**
     * Fail-closed default catalog. EDK replaces this whole module with
     * `@ContributesTo(replaces = [ConfigPipelineModule::class])` and provides its one canonical
     * exact-key catalog together with the ordinary interpolator bindings.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideInterpolationPolicyCatalog(): InterpolationPolicyCatalog = InterpolationPolicyCatalog()

    @Provides
    @SingleIn(AppScope::class)
    fun provideInterpolationPolicyProvider(catalog: InterpolationPolicyCatalog): InterpolationPolicyProvider =
        DefaultInterpolationPolicyProvider(catalog)
}

@ContributesTo(UserScope::class)
interface UserConfigPipelineModule {
    @Provides
    @SingleIn(UserScope::class)
    fun provideUserPropertyInterpolator(): PropertyInterpolator = DefaultPropertyInterpolator()

    @Provides
    fun provideNullableUserPropertyInterpolator(interpolator: PropertyInterpolator): PropertyInterpolator? = interpolator

    @Provides
    @SingleIn(UserScope::class)
    fun provideUserInterpolationPolicyProvider(catalog: InterpolationPolicyCatalog): InterpolationPolicyProvider =
        DefaultInterpolationPolicyProvider(catalog)
}

/** Fail-closed default until a normalized authenticated server runtime supplies the implementation. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<OpaqueSecretResolver>())
class DefaultOpaqueSecretResolver : OpaqueSecretResolver {
    override suspend fun resolve(secretId: String) =
        Err(
            IdkError.SERVICE_UNAVAILABLE_ERROR(
                message = "Opaque secret runtime is unavailable",
            ),
        )
}

/** User-scope fail-closed default; the authenticated server runtime replaces this binding. */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<OpaqueSecretResolver>())
class DefaultUserOpaqueSecretResolver : OpaqueSecretResolver {
    override suspend fun resolve(secretId: String) =
        Err(
            IdkError.SERVICE_UNAVAILABLE_ERROR(
                message = "Opaque secret runtime is unavailable",
            ),
        )
}
