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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreFactory
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.di.app.App
import com.sphereon.di.app.PlatformInfo
import com.sphereon.di.context.IdentityConstants
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<com.sphereon.crypto.core.kms.KeyStoreFactory>())
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("SoftwareKeyStoreFactoryImpl", exact = true)
class SoftwareKeyStoreFactoryImpl(
    private val realSoftwareKeyStoreFactory: RealSoftwareKeyStoreFactory,
    app: App,
) : SoftwareKeyStoreFactory {
    // Note: Serialization registration is now handled automatically via SerializerRegistration
    // when the AppScope is created. No manual registration needed.

    override val keyStoreType: String =
        when (app.platformInfo.osFamily) {
            PlatformInfo.OsFamily.IOS -> PredefinedKeyStoreTypes.APPLE.keyStoreType
            PlatformInfo.OsFamily.JS, PlatformInfo.OsFamily.WASM_JS -> PredefinedKeyStoreTypes.FILE.keyStoreType
            else -> PredefinedKeyStoreTypes.PKCS12.keyStoreType
        }

    override fun create(config: KeyStoreConfig): SoftwareKeyStoreService = realSoftwareKeyStoreFactory.create(config)

    /**
     * Tenant-aware creation: always derive a per-tenant keystore FILE from the active execution's
     * tenant id. An explicit `path` is treated as the configured file or subpath, but it is still
     * placed under a tenant directory by [TenantKeyStorePathResolver]. The resulting per-tenant file
     * is created/loaded/persisted by the underlying [SoftwareKeyStoreService] file IO.
     */
    override fun create(
        config: KeyStoreConfig,
        execution: SessionExecution?,
    ): SoftwareKeyStoreService {
        val effectiveConfig =
            if (config is SoftwareKeyStoreConfig) {
                TenantKeyStorePathResolver.withResolvedPath(config, tenantIdForKeystoreResolution(execution))
            } else {
                config
            }
        return realSoftwareKeyStoreFactory.create(effectiveConfig)
    }
}

internal fun tenantIdForKeystoreResolution(execution: SessionExecution?): String? {
    if (execution == null) {
        return null
    }
    val resolvedTenant =
        execution.tenantId
            .trim()
            .takeUnless { it.isBlank() || it == IdentityConstants.ANONYMOUS_TENANT_ID }
    if (resolvedTenant != null) {
        return resolvedTenant
    }
    return execution.sessionContext.context.tenant.tenantId
        .trim()
        .takeUnless { it.isBlank() }
}
