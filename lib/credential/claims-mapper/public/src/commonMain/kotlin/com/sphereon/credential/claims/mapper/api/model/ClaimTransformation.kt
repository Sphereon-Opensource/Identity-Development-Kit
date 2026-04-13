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

package com.sphereon.credential.claims.mapper.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes the expected format of an input date value for [ClaimTransformation.ToIsoDate].
 */
@Serializable
enum class DateInputFormat {
    /** ISO 8601 datetime (e.g., "2025-01-15T10:30:00Z") — parsed via Instant.parse() */
    @SerialName("iso8601")
    ISO_8601,

    /** ISO 8601 date only (e.g., "2025-01-15") — parsed via LocalDate.parse(), output as "2025-01-15T00:00:00Z" */
    @SerialName("iso8601_date")
    ISO_8601_DATE,

    /** Unix epoch seconds (e.g., "1736936400") */
    @SerialName("epoch_seconds")
    EPOCH_SECONDS,

    /** Unix epoch milliseconds (e.g., "1736936400000") */
    @SerialName("epoch_millis")
    EPOCH_MILLIS,

    /** Auto-detect: tries ISO 8601, then epoch, passthrough on failure */
    @SerialName("auto")
    AUTO,
}

/**
 * Transformation to apply to a claim value during mapping.
 *
 * Transformations are optional and allow modifying the extracted claim value
 * before it's placed in the output claims map.
 */
@Serializable
sealed interface ClaimTransformation {
    /**
     * Pass through the value in its canonical form without any transformation.
     *
     * This is the default behavior when no transformation is specified.
     * The value is preserved exactly as extracted from the source credential.
     */
    @Serializable
    @SerialName("canonical")
    data object Canonical : ClaimTransformation

    /**
     * Convert the claim value to a string.
     *
     * @property format Optional format pattern for the conversion (e.g., date format)
     */
    @Serializable
    @SerialName("toString")
    data class ToString(
        val format: String? = null,
    ) : ClaimTransformation

    /**
     * Concatenate multiple claim values into a single string.
     *
     * Values are concatenated in order: prefixPaths, then the primary value,
     * then suffixPaths, all separated by the specified separator.
     *
     * Example: Combining given_name and family_name into a full name
     * ```
     * Concatenate(
     *     separator = " ",
     *     suffixPaths = listOf(listOf("family_name"))
     * )
     * ```
     * With sourceClaimPath = ["given_name"], this produces "John Doe"
     *
     * @property separator String to use between concatenated values (default: space)
     * @property prefixPaths Claim paths whose values are prepended before the primary value
     * @property suffixPaths Claim paths whose values are appended after the primary value
     */
    @Serializable
    @SerialName("concatenate")
    data class Concatenate(
        val separator: String = " ",
        val prefixPaths: List<List<String>> = emptyList(),
        val suffixPaths: List<List<String>> = emptyList(),
    ) : ClaimTransformation

    /**
     * Convert a date value to ISO 8601 format.
     *
     * Supports epoch seconds/millis, ISO 8601 datetime/date, and auto-detection.
     * Output is always a normalized ISO 8601 datetime string (e.g., "2025-01-15T10:30:00Z").
     *
     * @property inputFormat The expected format of the input date (default: auto-detect)
     */
    @Serializable
    @SerialName("toIsoDate")
    data class ToIsoDate(
        val inputFormat: DateInputFormat = DateInputFormat.AUTO,
    ) : ClaimTransformation

    /**
     * Extract a substring from the value.
     *
     * @property startIndex Starting index (inclusive)
     * @property endIndex Ending index (exclusive), or null for end of string
     */
    @Serializable
    @SerialName("substring")
    data class Substring(
        val startIndex: Int,
        val endIndex: Int? = null,
    ) : ClaimTransformation

    /**
     * Apply a regular expression replacement.
     *
     * @property pattern Regular expression pattern to match
     * @property replacement Replacement string (can include group references)
     */
    @Serializable
    @SerialName("regexReplace")
    data class RegexReplace(
        val pattern: String,
        val replacement: String,
    ) : ClaimTransformation

    /**
     * Convert the value to uppercase.
     */
    @Serializable
    @SerialName("uppercase")
    data object Uppercase : ClaimTransformation

    /**
     * Convert the value to lowercase.
     */
    @Serializable
    @SerialName("lowercase")
    data object Lowercase : ClaimTransformation
}
