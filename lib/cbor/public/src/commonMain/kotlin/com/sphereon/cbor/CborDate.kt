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

package com.sphereon.cbor

import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.DateTimeUtilsDefault
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.core.compat.toKotlin
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import com.sphereon.core.compat.JsExportCompat
import kotlin.jvm.JvmInline

/**
 * We are using the LocalDateTimeKmp class to deserialize and serialize again, to make really sure the date is valid and that it become a full date-time according to 18013-5
 */
@JsExportCompat
class CborTDate(value: cddl_tdate) : CborTagged<cddl_tdate>(CDDL.tdate.info!!, CborString(LocalDateTimeKMP.fromString(value).toMdocTdateString())) {
    override fun toJsonSimple(): JsonElement {
        return JsonPrimitive(toValue())
    }
}

@JsExportCompat
class CborFullDate(
    value: cddl_full_date
) : CborTagged<cddl_full_date>(CDDL.full_date.info!!, CborString(value)) {
    override fun toJsonSimple(): JsonElement {
        return JsonPrimitive(toValue())
    }
}

@JvmInline
@Serializable
value class TDate(private val value: String) : HasToCbor<CborTDate> {
    init {
        // for validation purposes only
        LocalDateTimeKMP.fromString(value)
    }

    override fun toString(): String {
        return LocalDateTimeKMP.fromString(value).toMdocTdateString()
    }

    override fun toCborStructure(): CborTDate = CborTDate(toString())
}

@JsExportCompat
fun CborTDate.cborTDateToEpochSeconds(utils: DateTimeUtils = DateTimeUtils.DEFAULTS, timeZoneId: String? = null) =
    this.cborTDateToLocalDateTime(utils, timeZoneId).toInstant(utils.timeZone(timeZoneId)).epochSeconds

fun TDate.tDateToEpochSeconds(utils: DateTimeUtils = DateTimeUtils.DEFAULTS, timeZoneId: String? = null) =
    this.tDateToLocalDateTime(utils, timeZoneId).toInstant(utils.timeZone(timeZoneId)).epochSeconds

@JsExportCompat
fun CborFullDate.cborFullDateToEpochSeconds(utils: DateTimeUtils = DateTimeUtils.DEFAULTS, timeZoneId: String? = null) =
    this.cborFullDateToLocalDateTime(utils, timeZoneId).toInstant(utils.timeZone(timeZoneId)).epochSeconds

fun CborTDate.cborTDateToLocalDateTime(utils: DateTimeUtils = DateTimeUtils.DEFAULTS, timeZoneId: String? = null) =
    this.value.toLocalDateTime(utils, timeZoneId)


fun CborFullDate.cborFullDateToLocalDateTime(utils: DateTimeUtils = DateTimeUtils.DEFAULTS, timeZoneId: String? = null) =
    this.value.toLocalDateTime(utils, timeZoneId)

fun cddl_full_date.toLocalDateTime(utils: DateTimeUtils = DateTimeUtils.DEFAULTS, timeZoneId: String? = null) =
    utils.dateTime(timeZoneId, LocalDateTimeKMP.fromString(this).toEpochSeconds(timeZoneId).toInt()).toKotlin()

fun TDate.tDateToLocalDateTime(utils: DateTimeUtils = DateTimeUtils.DEFAULTS, timeZoneId: String? = null) =
    utils.dateTime(timeZoneId, LocalDateTimeKMP.fromString(this.toString()).toEpochSeconds(timeZoneId).toInt()).toKotlin()


@JsExportCompat
fun cddl_full_date.toCborFullDate(utils: DateTimeUtils = DateTimeUtils.DEFAULTS, timeZoneId: String? = null) =
    CborFullDate(this.toLocalDateTime(utils, timeZoneId).date.toString())

fun Instant.instantToDateStringISO(utils: DateTimeUtils = DateTimeUtils.DEFAULTS, timeZoneId: String? = null) =
    utils.dateISO(timeZoneId, epochSeconds = this.epochSeconds.toInt())

@JsExportCompat
fun LocalDateTimeKMP.localDateToDateStringISO(
    utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
    timeZoneId: String? = null
) =
    utils.dateISO(timeZoneId, this.toInstant(utils, timeZoneId).epochSeconds.toInt())

fun LocalDateTime.localDateTimeToDateStringISO(
    utils: DateTimeUtils = DateTimeUtilsDefault.INSTANCE,
    timeZoneId: String? = null
) =
    utils.dateISO(
        timeZoneId,
        epochSeconds = this.toInstant(TimeZone.of(timeZoneId ?: utils.timeZoneId)).epochSeconds.toInt()
    )

fun LocalDate.localDateToDateStringISO(
    utils: DateTimeUtils = DateTimeUtilsDefault.INSTANCE,
    timeZoneId: String? = null
) =
    utils.dateISO(
        timeZoneId,
        epochSeconds = this.atTime(0, 0).toInstant(TimeZone.of(timeZoneId ?: utils.timeZoneId)).epochSeconds.toInt()
    )

@JsExportCompat
fun LocalDateTimeKMP.localDateToCborFullDate() = this.toString().toCborFullDate()

/**
 * no local offset from UTC shall be used, as indicated by setting the time-offset defined in
 * RFC 3339 to "Z".
 */
fun LocalDateTimeKMP.localDateTimeToCborDate() = CborTDate(this.toMdocTdateString())
