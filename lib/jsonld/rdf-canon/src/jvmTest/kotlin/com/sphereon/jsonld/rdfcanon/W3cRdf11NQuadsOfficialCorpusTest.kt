/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.jsonld.rdfcanon

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Offline syntax conformance harness for the pinned W3C RDF 1.1 N-Quads corpus.
 *
 * The manifest declares syntax-positive and syntax-negative tests. This class
 * intentionally does not compare canonical output: N-Quads syntax conformance
 * is separate from RDF dataset evaluation and RDFC-1.0 canonicalization.
 */
class W3cRdf11NQuadsOfficialCorpusTest {
    private val loader = W3cRdf11NQuadsOfficialCorpusTest::class.java.classLoader

    @Test
    fun pinnedManifestIsCompleteAndDigestsMatch() {
        val tests = manifestTests()
        assertEquals(87, tests.size)
        assertEquals(53, tests.count { it.kind == Kind.POSITIVE })
        assertEquals(34, tests.count { it.kind == Kind.NEGATIVE })

        val digests = read("w3c-rdf11-nquads/SHA256SUMS.txt")
            .lineSequence()
            .filter { it.isNotBlank() }
            .associate {
                val fields = it.trim().split(Regex("\\s+"), limit = 2)
                assertEquals(2, fields.size, "malformed digest entry: $it")
                fields[0] to fields[1]
            }
        assertEquals(87, digests.size)

        tests.forEach { test ->
            val bytes = actionBytes(test.action)
            val expected = digests[resourceName(test.action)]
            val actual = sha256(bytes)
            // apply_patch necessarily terminates text files with LF. If the
            // upstream action had no terminal LF, verify its pinned digest
            // after removing only that transport-added byte.
            val withoutTransportLf = if (bytes.lastOrNull() == '\n'.code.toByte()) {
                sha256(bytes.copyOf(bytes.size - 1))
            } else {
                actual
            }
            assertTrue(expected == actual || expected == withoutTransportLf, test.name)
        }
    }

    @Test
    fun allManifestPositiveSyntaxActionsParse() {
        val tests = manifestTests().filter { it.kind == Kind.POSITIVE }
        tests.forEach { test ->
            try {
                RdfNQuads.parse(actionText(test.action))
            } catch (failure: Throwable) {
                throw AssertionError("W3C positive syntax case ${test.name} failed", failure)
            }
        }
    }

    @Test
    fun allManifestNegativeSyntaxActionsFailParsing() {
        val tests = manifestTests().filter { it.kind == Kind.NEGATIVE }
        tests.forEach { test ->
            assertFailsWith<RdfNQuadsParseException>("W3C negative syntax case ${test.name}") {
                RdfNQuads.parse(actionText(test.action))
            }
        }
    }

    private enum class Kind { POSITIVE, NEGATIVE }

    private data class ManifestTest(
        val name: String,
        val kind: Kind,
        val action: String,
    )

    private fun manifestTests(): List<ManifestTest> {
        val manifest = read("w3c-rdf11-nquads/manifest.ttl")
        val pattern = Regex(
            """(?s)<#([^>]+)>\s+a\s+rdft:TestNQuads(Positive|Negative)Syntax\s*;.*?mf:name\s+"([^"]+)".*?mf:action\s+<([^>]+)>""",
        )
        return pattern.findAll(manifest).map { match ->
            ManifestTest(
                name = match.groupValues[3],
                kind = if (match.groupValues[2] == "Positive") Kind.POSITIVE else Kind.NEGATIVE,
                action = match.groupValues[4],
            )
        }.toList()
    }

    private fun resourceName(action: String): String =
        if (action == "literal_ascii_boundaries.nq") "$action.b64" else action

    private fun actionBytes(action: String): ByteArray {
        val resource = resourceName(action)
        val bytes = readBytes("w3c-rdf11-nquads/$resource")
        return if (resource.endsWith(".b64")) {
            Base64.getMimeDecoder().decode(bytes.decodeToString())
        } else {
            bytes
        }
    }

    private fun actionText(action: String): String = actionBytes(action).decodeToString()

    private fun read(name: String): String = readBytes(name).decodeToString()

    private fun readBytes(name: String): ByteArray =
        loader.getResourceAsStream(name)?.use { it.readBytes() }
            ?: error("Missing pinned W3C resource $name")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
