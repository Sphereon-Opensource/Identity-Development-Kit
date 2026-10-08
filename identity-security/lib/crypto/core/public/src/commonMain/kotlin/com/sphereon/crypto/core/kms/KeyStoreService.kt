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

package com.sphereon.crypto.core.kms

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.x509.Certificate
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.native.ObjCName

/**
 * The interface for the PKI session adds key functionality to the simple signature interface.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStoreService", exact = true)
interface KeyStoreService {
    /**
     * Holds the configuration settings for a Key Provider.
     *
     * This includes the provider's unique identifier, a configuration object specifying the
     * provider's settings, and an optional callback for securely inputting passwords.
     */
    val settings: KeyProviderSettings?

    /**
     * Lists metadata-only key references for all managed keys in this key store.
     *
     * Returns [ManagedKeyReference] objects without resolved key material, certificates,
     * or JWKs. Use [getKey] to resolve full key information for a specific key.
     *
     * In iterating mode, this iterates all registered KMS providers and strips key material.
     * In persistent mode, this is served entirely from the database without provider iteration.
     *
     * @return An array of metadata-only key references.
     * @throws com.sphereon.crypto.core.PKIException if there is an error accessing the key store.
     */
    suspend fun listKeys(): Array<ManagedKeyReference>

    /**
     * Lists metadata-only key references filtered by the given criteria.
     *
     * In persistent mode, the filter is pushed down to the database query.
     * In iterating mode, keys are listed from all providers and filtered in-memory.
     *
     * Default implementation delegates to [listKeys] and filters in-memory.
     */
    @JsName("listKeysFiltered")
    suspend fun listKeys(filter: ManagedKeyReferenceFilter): Array<ManagedKeyReference> {
        val all = listKeys()
        return all
            .filter { ref ->
                (filter.providerId == null || ref.providerId == filter.providerId) &&
                    (filter.alias == null || ref.alias == filter.alias) &&
                    (filter.kid == null || ref.kid == filter.kid) &&
                    (filter.origin == null || ref.origin == filter.origin) &&
                    (filter.keyType == null || ref.keyType == filter.keyType)
            }.toTypedArray()
    }

    /**
     * Retrieves a managed cryptographic key based on the provided key information.
     *
     * @param keyInfo The key information used to locate and retrieve the managed key. This includes metadata and configuration details related to the key.
     * @return The managed key information, including the key and any additional details relevant to the managed key.
     * @throws PKIException If there is an error during key retrieval.
     */
    suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*>

    /**
     * Stores a resolved cryptographic key into the key management system (KMS) using the provided
     * KMS identifier and key reference.
     *
     * @param keyInfo The resolved key information that will be stored. This includes the cryptographic key
     *                and associated metadata that has been resolved and is ready for storage.
     * @param providerId The identifier of the key management system where the key will be stored. This is a
     *            string that uniquely identifies the KMS within the system.
     * @param alias The key reference within the key management system. This is a string that uniquely
     *                  identifies the specific key within the KMS, allowing for precise retrieval and management.
     * @return ManagedKeyInfo representing the successfully stored managed key information, including
     *         the key and any additional details relevant to the managed key.
     */
    suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>? = null,
    ): ManagedKeyInfoType<*>

    /**
     * Deletes a key from the key store.
     *
     * @param keyInfo Information about the key to be deleted.
     * @return true if the key was successfully deleted, false otherwise.
     */
    suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean

    /**
     * Determines the visibility of the key. If public then only public keys will be stored. Mainly useful for indexing keys of a KMS. If private then both public and private keys will be stored.
     *
     * The retrieval functions will return the public keys by default. The getKey function has a method to also list a private key if supported by the key store
     *
     */
    fun keyVisibility(): KeyVisibility

    /**
     * Whether this key store exposes private key material to the caller for signing operations.
     *
     * Software-based keystores (memory, PKCS12, JKS, file-based) return `true` because private
     * key material is stored and must be provided to the signing library for software signing.
     *
     * Hardware-backed keystores (e.g., iOS Secure Enclave, HSMs) return `false` because private
     * keys never leave the hardware; signing is delegated to the hardware directly.
     *
     * Defaults to `false` as the safe choice: callers must explicitly opt in to private key exposure.
     */
    fun exposesPrivateKeysForSigning(): Boolean = false
}
