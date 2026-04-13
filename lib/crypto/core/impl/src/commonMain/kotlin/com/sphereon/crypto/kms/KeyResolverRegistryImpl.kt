/*
 * Copyright (c) 2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.kms.KeyResolverRegistry
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Session-scoped implementation of [KeyResolverRegistry].
 *
 * This registry manages key resolvers for a session. Resolvers are injected via DI
 * and maintained per session, ensuring proper tenant isolation.
 *
 * Commands can inject this interface directly instead of `Lazy<KeyManagerService>`,
 * enabling them to work independently without circular dependencies.
 */
@JsExportCompat
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KeyResolverRegistry>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyResolverRegistryImpl", exact = true)
class KeyResolverRegistryImpl(
    keyResolvers: Set<KeyResolverService>,
) : KeyResolverRegistry {
    // Sort by ID to ensure deterministic iteration order for default selection
    private val keyResolversById: MutableMap<String, KeyResolverService> =
        keyResolvers
            .sortedBy { it.getId() }
            .associateBy { it.getId() }
            .toMutableMap()

    private var defaultResolverIdOverride: String? = null

    override fun defaultResolverId(): String {
        require(keyResolversById.isNotEmpty()) { "At least one key resolver is required" }
        return defaultResolverIdOverride ?: keyResolversById.values.first().getId()
    }

    override fun getResolverIds(): Array<String> = keyResolversById.keys.toTypedArray()

    override fun getResolverById(id: String): KeyResolverService =
        keyResolversById[id]
            ?: throw PKIException("Invalid Resolver id $id. Valid ids are: ${getResolverIds().joinToString(",")}")

    override fun getResolverByKeyTypeOrIdentifier(
        identifierMethod: IdentifierMethod?,
        keyType: KeyTypeMapping?,
        resolverId: String?,
    ): KeyResolverService {
        require(keyResolversById.isNotEmpty()) { "At least one resolver is required" }

        val resolver =
            keyResolversById.values.firstOrNull {
                if (resolverId != null && it.getId() == resolverId) {
                    return@firstOrNull true
                }
                if (keyType != null) {
                    if (identifierMethod != null) {
                        return@firstOrNull it.getSupportedKeyTypes(identifierMethod).contains(keyType)
                    } else if (it.allSupportedKeyTypes().contains(keyType)) {
                        return@firstOrNull true
                    }
                }
                if (identifierMethod != null && it.allSupportedIdentifierMethods().contains(identifierMethod)) {
                    return@firstOrNull true
                }
                // Only fall back to default resolver when no specific criteria were provided
                if (identifierMethod == null && keyType == null && resolverId == null) {
                    return@firstOrNull it.getId() == defaultResolverId()
                }
                return@firstOrNull false
            }

        return resolver
            ?: throw IllegalArgumentException("Could not find resolver for identifier method $identifierMethod and key type $keyType")
    }

    override fun registerResolver(
        resolver: KeyResolverService,
        makeDefaultResolver: Boolean?,
    ) {
        keyResolversById[resolver.getId()] = resolver
        if (makeDefaultResolver == true) {
            defaultResolverIdOverride = resolver.getId()
        }
    }
}
