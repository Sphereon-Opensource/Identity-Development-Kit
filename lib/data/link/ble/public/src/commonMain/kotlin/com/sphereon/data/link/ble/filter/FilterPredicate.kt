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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.link.ble.filter

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class FilterPredicate(
    /** A non-empty list of filters, all of which must match to satisfy this predicate. */
    val filters: List<Filter>,
) {
    init {
        require(filters.isNotEmpty())
    }
}

internal fun List<FilterPredicate>.flatten(): List<Filter> =
    flatMap(FilterPredicate::filters)

/**
 * Returns `true` if at least one of the predicates match the given parameters. Also returns
 * `true` if empty because there are no predicates that _do not_ match the inputs.
 */
internal fun List<FilterPredicate>.matches(
    services: List<Uuid>? = null,
    name: String? = null,
    address: String? = null,
    manufacturerData: ManufacturerData? = null,
    serviceData: Map<Uuid, ByteArray>? = null,
) = if (isEmpty()) {
    true
} else {
    any { it.matches(services, name, address, manufacturerData, serviceData) }
}

/** Returns `true` if all of the filters on this predicate match the given parameters. */
internal fun FilterPredicate.matches(
    services: List<Uuid>? = null,
    name: String? = null,
    address: String? = null,
    manufacturerData: ManufacturerData? = null,
    serviceData: Map<Uuid, ByteArray>? = null,
): Boolean = filters.all { it.matches(services, name, address, manufacturerData, serviceData) }

private fun Filter.matches(
    services: List<Uuid>?,
    name: String?,
    address: String?,
    manufacturerData: ManufacturerData?,
    serviceData: Map<Uuid, ByteArray>?,
): Boolean = when (this) {
    is Filter.Address -> matches(address)
    is Filter.ManufacturerData -> matches(manufacturerData?.code, manufacturerData?.data)
    is Filter.ServiceData -> serviceData != null && uuid in serviceData && matches(serviceData[uuid])
    is Filter.Name -> matches(name)
    is Filter.Service -> matches(services)
}
