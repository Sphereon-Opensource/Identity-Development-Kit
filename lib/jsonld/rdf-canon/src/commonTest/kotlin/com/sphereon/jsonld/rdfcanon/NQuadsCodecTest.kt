/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.jsonld.rdfcanon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NQuadsCodecTest {
    @Test
    fun acceptsInternalDotsAndRoundTripsThroughCanonicalSerialization() {
        val parsed = RdfNQuads.parse(
            "_:node.part <https://example.com/p> \"hello\\nworld\"@en-US _:graph.part .\n",
        )

        assertEquals(
            "_:node.part <https://example.com/p> \"hello\\nworld\"@en-US _:graph.part .\n",
            RdfNQuads.canonical(parsed.quads.single()),
        )
    }

    @Test
    fun rejectsBlankNodeLabelsWithTerminalDots() {
        assertParseFails("_:node. <https://example.com/p> \"value\" .")
        assertParseFails("_:node <https://example.com/p> _:object. .")
        assertParseFails("_:node <https://example.com/p> \"value\" _:graph. .")
    }

    @Test
    fun rejectsBlankNodeLabelsWithForbiddenCharacters() {
        assertParseFails("_:node$ <https://example.com/p> \"value\" .")
        assertParseFails("_:node/part <https://example.com/p> \"value\" .")
        assertParseFails("_: node <https://example.com/p> \"value\" .")
    }

    @Test
    fun validatesLanguageTags() {
        RdfNQuads.parse("<https://example.com/s> <https://example.com/p> \"x\"@en .")
        RdfNQuads.parse("<https://example.com/s> <https://example.com/p> \"x\"@EN-latn-US .")

        assertParseFails("<https://example.com/s> <https://example.com/p> \"x\"@ .")
        assertParseFails("<https://example.com/s> <https://example.com/p> \"x\"@en_us .")
        assertParseFails("<https://example.com/s> <https://example.com/p> \"x\"@en- .")
        assertParseFails("<https://example.com/s> <https://example.com/p> \"x\"@9en .")
    }

    @Test
    fun rejectsMalformedIrisEscapesAndForbiddenCharacters() {
        assertParseFails("<https://example.com/\\x> <https://example.com/p> \"x\" .")
        assertParseFails("<https://example.com/\\u12G4> <https://example.com/p> \"x\" .")
        assertParseFails("<https://example.com/\\U00110000> <https://example.com/p> \"x\" .")
        assertParseFails("<https://example.com/\u0001> <https://example.com/p> \"x\" .")
        assertParseFails("<https://example.com/\"quote> <https://example.com/p> \"x\" .")
        assertParseFails("<> <https://example.com/p> \"x\" .")
    }

    @Test
    fun rejectsMalformedLiteralsAndEscapes() {
        assertParseFails("<https://example.com/s> <https://example.com/p> \"raw\nnewline\" .")
        assertParseFails("<https://example.com/s> <https://example.com/p> \"raw\rreturn\" .")
        assertParseFails("<https://example.com/s> <https://example.com/p> \"bad\\xescape\" .")
        assertParseFails("<https://example.com/s> <https://example.com/p> \"bad\\u12G4\" .")
        assertParseFails("<https://example.com/s> <https://example.com/p> \"bad\\U00110000\" .")
        assertParseFails("<https://example.com/s> <https://example.com/p> \"bad\\uD800\" .")
    }

    @Test
    fun commentsMayPrecedeAndFollowStatements() {
        val parsed = RdfNQuads.parse(
            "# leading comment\n" +
                "<https://example.com/s> <https://example.com/p> \"value\" . # trailing\n",
        )

        assertEquals(1, parsed.quads.size)
    }

    @Test
    fun canonicalSerializationRejectsRelativeIris() {
        val quad =
            RdfQuad(
                subject = RdfIri("relative-subject"),
                predicate = RdfIri("https://example.com/p"),
                objectTerm = RdfLiteral("value"),
            )

        assertFailsWith<IllegalArgumentException> { RdfNQuads.canonical(quad) }
    }

    private fun assertParseFails(document: String) {
        assertFailsWith<RdfNQuadsParseException> { RdfNQuads.parse(document) }
    }
}
