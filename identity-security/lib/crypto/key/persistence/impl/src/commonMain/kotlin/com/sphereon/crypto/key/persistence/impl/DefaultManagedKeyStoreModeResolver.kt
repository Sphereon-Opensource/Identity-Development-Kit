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

package com.sphereon.crypto.key.persistence.impl

import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.crypto.core.kms.ManagedKeyStoreMode
import com.sphereon.crypto.core.kms.ManagedKeyStoreModeResolver
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Reads the managed key store mode from configuration.
 *
 * Config key: `sphereon.crypto.kms.managed-key-store.mode`
 * Values: `iterating`, `persistent`, `auto` (default: `auto`)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ManagedKeyStoreModeResolver>())
class DefaultManagedKeyStoreModeResolver(
    private val configService: PrincipalConfigService,
) : ManagedKeyStoreModeResolver {
    override fun resolve(): ManagedKeyStoreMode {
        val configured = configService.getPropertyAsString("sphereon.crypto.kms.managed-key-store.mode")
        return configured?.let {
            try {
                ManagedKeyStoreMode.valueOf(it.uppercase())
            } catch (_: IllegalArgumentException) {
                ManagedKeyStoreMode.AUTO
            }
        } ?: ManagedKeyStoreMode.AUTO
    }
}
