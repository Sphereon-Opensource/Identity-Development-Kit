/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.kms.keystore.managed

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.kms.KeyResolverRegistry
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.ManagedKeyStoreService
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.di.session.SessionScope

/**
 * A relatively naive implementation that relies on the KMS provider and resolver registries
 * to provide the key lookup and management functionality.
 *
 * This implementation uses the registries to access providers and resolvers, enabling
 * lazy provider access and proper tenant isolation through session-scoped registries.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class ManagedKeyStoreWithProviderLookups(
    private val providerRegistry: KmsProviderRegistry,
    private val resolverRegistry: KeyResolverRegistry
) : ManagedKeyStoreService {
    override val settings: KeyProviderSettings? = null

    override suspend fun listKeys(): Array<ManagedKeyInfoType<*>> {
        val keys = mutableSetOf<ManagedKeyInfoType<*>>()
        providerRegistry.getProviderIds().forEach { providerId ->
            try {
                val provider = providerRegistry.getProviderById(providerId)
                keys.addAll(provider.listKeys())
            } catch (_: Exception) {
                // Certain implementations of KMS may not support listing keys, so we ignore any errors.
            }
        }
        return keys.toTypedArray()
    }

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        if (keyInfo.providerId != null) {
            return providerRegistry.getProviderById(keyInfo.providerId!!).getKey(keyInfo)
        }
        if (keyInfo.alias != null || keyInfo.kid != null) {
            val triedSources = mutableListOf<String>()
            val errors = mutableListOf<Pair<String, Exception>>()

            // Try key resolvers first
            resolverRegistry.getResolverIds().forEach { resolverId ->
                try {
                    val resolver = resolverRegistry.getResolverById(resolverId)
                    val resolvedKeyInfo = resolver.resolvePublicKey(keyInfo)
                    if (resolvedKeyInfo.providerId != null) {
                        return providerRegistry.getProviderById(resolvedKeyInfo.providerId!!).getKey(resolvedKeyInfo)
                    }
                } catch (e: Exception) {
                    // Resolver didn't have the key, try next one
                    triedSources.add("resolver:$resolverId")
                    errors.add("resolver:$resolverId" to e)
                }
            }

            // Try KMS providers directly
            providerRegistry.getProviderIds().forEach { providerId ->
                try {
                    val provider = providerRegistry.getProviderById(providerId)
                    return provider.getKey(keyInfo)
                } catch (e: Exception) {
                    // Provider didn't have the key, try next one
                    triedSources.add("provider:$providerId")
                    errors.add("provider:$providerId" to e)
                }
            }

            // Build informative error message
            val sourcesInfo = if (triedSources.isNotEmpty()) " Tried: ${triedSources.joinToString(", ")}." else ""
            val lastError = errors.lastOrNull()?.second
            throw IllegalArgumentException("Could not find key for $keyInfo.$sourcesInfo", lastError)
        }
        throw IllegalArgumentException("Could not find key for $keyInfo (no alias or kid provided)")
    }

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?
    ): ManagedKeyInfoType<*> {
        if (keyInfo.providerId != null && keyInfo.providerId != providerId) {
            throw IllegalArgumentException("Provider id in keyInfo '${keyInfo.providerId}' does not match the requested provider id '$providerId'")
        }
        if (keyInfo.alias != null && keyInfo.alias != alias) {
            throw IllegalArgumentException("Alias in keyInfo '${keyInfo.alias}' does not match the requested alias '$alias'")
        }
        val provider = providerRegistry.getProviderById(providerId)
        return provider.storeKey(keyInfo, providerId, alias, certChain)
    }

    /**
     * Delete a key from the appropriate provider.
     * @return true if the key was found and deleted, false if not found or provider not available
     */
    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        if (keyInfo.alias == null && keyInfo.kid == null) {
            throw IllegalArgumentException("No key can be found without alias or kid for $keyInfo")
        }
        if (keyInfo.providerId == null) {
            return false // Provider ID required for delete
        }
        return try {
            val provider = providerRegistry.getProviderById(keyInfo.providerId!!)
            provider.deleteKey(keyInfo)
        } catch (e: com.sphereon.core.api.error.NotFoundException) {
            // Key not found is not an error for delete
            false
        } catch (e: com.sphereon.crypto.core.PKIException) {
            // Provider not found
            false
        }
    }

    override fun keyVisibility(): KeyVisibility = KeyVisibility.PRIVATE
}