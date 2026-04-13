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

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.annotations.Beta
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyDTOType
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyJsonDTOType
import com.sphereon.crypto.core.cose.CoseKeyJsonType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkDTOType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.cborToJwk
import com.sphereon.crypto.core.jose.jsonToJwk
import com.sphereon.core.compat.JsExportCompat
/**
 * CoseJoseKeyMappingService is an object designed to handle the conversion
 * between different key formats used in COSE (CBOR Object Signing and Encryption) and JOSE (JSON Object Signing and Encryption).
 */
@JsExportCompat
object CoseJoseKeyMappingService {
    /**
     * Safely converts the given key to a JOSE JWK format.
     *
     * @param key The key to be converted.
     * @return IdkResult containing the JWK, or an error if conversion fails.
     */
    @Beta(message = "Safe conversion API - may have minor changes in future versions")
    fun tryToJoseJwk(key: KeyType): IdkResult<Jwk, IdkError> {
        return when (key) {
            is Jwk -> Ok(key)
            is CoseKey -> Ok(key.cborToJwk())
            is CoseKeyJsonType -> Ok(key.jsonToJwk())
            else -> {
                // We cannot compare on classes, as these are external interfaces in JS
                if (key.kty is CborUInt) return Ok(CoseKey.fromDTO(key as CoseKeyDTOType).cborToJwk())
                if (key.kty is CoseKeyTypeEnum) return Ok(CoseKeyJson.fromJsonDTO(key as CoseKeyJsonDTOType).jsonToJwk())
                if (key.kty is JwaKeyType) return Ok(Jwk.from(key as JwkType))
                if (key.kty is String) return Ok(Jwk.fromDTO(key as JwkDTOType))
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Cannot convert key to jose/jwk"))
            }
        }
    }

    /**
     * Converts the given key to a JOSE JWK format.
     *
     * @param key The key to be converted. It can be of types CoseKey, CoseKey, CoseKeyJson, CoseKeyJson, Jwk, JwkType, or JwkTypeJson.
     * @return The equivalent key in JOSE JWK format.
     * @throws IllegalArgumentException if the key cannot be converted to JOSE JWK format.
     */
    fun toJoseJwk(key: KeyType): Jwk {
        return tryToJoseJwk(key).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Safely converts a `Key` instance to `CoseKey`.
     *
     * @param key The `Key` instance to be converted.
     * @return IdkResult containing the CoseKey, or an error if conversion fails.
     */
    @Beta(message = "Safe conversion API - may have minor changes in future versions")
    fun tryToCoseKey(key: KeyType): IdkResult<CoseKey, IdkError> {
        return when (key) {
            // WARNING: DO NOT CHANGE THE ORDER. Since we use actual interfaces in js, the CoseKey would match even when you pass in a jwk
            is CoseKey -> Ok(key)
            is Jwk -> Ok(key.jwkToCoseKey())
            is CoseKeyJson -> Ok(key.toCbor())
            else -> {
                // We cannot rely on comparisons for the interfaces, as that would go wrong in JS where these are marked as external intefaces
                if (key.kty is CborUInt) return Ok(CoseKey.fromDTO(key as CoseKeyDTOType))
                if (key.kty is CoseKeyTypeEnum) return Ok(CoseKeyJson.fromJsonDTO(key as CoseKeyJsonDTOType).toCbor())
                if (key.kty is JwaKeyType) return Ok(Jwk.from(key as JwkType).jwkToCoseKey())
                if (key.kty is String) return Ok(Jwk.fromDTO(key as JwkDTOType).jwkToCoseKey())
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Cannot convert key to cbor"))
            }
        }
    }

    /**
     * Converts an `Key` instance to `CoseKey`.
     *
     * @param key The `Key` instance to be converted. This can be one of several implementing types.
     * @return The converted `CoseKey` instance.
     * @throws IllegalArgumentException If the key cannot be converted to CBOR.
     */
    fun toCoseKey(key: KeyType): CoseKey {
        return tryToCoseKey(key).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Retrieves the X.509 certificate chain (x5c) from the provided key.
     *
     * @param key An instance of Key from which to obtain the x5c array.
     * @return An array of strings representing the x5c certificate chain, or null if not available.
     */
    fun getJoseX5c(key: KeyType): Array<String>? = key.getX509CertificateChain()

    /**
     * Safely converts an array of mixed types to an array of base64-encoded strings.
     *
     * @param x5c an array of items which can be of type String or CborByteString.
     * @return IdkResult containing the array, or an error if conversion fails.
     */
    @Beta(message = "Safe conversion API - may have minor changes in future versions")
    fun tryToJoseX5c(x5c: Array<Any>?): IdkResult<Array<String>?, IdkError> {
        if (x5c == null) return Ok(null)
        val result = mutableListOf<String>()
        for (item in x5c) {
            when (item) {
                is String -> result.add(item)
                is CborByteString -> result.add(item.encodeValueTo(Encoding.BASE64)) // x5c is always base64
                else -> return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Cannot convert value $item to base64 string"))
            }
        }
        return Ok(result.toTypedArray())
    }

    /**
     * Converts an array of mixed types to an array of base64-encoded strings.
     *
     * @param x5c an array of items which can be of type String or CborByteString.
     * @return an array of base64-encoded strings if the input array is non-null, otherwise returns null.
     * @throws IllegalArgumentException if an item in the input array is not a String or CborByteString.
     */
    fun toJoseX5c(x5c: Array<Any>?): Array<String>? {
        return tryToJoseX5c(x5c).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }

    /**
     * Converts a KeyInfoType to a KeyInfoType containing a JWK.
     *
     * @param keyInfo The key info to convert.
     * @return A KeyInfoType with the key converted to JWK format.
     */
    fun toJwkKeyInfo(keyInfo: KeyInfoType<*>): KeyInfoType<Jwk> {
        val key = keyInfo.key?.let { toJoseJwk(it) }
        return KeyInfo(
            key = key,
            kid = keyInfo.kid ?: key?.getKeyId(false),
            opts = keyInfo.opts,
            signatureAlgorithm = keyInfo.signatureAlgorithm ?: key?.getSignatureAlgorithm(),
            keyVisibility = keyInfo.keyVisibility,
            providerId = keyInfo.providerId,
            alias = keyInfo.alias,
            keyEncoding = keyInfo.keyEncoding,
            x5c = keyInfo.x5c ?: key?.getX509CertificateChain(),
            keyType = keyInfo.keyType,
        )
    }

    /**
     * Converts a ResolvedKeyInfoType to a ResolvedKeyInfo containing a JWK.
     *
     * @param resolvedKeyInfo The resolved key info to convert.
     * @return A ResolvedKeyInfo with the key converted to JWK format.
     */
    fun toResolvedJwkKeyInfo(resolvedKeyInfo: ResolvedKeyInfoType<*>): ResolvedKeyInfo<Jwk> {
        val jwk = toJoseJwk(key = resolvedKeyInfo.key)
        with(resolvedKeyInfo) {
            return ResolvedKeyInfo(
                key = jwk,
                kid = kid ?: jwk.getKeyId(false),
                signatureAlgorithm = signatureAlgorithm ?: jwk.getSignatureAlgorithm(),
                opts = opts,
                keyVisibility = keyVisibility,
                keyType = keyType,
                providerId = providerId,
                alias = alias,
                keyEncoding = keyEncoding,
                x5c = x5c ?: jwk.getX509CertificateChain(),
            )
        }
    }

    /**
     * Converts a KeyInfoType to a KeyInfoType containing a COSE key.
     *
     * @param keyInfo The key info to convert.
     * @return A KeyInfoType with the key converted to COSE format.
     */
    fun toCoseKeyInfo(keyInfo: KeyInfoType<*>): KeyInfoType<CoseKeyType> {
        val key = keyInfo.key?.let { toCoseKey(it) }
        return KeyInfo(
            key = key,
            kid = keyInfo.kid ?: key?.getKeyId(false),
            opts = keyInfo.opts,
            signatureAlgorithm = keyInfo.signatureAlgorithm ?: key?.getSignatureAlgorithm(),
            keyVisibility = keyInfo.keyVisibility,
            providerId = keyInfo.providerId,
            alias = keyInfo.alias
        )
    }

    /**
     * Converts a ResolvedKeyInfoType to a ResolvedKeyInfo containing a COSE key.
     *
     * @param resolvedKeyInfo The resolved key info to convert.
     * @return A ResolvedKeyInfo with the key converted to COSE format.
     */
    fun toResolvedCoseKeyInfo(resolvedKeyInfo: ResolvedKeyInfoType<*>): ResolvedKeyInfo<CoseKey> {
        val coseKey = toCoseKey(key = resolvedKeyInfo.key)
        with(resolvedKeyInfo) {
            return ResolvedKeyInfo(
                key = coseKey,
                kid = kid ?: coseKey.getKeyId(false),
                signatureAlgorithm = signatureAlgorithm ?: coseKey.getSignatureAlgorithm(),
                opts = opts,
                keyType = keyType,
                providerId = providerId,
                alias = alias
            )
        }
    }

    /**
     * Checks if the given key info has a resolved key.
     *
     * @param keyInfo The key info to check.
     * @return true if the key info contains a non-null key, false otherwise.
     */
    fun isResolvedKeyInfo(keyInfo: KeyInfoType<*>): Boolean = keyInfo.key != null

    /**
     * Converts a KeyInfoType to a ResolvedKeyInfoType.
     *
     * @param keyInfo The key info to convert.
     * @param key Optional key to use. If null, uses the key from keyInfo.
     * @return A ResolvedKeyInfoType with the resolved key.
     * @throws IllegalArgumentException if no key is available.
     */
    fun <KeyType : com.sphereon.crypto.core.KeyType> toResolvedKeyInfo(keyInfo: KeyInfoType<*>, key: KeyType? = null): ResolvedKeyInfoType<KeyType> =
        ResolvedKeyInfo.fromKeyInfo(keyInfo, key)


    /**
     * Safely resolves key info using an optional resolver callback.
     *
     * @return IdkResult containing the resolved key info, or an error if resolution fails.
     */
    @Beta(message = "Safe conversion API - may have minor changes in future versions")
    fun <KeyType : com.sphereon.crypto.core.KeyType> tryToResolvedKeyInfoWithResolver(
        keyInfo: KeyInfoType<KeyType>,
        resolveCallback: ((keyInfo: KeyInfoType<KeyType>) -> ResolvedKeyInfoType<KeyType>)?
    ): IdkResult<ResolvedKeyInfoType<KeyType>, IdkError> {
        if (!isResolvedKeyInfo(keyInfo)) {
            if (resolveCallback == null) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "KeyInfo is not resolved and no resolve callback is provided"))
            }
            return Ok(resolveCallback(keyInfo))
        }
        @Suppress("UNCHECKED_CAST")
        return Ok(keyInfo as ResolvedKeyInfoType<KeyType>)
    }

    /**
     * Resolves key info using an optional resolver callback.
     * @throws IllegalArgumentException if resolution fails.
     */
    fun <KeyType : com.sphereon.crypto.core.KeyType> toResolvedKeyInfoWithResolver(
        keyInfo: KeyInfoType<KeyType>,
        resolveCallback: ((keyInfo: KeyInfoType<KeyType>) -> ResolvedKeyInfoType<KeyType>)?
    ): ResolvedKeyInfoType<KeyType> {
        return tryToResolvedKeyInfoWithResolver(keyInfo, resolveCallback).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }


    /**
     * Retrieves the x5chain field from a COSE key derived from the given Key instance.
     *
     * @param key The input key implementing the Key interface.
     * @return A CborArray containing CborByteString elements that represent the x5chain, or null if not present.
     */
    fun getCoseX5chain(key: KeyType): CborArray<CborByteString>? = toCoseKey(key).x5chain

    /**
     * Safely converts a given array of X.509 certificate values to a COSE X.509 Chain.
     *
     * @param x5c An array of values; each value should be either a CborByteString or a base64 encoded String.
     * @return IdkResult containing the CborArray, or an error if conversion fails.
     */
    @Beta(message = "Safe conversion API - may have minor changes in future versions")
    fun tryToCoseX5chain(x5c: Array<Any>?): IdkResult<CborArray<CborByteString>?, IdkError> {
        if (x5c == null) return Ok(null)
        val result = mutableListOf<CborByteString>()
        for (item in x5c) {
            when (item) {
                is CborByteString -> result.add(item)
                is String -> result.add(item.toCborByteString(Encoding.BASE64)) // x5c is always base64
                else -> return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Cannot convert value $item to Cbor Byte String"))
            }
        }
        return Ok(CborArray(result))
    }

    /**
     * Converts a given array of X.509 certificate values to a COSE X.509 Chain represented as a CborArray of CborByteString.
     *
     * @param x5c An array of values; each value should be either a CborByteString or a base64 encoded String representing the X.509 certificate.
     * @return A CborArray containing the CborByteString of each X.509 certificate if the input is not null, otherwise returns null.
     * @throws IllegalArgumentException if any elements in the input array are not a CborByteString or a base64 encoded String.
     */
    fun toCoseX5chain(x5c: Array<Any>?): CborArray<CborByteString>? {
        return tryToCoseX5chain(x5c).getOrElse {
            throw IllegalArgumentException(it.message.defaultMessage)
        }
    }


}
