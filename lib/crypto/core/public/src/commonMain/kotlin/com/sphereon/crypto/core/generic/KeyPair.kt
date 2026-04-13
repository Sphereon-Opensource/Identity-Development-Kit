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

@file:OptIn(ExperimentalStdlibApi::class)

package com.sphereon.crypto.core.generic

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwkUse
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("PlatformKey", exact = true)
interface PlatformKey : KeyType {
//    fun toJwk(): Jwk
}

/**
 * Represents a key pair used by a crypto provider, encapsulating both JOSE and COSE key pairs.
 *
 * @property cose The COSE key pair which contains the private and public keys used for COSE operations.
 * @property jose The JOSE key pair which contains the private and public keys used for JOSE operations.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ManagedKeyPair", exact = true)
@JsExportCompat
data class ManagedKeyPair(
    val kid: String?,
    val providerId: String,
    val alias: String,
    val cose: CoseKeyPair,
    val jose: JoseKeyPair,
    val privateKey: PlatformKey? = null,
) {
    fun cborToManagedKeyInfo(visibility: KeyVisibility = KeyVisibility.PUBLIC) = toManagedKeyInfo<CoseKeyType>(visibility, KeyEncoding.COSE)

    fun joseToManagedKeyInfo(visibility: KeyVisibility = KeyVisibility.PUBLIC) = toManagedKeyInfo<JwkType>(visibility, KeyEncoding.JOSE)

    @Suppress("UNCHECKED_CAST")
    fun <KT : KeyType> toManagedKeyInfo(
        visibility: KeyVisibility = KeyVisibility.PUBLIC,
        keyEncoding: KeyEncoding,
    ): ManagedKeyInfoType<KT> {
        val resolvedKeyInfo: ResolvedKeyInfoType<KT>
        val key =
            if (keyEncoding === KeyEncoding.COSE && visibility === KeyVisibility.PRIVATE) {
                cose.privateCoseKey ?: cose.publicCoseKey
            } else if (keyEncoding === KeyEncoding.COSE && visibility === KeyVisibility.PUBLIC) {
                cose.publicCoseKey
            } else if (keyEncoding === KeyEncoding.JOSE && visibility === KeyVisibility.PRIVATE) {
                jose.privateJwk ?: jose.publicJwk
            } else if (keyEncoding === KeyEncoding.JOSE && visibility === KeyVisibility.PUBLIC) {
                jose.publicJwk
            } else {
                throw IllegalArgumentException("Invalid class or visibility combination")
            }

        resolvedKeyInfo =
            ResolvedKeyInfo(
                key = key as KT,
                alias = alias,
                providerId = providerId,
                keyVisibility = visibility,
                keyType = key.getKeyType(),
                x5c = key.getX509CertificateChain(),
                kid = kid ?: key.getKeyId(false),
                signatureAlgorithm = key.getSignatureAlgorithm(),
            )
        return ManagedKeyInfo(
            providerId = providerId,
            alias = alias,
            resolvedKeyInfo = resolvedKeyInfo,
        )
    }
}

/**
 * Data class representing a cryptographic key pair used with JOSE (JSON Object Signing and Encryption).
 *
 * @property privateJwk The private key in JWK (JSON Web Key) format. This may be null.
 * @property publicJwk The public key in JWK (JSON Web Key) format.
 */
@JsExportCompat
data class JoseKeyPair(
    val privateJwk: Jwk?,
    val publicJwk: Jwk,
)

/**
 * Represents a cryptographic key pair for COSE (CBOR Object Signing and Encryption) operations.
 *
 * @property privateCoseKey The private COSE key in CBOR (Concise Binary Object Representation) format.
 *                          This can be null if only the public key is available.
 * @property publicCoseKey The public COSE key in CBOR format. This is mandatory.
 */
@JsExportCompat
data class CoseKeyPair(
    val privateCoseKey: CoseKey?,
    val publicCoseKey: CoseKey,
)

@JsExportCompat
interface GenerateKeyParams {
    val use: JwkUse?
    val keyOperations: Array<out KeyOperations>?
    val curve: Curve?
    val alg: SignatureAlgorithm?
}
