/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.data.store.vault.portability

import doist.x.normalize.Form
import doist.x.normalize.normalize

enum class PortablePathProblem {
    EMPTY,
    ABSOLUTE,
    BACKSLASH,
    CONTROL_CHARACTER,
    EMPTY_SEGMENT,
    TRAVERSAL,
    RESERVED_NAME,
    TRAILING_DOT_OR_SPACE,
    NON_CANONICAL_UNICODE,
    PATH_TOO_LONG,
    SEGMENT_TOO_LONG,
}

data class PortablePathValidation(
    val valid: Boolean,
    val problem: PortablePathProblem? = null,
)

object VaultPortablePath {
    const val MAX_PATH_LENGTH = 4096
    const val MAX_SEGMENT_LENGTH = 255

    fun validate(
        path: String,
        requireCanonicalUnicode: Boolean = true,
    ): PortablePathValidation {
        if (path.isEmpty()) return invalid(PortablePathProblem.EMPTY)
        if (path.startsWith('/') || DRIVE_PREFIX.containsMatchIn(path)) return invalid(PortablePathProblem.ABSOLUTE)
        if ('\\' in path) return invalid(PortablePathProblem.BACKSLASH)
        if (path.any { it.isISOControl() }) return invalid(PortablePathProblem.CONTROL_CHARACTER)
        if (path.length > MAX_PATH_LENGTH) return invalid(PortablePathProblem.PATH_TOO_LONG)
        if (requireCanonicalUnicode && path != path.normalize(Form.NFC)) return invalid(PortablePathProblem.NON_CANONICAL_UNICODE)

        for (segment in path.split('/')) {
            if (segment.isEmpty()) return invalid(PortablePathProblem.EMPTY_SEGMENT)
            if (segment == "." || segment == "..") return invalid(PortablePathProblem.TRAVERSAL)
            if (segment.length > MAX_SEGMENT_LENGTH) return invalid(PortablePathProblem.SEGMENT_TOO_LONG)
            if (segment.endsWith('.') || segment.endsWith(' ')) return invalid(PortablePathProblem.TRAILING_DOT_OR_SPACE)
            if (isReservedSegment(segment)) return invalid(PortablePathProblem.RESERVED_NAME)
        }
        return PortablePathValidation(valid = true)
    }

    fun collisionKey(path: String): String = path.normalize(Form.NFKC).lowercase()

    fun depth(path: String): Int = path.count { it == '/' } + 1

    fun canonicalUtf8Comparator(): Comparator<String> = Comparator(::compareUtf8)

    private fun compareUtf8(
        left: String,
        right: String,
    ): Int {
        val a = left.encodeToByteArray()
        val b = right.encodeToByteArray()
        val common = minOf(a.size, b.size)
        for (index in 0 until common) {
            val result = (a[index].toInt() and 0xff).compareTo(b[index].toInt() and 0xff)
            if (result != 0) return result
        }
        return a.size.compareTo(b.size)
    }

    private fun isReservedSegment(segment: String): Boolean {
        if (':' in segment) return true
        val base = segment.substringBefore('.').uppercase()
        return base in RESERVED_NAMES ||
            (base.startsWith("COM") && base.drop(3).toIntOrNull() in 1..9) ||
            (base.startsWith("LPT") && base.drop(3).toIntOrNull() in 1..9)
    }

    private fun invalid(problem: PortablePathProblem) = PortablePathValidation(false, problem)

    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
    private val RESERVED_NAMES = setOf("CON", "PRN", "AUX", "NUL", "CLOCK$")
}
