/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.jsonld.rdfcanon

/** Strict N-Quads parser and canonical N-Quads serializer used by RDFC-1.0. */
object RdfNQuads {
    fun parse(document: String): RdfDataset {
        val quads = mutableListOf<RdfQuad>()
        var lineStart = 0
        var lineNumber = 1
        var index = 0
        while (index <= document.length) {
            if (index == document.length || document[index] == '\n' || document[index] == '\r') {
                val line = document.substring(lineStart, index)
                var first = 0
                while (first < line.length && line[first] in charArrayOf(' ', '\t')) first++
                if (first < line.length && line[first] != '#') quads += parseLine(line, lineNumber)
                if (index < document.length && document[index] == '\r' && index + 1 < document.length && document[index + 1] == '\n') index++
                lineStart = index + 1
                lineNumber++
            }
            index++
        }
        return RdfDataset(quads)
    }

    fun canonical(quad: RdfQuad, blankNode: (String) -> String = { "_:$it" }): String = buildString {
        append(term(quad.subject, blankNode)).append(' ').append(iri(quad.predicate.value)).append(' ')
            .append(term(quad.objectTerm, blankNode))
        if (quad.graphName != null) append(' ').append(term(quad.graphName, blankNode))
        append(" .\n")
    }

    private fun term(term: RdfTerm, blankNode: (String) -> String): String = when (term) {
        is RdfIri -> iri(term.value)
        is RdfBlankNode -> {
            require(isValidBlankNodeLabel(term.identifier)) { "Invalid RDF blank node identifier" }
            val serialized = blankNode(term.identifier)
            require(serialized.startsWith("_:") && isValidBlankNodeLabel(serialized.substring(2))) {
                "Blank node serializer returned an invalid label"
            }
            serialized
        }
        is RdfLiteral -> literal(term)
        is RdfQuotedTriple -> throw RdfCanonicalizationException("RDFC-1.0 does not support quoted triples")
    }

    private fun iri(value: String): String {
        require(isAbsoluteIri(value)) { "An RDF IRI must be absolute" }
        return "<${escapeIri(value)}>"
    }

    private fun literal(value: RdfLiteral): String = buildString {
        append('"').append(escapeString(value.lexicalForm)).append('"')
        when {
            value.language != null -> {
                require(isValidLanguageTag(value.language)) { "Invalid RDF language tag" }
                append('@').append(value.language)
            }
            value.datatype != null && value.datatype != RdfVocab.XSD_STRING -> append("^^").append(iri(value.datatype))
        }
    }

    private fun escapeIri(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (isSurrogatePairAt(value, index)) {
                append(ch).append(value[index + 1]); index += 2; continue
            }
            if (ch.code in 0xd800..0xdfff) throw IllegalArgumentException("An RDF IRI must contain valid Unicode scalar values")
            when {
                ch == '\\' -> append("\\u005c")
                ch == '<' -> append("\\u003C")
                ch == '>' -> append("\\u003E")
                ch == '"' -> append("\\u0022")
                ch == '{' -> append("\\u007B")
                ch == '}' -> append("\\u007D")
                ch == '|' -> append("\\u007C")
                ch == '^' -> append("\\u005E")
                ch == '`' -> append("\\u0060")
                ch.code < 0x20 || ch.code in 0x7f..0x9f -> append("\\u${ch.code.toString(16).padStart(4, '0').uppercase()}")
                else -> append(ch)
            }
            index++
        }
    }

    private fun escapeString(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (isSurrogatePairAt(value, index)) {
                append(ch).append(value[index + 1]); index += 2; continue
            }
            if (ch.code in 0xd800..0xdfff) throw IllegalArgumentException("An RDF literal must contain valid Unicode scalar values")
            when (ch) {
                '\b' -> append("\\b"); '\t' -> append("\\t"); '\n' -> append("\\n")
                '\u000C' -> append("\\f"); '\r' -> append("\\r"); '"' -> append("\\\""); '\\' -> append("\\\\")
                else -> if (ch.code < 0x20 || ch.code == 0x7f) append("\\u${ch.code.toString(16).padStart(4, '0').uppercase()}") else append(ch)
            }
            index++
        }
    }

    private fun isSurrogatePairAt(value: String, index: Int): Boolean =
        index >= 0 && index + 1 < value.length && value[index].code in 0xd800..0xdbff && value[index + 1].code in 0xdc00..0xdfff

    private class Cursor(val line: String, val lineNumber: Int) {
        var index = 0
        fun skipSpace() { while (index < line.length && (line[index] == ' ' || line[index] == '\t')) index++ }
        fun atEnd() = index >= line.length
        fun take(expected: Char) { if (atEnd() || line[index] != expected) fail("Expected '$expected'") else index++ }
        fun fail(message: String): Nothing = throw RdfNQuadsParseException("Line $lineNumber: $message")
    }

    private fun parseLine(line: String, lineNumber: Int): RdfQuad {
        val c = Cursor(line, lineNumber)
        c.skipSpace(); val subject = parseSubject(c)
        c.skipSpace(); val predicate = parseIri(c)
        c.skipSpace(); val objectTerm = parseObject(c)
        c.skipSpace(); val graph = if (!c.atEnd() && line[c.index] != '.') parseGraph(c) else null
        c.skipSpace(); c.take('.')
        c.skipSpace(); if (!c.atEnd() && line[c.index] != '#') c.fail("Unexpected trailing content")
        return RdfQuad(subject, predicate, objectTerm, graph)
    }

    private fun parseSubject(c: Cursor): RdfSubject = when {
        c.line.getOrNull(c.index) == '<' -> parseIri(c)
        c.line.startsWith("_:", c.index) -> parseBlank(c)
        c.line.startsWith("<<", c.index) -> throw c.fail("Quoted triples are not supported by RDFC-1.0")
        else -> c.fail("Expected IRI or blank node subject")
    }

    private fun parseObject(c: Cursor): RdfObject = when {
        c.line.getOrNull(c.index) == '<' -> parseIri(c)
        c.line.startsWith("_:", c.index) -> parseBlank(c)
        c.line.getOrNull(c.index) == '"' -> parseLiteral(c)
        c.line.startsWith("<<", c.index) -> throw c.fail("Quoted triples are not supported by RDFC-1.0")
        else -> c.fail("Expected RDF object")
    }

    private fun parseGraph(c: Cursor): RdfGraphName = when {
        c.line.getOrNull(c.index) == '<' -> parseIri(c)
        c.line.startsWith("_:", c.index) -> parseBlank(c)
        else -> c.fail("Expected graph IRI or blank node")
    }

    private fun parseIri(c: Cursor): RdfIri {
        c.take('<'); val start = c.index
        while (!c.atEnd() && c.line[c.index] != '>') c.index++
        if (c.atEnd()) c.fail("Unterminated IRI")
        val value = unescapeIri(c.line.substring(start, c.index), c); c.index++
        if (value.isEmpty()) c.fail("An IRI must not be empty")
        if (!isAbsoluteIri(value)) c.fail("An IRI must be absolute")
        return RdfIri(value)
    }

    private fun parseBlank(c: Cursor): RdfBlankNode {
        c.take('_'); c.take(':'); val start = c.index
        while (!c.atEnd() && c.line[c.index] !in charArrayOf(' ', '\t', '#', '<', '"')) c.index++
        var labelEnd = c.index
        if (labelEnd > start && c.line[labelEnd - 1] == '.') labelEnd--
        if (start == labelEnd) c.fail("Empty blank node identifier")
        val label = c.line.substring(start, labelEnd)
        validateBlankNodeLabel(label, c); c.index = labelEnd
        return RdfBlankNode(label)
    }

    private fun parseLiteral(c: Cursor): RdfLiteral {
        c.take('"'); val value = StringBuilder()
        while (!c.atEnd() && c.line[c.index] != '"') {
            if (c.line[c.index] == '\\') {
                c.index++; if (c.atEnd()) c.fail("Unterminated literal escape")
                when (c.line[c.index++]) {
                    'b' -> value.append('\b'); 't' -> value.append('\t'); 'n' -> value.append('\n')
                    'f' -> value.append('\u000C'); 'r' -> value.append('\r'); '"' -> value.append('"')
                    '\'' -> value.append('\''); '\\' -> value.append('\\')
                    'u' -> value.append(parseUnicodeEscape(c, 4)); 'U' -> value.append(parseUnicodeEscape(c, 8))
                    else -> c.fail("Invalid literal escape")
                }
            } else {
                val code = c.line[c.index].code
                if (code == '\n'.code || code == '\r'.code) c.fail("Literal may not contain an unescaped line break")
                if (code in 0xd800..0xdbff) {
                    if (!isSurrogatePairAt(c.line, c.index)) c.fail("Invalid Unicode surrogate in literal")
                    value.append(c.line[c.index]).append(c.line[c.index + 1]); c.index += 2
                } else if (code in 0xdc00..0xdfff) c.fail("Invalid Unicode surrogate in literal")
                else { value.append(c.line[c.index]); c.index++ }
            }
        }
        c.take('"')
        val language: String?; val datatype: String?
        when {
            c.line.getOrNull(c.index) == '@' -> { language = parseLanguageTag(c); datatype = null }
            c.line.startsWith("^^", c.index) -> { c.index += 2; datatype = parseIri(c).value; language = null }
            else -> { language = null; datatype = null }
        }
        return RdfLiteral(value.toString(), language, datatype)
    }

    private fun parseLanguageTag(c: Cursor): String {
        c.take('@'); val start = c.index
        if (c.atEnd() || !isAsciiLetter(c.line[c.index])) c.fail("Language tag must start with a letter")
        while (!c.atEnd() && isAsciiLetter(c.line[c.index])) c.index++
        while (c.line.getOrNull(c.index) == '-') {
            c.index++; val subtagStart = c.index
            while (!c.atEnd() && isAsciiLetterOrDigit(c.line[c.index])) c.index++
            if (subtagStart == c.index) c.fail("Language tag subtags must not be empty")
        }
        if (!c.atEnd() && c.line[c.index] !in charArrayOf(' ', '\t', '.', '#')) c.fail("Invalid language tag")
        return c.line.substring(start, c.index)
    }

    private fun parseUnicodeEscape(c: Cursor, count: Int): String {
        if (c.index + count > c.line.length) c.fail("Truncated Unicode escape")
        val raw = c.line.substring(c.index, c.index + count)
        if (!raw.all { it in "0123456789abcdefABCDEF" }) c.fail("Invalid Unicode escape")
        c.index += count; val codePoint = raw.toLong(16).toInt()
        if (codePoint !in 0..0x10ffff || codePoint in 0xd800..0xdfff) c.fail("Invalid Unicode code point")
        return codePointToString(codePoint)
    }

    private fun unescapeIri(value: String, c: Cursor): String {
        val out = StringBuilder(); var index = 0
        while (index < value.length) {
            if (value[index] != '\\') {
                val code = value[index].code
                if (code in 0xd800..0xdbff) {
                    if (!isSurrogatePairAt(value, index)) c.fail("IRI contains an invalid Unicode surrogate")
                    out.append(value[index]).append(value[index + 1]); index += 2; continue
                }
                if (code in 0xdc00..0xdfff) c.fail("IRI contains an invalid Unicode surrogate")
                if (isForbiddenIriCharacter(code, value[index])) c.fail("IRI contains a forbidden character")
                out.append(value[index++])
            } else {
                index++; if (index >= value.length) c.fail("Invalid IRI escape")
                when (value[index++]) {
                    'u' -> {
                        if (index + 4 > value.length) c.fail("Truncated IRI Unicode escape")
                        val codePoint = value.substring(index, index + 4).toLongOrNull(16)?.toInt() ?: c.fail("Invalid IRI Unicode escape")
                        if (codePoint in 0xd800..0xdfff) c.fail("Invalid IRI Unicode code point")
                        out.append(codePointToString(codePoint)); index += 4
                    }
                    'U' -> {
                        if (index + 8 > value.length) c.fail("Truncated IRI Unicode escape")
                        val codePoint = value.substring(index, index + 8).toLongOrNull(16)?.toInt() ?: c.fail("Invalid IRI Unicode escape")
                        if (codePoint !in 0..0x10ffff || codePoint in 0xd800..0xdfff) c.fail("Invalid IRI code point")
                        out.append(codePointToString(codePoint)); index += 8
                    }
                    else -> c.fail("IRI may only use Unicode escapes")
                }
            }
        }
        return out.toString()
    }

    private fun validateBlankNodeLabel(label: String, c: Cursor) {
        var index = 0
        while (index < label.length) {
            val (codePoint, width) = codePointAt(label, index, c)
            val valid = if (index == 0) isPnCharsU(codePoint) || isAsciiDigit(codePoint) else isPnChars(codePoint) || codePoint == '.'.code
            if (!valid) c.fail("Invalid blank node label")
            index += width
        }
        if (label.lastOrNull() == '.') c.fail("Blank node label must not end with a dot")
    }

    private fun isValidBlankNodeLabel(label: String): Boolean {
        if (label.isEmpty() || label.last() == '.') return false
        var index = 0
        while (index < label.length) {
            val code = label[index].code
            val codePoint: Int
            val width: Int
            when {
                code in 0xd800..0xdbff && index + 1 < label.length && label[index + 1].code in 0xdc00..0xdfff -> {
                    codePoint = 0x10000 + ((code - 0xd800) shl 10) + (label[index + 1].code - 0xdc00); width = 2
                }
                code in 0xd800..0xdfff -> return false
                else -> { codePoint = code; width = 1 }
            }
            val valid = if (index == 0) isPnCharsU(codePoint) || isAsciiDigit(codePoint) else isPnChars(codePoint) || codePoint == '.'.code
            if (!valid) return false
            index += width
        }
        return true
    }

    private fun codePointAt(value: String, index: Int, c: Cursor): Pair<Int, Int> {
        val code = value[index].code
        if (code in 0xd800..0xdbff) {
            if (index + 1 >= value.length || value[index + 1].code !in 0xdc00..0xdfff) c.fail("Invalid Unicode surrogate")
            return Pair(0x10000 + ((code - 0xd800) shl 10) + (value[index + 1].code - 0xdc00), 2)
        }
        if (code in 0xdc00..0xdfff) c.fail("Invalid Unicode surrogate")
        return Pair(code, 1)
    }

    private fun isPnCharsBase(codePoint: Int): Boolean = codePoint in 'A'.code..'Z'.code || codePoint in 'a'.code..'z'.code ||
        codePoint in 0x00c0..0x00d6 || codePoint in 0x00d8..0x00f6 || codePoint in 0x00f8..0x02ff ||
        codePoint in 0x0370..0x037d || codePoint in 0x037f..0x1fff || codePoint in 0x200c..0x200d ||
        codePoint in 0x2070..0x218f || codePoint in 0x2c00..0x2fef || codePoint in 0x3001..0xd7ff ||
        codePoint in 0xf900..0xfdcf || codePoint in 0xfdf0..0xfffd || codePoint in 0x10000..0xeffff
    private fun isPnCharsU(codePoint: Int): Boolean = isPnCharsBase(codePoint) || codePoint == '_'.code
    private fun isPnChars(codePoint: Int): Boolean = isPnCharsU(codePoint) || codePoint == '-'.code || isAsciiDigit(codePoint) ||
        codePoint == 0x00b7 || codePoint in 0x0300..0x036f || codePoint in 0x203f..0x2040
    private fun isAsciiLetter(code: Char): Boolean = code in 'A'..'Z' || code in 'a'..'z'
    private fun isAsciiLetterOrDigit(code: Char): Boolean = isAsciiLetter(code) || code in '0'..'9'
    private fun isAsciiDigit(codePoint: Int): Boolean = codePoint in '0'.code..'9'.code
    private fun isValidLanguageTag(tag: String): Boolean {
        if (tag.isEmpty() || !isAsciiLetter(tag[0])) return false
        var index = 1
        while (index < tag.length && isAsciiLetter(tag[index])) index++
        while (index < tag.length) {
            if (tag[index++] != '-') return false
            val start = index
            while (index < tag.length && isAsciiLetterOrDigit(tag[index])) index++
            if (start == index) return false
        }
        return true
    }
    private fun isAbsoluteIri(value: String): Boolean {
        if (value.isEmpty() || !isAsciiLetter(value[0])) return false
        var index = 1
        while (index < value.length) {
            val ch = value[index]
            if (ch == ':') return true
            if (!(isAsciiLetter(ch) || ch in '0'..'9' || ch in charArrayOf('+', '-', '.'))) return false
            index++
        }
        return false
    }
    private fun isForbiddenIriCharacter(codePoint: Int, character: Char): Boolean =
        codePoint <= 0x20 || codePoint in 0x7f..0x9f || character in charArrayOf('<', '>', '"', '{', '}', '|', '^', '`', '\\')
    private fun codePointToString(codePoint: Int): String = if (codePoint <= 0xffff) codePoint.toChar().toString() else {
        val scalar = codePoint - 0x10000
        charArrayOf((0xd800 + (scalar ushr 10)).toChar(), (0xdc00 + (scalar and 0x3ff)).toChar()).concatToString()
    }
}

class RdfNQuadsParseException(message: String) : IllegalArgumentException(message)
