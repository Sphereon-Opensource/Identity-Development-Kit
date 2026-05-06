/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.mdoc

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNil
import com.sphereon.cbor.CborParser
import com.sphereon.cbor.CborParserImpl
import com.sphereon.cbor.CborSimple
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.dsl.cborArray
import com.sphereon.cbor.dsl.encode
import com.sphereon.cbor.toCborItem
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.mdoc.data.device.decodeDeviceItemsRequest
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodecImpl
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transfer.reader.NfcHandover
import com.sphereon.mdoc.transfer.reader.OID4VPHandover
import com.sphereon.mdoc.transfer.reader.QrHandover
import com.sphereon.mdoc.transfer.reader.ReaderAuthentication
import com.sphereon.mdoc.transfer.reader.RestApiHandover
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<HandoverCborCodec>())
class HandoverCborCodecImpl(
    private val cborParser: CborParser = CborParserImpl(),
) : HandoverCborCodec {
    override fun encode(value: Handover<*, CborItem<*>>): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "Handover",
            operation = {
                com.sphereon.cbor.Cbor
                    .encode(encodeHandoverItem(value))
            },
        )

    override fun decode(bytes: ByteArray): IdkResult<Handover<*, CborItem<*>>, IdkError> = cborParser.parse(bytes).flatMap { item -> decode(item) }

    override fun decode(item: CborItem<*>): IdkResult<Handover<*, CborItem<*>>, IdkError> =
        try {
            Ok(decodeHandoverItem(item))
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode Handover: ${e.message}", throwable = e))
        } catch (expected: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode Handover: ${expected.message}", exception = expected))
        }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SessionEstablishmentCborCodec>())
class SessionEstablishmentCborCodecImpl(
    private val cborParser: CborParser,
    private val coseKeyCodec: CoseKeyCborCodec,
) : SessionEstablishmentCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        coseKeyCodec = CoseKeyCborCodecImpl(cborParser),
    )

    override fun encode(value: SessionEstablishment): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "SessionEstablishment",
            operation = { encodeSessionEstablishment(value) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<SessionEstablishment>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val map = requireStringLabelMap(item, "SessionEstablishment")
            decodeValue(
                typeName = "SessionEstablishment",
                bytes = bytes,
                operation = { decodeSessionEstablishment(map, bytes, coseKeyCodec) },
            )
        }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SessionDataCborCodec>())
class SessionDataCborCodecImpl(
    private val cborParser: CborParser = CborParserImpl(),
) : SessionDataCborCodec {
    override fun encode(value: SessionData): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "SessionData",
            operation = { encodeSessionData(value) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<SessionData>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val map = requireStringLabelMap(item, "SessionData")
            decodeValue(
                typeName = "SessionData",
                bytes = bytes,
                operation = { decodeSessionData(map, bytes) },
            )
        }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SessionTranscriptCborCodec>())
class SessionTranscriptCborCodecImpl(
    private val cborParser: CborParser,
    private val deviceEngagementCodec: DeviceEngagementCborCodec,
    private val coseKeyCodec: CoseKeyCborCodec,
    private val handoverCodec: HandoverCborCodec,
) : SessionTranscriptCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        deviceEngagementCodec = DeviceEngagementCborCodecImpl(cborParser),
        coseKeyCodec = CoseKeyCborCodecImpl(cborParser),
        handoverCodec = HandoverCborCodecImpl(cborParser),
    )

    override fun encode(value: SessionTranscript): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "SessionTranscript",
            operation = { encodeSessionTranscript(value) },
        )

    override fun encodeItem(value: SessionTranscript): IdkResult<CborEncodedItem<SessionTranscript>, IdkError> =
        encode(value).map { encoded ->
            CborEncodedItem(encoded, value.copy(original = encoded))
        }

    override fun encodeTag24(value: SessionTranscript): IdkResult<ByteArray, IdkError> =
        encodeItem(value).flatMap { encodedItem ->
            encodeValue(
                typeName = "SessionTranscript tag 24 wrapper",
                operation = {
                    com.sphereon.cbor.Cbor
                        .encode(encodedItem)
                },
            )
        }

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<SessionTranscript>, IdkError> =
        parseArrayAllowingTag24(cborParser, bytes, "SessionTranscript").flatMap { (structure, originalBytes) ->
            decodeValue(
                typeName = "SessionTranscript",
                bytes = originalBytes,
                operation = { decodeSessionTranscript(structure, originalBytes, deviceEngagementCodec, coseKeyCodec, handoverCodec) },
            )
        }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ReaderAuthenticationCborCodec>())
class ReaderAuthenticationCborCodecImpl(
    private val cborParser: CborParser,
    private val sessionTranscriptCodec: SessionTranscriptCborCodec,
) : ReaderAuthenticationCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        sessionTranscriptCodec = SessionTranscriptCborCodecImpl(cborParser),
    )

    override fun encode(value: ReaderAuthentication): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "ReaderAuthentication",
            operation = { encodeReaderAuthentication(value, sessionTranscriptCodec, cborParser) },
        )

    override fun encodeItem(value: ReaderAuthentication): IdkResult<CborEncodedItem<ReaderAuthentication>, IdkError> =
        encode(value).map { encoded ->
            CborEncodedItem(encoded, value.copy())
        }

    override fun encodeTag24(value: ReaderAuthentication): IdkResult<ByteArray, IdkError> =
        encodeItem(value).flatMap { encodedItem ->
            encodeValue(
                typeName = "ReaderAuthentication tag 24 wrapper",
                operation = {
                    com.sphereon.cbor.Cbor
                        .encode(encodedItem)
                },
            )
        }

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<ReaderAuthentication>, IdkError> =
        parseArrayAllowingTag24(cborParser, bytes, "ReaderAuthentication").flatMap { (structure, originalBytes) ->
            decodeValue(
                typeName = "ReaderAuthentication",
                bytes = originalBytes,
                operation = { decodeReaderAuthentication(structure, sessionTranscriptCodec) },
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
): IdkResult<DecodedMdoc<T>, IdkError> =
    try {
        Ok(DecodedMdoc(operation(), bytes))
    } catch (e: IllegalArgumentException) {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode $typeName: ${e.message}", throwable = e))
    } catch (expected: Throwable) {
        Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode $typeName: ${expected.message}", exception = expected))
    }

private fun encodeSessionEstablishment(value: SessionEstablishment): ByteArray {
    value.original?.let { return it }

    return com.sphereon.cbor.Cbor.encode(
        CborMap(
            mutableMapOf(
                SessionEstablishment.E_READER_KEY to value.encodedReaderKey,
                SessionEstablishment.DATA to value.data,
            ),
        ),
    )
}

private fun encodeSessionData(value: SessionData): ByteArray {
    value.original?.let { return it }

    val entries = mutableMapOf<StringLabel, CborItem<*>>()
    value.data?.let { entries[SessionData.DATA] = it }
    value.status?.let { entries[SessionData.STATUS] = it }
    return com.sphereon.cbor.Cbor
        .encode(CborMap(entries))
}

private fun encodeSessionTranscript(value: SessionTranscript): ByteArray {
    value.original?.let { return it }

    return com.sphereon.cbor.Cbor.encode(
        CborArray(
            mutableListOf(
                value.deviceEngagement.toCborItem(),
                value.eReaderKey.toCborItem(),
                encodeHandoverItem(value.handover),
            ),
        ),
    )
}

private fun encodeReaderAuthentication(
    value: ReaderAuthentication,
    sessionTranscriptCodec: SessionTranscriptCborCodec,
    cborParser: CborParser,
): ByteArray {
    val sessionTranscriptItem =
        cborParser
            .parse(
                sessionTranscriptCodec.encode(value.sessionTranscript).getOrThrow(),
            ).getOrThrow()

    return com.sphereon.cbor.Cbor.encode(
        CborArray(
            mutableListOf(
                ReaderAuthentication.READER_AUTHENTICATION,
                sessionTranscriptItem,
                value.itemsRequestBytes,
            ),
        ),
    )
}

private fun decodeSessionEstablishment(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
): SessionEstablishment {
    val eReaderKeyBytes: CborEncodedItem<CborMap<NumberLabel, CborItem<*>>> =
        SessionEstablishment.E_READER_KEY.required(structure)
    val encodedReaderKey = eReaderKeyBytes.copy(decodeCoseKey(eReaderKeyBytes.value.taggedItem.value, coseKeyCodec))
    val data: CborByteString = SessionEstablishment.DATA.required(structure)

    return SessionEstablishment(
        encodedReaderKey = encodedReaderKey,
        data = data,
        original = original,
    )
}

private fun decodeSessionData(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray,
): SessionData =
    SessionData(
        data = SessionData.DATA.optional<CborByteString>(structure),
        status = SessionData.STATUS.optional<CborUInt>(structure),
        original = original,
    )

private fun decodeSessionTranscript(
    structure: CborArray<CborItem<*>>,
    original: ByteArray,
    deviceEngagementCodec: DeviceEngagementCborCodec,
    coseKeyCodec: CoseKeyCborCodec,
    handoverCodec: HandoverCborCodec,
): SessionTranscript {
    val deviceEngagement =
        structure
            .optional<CborEncodedItem<CborMap<NumberLabel, CborItem<*>>>>(SessionTranscript.DEVICE_ENGAGEMENT)
            ?.let { encodedItem ->
                val decoded = deviceEngagementCodec.decode(encodedItem.value.taggedItem.value).getOrThrow().value
                encodedItem.copy(decoded)
            }
    val eReaderKey =
        structure
            .optional<CborEncodedItem<CborMap<NumberLabel, CborItem<*>>>>(SessionTranscript.ENGAGEMENT_READER_KEY)
            ?.let { encodedItem ->
                val decoded = coseKeyCodec.decode(encodedItem.value.taggedItem.value).getOrThrow().value
                encodedItem.copy(decoded)
            }
    val handoverItem = structure.optional<CborItem<*>>(SessionTranscript.HANDOVER)

    @Suppress("UNCHECKED_CAST")
    val handover = (
        handoverItem?.let { handoverCodec.decode(it).getOrThrow() }
            ?: QrHandover() as Handover<*, CborItem<*>>
    )

    return SessionTranscript(
        deviceEngagement = deviceEngagement,
        eReaderKey = eReaderKey,
        handover = handover,
        original = original,
    )
}

private fun decodeReaderAuthentication(
    structure: CborArray<CborItem<*>>,
    sessionTranscriptCodec: SessionTranscriptCborCodec,
): ReaderAuthentication {
    val discriminator = structure.required<CborString>(0)
    require(discriminator == ReaderAuthentication.READER_AUTHENTICATION) { "ReaderAuthentication must start with \"ReaderAuthentication\"" }

    val sessionTranscript =
        sessionTranscriptCodec
            .decode(
                com.sphereon.cbor.Cbor
                    .encode(structure.required<CborItem<*>>(1)),
            ).getOrThrow()
            .value

    val itemsRequestBytes = structure.required<CborEncodedItem<CborMap<StringLabel, CborItem<*>>>>(2)
    val decodedItemsRequest =
        decodeDeviceItemsRequest(
            structure =
                normalizeStringLabelMap(
                    com.sphereon.cbor.Cbor
                        .decode(itemsRequestBytes.value.taggedItem.value),
                    "DeviceItemsRequest",
                ),
            original = itemsRequestBytes.value.taggedItem.value,
        ).copy(original = itemsRequestBytes.value.taggedItem.value)

    return ReaderAuthentication(
        sessionTranscript = sessionTranscript,
        itemsRequestBytes = itemsRequestBytes.copy(decodedItemsRequest),
    )
}

private fun decodeCoseKey(
    bytes: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
): CoseKey = coseKeyCodec.decode(bytes).getOrThrow().value

private fun normalizeStringLabelMap(
    map: CborMap<CborItem<*>, CborItem<*>>,
    context: String,
): CborMap<StringLabel, CborItem<*>> =
    CborMap(
        map.value.entries
            .associate { (key, value) ->
                when (key) {
                    is StringLabel -> key to value
                    is CborString -> StringLabel(key.value) to value
                    else -> throw IllegalArgumentException("Expected string label in $context but got ${key::class.simpleName}")
                }
            }.toMutableMap(),
    )

@Suppress("UNCHECKED_CAST")
private fun encodeHandoverItem(value: Handover<*, CborItem<*>>): CborItem<*> =
    when (value) {
        is QrHandover -> {
            CborNil()
        }

        is RestApiHandover -> {
            CborByteString(value.readerEngagementHash)
        }

        is NfcHandover -> {
            CborArray(
                mutableListOf(
                    CborByteString(value.handoverSelectMessage),
                    value.handoverRequestMessage?.let(::CborByteString) ?: CborNil(),
                ),
            )
        }

        is OID4VPHandover -> {
            // OID4VP 1.0 final §B.2.6:
            //   OpenID4VPHandover = ["OpenID4VPHandover", sha256(OpenID4VPHandoverInfoBytes)]
            //   OpenID4VPHandoverInfo = [client_id, nonce, JwkThumbprint OR null, response_uri]
            // Build the inner info array, encode it, hash, and wrap with the magic string.
            val handoverInfoBytes =
                cborArray {
                    add(value.clientId)
                    add(value.nonce)
                    if (value.jwkThumbprint != null) add(value.jwkThumbprint) else addNull()
                    add(value.responseUri)
                }.encode()
            val handoverInfoHash = hash(handoverInfoBytes, DigestAlg.SHA256)
            CborArray(
                mutableListOf(
                    CborString("OpenID4VPHandover"),
                    CborByteString(handoverInfoHash),
                ),
            )
        }

        else -> {
            throw IllegalArgumentException("Unsupported handover type: ${value::class.simpleName}")
        }
    }

@Suppress("UNCHECKED_CAST")
private fun decodeHandoverItem(item: CborItem<*>): Handover<*, CborItem<*>> =
    when (item) {
        is CborNil -> {
            QrHandover() as Handover<*, CborItem<*>>
        }

        is CborSimple<*> -> {
            require(item.value == null) { "Handover null/simple item must encode QR handover" }
            QrHandover() as Handover<*, CborItem<*>>
        }

        is CborByteString -> {
            RestApiHandover(item.value) as Handover<*, CborItem<*>>
        }

        is CborArray<*> -> {
            decodeArrayHandover(item as CborArray<CborItem<*>>)
        }

        else -> {
            throw IllegalArgumentException("Handover must be encoded as nil, byte string, or array")
        }
    }

@Suppress("UNCHECKED_CAST")
private fun decodeArrayHandover(item: CborArray<CborItem<*>>): Handover<*, CborItem<*>> {
    require(item.value.size == 2) {
        "Handover array must contain exactly 2 items (NFC: [select, request]; OID4VP: [\"OpenID4VPHandover\", hash])"
    }
    // OID4VP §B.2.6 wraps the handover hash with a literal type tag in the first slot.
    // NFC encodes byte strings (or nil) in both slots, so the type tag of element 0 is the
    // unambiguous discriminator: text string => OID4VP, byte string => NFC.
    val first = item.value[0]
    if (first is CborString && first.value == "OpenID4VPHandover") {
        // Decoding a §B.2.6 handover from CBOR alone cannot recover the original
        // handoverInfo inputs (`client_id`, `nonce`, `jwkThumbprint`, `response_uri`) —
        // they were SHA-256-hashed and discarded by the encoder per spec. This codec
        // therefore refuses to materialise an OID4VPHandover from its serialized form.
        // Both holder and verifier independently reconstruct the handover from the
        // authorization request inputs (and re-encode), which is the spec-intended
        // model — there's no wire format that carries the handover.
        throw IllegalArgumentException(
            "Cannot decode an OID4VP §B.2.6 OpenID4VPHandover from CBOR — the original " +
                "inputs were hashed and are not recoverable. Reconstruct it from the " +
                "authorization request via OID4VPHandover.fromOid4vpInputs(...).",
        )
    }
    return NfcHandover(
        handoverSelectMessage = requireByteString(first, "NfcHandover handoverSelectMessage").value,
        handoverRequestMessage =
            when (val requestMessage = item.value[1]) {
                is CborNil -> null
                else -> requireByteString(requestMessage, "NfcHandover handoverRequestMessage").value
            },
    ) as Handover<*, CborItem<*>>
}

@Suppress("UNCHECKED_CAST")
private fun requireStringLabelMap(
    item: CborItem<*>,
    typeName: String,
): CborMap<StringLabel, CborItem<*>> {
    require(item is CborMap<*, *>) { "$typeName must be encoded as a CBOR map" }

    val normalizedEntries =
        item.value.entries
            .associate { (key, value) ->
                val keyItem =
                    key as? CborItem<*>
                        ?: throw IllegalArgumentException("$typeName map keys must be CBOR items")
                val valueItem =
                    value as? CborItem<*>
                        ?: throw IllegalArgumentException("$typeName map values must be CBOR items")
                StringLabel.fromCborItem(keyItem) to valueItem
            }.toMutableMap()
    return CborMap(normalizedEntries, item.indefiniteLength)
}

private fun parseArrayAllowingTag24(
    cborParser: CborParser,
    bytes: ByteArray,
    typeName: String,
): IdkResult<Pair<CborArray<CborItem<*>>, ByteArray>, IdkError> =
    cborParser.parse(bytes).flatMap { item ->
        val actualBytes =
            when (item) {
                is CborEncodedItem<*> -> item.value.taggedItem.value
                else -> bytes
            }
        val actualItemResult =
            if (actualBytes.contentEquals(bytes)) {
                Ok(item)
            } else {
                cborParser.parse(actualBytes)
            }

        actualItemResult.map { actualItem ->
            requireArray(actualItem, typeName) to actualBytes
        }
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
): CborByteString =
    item as? CborByteString
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR byte string")

private fun requireString(
    item: CborItem<*>,
    fieldName: String,
): CborString =
    item as? CborString
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR string")
