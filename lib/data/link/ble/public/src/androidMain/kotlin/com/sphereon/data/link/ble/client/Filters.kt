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

package com.sphereon.data.link.ble.client

import android.bluetooth.le.ScanFilter
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM
import android.os.ParcelUuid
import com.sphereon.data.link.ble.filter.Filter
import com.sphereon.data.link.ble.filter.FilterPredicate
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

internal fun List<FilterPredicate>.toScanFilters(): ScanFilters =
    if (all(FilterPredicate::supportsNativeScanFiltering)) {
        ScanFilters(
            native = map(FilterPredicate::toNativeScanFilter),
            flow = emptyList(),
        )
    } else if (count() == 1) {
        val nativeFilters = mutableMapOf<KClass<*>, Filter>()
        val flowFilters = mutableListOf<Filter>()
        single().filters.forEach { filter ->
            if (filter.canFilterNatively && filter::class !in nativeFilters) {
                nativeFilters[filter::class] = filter
            } else {
                flowFilters += filter
            }
        }
        ScanFilters(
            native = listOf(nativeFilters.values.toList().toNativeScanFilter()),
            flow = listOf(FilterPredicate(flowFilters)),
        )
    } else {
        ScanFilters(
            native = emptyList(),
            flow = this,
        )
    }

// Android's `ScanFilter` does not support name prefix filtering, and only allows at most one of each filter type.
private val FilterPredicate.supportsNativeScanFiltering: Boolean
    get() {
        var service = 0
        var nameExact = 0
        var address = 0
        var manufacturerData = 0
        var serviceData = 0
        filters.forEach { filter ->
            when (filter) {
                is Filter.Service -> if (++service > 1) return false
                is Filter.Name.Exact -> if (++nameExact > 1) return false
                is Filter.Name.Prefix -> return false
                is Filter.Address -> if (++address > 1) return false
                is Filter.ManufacturerData -> if (++manufacturerData > 1) return false
                is Filter.ServiceData -> if (++serviceData > 1) return false
            }
        }
        return true
    }

private val Filter.canFilterNatively: Boolean
    get() = when (this) {
        is Filter.Service -> true
        is Filter.Name.Exact -> true
        is Filter.Address -> true
        is Filter.ManufacturerData -> true
        is Filter.ServiceData -> true
        else -> false
    }

private fun FilterPredicate.toNativeScanFilter(): ScanFilter = filters.toNativeScanFilter()

private fun List<Filter>.toNativeScanFilter(): ScanFilter =
    ScanFilter.Builder().apply {
        onEach { filter ->
            when (filter) {
                is Filter.Service -> setServiceUuid(ParcelUuid(filter.uuid.toJavaUuid()))
                is Filter.Name.Exact -> setDeviceName(filter.exact)
                is Filter.Address -> setDeviceAddress(filter.address)
                is Filter.ManufacturerData -> setManufacturerData(filter.id, filterDataCompat(filter.data), filter.dataMask)
                is Filter.ServiceData -> setServiceData(ParcelUuid(filter.uuid.toJavaUuid()), filterDataCompat(filter.data), filter.dataMask)
                else -> throw AssertionError("Unsupported filter element")
            }
        }
    }.build()

// Android doesn't properly check for nullness of manufacturer or service data until Android 16.
// See https://github.com/JuulLabs/kable/issues/854 for more details.
private fun filterDataCompat(data: ByteArray?): ByteArray? =
    if (data == null && SDK_INT <= VANILLA_ICE_CREAM) byteArrayOf() else data


internal data class ScanFilters(

    /** [ScanFilter]s applied using Android's native filtering. */
    val native: List<ScanFilter>,

    /** [FilterPredicate]s applied via flow [filter][Flow.filter] operator. */
    val flow: List<FilterPredicate>,
)
