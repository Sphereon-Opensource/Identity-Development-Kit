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
 */

package com.sphereon.core.api.conf

import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Startup hook for registering enabled secret providers into the app-scoped
 * [SecretProviderRegistry].
 */
interface SecretProviderBootstrap {
    val registeredProviderCount: Int

    fun registerSecretProviders()

    @ContributesTo(AppScope::class)
    interface Graph {
        val secretProviderBootstrap: SecretProviderBootstrap
    }

    @ContributesTo(UserScope::class)
    interface UserGraph {
        val userSecretProviderBootstrap: UserSecretProviderBootstrap
    }
}

/**
 * User-scope startup hook for registering secret providers into the user-scoped
 * [SecretProviderRegistry]. User-scope providers can resolve configuration from
 * [PrincipalConfigService], which already cascades through tenant and app config.
 */
interface UserSecretProviderBootstrap {
    val registeredProviderCount: Int

    fun registerSecretProviders()
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NoOpSecretProviderBootstrap : SecretProviderBootstrap {
    override val registeredProviderCount: Int = 0

    override fun registerSecretProviders() {
        // No-op. EDK secret-public replaces this binding when provider contributions are available.
    }
}

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class NoOpUserSecretProviderBootstrap : UserSecretProviderBootstrap {
    override val registeredProviderCount: Int = 0

    override fun registerSecretProviders() {
        // No-op. EDK secret-public replaces this binding when provider contributions are available.
    }
}
