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

package com.sphereon.crypto.core.kms

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Registry interface for key resolvers.
 *
 * This interface provides methods for registering, retrieving, and querying key resolvers.
 * Commands that need to resolve public keys can inject this interface directly instead of
 * the full [KeyManagerService], enabling independent command usage in pipelines without
 * circular dependencies.
 *
 * The registry is session-scoped, meaning resolvers are available per-session,
 * maintaining proper tenant isolation.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyResolverRegistry", exact = true)
interface KeyResolverRegistry {
    /**
     * Provides the default resolver identifier.
     *
     * @return The default resolver ID as a String.
     */
    fun defaultResolverId(): String

    /**
     * Retrieves an array of all registered key resolver IDs.
     *
     * @return An array of strings representing the IDs of the registered key resolvers.
     */
    fun getResolverIds(): Array<String>

    /**
     * Retrieves a key resolver by its identifier.
     *
     * @param id The identifier of the key resolver. Defaults to [defaultResolverId].
     * @return The KeyResolverService corresponding to the specified ID.
     * @throws PKIException if no resolver is found for the given ID.
     */
    fun getResolverById(id: String = defaultResolverId()): KeyResolverService

    /**
     * Retrieves a key resolver based on the given identifier method, key type, or resolver ID.
     *
     * This function facilitates the resolution of key management or cryptographic operations
     * by using a specified identifier method, key type, or a unique resolver identifier.
     *
     * @param identifierMethod The method used to identify cryptographic keys, can be null.
     *        When provided, it restricts the resolvers to those which support the specified
     *        identifier method.
     * @param keyType The type of key to be resolved, can be null. When provided, it restricts
     *        the resolvers to those which support the specified key type.
     * @param resolverId The unique identifier of the resolver, can be null. When provided,
     *        it directly selects the resolver with the specified ID.
     * @return A KeyResolverService that matches the provided criteria.
     * @throws IllegalArgumentException if no matching resolver is found.
     */
    fun getResolverByKeyTypeOrIdentifier(
        identifierMethod: IdentifierMethod? = null,
        keyType: KeyTypeMapping? = null,
        resolverId: String? = null,
    ): KeyResolverService

    /**
     * Registers a key resolver with the registry.
     *
     * @param resolver The KeyResolverService instance to be registered.
     * @param makeDefaultResolver If true, makes this resolver the default. Defaults to false.
     */
    fun registerResolver(
        resolver: KeyResolverService,
        makeDefaultResolver: Boolean? = false,
    )
}

/**
 * Graph interface for DI graph integration.
 * Allows other components to access the KeyResolverRegistry from the session scope.
 */
@ContributesTo(scope = SessionScope::class)
interface KeyResolverRegistryGraph {
    val keyResolverRegistry: KeyResolverRegistry
}
