/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborParser
import com.sphereon.cbor.CborParserImpl
import com.sphereon.cbor.CborString
import com.sphereon.cbor.StringLabel
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Codec for the map encrypted by the second-edition document-response envelope. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<EncryptedDocumentsPlaintextCborCodec>())
class EncryptedDocumentsPlaintextCborCodecImpl(
    private val cborParser: CborParser,
    private val documentCodec: DocumentCborCodec,
) : EncryptedDocumentsPlaintextCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        documentCodec = DocumentCborCodecImpl(cborParser),
    )

    override fun encode(value: EncryptedDocumentsPlaintext): IdkResult<ByteArray, IdkError> =
        try {
            val fields = mutableMapOf<StringLabel, CborItem<*>>()
            value.documents?.let { documents ->
                fields[StringLabel("documents")] =
                    CborArray(
                        documents
                            .map { document -> Cbor.tryDecode(documentCodec.encode(document).getOrThrow()).getOrThrow() }
                            .toMutableList(),
                    )
            }
            value.zkDocuments?.let { documents ->
                fields[StringLabel("zkDocuments")] =
                    CborArray(documents.map { document -> encodeZkDocument(document, cborParser) }.toMutableList())
            }
            value.unknown?.let { unknown -> fields.putAll(unknown.mapKeys { (key, _) -> StringLabel(key) }) }
            Ok(Cbor.encode(CborMap(fields)))
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to encode EncryptedDocumentsPlaintext: ${e.message}", throwable = e))
        } catch (e: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to encode EncryptedDocumentsPlaintext: ${e.message}", exception = e))
        }

    override fun decode(bytes: ByteArray): IdkResult<EncryptedDocumentsPlaintext, IdkError> =
        try {
            val item = Cbor.tryDecode(bytes).getOrThrow()
            val map = normalizeMap(item)
            val documents =
                map.value[StringLabel("documents")]?.let { documentItems ->
                    val array = documentItems as? CborArray<*> ?: error("EncryptedDocumentsPlaintext.documents must be an array")
                    array.value.map { documentCodec.decode(it as CborItem<*>).getOrThrow() }
                }
            val zkDocuments =
                map.value[StringLabel("zkDocuments")]?.let { documentItems ->
                    val array = documentItems as? CborArray<*> ?: error("EncryptedDocumentsPlaintext.zkDocuments must be an array")
                    array.value.map { decodeZkDocument(it as CborItem<*>, cborParser) }
                }
            Ok(
                EncryptedDocumentsPlaintext(
                    documents = documents,
                    zkDocuments = zkDocuments,
                    unknown =
                        map.value
                            .filterKeys { it.value != "documents" && it.value != "zkDocuments" }
                            .mapKeys { (key, _) -> key.value }
                            .takeIf { it.isNotEmpty() },
                ),
            )
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode EncryptedDocumentsPlaintext: ${e.message}", throwable = e))
        } catch (e: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode EncryptedDocumentsPlaintext: ${e.message}", exception = e))
        }

    private fun normalizeMap(item: CborItem<*>): CborMap<StringLabel, CborItem<*>> {
        val map = item as? CborMap<*, *> ?: error("EncryptedDocumentsPlaintext must be a CBOR map")
        return CborMap(
            map.value.entries.associate { (key, value) ->
                val normalizedKey =
                    when (key) {
                        is StringLabel -> key
                        is CborString -> StringLabel(key.value)
                        else -> error("EncryptedDocumentsPlaintext keys must be text")
                    }
                normalizedKey to (value as? CborItem<*> ?: error("EncryptedDocumentsPlaintext values must be CBOR items"))
            }.toMutableMap(),
            map.indefiniteLength,
        )
    }
}
