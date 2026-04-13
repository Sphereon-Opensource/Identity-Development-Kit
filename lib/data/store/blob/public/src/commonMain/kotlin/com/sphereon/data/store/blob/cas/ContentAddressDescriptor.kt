package com.sphereon.data.store.blob.cas

import com.sphereon.data.store.blob.BlobDescriptor
import kotlinx.serialization.Serializable

/**
 * Descriptor for a content-addressed blob, combining the content address with the blob descriptor.
 */
@Serializable
data class ContentAddressDescriptor(
    val contentAddress: ContentAddress,
    val descriptor: BlobDescriptor,
    val referenceCount: Int = 1,
)
