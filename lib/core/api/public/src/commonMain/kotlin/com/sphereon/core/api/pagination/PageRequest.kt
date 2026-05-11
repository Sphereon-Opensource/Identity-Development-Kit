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

package com.sphereon.core.api.pagination

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Sort direction for query results.
 */
@JsExportCompat
@Serializable
enum class SortDirection {
    @SerialName("asc")
    ASC,

    @SerialName("desc")
    DESC,
}

/**
 * Sort specification for a single field.
 */
@JsExportCompat
@Serializable
data class SortSpec
    @JvmOverloads
    constructor(
        /** The field name to sort by */
        val field: String,
        /** Sort direction */
        val direction: SortDirection = SortDirection.ASC,
    )

/**
 * Pagination and sorting parameters for list queries.
 *
 * Usage:
 * ```kotlin
 * // Simple pagination
 * PageRequest(limit = 20, offset = 0)
 *
 * // With sorting
 * PageRequest(limit = 20, sort = listOf(SortSpec("created_at", SortDirection.DESC)))
 *
 * // Using companion helpers
 * PageRequest.of(20).withSort("name")
 * ```
 */
@JsExportCompat
@Serializable
data class PageRequest
    @JvmOverloads
    constructor(
        /** Maximum number of items to return (default 50) */
        val limit: Int = 50,
        /** Number of items to skip (for pagination) */
        val offset: Int = 0,
        /** Sort specifications (applied in order) */
        val sort: List<SortSpec> = emptyList(),
    ) {
        companion object {
            /** Default page request (50 items, no sorting) */
            val DEFAULT = PageRequest()

            /** Create a page request with specified limit */
            fun of(
                limit: Int,
                offset: Int = 0,
            ) = PageRequest(limit = limit, offset = offset)

            /** Create a page request sorted by a single field */
            fun sorted(
                field: String,
                direction: SortDirection = SortDirection.ASC,
            ) = PageRequest(sort = listOf(SortSpec(field, direction)))

            /**
             * Parse pagination parameters from HTTP query params.
             * Supports both 'page/size' style (OpenAPI) and 'limit/offset' style.
             *
             * @param params query parameter map
             * @param maxLimit upper bound for limit (default 100)
             * @param defaultLimit limit when none specified (default 20)
             * @param defaultSortField sort field when none specified (default "createdAt")
             * @param defaultSortDirection sort direction when none specified (default DESC)
             */
            fun fromQueryParams(
                params: Map<String, String?>,
                maxLimit: Int = 100,
                defaultLimit: Int = 20,
                defaultSortField: String = "createdAt",
                defaultSortDirection: SortDirection = SortDirection.DESC,
            ): PageRequest {
                val page = params["page"]?.toIntOrNull() ?: 0
                val size = params["size"]?.toIntOrNull()
                val clampedLimit = (params["limit"]?.toIntOrNull() ?: size ?: defaultLimit).coerceIn(1, maxLimit)
                val offset = params["offset"]?.toIntOrNull() ?: (page * clampedLimit)

                val sortField = params["sort"] ?: defaultSortField
                val sortDirection =
                    params["sortDirection"]?.let {
                        when (it.uppercase()) {
                            "ASC" -> SortDirection.ASC
                            "DESC" -> SortDirection.DESC
                            else -> defaultSortDirection
                        }
                    } ?: defaultSortDirection

                return PageRequest(
                    limit = clampedLimit,
                    offset = offset.coerceAtLeast(0),
                    sort = listOf(SortSpec(sortField, sortDirection)),
                )
            }
        }

        /** Add a sort specification */
        fun withSort(
            field: String,
            direction: SortDirection = SortDirection.ASC,
        ) = copy(sort = sort + SortSpec(field, direction))

        /** Get the next page request */
        fun nextPage() = copy(offset = offset + limit)

        /** Get the previous page request (minimum offset is 0) */
        fun previousPage() = copy(offset = maxOf(0, offset - limit))
    }

/**
 * Paginated result wrapper.
 *
 * Contains the items for the current page along with metadata for navigation.
 */
@JsExportCompat
@Serializable
data class Page<T>(
    /** The items in this page */
    val items: List<T>,
    /** Total count of items across all pages */
    @SerialName("total_count")
    val totalCount: Long,
    /** The limit that was used */
    val limit: Int,
    /** The offset that was used */
    val offset: Int,
) {
    /** Whether there are more items after this page */
    @SerialName("has_more")
    val hasMore: Boolean get() = offset + items.size < totalCount

    /** Current page number (0-indexed) */
    @SerialName("page_number")
    val pageNumber: Int get() =
        if (limit > 0) {
            offset / limit
        } else {
            0
        }

    /** Total number of pages */
    @SerialName("total_pages")
    val totalPages: Int get() =
        if (limit > 0) {
            ((totalCount + limit - 1) / limit).toInt()
        } else {
            1
        }

    companion object {
        /** Create an empty page */
        fun <T> empty(limit: Int = 50) =
            Page<T>(
                items = emptyList(),
                totalCount = 0,
                limit = limit,
                offset = 0,
            )

        /** Create a page from a list (no pagination) */
        fun <T> of(items: List<T>) =
            Page(
                items = items,
                totalCount = items.size.toLong(),
                limit = items.size,
                offset = 0,
            )
    }

    /** Map the items to a different type */
    fun <R> map(transform: (T) -> R) =
        Page(
            items = items.map(transform),
            totalCount = totalCount,
            limit = limit,
            offset = offset,
        )
}

/**
 * Raw pagination metadata values, suitable for constructing module-specific
 * API PagingMeta objects without duplicating computation logic.
 */
@JsExportCompat
data class PagingValues(
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
)

/**
 * Extract computed pagination values from this Page.
 * Each REST module can use these values to construct its own generated PagingMeta type.
 */
fun <T> Page<T>.toPagingValues(): PagingValues =
    PagingValues(
        page = pageNumber,
        size = limit,
        totalElements = totalCount.toInt(),
        totalPages = totalPages,
    )
