/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.json.jcs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * RFC 8785 JSON Canonicalization Scheme.
 *
 * Produces a single canonical byte sequence for a JSON value so that signatures
 * over that sequence verify regardless of how the input was originally serialized.
 *
 * Rules implemented (RFC 8785 §3 plus §4 test vectors):
 * - Object members sorted by UTF-16 code-unit lexicographic order of their names.
 * - No insignificant whitespace.
 * - Strings escaped with the minimum RFC 8259 set: `\"`, `\\`, and `\u00xx` for
 *   control characters U+0000..U+001F. No optional forward-slash escape.
 * - `null`, `true`, `false` emitted as literal tokens.
 * - Integer JSON numbers emitted without fractional component or leading zeros.
 *
 * **Known gap**: non-integer (fractional) JSON numbers require ECMAScript 6
 * `Number.prototype.toString` formatting per RFC 8785 §3.2.2.3. This
 * implementation passes through [JsonPrimitive.content] for such numbers,
 * which is sufficient when the consumer supplies already-canonical numeric
 * strings (for example, a policy-binding covered-field projection containing
 * no fractional numbers). Full ES6 number formatting lands in a follow-up.
 *
 * Thread-safety: this object is stateless and safe to share across threads.
 */
object Jcs {
    /** Canonical bytes for [element]. UTF-8 encoded. */
    fun canonicalize(element: JsonElement): ByteArray = canonicalString(element).encodeToByteArray()

    /** Canonicalize [json] using the supplied [parser] (default: strict). */
    fun canonicalize(
        json: String,
        parser: Json = StrictJson,
    ): ByteArray = canonicalize(parser.parseToJsonElement(json))

    /** Canonical string form of [element]. Prefer [canonicalize] for signing (UTF-8 byte identity matters). */
    fun canonicalString(element: JsonElement): String {
        val out = StringBuilder()
        writeElement(out, element)
        return out.toString()
    }

    private val StrictJson =
        Json {
            ignoreUnknownKeys = false
            isLenient = false
        }

    // Note: these helpers intentionally do NOT use the name `append(JsonElement)`
    // because `java.lang.StringBuilder.append(Object)` is a member of StringBuilder
    // on the JVM, and member functions shadow extensions — the canonicalizer
    // would silently fall back to `JsonElement.toString()` (which preserves
    // insertion order and bypasses RFC 8785 altogether). Giving the helpers
    // distinct names avoids the collision entirely.

    private fun writeElement(
        out: StringBuilder,
        element: JsonElement,
    ) {
        when (element) {
            is JsonNull -> out.append("null")
            is JsonPrimitive -> writePrimitive(out, element)
            is JsonArray -> writeArray(out, element)
            is JsonObject -> writeObject(out, element)
        }
    }

    private fun writePrimitive(
        out: StringBuilder,
        p: JsonPrimitive,
    ) {
        if (p.isString) {
            writeEscapedString(out, p.content)
        } else {
            out.append(p.content)
        }
    }

    private fun writeArray(
        out: StringBuilder,
        a: JsonArray,
    ) {
        out.append('[')
        for ((index, value) in a.withIndex()) {
            if (index > 0) out.append(',')
            writeElement(out, value)
        }
        out.append(']')
    }

    private fun writeObject(
        out: StringBuilder,
        o: JsonObject,
    ) {
        out.append('{')
        // RFC 8785 §3.2.3 mandates UTF-16 code-unit ordering for member names.
        // Kotlin's natural String order is exactly UTF-16 code-unit order,
        // matching the specification.
        val sortedKeys = o.keys.sorted()
        for ((index, key) in sortedKeys.withIndex()) {
            if (index > 0) out.append(',')
            writeEscapedString(out, key)
            out.append(':')
            writeElement(out, o.getValue(key))
        }
        out.append('}')
    }

    private fun writeEscapedString(
        out: StringBuilder,
        s: String,
    ) {
        out.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> {
                    out.append("\\\"")
                }

                '\\' -> {
                    out.append("\\\\")
                }

                '\b' -> {
                    out.append("\\b")
                }

                '\u000C' -> {
                    out.append("\\f")
                }

                '\n' -> {
                    out.append("\\n")
                }

                '\r' -> {
                    out.append("\\r")
                }

                '\t' -> {
                    out.append("\\t")
                }

                else -> {
                    if (ch.code < 0x20) {
                        out.append("\\u")
                        out.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(ch)
                    }
                }
            }
        }
        out.append('"')
    }
}
