/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.jsonld.rdfcanon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RdfDatasetCanonicalizerTest {
    @Test
    fun parsesAndSerializesCanonicalNQuads() {
        val input = "_:z <http://example.com/p> \"line\\nvalue\" .\n"
        val result = RdfDatasetCanonicalizer().canonicalize(RdfNQuads.parse(input))
        assertEquals("_:c14n0 <http://example.com/p> \"line\\nvalue\" .\n", result)
    }

    @Test
    fun canonicalizesIsomorphicBlankNodeGraphs() {
        val first = RdfNQuads.parse(
            "_:a <http://example.com/p> _:b .\n" +
                "_:b <http://example.com/q> \"value\" .\n",
        )
        val second = RdfNQuads.parse(
            "_:x <http://example.com/p> _:y .\n" +
                "_:y <http://example.com/q> \"value\" .\n",
        )
        assertEquals(RdfDatasetCanonicalizer().canonicalize(first), RdfDatasetCanonicalizer().canonicalize(second))
    }

    @Test
    fun blankNodeNamedGraphIsCanonicalized() {
        val result = RdfDatasetCanonicalizer().canonicalize(
            RdfNQuads.parse("<http://example.com/s> <http://example.com/p> <http://example.com/o> _:graph .\n"),
        )
        assertEquals("<http://example.com/s> <http://example.com/p> <http://example.com/o> _:c14n0 .\n", result)
    }

    @Test
    fun duplicateQuadsAreSetSemantics() {
        val result = RdfDatasetCanonicalizer().canonicalize(
            RdfNQuads.parse(
                "<https://www.example.org/s> <https://www.example.org/p> <https://www.example.org/o> .\n" +
                    "<https://www.example.org/s> <https://www.example.org/p> <https://www.example.org/o> .\n",
            ),
        )
        assertEquals("<https://www.example.org/s> <https://www.example.org/p> <https://www.example.org/o> .\n", result)
    }

    @Test
    fun quotedTriplesFailClosed() {
        assertFailsWith<RdfNQuadsParseException> {
            RdfNQuads.parse("<< <http://example.com/s> <http://example.com/p> <http://example.com/o> >> <http://example.com/p> \"x\" .\n")
        }
    }

    @Test
    fun permutationLimitFailsClosed() {
        // Two indistinguishable centers keep both stars in the n-degree step.
        // Their related blank-node buckets exceed the limit even leaf-first.
        val dataset = RdfDataset(
            (0 until 2).flatMap { component ->
                (0 until 13).map { leaf ->
                    RdfQuad(
                        RdfBlankNode("center$component"),
                        RdfIri("http://example.com/p"),
                        RdfBlankNode("leaf${component}_$leaf"),
                    )
                }
            },
        )
        val error = assertFailsWith<RdfCanonicalizationException> {
            RdfDatasetCanonicalizer(RdfCanonicalizationLimits(maxPermutationItems = 2)).canonicalize(dataset)
        }
        assertEquals("RDFC-1.0 permutation item limit exceeded", error.message)
    }

    @Test
    fun disconnectedBlankNodesDoNotConsumePermutationItems() {
        val dataset = RdfDataset(
            (0 until 13).map { index ->
                RdfQuad(RdfBlankNode("b$index"), RdfIri("http://example.com/p"), RdfLiteral("same"))
            },
        )
        val result = RdfDatasetCanonicalizer(RdfCanonicalizationLimits(maxPermutationItems = 2))
            .canonicalizeDetailed(dataset)
        assertEquals(13, result.issuedIdentifiers.size)
        assertEquals(13, result.canonicalNQuads.lines().count { it.isNotBlank() })
    }
}
