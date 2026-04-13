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

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("KmsRestService", exact = true)
interface KmsRestService {
    suspend fun getKey(aliasOrKid: String, providerId: String? = null): ManagedKeyInfoType<*>

    suspend fun listKeys(providerId: String? = null): Array<ManagedKeyInfoType<*>>

    suspend fun storeKey(keyInfo: ResolvedKeyInfoType<*>, certChain: Array<String>? = null): ManagedKeyInfoType<*>

    suspend fun generateKey(
        alias: String? = null,
        use: JwkUse? = null,
        keyOperations: Array<KeyOperations>? = null,
        alg: SignatureAlgorithm? = null,
        providerId: String? = null
    ): ManagedKeyPair

    suspend fun deleteKey(aliasOrKid: String, providerId: String? = null): Boolean
}