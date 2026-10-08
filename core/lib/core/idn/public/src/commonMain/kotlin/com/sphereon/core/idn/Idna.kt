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

package com.sphereon.core.idn

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import doist.x.normalize.Form
import doist.x.normalize.normalize

/**
 * IDNA2008 host normalization (RFC 5890 / 5891) over Punycode.
 *
 * Pipeline: NFC normalize the input (via [doistx-normalize](https://github.com/Doist/doistx-normalize)),
 * split on `.`, lowercase ASCII labels, encode non-ASCII labels with [Punycode]
 * and the `xn--` ACE prefix, validate label and total domain length, return
 * the joined ASCII host.
 *
 * Out of scope:
 * - RFC 5892 PValid / CONTEXTJ / CONTEXTO / DISALLOWED code-point validation.
 * - RFC 5893 bidi rule for RTL labels.
 */
object Idna {
    private const val ACE_PREFIX = "xn--"
    private const val MAX_LABEL_OCTETS = 63
    private const val MAX_DOMAIN_OCTETS = 253
    private const val ASCII_CUTOFF = 0x80
    private const val ASCII_CONTROL_END = 0x1F
    private const val ASCII_DEL = 0x7F

    /**
     * Convert a Unicode domain to its ASCII (Punycode) form.
     */
    fun toAscii(
        domain: String,
        options: IdnaOptions = IdnaOptions()
    ): IdkResult<String, IdkError> {
        if (domain.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IDNA: empty domain"))
        }
        val normalized = domain.normalize(Form.NFC)
        val labels = normalized.split('.')
        val asciiLabels = mutableListOf<String>()

        for ((idx, label) in labels.withIndex()) {
            if (label.isEmpty()) {
                if (idx == labels.lastIndex && idx > 0) {
                    asciiLabels.add("")
                    continue
                }
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IDNA: empty label in '$domain'"))
            }
            val ascii = labelToAscii(label, options).getOrElse { return Err(it) }
            if (ascii.length > MAX_LABEL_OCTETS) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IDNA: label exceeds 63 octets: '$ascii'"))
            }
            asciiLabels.add(ascii)
        }

        val joined = asciiLabels.joinToString(".")
        if (options.verifyDnsLength && joined.length > MAX_DOMAIN_OCTETS) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IDNA: domain exceeds 253 octets: '$joined'"))
        }
        return Ok(joined)
    }

    /**
     * Convert an ASCII (Punycode) domain back to its Unicode form.
     */
    fun toUnicode(domain: String): IdkResult<String, IdkError> {
        if (domain.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IDNA: empty domain"))
        }
        val labels = domain.split('.')
        val unicodeLabels = mutableListOf<String>()
        for (label in labels) {
            unicodeLabels.add(labelToUnicode(label).getOrElse { return Err(it) })
        }
        return Ok(unicodeLabels.joinToString("."))
    }

    private fun labelToAscii(
        label: String,
        options: IdnaOptions
    ): IdkResult<String, IdkError> {
        if (label.all { it.code < ASCII_CUTOFF }) {
            if (options.strict && !isValidAsciiLabel(label)) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IDNA: invalid ASCII label '$label'"))
            }
            return Ok(label.lowercase())
        }
        if (options.strict) {
            for (cp in label.toCodePoints()) {
                if (cp in 0..ASCII_CONTROL_END || cp == ASCII_DEL) {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IDNA: control code point in label"))
                }
            }
        }
        return Punycode.encode(label.lowercase()).map { ACE_PREFIX + it }
    }

    private fun labelToUnicode(label: String): IdkResult<String, IdkError> {
        if (!label.startsWith(ACE_PREFIX, ignoreCase = true)) {
            return Ok(label)
        }
        return Punycode.decode(label.substring(ACE_PREFIX.length))
    }

    private fun isValidAsciiLabel(label: String): Boolean {
        if (label.isEmpty()) {
            return false
        }
        if (label.first() == '-' || label.last() == '-') {
            return false
        }
        for (ch in label) {
            val ok = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-'
            if (!ok) {
                return false
            }
        }
        return true
    }
}

private inline fun <V, E> IdkResult<V, E>.map(transform: (V) -> V): IdkResult<V, E> =
    if (isOk) {
        Ok(transform(value))
    } else {
        this
    }

private inline fun <V, E> IdkResult<V, E>.getOrElse(onErr: (E) -> Nothing): V =
    if (isOk) {
        value
    } else {
        onErr(error)
    }
