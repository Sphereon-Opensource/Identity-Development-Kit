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

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KmsProvider
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("ProvidersRestService", exact = true)
interface ProvidersRestService {
    suspend fun listKeyProviders(): Array<KmsProvider>

    suspend fun getKeyProvider(providerId: String): KmsProvider

    suspend fun providerListKeys(providerId: String): Array<ManagedKeyReference>

    suspend fun providerStoreKey(
        providerId: String,
        keyInfo: ResolvedKeyInfoType<*>,
        certChain: Array<String>? = null,
    ): ManagedKeyInfoType<*>

    suspend fun providerGenerateKey(
        providerId: String,
        alias: String? = null,
        use: JwkUse? = null,
        keyOperations: Array<KeyOperations>? = null,
        alg: SignatureAlgorithm? = null,
    ): ManagedKeyPair

    suspend fun providerGetKey(
        providerId: String,
        aliasOrKid: String,
    ): ManagedKeyInfoType<*>

    suspend fun providerDeleteKey(
        providerId: String,
        aliasOrKid: String,
    ): Boolean
}
