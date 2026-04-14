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
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

// TODO: DidResolverService

@OptIn(ExperimentalObjCName::class)
@ObjCName("PublicKeyResolver", exact = true)
@JsExportCompat
interface PublicKeyResolver {
    /**
     * Resolves the public key asynchronously given the key information and additional optional parameters.
     *
     * @param keyInfo The key information containing metadata and the cryptographic key to be resolved.
     * @param identifierMethod An optional parameter to specify the method used to identify the key (e.g., jwk, kid, cose_key, x5c).
     * @param trustedCerts An optional array of trusted certificates that may be used in the resolution process.
     * @param verifyX509CertificateChain An optional boolean indicating whether the X.509 certificate chain should be verified.
     * @return An `ResolvedKeyInfo` object containing the resolved key information.
     */
    suspend fun <KT : KeyType> resolvePublicKey(
        keyInfo: KeyInfoType<KT>,
        identifierMethod: IdentifierMethod? = null,
        trustedCerts: Array<String>? = null,
        verifyX509CertificateChain: Boolean? = null,
    ): ResolvedKeyInfoType<KT>
}

/**
 * Interface for a session that resolves keys based on identifier methods and key types.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyResolverService", exact = true)
interface KeyResolverService : PublicKeyResolver {
    /**
     * Retrieves the unique identifier.
     *
     * @return The unique identifier as a String.
     */
    fun getId(): String

    /**
     * Retrieves all supported identifier methods.
     *
     * @return An array of supported identifier methods.
     */
    fun allSupportedIdentifierMethods(): Array<IdentifierMethod>

    /**
     * Retrieves an array of all supported KeyType values.
     *
     * @return An array containing all the supported KeyType enums.
     */
    fun allSupportedKeyTypes(): Array<KeyTypeMapping>

    /**
     * Provides a mapping of supported identifier methods to their corresponding array of key types.
     *
     * @return A Map where the key is an IdentifierMethod and the value is an array of KeyType instances supported by that method.
     */
    fun supportedKeyTypesAndIdentifierMethods(): Map<IdentifierMethod, Array<KeyTypeMapping>>

    /**
     * Retrieves the supported key types for a given identifier method.
     *
     * @param identifierMethod The identifier method for which to retrieve supported key types.
     * @return An array of supported key types corresponding to the provided identifier method.
     */
    fun getSupportedKeyTypes(identifierMethod: IdentifierMethod): Array<KeyTypeMapping>

    /**
     * Retrieves a list of supported identifier methods for the specified key type.
     *
     * @param keyType the type of key for which to get the supported identifier methods
     * @return an array of IdentifierMethod representing the supported methods for the provided key type
     */
    fun getSupportedIdentifierMethods(keyType: KeyTypeMapping): Array<IdentifierMethod>
}
