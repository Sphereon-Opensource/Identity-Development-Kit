/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.jsonld.rdfcanon

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash

/** Deterministic resource limits for defending the n-degree permutation step. */
data class RdfCanonicalizationLimits(
    val maxQuads: Int = 100_000,
    val maxBlankNodes: Int = 10_000,
    val maxPermutations: Long = 100_000,
    val maxPermutationItems: Int = 12,
    val maxRecursionDepth: Int = 128,
    val maxCanonicalBytes: Int = 16 * 1024 * 1024,
) {
    init {
        require(maxQuads > 0 && maxBlankNodes > 0 && maxPermutations > 0 && maxPermutationItems > 0)
        require(maxRecursionDepth > 0 && maxCanonicalBytes > 0)
    }
}

class RdfCanonicalizationException(message: String) : IllegalArgumentException(message)

data class RdfCanonicalizationResult(
    val canonicalNQuads: String,
    /** Input blank-node identifiers (without the `_:` serialization prefix) to canonical IDs. */
    val issuedIdentifiers: Map<String, String>,
)

/**
 * W3C RDF Dataset Canonicalization 1.0 (RDFC-1.0), SHA-256 profile.
 *
 * The implementation follows sections 4.4 through 4.8 of the Recommendation:
 * first-degree hashes identify uncomplicated nodes, related hashes encode the
 * incident position and predicate, and n-degree hashes exhaustively compare
 * issuer permutations. The input model is RDF 1.1, so RDF-star quoted triples
 * are rejected explicitly rather than silently applying an undefined extension.
 */
class RdfDatasetCanonicalizer(
    private val limits: RdfCanonicalizationLimits = RdfCanonicalizationLimits(),
    private val digestAlgorithm: DigestAlg = DigestAlg.SHA256,
) {
    init {
        require(digestAlgorithm == DigestAlg.SHA256 || digestAlgorithm == DigestAlg.SHA384) {
            "RDFC-1.0 supports SHA-256 and SHA-384"
        }
    }

    fun canonicalize(dataset: RdfDataset): String = canonicalizeDetailed(dataset).canonicalNQuads

    fun canonicalizeDetailed(dataset: RdfDataset): RdfCanonicalizationResult {
        val quads = dataset.distinctQuads
        if (quads.size > limits.maxQuads) fail("Dataset has ${quads.size} quads; limit is ${limits.maxQuads}")
        ensureSupported(quads)
        val identifiers = linkedSetOf<String>()
        for (quad in quads) {
            identifiers += blankIdentifiers(quad.subject)
            identifiers += blankIdentifiers(quad.objectTerm)
            quad.graphName?.let { identifiers += blankIdentifiers(it) }
        }
        if (identifiers.size > limits.maxBlankNodes) fail("Dataset has ${identifiers.size} blank nodes; limit is ${limits.maxBlankNodes}")

        val quadsByBlankNode = identifiers.associateWith { id ->
            quads.filter { id in blankIdentifiers(it.subject) || id in blankIdentifiers(it.objectTerm) || (it.graphName != null && id in blankIdentifiers(it.graphName)) }
        }
        val firstDegree = identifiers.associateWith { id -> hashFirstDegree(id, quadsByBlankNode.getValue(id)) }
        val hashToIdentifiers = firstDegree.entries.groupBy({ it.value }, { it.key })
        val canonicalIssuer = IdentifierIssuer("c14n")

        // Section 4.4.3 step 4: immediately issue nodes with unique first-degree hashes.
        for ((_, ids) in hashToIdentifiers.entries.sortedWith { a, b -> compareCodePoints(a.key, b.key) }) {
            if (ids.size == 1) canonicalIssuer.issue(ids.single())
        }

        var permutations = 0L
        for ((_, ids) in hashToIdentifiers.entries.sortedWith { a, b -> compareCodePoints(a.key, b.key) }) {
            if (ids.size <= 1) continue
            val hashPaths = mutableListOf<NDegreeResult>()
            for (id in ids) {
                if (canonicalIssuer.hasId(id)) continue
                val issuer = IdentifierIssuer("b")
                issuer.issue(id)
                hashPaths += hashNDegree(id, issuer, quadsByBlankNode, firstDegree, canonicalIssuer, 0) { permutations++ ; if (permutations > limits.maxPermutations) fail("RDFC-1.0 permutation limit exceeded") }
            }
            for (result in hashPaths.sortedWith { a, b -> compareCodePoints(a.hash, b.hash) }) {
                for (inputId in result.issuer.issued.keys) {
                    if (!canonicalIssuer.hasId(inputId)) canonicalIssuer.issue(inputId)
                }
            }
        }

        val labels = canonicalIssuer.issued
        if (labels.size != identifiers.size) fail("Canonicalization did not issue an identifier for every blank node")
        val canonical = quads.map { quad -> RdfNQuads.canonical(quad) { id -> "_:${labels[id] ?: fail("Unknown blank node $id")}" } }
            .sortedWith(::compareCodePoints)
            .joinToString("")
        if (canonical.encodeToByteArray().size > limits.maxCanonicalBytes) fail("Canonical N-Quads exceeds byte limit")
        return RdfCanonicalizationResult(canonical, labels.toMap())
    }

    private fun hashFirstDegree(identifier: String, quads: List<RdfQuad>): String {
        val serializations = quads.map { quad ->
            RdfNQuads.canonical(quad) { id -> if (id == identifier) "_:a" else "_:z" }
        }.sortedWith(::compareCodePoints)
        return digest(serializations.joinToString(""))
    }

    private fun hashRelated(
        related: String,
        quad: RdfQuad,
        issuer: IdentifierIssuer,
        canonicalIssuer: IdentifierIssuer,
        position: Char,
        firstDegree: Map<String, String>,
    ): String {
        val input = StringBuilder().append(position)
        if (position != 'g') input.append('<').append(quad.predicate.value).append('>')
        val known = canonicalIssuer.identifierFor(related) ?: issuer.identifierFor(related)
        if (known != null) input.append("_:").append(known) else input.append(firstDegree.getValue(related))
        return digest(input.toString())
    }

    private fun hashNDegree(
        identifier: String,
        issuer: IdentifierIssuer,
        quadsByBlankNode: Map<String, List<RdfQuad>>,
        firstDegree: Map<String, String>,
        canonicalIssuer: IdentifierIssuer,
        depth: Int,
        countPermutation: () -> Unit,
    ): NDegreeResult {
        if (depth > limits.maxRecursionDepth) fail("RDFC-1.0 recursion limit exceeded")
        val related = mutableMapOf<String, MutableList<String>>()
        for (quad in quadsByBlankNode.getValue(identifier)) {
            relatedComponents(quad, identifier).forEach { (id, position) ->
                val relatedHash = hashRelated(id, quad, issuer, canonicalIssuer, position, firstDegree)
                related.getOrPut(relatedHash) { mutableListOf() }.add(id)
            }
        }
        val data = StringBuilder()
        var selectedIssuer = issuer
        for ((relatedHash, candidates) in related.entries.sortedWith { a, b -> compareCodePoints(a.key, b.key) }) {
            data.append(relatedHash)
            if (candidates.size > limits.maxPermutationItems) fail("RDFC-1.0 permutation item limit exceeded")
            var chosenPath: String? = null
            var chosenIssuer: IdentifierIssuer? = null
            permutationLoop@ for (permutation in permutations(candidates, countPermutation)) {
                var issuerCopy = selectedIssuer.copy()
                val path = StringBuilder()
                val recursion = mutableListOf<String>()
                for (candidate in permutation) {
                    val canonicalId = canonicalIssuer.identifierFor(candidate)
                    if (canonicalId != null) {
                        path.append("_:").append(canonicalId)
                    } else {
                        if (!issuerCopy.hasId(candidate)) recursion += candidate
                        path.append("_:").append(issuerCopy.issue(candidate))
                    }
                    if (chosenPath != null && path.length >= chosenPath!!.length && compareCodePoints(path.toString(), chosenPath!!) > 0) {
                        continue@permutationLoop
                    }
                }
                for (candidate in recursion) {
                    val nested = hashNDegree(candidate, issuerCopy, quadsByBlankNode, firstDegree, canonicalIssuer, depth + 1, countPermutation)
                    path.append("_:").append(issuerCopy.issue(candidate))
                    path.append('<').append(nested.hash).append('>')
                    // Nested issuer is intentionally threaded through the path's issuer.
                    issuerCopy = nested.issuer
                    if (chosenPath != null && path.length >= chosenPath!!.length && compareCodePoints(path.toString(), chosenPath!!) > 0) {
                        continue@permutationLoop
                    }
                }
                val pathString = path.toString()
                if (chosenPath == null || compareCodePoints(pathString, chosenPath!!) < 0) {
                    chosenPath = pathString
                    chosenIssuer = issuerCopy
                }
            }
            data.append(chosenPath ?: fail("No issuer permutation produced a path"))
            selectedIssuer = chosenIssuer ?: selectedIssuer
        }
        return NDegreeResult(digest(data.toString()), selectedIssuer)
    }

    private fun permutations(values: List<String>, count: () -> Unit): Sequence<List<String>> = sequence {
        val used = BooleanArray(values.size)
        val current = ArrayList<String>(values.size)
        suspend fun SequenceScope<List<String>>.visit() {
            if (current.size == values.size) {
                count(); yield(current.toList()); return
            }
            for (i in values.indices) if (!used[i]) {
                used[i] = true; current += values[i]
                visit()
                current.removeAt(current.lastIndex); used[i] = false
            }
        }
        visit()
    }

    private fun relatedComponents(quad: RdfQuad, identifier: String): List<Pair<String, Char>> = buildList {
        blankIdentifiers(quad.subject).filter { it != identifier }.forEach { add(it to 's') }
        blankIdentifiers(quad.objectTerm).filter { it != identifier }.forEach { add(it to 'o') }
        quad.graphName?.let { blankIdentifiers(it).filter { id -> id != identifier }.forEach { add(it to 'g') } }
    }

    private fun blankIdentifiers(term: RdfTerm): Set<String> = when (term) {
        is RdfBlankNode -> setOf(term.identifier)
        is RdfQuotedTriple -> throw RdfCanonicalizationException("RDFC-1.0 does not support quoted triples")
        else -> emptySet()
    }

    private fun ensureSupported(quads: List<RdfQuad>) {
        quads.forEach {
            if (it.predicate.value.isEmpty()) fail("Predicate IRI must not be empty")
            if (it.subject is RdfQuotedTriple || it.objectTerm is RdfQuotedTriple) fail("RDFC-1.0 does not support quoted triples")
        }
    }

    private fun digest(value: String): String = bytesToHex(hash(value.encodeToByteArray(), digestAlgorithm))

    private fun fail(message: String): Nothing = throw RdfCanonicalizationException(message)

    private data class NDegreeResult(val hash: String, val issuer: IdentifierIssuer)

    private class IdentifierIssuer(private val prefix: String, private var counter: Int = 0, val issued: LinkedHashMap<String, String> = linkedMapOf()) {
        fun hasId(input: String): Boolean = input in issued
        fun identifierFor(input: String): String? = issued[input]
        fun issue(input: String): String = issued.getOrPut(input) { "$prefix${counter++}" }
        fun copy(): IdentifierIssuer = IdentifierIssuer(prefix, counter, LinkedHashMap(issued))
    }

    companion object {
        private fun bytesToHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
            for (b in bytes) append("0123456789abcdef"[(b.toInt() ushr 4) and 0xf]).append("0123456789abcdef"[b.toInt() and 0xf])
        }

        /** Unicode code-point order (UTF-8 byte order is equivalent). */
        private fun compareCodePoints(a: String, b: String): Int {
            val aa = a.encodeToByteArray(); val bb = b.encodeToByteArray()
            for (i in 0 until minOf(aa.size, bb.size)) {
                val x = aa[i].toInt() and 0xff; val y = bb[i].toInt() and 0xff
                if (x != y) return x - y
            }
            return aa.size - bb.size
        }

    }
}
