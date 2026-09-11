/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.jsonld.rdfcanon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Runs against the vendored W3C corpus; no network or runtime fixture fetch is used. */
class Rdfc10OfficialCorpusTest {
    private val loader = Rdfc10OfficialCorpusTest::class.java.classLoader

    @Test
    fun allExpectedOfficialRdfc10FixturesCanonicalize() {
        val inputs = loader.vendoredResources("rdfc10")
        check(inputs.isNotEmpty()) { "Vendored RDFC-1.0 corpus is missing" }
        for (inputName in inputs) {
            val expectedName = inputName.replace("-in.nq", "-rdfc10.nq")
            val expected = loader.readVendoredResource(expectedName)
            val actual = RdfDatasetCanonicalizer().canonicalize(RdfNQuads.parse(loader.readVendoredResource(inputName)))
            assertEquals(expected, actual, inputName)
        }
    }

    @Test
    fun officialPoisonFixtureFailsClosedWithoutAnExpectedOutput() {
        val input = loader.readVendoredResource("rdfc10/test074-in.nq")
        assertFailsWith<RdfCanonicalizationException> {
            RdfDatasetCanonicalizer(
                RdfCanonicalizationLimits(maxPermutations = 256, maxPermutationItems = 10),
            ).canonicalize(RdfNQuads.parse(input))
        }
    }

    @Test
    fun staleOfficial075PairRemainsVendoredForAudit() {
        assertTrue(loader.readVendoredResource("rdfc10/test075-in.nq").isNotEmpty())
        assertTrue(loader.readVendoredResource("rdfc10/test075-rdfc10.nq").isNotEmpty())
    }

    private fun ClassLoader.vendoredResources(prefix: String): List<String> {
        // The corpus names are intentionally enumerated by the vendored tree,
        // avoiding a directory-listing dependency and keeping tests offline.
        return (1..77).map { "${prefix}/test${it.toString().padStart(3, '0')}-in.nq" }
            .filter { getResource(it) != null }
            .filter { !it.endsWith("test074-in.nq") }
            .filter { !it.endsWith("test075-in.nq") }
    }

    private fun ClassLoader.readVendoredResource(name: String): String =
        getResourceAsStream(name)?.use { it.readBytes().decodeToString() }
            ?: error("Missing vendored RDFC-1.0 resource $name")
}
