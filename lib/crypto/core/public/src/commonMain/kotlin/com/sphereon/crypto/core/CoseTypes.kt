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

package com.sphereon.crypto.core

import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseMac0Cbor
import com.sphereon.crypto.core.cose.CoseMac0InputCbor
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.core.cose.CoseSign1Input
import com.sphereon.crypto.core.cose.ToBeSignedCbor
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.core.compat.JsExportCompat

/**
 * The main interface used for the platform-specific callback. Has to be implemented by external developers.
 *
 * Not exported to JS as it has a similar interface exported using Promises instead of coroutines
 */
interface CoseCryptoCallbackCoroutines {
    suspend fun sign(
        input: ToBeSignedCbor,
        requireX5Chain: Boolean?,
    ): ByteArray

    suspend fun verify1(
        input: CoseSign1<*>,
        keyInfo: KeyInfoType<*>?,
        requireX5Chain: Boolean?,
    ): VerifySignatureResultType<CoseKeyType>

    suspend fun mac0(
        input: CoseMac0InputCbor,
        sharedSecret: ByteArray,
        alg: SignatureAlgorithm,
    ): CoseMac0Result

    suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(keyInfo: KeyInfoType<KeyType>): ResolvedKeyInfoType<KeyType>
}


/**
 * The main interface used for the platform specific callback. Has to be implemented by external developers.
 *
 * Not exported to JS as it has a similar interface exported using Promises instead of coroutines
 */
interface CoseCryptoService {
    suspend fun <CborType : Any> sign1(
        input: CoseSign1Input,
        keyInfo: KeyInfoType<*>? = null,
        requireX5Chain: Boolean? = true,
    ): CoseSign1Result<CborType>

    suspend fun verify1(
        input: CoseSign1<*>,
        keyInfo: KeyInfoType<*>? = null,
        requireX5Chain: Boolean? = true,
    ): VerifySignatureResultType<CoseKeyType>


    suspend fun mac0(
        input: CoseMac0InputCbor,
        sharedSecret: ByteArray,
        alg: SignatureAlgorithm,
    ): CoseMac0Result

    suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(keyInfo: KeyInfoType<KeyType>): ResolvedKeyInfoType<KeyType>
}

@JsExportCompat
data class CoseSign1Result<CborType : Any>(val coseSign1: CoseSign1<CborType>, val keyInfo: KeyInfoType<CoseKeyType>, val input: CoseSign1Input)


@JsExportCompat
data class CoseMac0Result(val coseMac0: CoseMac0Cbor, val input: CoseMac0InputCbor)
