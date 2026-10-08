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

import com.sphereon.core.api.codec.CommandSerializerEntry
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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.serialization.builtins.ListSerializer
import kotlin.reflect.typeOf

/**
 * Compile-time serializer registry entries for every payload type in
 * [BlobCommandDescriptors].
 *
 * ## Why this file exists
 *
 * The JSON, CBOR and Protobuf streaming codecs resolve serializers from this
 * multibinding **and nowhere else**. There is deliberately no reflective fallback
 * (`serializer(kType)` / `value::class.serializer()`), because kotlin-reflect is
 * excluded from the runtime classpath for GraalVM native-image support. A payload
 * type that is `@Serializable` but not registered here therefore cannot cross the
 * binary transport at all: the command authenticates, routes, reaches its handler,
 * and then dies decoding its own request body with
 * `No registered serializer for <Type>`.
 *
 * That is exactly what happened to `blob.store.get`. The OCR worker calls it over
 * gRPC to fetch the document it is about to process; every call failed to decode,
 * every `ocr.process` activity failed non-retryably, and no OCR job ever completed.
 * Commands generated from `@ServiceCommandBinding` get their entries emitted by
 * `ServiceCommandBindingsProcessor`; the blob store commands are hand-written
 * [com.sphereon.core.api.service.TypedServiceCommandAdapter]s, so nothing generated
 * them and nothing noticed.
 *
 * ## Maintenance
 *
 * One entry per distinct input and output type across all eleven blob commands.
 * Adding a command to [BlobCommandDescriptors] means adding its payload types here;
 * `BlobStoreCommandSerializerModuleTest` fails when the two drift apart.
 *
 * Entries are keyed by class (`kType = null`), which serves both the class-keyed
 * lookup used when encoding a value and the type-keyed lookup used when decoding
 * against a `TypeToken`. The one exception is `blob.metadata.search`, whose output
 * is a `List<BlobDescriptor>`: its runtime class is erased, so it must be keyed by
 * its exact [kotlin.reflect.KType].
 */
@ContributesTo(AppScope::class)
interface BlobStoreCommandSerializerModule {

    // -- inputs --

    @Provides
    @IntoSet
    fun provideBlobPutInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobPutInput::class, BlobPutInput.serializer())

    @Provides
    @IntoSet
    fun provideBlobGetInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobGetInput::class, BlobGetInput.serializer())

    @Provides
    @IntoSet
    fun provideBlobDeleteInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobDeleteInput::class, BlobDeleteInput.serializer())

    @Provides
    @IntoSet
    fun provideBlobStatInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobStatInput::class, BlobStatInput.serializer())

    @Provides
    @IntoSet
    fun provideBlobListInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobListInput::class, BlobListInput.serializer())

    @Provides
    @IntoSet
    fun provideBlobCopyInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobCopyInput::class, BlobCopyInput.serializer())

    @Provides
    @IntoSet
    fun provideBlobMoveInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobMoveInput::class, BlobMoveInput.serializer())

    @Provides
    @IntoSet
    fun provideCasStoreInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CasStoreInput::class, CasStoreInput.serializer())

    @Provides
    @IntoSet
    fun provideCasGetInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CasGetInput::class, CasGetInput.serializer())

    @Provides
    @IntoSet
    fun provideCasVerifyInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CasVerifyInput::class, CasVerifyInput.serializer())

    @Provides
    @IntoSet
    fun provideMetadataSearchInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(MetadataSearchInput::class, MetadataSearchInput.serializer())

    // -- outputs --

    /** Output of `blob.store.put`, `blob.store.stat`, `blob.store.copy` and `blob.store.move`. */
    @Provides
    @IntoSet
    fun provideBlobDescriptorSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobDescriptor::class, BlobDescriptor.serializer())

    /** Output of `blob.store.get` and `blob.cas.get`. */
    @Provides
    @IntoSet
    fun provideBlobGetOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobGetOutput::class, BlobGetOutput.serializer())

    @Provides
    @IntoSet
    fun provideBlobDeleteOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(BlobDeleteOutput::class, BlobDeleteOutput.serializer())

    @Provides
    @IntoSet
    fun provideListResultSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(ListResult::class, ListResult.serializer())

    @Provides
    @IntoSet
    fun provideContentAddressDescriptorSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(ContentAddressDescriptor::class, ContentAddressDescriptor.serializer())

    @Provides
    @IntoSet
    fun provideCasVerifyOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CasVerifyOutput::class, CasVerifyOutput.serializer())

    /**
     * Output of `blob.metadata.search`. Keyed by [kotlin.reflect.KType] rather than by
     * class: `List::class` is shared by every list payload, so only the exact type can
     * identify this one.
     */
    @Provides
    @IntoSet
    fun provideBlobDescriptorListSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(
            kClass = List::class,
            serializer = ListSerializer(BlobDescriptor.serializer()),
            kType = typeOf<List<BlobDescriptor>>(),
        )
}
