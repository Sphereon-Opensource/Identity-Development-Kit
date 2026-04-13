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

package com.sphereon.core.compat

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate.Formats.ISO
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

@JsExportCompat
class DateTimeUtils(
    var clock: Clock = Clock.System,
    var timeZoneId: String = TimeZone.currentSystemDefault().id,
) {
    // TODO: Let's hope we can properly use Longs in a KMP project in 2038, when the epoch would run out of max int
    fun epochSeconds() = now().epochSeconds.toInt()
    fun dateTimeUTC(epochSeconds: Int? = epochSeconds()) = dateTime(TimeZone.UTC.id, epochSeconds)
    fun dateTimeLocal(epochSeconds: Int? = epochSeconds()) = dateTime(timeZoneId, epochSeconds)
    fun dateTime(timeZoneId: String? = null, epochSeconds: Int? = epochSeconds()) =
        Instant.fromEpochSeconds(epochSeconds?.toLong() ?: epochSeconds().toLong())
            .toLocalDateTime(timeZone(timeZoneId)).toKMP()

    fun dateLocalISO(epochSeconds: Int? = epochSeconds()) = dateISO(timeZoneId, epochSeconds)
    fun dateISO(timeZoneId: String?, epochSeconds: Int? = epochSeconds()) =
        dateTime(timeZoneId ?: this.timeZoneId, epochSeconds).toKotlin().date.format(ISO)

    companion object {
        @JsStatic
        val DEFAULTS: DateTimeUtils = DateTimeUtils()
    }


    private fun now() = clock.now()

    fun timeZone(timeZoneId: String?): TimeZone = timeZoneId?.let { TimeZone.of(it) } ?: defaultTimeZone.value
    private val defaultTimeZone = lazy { TimeZone.of(this.timeZoneId) }
}

@JsExportCompat
object DateTimeUtilsDefault {
    val INSTANCE = DateTimeUtils()
}

fun LocalDateTimeKMP.toKotlin(): LocalDateTime = LocalDateTime.parse(this.toString())
fun LocalDateTime.toKMP(): LocalDateTimeKMP = LocalDateTimeKMP.fromString(this.toString())

fun Long.toLocalDateTimeKMP(dateTimeUtils: DateTimeUtils? = null): LocalDateTimeKMP =
    getDateTime(dateTimeUtils).dateTimeLocal(this.toInt())

fun getDateTime(dateTimeUtils: DateTimeUtils? = null) = dateTimeUtils ?: DateTimeUtilsDefault.INSTANCE
