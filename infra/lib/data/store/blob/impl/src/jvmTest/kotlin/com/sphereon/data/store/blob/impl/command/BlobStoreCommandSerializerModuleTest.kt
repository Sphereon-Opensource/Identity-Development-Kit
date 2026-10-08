/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.data.store.blob.impl.command

import com.sphereon.core.api.binary.StreamingBody
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.codec.CommandSerializerEntry
import com.sphereon.core.api.codec.JsonStreamingCodec
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.cas.ContentAddressDescriptor
import com.sphereon.data.store.blob.command.BlobCopyInput
import com.sphereon.data.store.blob.command.BlobDeleteInput
import com.sphereon.data.store.blob.command.BlobDeleteOutput
import com.sphereon.data.store.blob.command.BlobGetInput
import com.sphereon.data.store.blob.command.BlobGetOutput
import com.sphereon.data.store.blob.command.BlobListInput
import com.sphereon.data.store.blob.command.BlobMoveInput
import com.sphereon.data.store.blob.command.BlobPutInput
import com.sphereon.data.store.blob.command.BlobStatInput
import com.sphereon.data.store.blob.command.CasGetInput
import com.sphereon.data.store.blob.command.CasStoreInput
import com.sphereon.data.store.blob.command.CasVerifyInput
import com.sphereon.data.store.blob.command.CasVerifyOutput
import com.sphereon.data.store.blob.command.MetadataSearchInput
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the compile-time serializer coverage of the blob store command family.
 *
 * The production failure this guards: `blob.store.get` authenticated, routed and
 * reached its handler, then failed decoding its own request body with
 * `No registered serializer for BlobGetInput`, because the codecs resolve
 * serializers from the [CommandSerializerEntry] multibinding and have no reflective
 * fallback. Every `ocr.process` activity died on it, non-retryably.
 *
 * The type tokens below mirror the `inputTypeToken` / `outputTypeToken` declared by
 * each command in `BlobStoreCommands.kt`; a command added to [BlobCommandDescriptors]
 * without its payload types belongs in both places.
 */
class BlobStoreCommandSerializerModuleTest {

    /**
     * Every entry the module contributes, discovered rather than listed, so a provider
     * added to the module is covered here without touching this test.
     */
    private val entries: Set<CommandSerializerEntry> = run {
        val module = object : BlobStoreCommandSerializerModule {}
        module.javaClass.methods
            .filter { it.parameterCount == 0 && CommandSerializerEntry::class.java.isAssignableFrom(it.returnType) }
            .map { it.invoke(module) as CommandSerializerEntry }
            .toSet()
    }

    /** The codecs' own lookup rule: exact type first, then class, and only class-keyed entries. */
    private fun resolves(typeToken: TypeToken<*>): Boolean {
        val byType = entries.mapNotNull { entry -> entry.kType?.let { it to entry.serializer } }.toMap()
        val byClass = entries.filter { it.kType == null }.associate { it.kClass to it.serializer }
        if (byType.containsKey(typeToken.kType)) return true
        val kClass = typeToken.kType.classifier as? KClass<*> ?: return false
        return byClass.containsKey(kClass)
    }

    private val payloadTypes: List<Pair<String, TypeToken<*>>> = listOf(
        "blob.store.put in" to typeToken<BlobPutInput>(),
        "blob.store.put out" to typeToken<BlobDescriptor>(),
        "blob.store.get in" to typeToken<BlobGetInput>(),
        "blob.store.get out" to typeToken<BlobGetOutput>(),
        "blob.store.delete in" to typeToken<BlobDeleteInput>(),
        "blob.store.delete out" to typeToken<BlobDeleteOutput>(),
        "blob.store.stat in" to typeToken<BlobStatInput>(),
        "blob.store.stat out" to typeToken<BlobDescriptor>(),
        "blob.store.list in" to typeToken<BlobListInput>(),
        "blob.store.list out" to typeToken<ListResult>(),
        "blob.store.copy in" to typeToken<BlobCopyInput>(),
        "blob.store.copy out" to typeToken<BlobDescriptor>(),
        "blob.store.move in" to typeToken<BlobMoveInput>(),
        "blob.store.move out" to typeToken<BlobDescriptor>(),
        "blob.cas.store in" to typeToken<CasStoreInput>(),
        "blob.cas.store out" to typeToken<ContentAddressDescriptor>(),
        "blob.cas.get in" to typeToken<CasGetInput>(),
        "blob.cas.get out" to typeToken<BlobGetOutput>(),
        "blob.cas.verify in" to typeToken<CasVerifyInput>(),
        "blob.cas.verify out" to typeToken<CasVerifyOutput>(),
        "blob.metadata.search in" to typeToken<MetadataSearchInput>(),
        "blob.metadata.search out" to typeToken<List<BlobDescriptor>>(),
    )

    @Test
    fun `every blob command payload type has a registered serializer`() {
        val missing = payloadTypes.filterNot { (_, token) -> resolves(token) }
            .map { (what, token) -> "$what (${token.qualifiedName})" }

        assertTrue(
            missing.isEmpty(),
            "these blob command payload types cannot cross the binary transport: $missing",
        )
    }

    @Test
    fun `the get request the OCR worker sends decodes through the JSON codec`() {
        val codec = JsonStreamingCodec(serializerEntries = entries)
        val body = StreamingBody.Text(
            """{"info":{"storeId":"ocr","path":"ocr/inputs/job-46.pdf","tenantId":"platform"}}""",
        )

        val decoded = codec.decode(body, typeToken<BlobGetInput>())

        assertTrue(decoded.isOk, "blob.store.get input must decode: ${if (decoded.isErr) decoded.error.message.defaultMessage else ""}")
        assertEquals("ocr/inputs/job-46.pdf", decoded.value.info.path)
    }

    @Test
    fun `the get response the worker reads round-trips through the JSON codec`() {
        val codec = JsonStreamingCodec(serializerEntries = entries)
        val output = BlobGetOutput(
            dataBase64 = "AQID",
            path = "ocr/inputs/job-46.pdf",
            storeId = "ocr",
            sizeBytes = 3,
            contentType = "application/pdf",
        )

        val encoded = codec.encode(output)
        assertTrue(encoded.isOk, "blob.store.get output must encode")

        val decoded = codec.decode(encoded.value, typeToken<BlobGetOutput>())
        assertTrue(decoded.isOk, "blob.store.get output must decode")
        assertEquals(output, decoded.value)
    }

    @Test
    fun `the metadata search list output resolves by its exact type, not by List`() {
        val codec = JsonStreamingCodec(serializerEntries = entries)
        val descriptors = listOf(
            BlobDescriptor(path = "a.pdf", storeId = "ocr", sizeBytes = 1),
        )

        val encoded = codec.encode(descriptors, typeToken<List<BlobDescriptor>>())

        assertTrue(encoded.isOk, "blob.metadata.search output must encode by its exact KType")
    }
}
