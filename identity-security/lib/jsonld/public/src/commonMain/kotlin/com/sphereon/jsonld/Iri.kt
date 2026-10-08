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
 */

package com.sphereon.jsonld

import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * An Internationalized Resource Identifier per RFC 3987, in the form
 *
 *     [scheme ":"] ["//" authority] path ["?" query] ["#" fragment]
 *
 * Backed by a single String. Components are computed on access; the value class
 * holds no per-instance state beyond the raw value.
 *
 * Construct via [tryParse] (RFC 3987 syntactic validation) or [unsafeOf]
 * (skip validation; caller must guarantee well-formedness, e.g. from a trusted
 * upstream parser).
 *
 * Not annotated `@JsExportCompat`: value classes can't be exported to JS
 * (they get unboxed to the underlying String at the boundary). JS callers
 * receive plain strings and can re-parse via [tryParse] if they want the
 * typed shape.
 */
@JvmInline
value class Iri private constructor(
    val value: String
) {
    /** True iff the IRI has a scheme component (i.e. it is an absolute IRI). */
    val isAbsolute: Boolean
        get() = decompose().scheme != null

    /** Scheme component, e.g. `"https"`. `null` for relative references. */
    val scheme: String?
        get() = decompose().scheme

    /**
     * Authority component, e.g. `"example.com:8080"`. `null` if the IRI has
     * no authority. An empty authority (`"file:///path"`) returns `""`.
     */
    val authority: String?
        get() = decompose().authority

    /** Path component. May be empty. */
    val path: String
        get() = decompose().path

    /** Query component without the leading `?`. `null` if absent. */
    val query: String?
        get() = decompose().query

    /** Fragment component without the leading `#`. `null` if absent. */
    val fragment: String?
        get() = decompose().fragment

    /**
     * Resolve [reference] against `this` as base, per RFC 3986 §5.3.
     *
     * If [reference] is absolute, returns it unchanged. Otherwise merges base
     * scheme, authority, and path with the reference's path, query, and
     * fragment.
     */
    fun resolve(reference: Iri): Iri {
        val refC = reference.decompose()
        if (refC.scheme != null) {
            return reference
        }
        val baseC = decompose()
        return Iri(IriComponents.recompose(merge(baseC, refC)))
    }

    /** Convenience overload: parse [reference] then [resolve]. */
    fun resolve(reference: String): Iri? = tryParse(reference)?.let { resolve(it) }

    override fun toString(): String = value

    companion object {
        /**
         * Parse [value] per RFC 3987 syntax. Returns `null` if the input is
         * not a syntactically valid IRI reference.
         *
         * Permits relative references (scheme absent). For an absolute-IRI
         * check, inspect [isAbsolute] on the result.
         */
        @JvmStatic
        fun tryParse(value: String): Iri? {
            if (!isSyntacticallyValid(value)) return null
            return Iri(value)
        }

        /**
         * Wrap [value] without validation. Callers must guarantee [value] is a
         * well-formed IRI; otherwise component accessors yield undefined
         * behaviour.
         */
        @JvmStatic
        fun unsafeOf(value: String): Iri = Iri(value)

        // RFC 3986 Appendix B: the canonical IRI/URI decomposition regex.
        // Group 2 = scheme, group 4 = authority, group 5 = path, group 7 =
        // query, group 9 = fragment. Authority is null if "//" is absent.
        private val DECOMPOSE_REGEX =
            Regex("""^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?$""")

        // RFC 3986 §3.1: scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ).
        private val SCHEME_REGEX = Regex("""^[A-Za-z][A-Za-z0-9+\-.]*$""")

        private fun isSyntacticallyValid(value: String): Boolean {
            // Reject control characters and whitespace; RFC 3987 forbids them
            // in unencoded form.
            for (ch in value) {
                val code = ch.code
                if (code <= 0x20 || code == 0x7F) return false
            }
            val match = DECOMPOSE_REGEX.matchEntire(value) ?: return false
            val scheme = match.groupValues[2]
            if (scheme.isNotEmpty() && !SCHEME_REGEX.matches(scheme)) return false
            return true
        }

        // Internal: decompose for a value already known to parse (called from
        // accessors after construction succeeded). Falls back to empty-path
        // when the regex unexpectedly does not match.
        private fun decompose(value: String): IriComponents {
            val match =
                DECOMPOSE_REGEX.matchEntire(value)
                    ?: return IriComponents(null, null, value, null, null)
            return IriComponents(
                scheme = match.groupValues[2].ifEmpty { null },
                authority = if (match.groups[3] != null) match.groupValues[4] else null,
                path = match.groupValues[5],
                query = if (match.groups[6] != null) match.groupValues[7] else null,
                fragment = if (match.groups[8] != null) match.groupValues[9] else null,
            )
        }

        // RFC 3986 §5.2.3: merge base path with reference path.
        private fun merge(
            base: IriComponents,
            ref: IriComponents
        ): IriComponents {
            // §5.2.2: if ref has authority, take ref's authority+path+query;
            // keep base's scheme.
            if (ref.authority != null) {
                return IriComponents(
                    scheme = base.scheme,
                    authority = ref.authority,
                    path = removeDotSegments(ref.path),
                    query = ref.query,
                    fragment = ref.fragment,
                )
            }
            // §5.2.2: ref has no authority.
            val mergedPath: String
            val mergedQuery: String?
            if (ref.path.isEmpty()) {
                mergedPath = base.path
                mergedQuery = ref.query ?: base.query
            } else {
                mergedPath =
                    if (ref.path.startsWith("/")) {
                        removeDotSegments(ref.path)
                    } else {
                        removeDotSegments(mergeBasePath(base, ref.path))
                    }
                mergedQuery = ref.query
            }
            return IriComponents(
                scheme = base.scheme,
                authority = base.authority,
                path = mergedPath,
                query = mergedQuery,
                fragment = ref.fragment,
            )
        }

        // RFC 3986 §5.2.3.
        private fun mergeBasePath(
            base: IriComponents,
            refPath: String
        ): String {
            // If base has authority and empty path, prepend "/" to ref.
            if (base.authority != null && base.path.isEmpty()) {
                return "/$refPath"
            }
            // Otherwise merge: take base path up to and including last "/",
            // append ref path.
            val lastSlash = base.path.lastIndexOf('/')
            return if (lastSlash >= 0) base.path.substring(0, lastSlash + 1) + refPath else refPath
        }

        // RFC 3986 §5.2.4.
        private fun removeDotSegments(input: String): String {
            if (input.isEmpty()) return input
            val output = StringBuilder()
            var remaining = input
            while (remaining.isNotEmpty()) {
                when {
                    remaining.startsWith("../") -> {
                        remaining = remaining.substring(3)
                    }

                    remaining.startsWith("./") -> {
                        remaining = remaining.substring(2)
                    }

                    remaining.startsWith("/./") -> {
                        remaining = "/" + remaining.substring(3)
                    }

                    remaining == "/." -> {
                        remaining = "/"
                    }

                    remaining.startsWith("/../") -> {
                        remaining = "/" + remaining.substring(4)
                        removeLastSegment(output)
                    }

                    remaining == "/.." -> {
                        remaining = "/"
                        removeLastSegment(output)
                    }

                    remaining == "." || remaining == ".." -> {
                        remaining = ""
                    }

                    else -> {
                        // Move first segment (up to but not including next "/"
                        // after position 0) from remaining to output.
                        val nextSlash = remaining.indexOf('/', startIndex = 1)
                        if (nextSlash < 0) {
                            output.append(remaining)
                            remaining = ""
                        } else {
                            output.append(remaining, 0, nextSlash)
                            remaining = remaining.substring(nextSlash)
                        }
                    }
                }
            }
            return output.toString()
        }

        private fun removeLastSegment(buf: StringBuilder) {
            val lastSlash = buf.lastIndexOf('/')
            if (lastSlash >= 0) buf.setLength(lastSlash) else buf.setLength(0)
        }
    }

    private fun decompose(): IriComponents = decompose(value)
}

/**
 * Parsed components of an [Iri]. Internal; consumers use the accessors on
 * [Iri] itself.
 */
internal data class IriComponents(
    val scheme: String?,
    val authority: String?,
    val path: String,
    val query: String?,
    val fragment: String?,
) {
    companion object {
        // RFC 3986 §5.3: re-compose components into a string.
        fun recompose(c: IriComponents): String {
            val sb = StringBuilder()
            if (c.scheme != null) sb.append(c.scheme).append(':')
            if (c.authority != null) sb.append("//").append(c.authority)
            sb.append(c.path)
            if (c.query != null) sb.append('?').append(c.query)
            if (c.fragment != null) sb.append('#').append(c.fragment)
            return sb.toString()
        }
    }
}
