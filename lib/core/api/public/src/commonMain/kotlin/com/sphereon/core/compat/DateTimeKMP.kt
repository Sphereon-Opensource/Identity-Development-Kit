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

package com.sphereon.core.compat

import com.sphereon.core.compat.JsExportCompat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName
import kotlin.time.Instant

@OptIn(ExperimentalObjCName::class)
@ObjCName("LocalDateTimeKMP", exact = true)
@JsExportCompat
@Serializable(with = LocalDateTimeIso8601SerializerKMP::class)
class LocalDateTimeKMP(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int = 0,
    val nanosecond: Int = 0,
) : Comparable<LocalDateTimeKMP> {
    private val delegate =
        LocalDateTime(
            year = year,
            month = month,
            day = day,
            hour = hour,
            minute = minute,
            second = second,
            nanosecond = nanosecond,
        )

    override fun compareTo(other: LocalDateTimeKMP): Int = fromString(this.toString()).compareTo(fromString(other.toString()))

    /**
     * - fraction of seconds shall not be used;
     * — no local offset from UTC shall be used, as indicated by setting the time-offset defined in
     * RFC 3339 to “Z”.
     */
    fun toMdocTdateString() = (toInstant().toString().split(".")[0] + "Z").replace("ZZ", "Z")

    override fun toString(): String = delegate.toString()

    fun toEpochSeconds(timeZoneId: String? = TimeZone.UTC.id): Long = delegate.toInstant(DateTimeUtils.DEFAULTS.timeZone(timeZoneId)).epochSeconds

    fun toInstant(
        utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
        timeZoneId: String? = null,
    ) = delegate.toInstant(utils.timeZone(timeZoneId))

    companion object {
        @JsStatic
        fun now() = nowWithTimezone()

        @JsStatic
        fun nowWithTimezone(
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) = utils.dateTime(timeZoneId)

        @JsStatic
        fun fromString(value: String): LocalDateTimeKMP {
            val datetime: LocalDateTime =
                if (value.lowercase().endsWith('z')) {
                    val instant = Instant.parse(value)
                    instant.toLocalDateTime(TimeZone.of(DateTimeUtils.DEFAULTS.timeZoneId))
                } else {
                    LocalDateTime.parse(value)
                }

            return LocalDateTimeKMP(
                datetime.year,
                datetime.monthNumber,
                datetime.dayOfMonth,
                datetime.hour,
                datetime.minute,
                datetime.second,
                datetime.nanosecond,
            )
        }
    }
}

// fixme
// fun LocalDateTimeKMP.localDateToCborFullDate() = this.localDateToDateStringISO().toCborFullDate()

// typealias InstantIso8601SerializerKMP = InstantIso8601Serializer

/**
 * A serializer for [LocalDateTime] that uses the ISO-8601 representation.
 *
 * JSON example: `"2007-12-31T23:59:01"`
 *
 * @see LocalDateTime.parse
 * @see LocalDateTime.toString
 */
object LocalDateTimeIso8601SerializerKMP : KSerializer<LocalDateTimeKMP> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sphereon.core.compat.datetime.LocalDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDateTimeKMP {
        val parsed = LocalDateTime.parse(decoder.decodeString())
        return LocalDateTimeKMP(
            year = parsed.year,
            month = parsed.monthNumber,
            day = parsed.dayOfMonth,
            hour = parsed.hour,
            minute = parsed.minute,
            second = parsed.second,
            nanosecond = parsed.nanosecond,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: LocalDateTimeKMP,
    ) {
        encoder.encodeString(value.toString())
    }
}
