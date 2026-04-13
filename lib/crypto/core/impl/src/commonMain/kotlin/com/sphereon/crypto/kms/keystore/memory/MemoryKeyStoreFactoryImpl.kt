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

package com.sphereon.crypto.kms.keystore.memory

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreFactory
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
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
@ObjCName("MemoryKeyStoreFactoryImpl", exact = true)
class MemoryKeyStoreFactoryImpl(
    private val backingStorage: MemoryKeyStoreBackingStorage,
    private val realFactory: MemoryKeyStoreServiceFactory,
) : MemoryKeyStoreFactory {
    // Note: Serialization registration is now handled automatically via SerializerRegistration
    // when the AppScope is created. No manual registration needed.

    override val keyStoreType: String = PredefinedKeyStoreTypes.MEMORY.keyStoreType

    override fun create(config: KeyStoreConfig): MemoryKeyStoreService = create(config, null)

    override fun create(
        config: KeyStoreConfig,
        execution: SessionExecution?,
    ): MemoryKeyStoreService {
        val memoryConfig =
            config as? MemoryKeyStoreConfig
                ?: throw IllegalArgumentException("Config must be MemoryKeyStoreConfig")

        val scopeBinding = MemoryKeyStoreScopeBinding.fromValue(memoryConfig.scopeBinding)

        // For APP scope or when execution is null, use backing storage with appropriate partition
        // For legacy/direct instantiation (execution == null with non-APP scope), fall back to session-local storage
        if (execution == null && scopeBinding != MemoryKeyStoreScopeBinding.APP) {
            // Legacy behavior: no execution context means session-scoped instance with local storage
            return realFactory.create(config, null, null)
        }

        val partitionKey =
            when (scopeBinding) {
                MemoryKeyStoreScopeBinding.APP -> {
                    StoragePartitionKey.appLevel(keystoreId = config.id)
                }

                MemoryKeyStoreScopeBinding.TENANT -> {
                    val tenantId =
                        checkNotNull(
                            execution
                                ?.sessionContext
                                ?.context
                                ?.tenant
                                ?.tenantId,
                        ) { "Cannot create TENANT-scoped keystore without execution context with tenant information" }
                    StoragePartitionKey.forTenant(keystoreId = config.id, tenantId = tenantId)
                }

                MemoryKeyStoreScopeBinding.PRINCIPAL_TENANT -> {
                    val context =
                        checkNotNull(execution?.sessionContext?.context) { "Cannot create PRINCIPAL_TENANT-scoped keystore without execution context" }
                    val tenantId = context.tenant.tenantId
                    val principalId =
                        checkNotNull(context.principal?.toString()) { "Cannot create PRINCIPAL_TENANT-scoped keystore without principal information" }
                    StoragePartitionKey.forPrincipalTenant(keystoreId = config.id, tenantId = tenantId, principalId = principalId)
                }

                MemoryKeyStoreScopeBinding.SESSION -> {
                    val sessionContext =
                        checkNotNull(execution?.sessionContext) { "Cannot create SESSION-scoped keystore without execution context with session information" }
                    val tenantId = sessionContext.context.tenant.tenantId
                    val principalId =
                        checkNotNull(sessionContext.context.principal?.toString()) { "Cannot create SESSION-scoped keystore without principal information" }
                    val sessionId = sessionContext.sessionId
                    StoragePartitionKey.forSession(keystoreId = config.id, tenantId = tenantId, principalId = principalId, sessionId = sessionId)
                }
            }

        return realFactory.create(config, backingStorage, partitionKey)
    }
}
