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
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Registry interface for KMS providers.
 *
 * This interface provides methods for registering, retrieving, and querying KMS providers.
 * Commands that need to lookup providers can inject this interface directly instead of
 * the full [KeyManagerService], enabling independent command usage in pipelines without
 * circular dependencies.
 *
 * The registry is session-scoped, meaning providers are created per-session from
 * tenant/principal configuration, maintaining proper tenant isolation.
 *
 * Resolving a provider suspends. An implementation may have to reach an authority before it knows
 * what the provider it is about to hand back can do, and a provider whose capabilities are only
 * discovered after construction would answer capability questions from an incomplete picture.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KmsProviderRegistry", exact = true)
interface KmsProviderRegistry {
    /**
     * Retrieves the default Key Management System (KMS) provider identifier.
     *
     * @return The identifier of the default KMS provider as a string.
     */
    fun defaultProviderId(): String

    /**
     * Retrieves an array of all registered KMS provider IDs.
     *
     * @return An array of strings representing the IDs of the registered KMS providers.
     */
    fun getProviderIds(): Array<String>

    /**
     * Retrieves a KMS provider by its identifier.
     *
     * @param id The identifier of the key management system. Defaults to [defaultProviderId].
     * @return The KmsProvider corresponding to the specified ID.
     * @throws PKIException if no provider is found for the given ID.
     */
    suspend fun getProviderById(id: String = defaultProviderId()): KmsProvider

    /**
     * Retrieves a KMS provider by provider ID or signature algorithm.
     *
     * If providerId is null but algorithm is provided, returns the first provider
     * that supports the signature algorithm. If both are null, returns the default provider.
     *
     * @param providerId Optional provider ID. If null, uses algorithm or default.
     * @param alg Optional signature algorithm to match against provider capabilities.
     * @return The matching KmsProvider.
     * @throws PKIException if no matching provider is found.
     */
    suspend fun getProvider(
        providerId: String? = null,
        alg: SignatureAlgorithm? = null,
    ): KmsProvider

    /**
     * Retrieves the KMS provider that supports the specified signature algorithm.
     *
     * @param signatureAlgorithm The signature algorithm for which the corresponding KMS is requested.
     * @return The KmsProvider that supports the specified signature algorithm.
     * @throws PKIException if no provider supports the algorithm.
     */
    suspend fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): KmsProvider

    /**
     * Registers a new KMS provider with the registry.
     *
     * @param provider The KmsProvider instance to be registered.
     * @param makeDefaultKms If true, makes this provider the default. Defaults to false.
     */
    fun registerProvider(
        provider: KmsProvider,
        makeDefaultKms: Boolean? = false,
    )
}

/**
 * Graph interface for DI graph integration.
 * Allows other components to access the KmsProviderRegistry from the session scope.
 */
@ContributesTo(scope = SessionScope::class)
@JsExportCompat
interface KmsProviderRegistryGraph {
    val kmsProviderRegistry: KmsProviderRegistry
}
