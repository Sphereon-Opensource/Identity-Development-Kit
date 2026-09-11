package com.sphereon.core.api.pagination

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Canonical pagination metadata for a REST list response, mirroring the OpenAPI
 * `common-components.PageMeta` schema and the `pagination` object emitted by
 * `ResponseBuilder.paginated()`.
 *
 * `limit` / `offset` / `total` / `hasMore` are the stable legacy fields; `page` / `size` /
 * `totalPages` are the additive unified fields. `size` is the contract's declared alias of `limit`
 * (the page window), not the number of items actually returned on the last page.
 *
 * This is the one definition every REST module maps its [Page] onto — a module that mirrors it
 * locally drifts from the schema the clients are generated from.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PageMeta", exact = true)
@JsExportCompat
@Serializable
data class PageMeta(
    val limit: Int,
    val offset: Int,
    val page: Int,
    val size: Int,
    val total: Long,
    val totalPages: Int,
    val hasMore: Boolean,
)

/** Projects the platform [Page] envelope onto the wire [PageMeta]. */
fun <T> Page<T>.toPageMeta(): PageMeta = PageMeta(
    limit = limit,
    offset = offset,
    page = pageNumber,
    size = limit,
    total = totalCount,
    totalPages = totalPages,
    hasMore = hasMore,
)

/**
 * Canonical REST list envelope used by OpenAPI resources that reference
 * `common-components.PageMeta`.
 *
 * Service-command transports continue to use [Page] so their typed/native
 * serializers retain the declared element type. REST adapters serialize that
 * same value through [CanonicalPageSerializer] to prevent the command envelope
 * (`total_count`, `limit`, `offset`) from drifting from the public wire shape.
 */
@Serializable
data class CanonicalPage<T>(
    val items: List<T>,
    val pagination: PageMeta,
)

fun <T> Page<T>.toCanonicalPage(): CanonicalPage<T> = CanonicalPage(items, toPageMeta())

fun <T> CanonicalPage<T>.toPage(): Page<T> = Page(
    items = items,
    totalCount = pagination.total,
    limit = pagination.limit,
    offset = pagination.offset,
)

/** Serializes the platform [Page] as the canonical REST list envelope. */
class CanonicalPageSerializer<T>(elementSerializer: KSerializer<T>) : KSerializer<Page<T>> {
    private val delegate = CanonicalPage.serializer(elementSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Page<T>) =
        encoder.encodeSerializableValue(delegate, value.toCanonicalPage())

    override fun deserialize(decoder: Decoder): Page<T> =
        decoder.decodeSerializableValue(delegate).toPage()
}
