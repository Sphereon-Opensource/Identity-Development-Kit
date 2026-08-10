/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.context.ContextScopedResourceInvalidator
import com.sphereon.core.api.log.AppLogManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ContextScopedResourceInvalidator>())
class SoftwareKeyStoreContextResourceInvalidator(
    appLogManager: AppLogManager,
) : ContextScopedResourceInvalidator {
    private val log = appLogManager.withTag("SoftwareKeyStoreContextResourceInvalidator")

    override suspend fun invalidateTenantContext(
        tenantId: String,
        reason: String,
    ) {
        val loader = KeyStoreLoaderFactory.invalidateTenant(tenantId)
        val keyManagers = KeyManagerFactoryCache.invalidateTenant(tenantId)
        val resolvedKeys = SoftwareKeyStoreStateCache.invalidateTenant(tenantId)

        val message =
            "tenant=${tenantId.sanitizeLogToken()} reason=${reason.sanitizeLogToken()} " +
                "keystore.before=${loader.keyStoresBefore} keystore.after=${loader.keyStoresAfter} " +
                "keystore.evicted=${loader.keyStoresEvicted} " +
                "inFlight.before=${loader.inFlightBefore} inFlight.after=${loader.inFlightAfter} " +
                "inFlight.evicted=${loader.inFlightEvicted} " +
                "keyManager.before=${keyManagers.before} keyManager.after=${keyManagers.after} " +
                "keyManager.evicted=${keyManagers.evicted} " +
                "resolvedKeys.before=${resolvedKeys.before} resolvedKeys.after=${resolvedKeys.after} " +
                "resolvedKeys.evicted=${resolvedKeys.evicted}"

        if (loader.keyStoresEvicted > 0 || loader.inFlightEvicted > 0 || keyManagers.evicted > 0 || resolvedKeys.evicted > 0) {
            log.info("VDX_SOFTWARE_KEYSTORE_CACHE_INVALIDATED $message")
        } else {
            log.debug("VDX_SOFTWARE_KEYSTORE_CACHE_RETAINED $message")
        }
    }

    override suspend fun invalidatePrincipalContext(
        tenantId: String,
        principalId: String,
        reason: String,
    ) {
        log.debug(
            "VDX_SOFTWARE_KEYSTORE_CACHE_PRINCIPAL_RETAINED tenant=${tenantId.sanitizeLogToken()} " +
                "principal=${principalId.sanitizeLogToken()} reason=${reason.sanitizeLogToken()} scope=tenant",
        )
    }
}

private fun String.sanitizeLogToken(): String =
    trim()
        .ifBlank { "<blank>" }
        .replace(Regex("[^A-Za-z0-9._:@-]"), "_")
        .take(160)
