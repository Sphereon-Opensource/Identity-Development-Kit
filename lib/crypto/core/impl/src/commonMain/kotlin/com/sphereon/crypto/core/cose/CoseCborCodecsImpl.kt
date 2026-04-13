/*
 * Ã‚Â© 2026 Sphereon International B.V.
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

@file:Suppress("TooGenericExceptionCaught")

package com.sphereon.crypto.core.cose

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNull
import com.sphereon.cbor.CborParser
import com.sphereon.cbor.CborParserImpl
import com.sphereon.cbor.CborString
import com.sphereon.cbor.NumberLabel
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CoseKeyCborCodec>())
class CoseKeyCborCodecImpl(
    private val cborParser: CborParser = CborParserImpl(),
) : CoseKeyCborCodec {
    override fun encode(value: CoseKey): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "COSE_Key",
            operation = { encodeCoseKey(value) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedCbor<CoseKey>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val map = requireMap(item, "COSE_Key")
            decodeValue(
                typeName = "COSE_Key",
                bytes = bytes,
                operation = { decodeCoseKey(map) },
            ).map { decoded ->
                decoded.copy(value = decoded.value.copy(original = bytes))
            }
        }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CoseHeaderCborCodec>())
class CoseHeaderCborCodecImpl(
    private val cborParser: CborParser = CborParserImpl(),
) : CoseHeaderCborCodec {
    override fun encode(value: CoseHeaderCbor): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "COSE header",
            operation = { encodeCoseHeader(value) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedCbor<CoseHeaderCbor>, IdkError> {
        if (bytes.isEmpty()) {
            return Ok(DecodedCbor(CoseHeaderCbor(), bytes))
        }
        return cborParser.parse(bytes).flatMap { item ->
            val map = requireMap(item, "COSE header")
            decodeValue(
                typeName = "COSE header",
                bytes = bytes,
                operation = { decodeCoseHeader(map) },
            )
        }
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CoseSign1CborCodec>())
class CoseSign1CborCodecImpl(
    private val cborParser: CborParser,
    private val headerCodec: CoseHeaderCborCodec,
) : CoseSign1CborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        headerCodec = CoseHeaderCborCodecImpl(cborParser),
    )

    override fun encode(value: CoseSign1<*>): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "COSE_Sign1",
            operation = { encodeCoseSign1(value) },
        )

    @Suppress("MagicNumber")
    override fun decode(bytes: ByteArray): IdkResult<DecodedCbor<CoseSign1<Any>>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val array = requireArray(item, "COSE_Sign1")
            if (array.value.size != 4) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "COSE_Sign1 must contain exactly 4 items, found ${array.value.size}",
                    ),
                )
            }

            val protectedHeaderBytes = requireByteString(array.value[0], "COSE_Sign1 protected header")
            val unprotectedHeader =
                decodeOptionalHeader(array.value[1], "COSE_Sign1 unprotected header")
                    .getOrElse { return Err(it) }
            val payload = decodeOptionalByteString(array.value[2], "COSE_Sign1 payload").getOrElse { return Err(it) }
            val signature = requireByteString(array.value[3], "COSE_Sign1 signature")

            headerCodec.decode(protectedHeaderBytes.value).flatMap { decodedHeader ->
                decodeValue(
                    typeName = "COSE_Sign1",
                    bytes = bytes,
                    operation = {
                        CoseSign1(
                            protectedHeader = decodedHeader.value,
                            unprotectedHeader = unprotectedHeader,
                            payload = payload,
                            signature = signature,
                        )
                    },
                )
            }
        }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CoseMac0CborCodec>())
class CoseMac0CborCodecImpl(
    private val cborParser: CborParser,
    private val headerCodec: CoseHeaderCborCodec,
) : CoseMac0CborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        headerCodec = CoseHeaderCborCodecImpl(cborParser),
    )

    override fun encode(value: CoseMac0Cbor): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "COSE_Mac0",
            operation = { encodeCoseMac0(value) },
        )

    @Suppress("MagicNumber")
    override fun decode(bytes: ByteArray): IdkResult<DecodedCbor<CoseMac0Cbor>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val array = requireArray(item, "COSE_Mac0")
            if (array.value.size != 4) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "COSE_Mac0 must contain exactly 4 items, found ${array.value.size}",
                    ),
                )
            }

            val protectedHeaderBytes = requireByteString(array.value[0], "COSE_Mac0 protected header")
            val unprotectedHeader =
                decodeOptionalHeader(array.value[1], "COSE_Mac0 unprotected header")
                    .getOrElse { return Err(it) }
            val payload = decodeOptionalByteString(array.value[2], "COSE_Mac0 payload").getOrElse { return Err(it) }
            val tag = requireByteString(array.value[3], "COSE_Mac0 tag")

            headerCodec.decode(protectedHeaderBytes.value).flatMap { decodedHeader ->
                decodeValue(
                    typeName = "COSE_Mac0",
                    bytes = bytes,
                    operation = {
                        CoseMac0Cbor(
                            protectedHeader = decodedHeader.value,
                            unprotectedHeader = unprotectedHeader,
                            payload = payload,
                            tag = tag,
                        )
                    },
                )
            }
        }
}

internal fun createToBeSignedCbor(
    protectedHeader: CoseHeaderCbor,
    payload: CborByteString,
    keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
    alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
    headerCodec: CoseHeaderCborCodec,
): IdkResult<ToBeSignedCbor, IdkError> =
    headerCodec.encode(protectedHeader).flatMap { protectedHeaderBytes ->
        encodeValue(typeName = "Sig_structure") {
            ToBeSignedCbor(
                value =
                    encodeCoseSignatureStructure(
                        CoseSignatureStructureCbor(
                            structure = CborString(SigStructure.Signature1.value),
                            bodyProtected = CborByteString(protectedHeaderBytes),
                            externalAad = CborByteString(byteArrayOf()),
                            payload = payload,
                        ),
                    ),
                keyInfo = keyInfo,
                alg = alg,
            )
        }
    }

internal fun createToBeMacedCbor(
    protectedHeader: CoseHeaderCbor,
    payload: ByteArray,
    externalAad: ByteArray = byteArrayOf(),
    headerCodec: CoseHeaderCborCodec,
): IdkResult<CborByteString, IdkError> =
    headerCodec.encode(protectedHeader).flatMap { protectedHeaderBytes ->
        encodeValue(typeName = "Mac_structure") {
            CborByteString(
                encodeCoseMacStructure(
                    CoseMacStructureCbor(
                        context = CborString(MacContext.Mac0.value),
                        protected = CborByteString(protectedHeaderBytes),
                        externalAad = CborByteString(externalAad),
                        payload = CborByteString(payload),
                    ),
                ),
            )
        }
    }

private inline fun <T> encodeValue(
    typeName: String,
    operation: () -> T,
): IdkResult<T, IdkError> =
    try {
        Ok(operation())
    } catch (e: IllegalArgumentException) {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to encode $typeName: ${e.message}", throwable = e))
    } catch (expected: Throwable) {
        Err(IdkError.UNKNOWN_ERROR(message = "Failed to encode $typeName: ${expected.message}", exception = expected))
    }

private inline fun <T> decodeValue(
    typeName: String,
    bytes: ByteArray,
    operation: () -> T,
): IdkResult<DecodedCbor<T>, IdkError> =
    try {
        Ok(DecodedCbor(operation(), bytes))
    } catch (e: IllegalArgumentException) {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode $typeName: ${e.message}", throwable = e))
    } catch (expected: Throwable) {
        Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode $typeName: ${expected.message}", exception = expected))
    }

@Suppress("UNCHECKED_CAST")
private fun requireMap(
    item: CborItem<*>,
    typeName: String,
): CborMap<NumberLabel, CborItem<*>> {
    require(item is CborMap<*, *>) { "$typeName must be encoded as a CBOR map" }
    // The CBOR decoder produces CborUInt/CborNInt keys, not NumberLabel.
    // On JVM type erasure hides this, but wasmJs enforces generic types at runtime.
    // Convert keys via CoseLabel.fromCborItem() which maps CborUInt→NumberLabel.
    val converted = mutableMapOf<NumberLabel, CborItem<*>>()
    for ((key, value) in item.value) {
        val label =
            when (key) {
                is NumberLabel -> key
                is CborItem<*> -> NumberLabel.fromCborItem(key)
                else -> throw IllegalArgumentException("$typeName map key is not a CBOR item")
            }
        converted[label] = value as CborItem<*>
    }
    return CborMap(converted)
}

@Suppress("UNCHECKED_CAST")
private fun requireArray(
    item: CborItem<*>,
    typeName: String,
): CborArray<CborItem<*>> {
    require(item is CborArray<*>) { "$typeName must be encoded as a CBOR array" }
    return item as CborArray<CborItem<*>>
}

private fun requireByteString(
    item: CborItem<*>,
    fieldName: String,
): CborByteString {
    require(item is CborByteString) { "$fieldName must be encoded as a CBOR byte string" }
    return item
}

private fun decodeOptionalByteString(
    item: CborItem<*>,
    fieldName: String,
): IdkResult<CborByteString?, IdkError> =
    when (item) {
        is CborNull -> Ok(null)
        is CborByteString -> Ok(item)
        else -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "$fieldName must be a CBOR byte string or null"))
    }

private fun decodeOptionalHeader(
    item: CborItem<*>,
    fieldName: String,
): IdkResult<CoseHeaderCbor?, IdkError> =
    when (item) {
        is CborNull -> Ok(null)
        is CborMap<*, *> -> decodeMapHeader(item, fieldName)
        else -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "$fieldName must be a CBOR map or null"))
    }

@Suppress("UNCHECKED_CAST")
private fun decodeMapHeader(
    item: CborMap<*, *>,
    fieldName: String,
): IdkResult<CoseHeaderCbor?, IdkError> =
    try {
        val converted = mutableMapOf<NumberLabel, CborItem<*>>()
        for ((key, value) in item.value) {
            val label =
                when (key) {
                    is NumberLabel -> key
                    is CborItem<*> -> NumberLabel.fromCborItem(key)
                    else -> throw IllegalArgumentException("$fieldName map key is not a CBOR item")
                }
            converted[label] = value as CborItem<*>
        }
        Ok(decodeCoseHeader(CborMap(converted)))
    } catch (e: IllegalArgumentException) {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode $fieldName: ${e.message}", throwable = e))
    } catch (expected: Throwable) {
        Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode $fieldName: ${expected.message}", exception = expected))
    }
