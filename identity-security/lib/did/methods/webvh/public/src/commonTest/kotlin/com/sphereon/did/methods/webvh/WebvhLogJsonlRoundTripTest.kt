/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh

import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.did.methods.webvh.log.WebvhLogReader
import com.sphereon.did.methods.webvh.log.WebvhLogWriter
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.model.WebvhParameters
import com.sphereon.did.models.DidDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebvhLogJsonlRoundTripTest {
    private fun proof(): DataIntegrityProof =
        DataIntegrityProof(
            cryptosuite = "eddsa-jcs-2022",
            proofPurpose = ProofPurpose.ASSERTION_METHOD,
            verificationMethod = "did:webvh:Qm:example.com#key-1",
            proofValue = "z000000000000000000000000000000000000000000000000000",
            created = "2026-04-30T12:00:00Z",
        )

    private fun entry(versionId: String): WebvhLogEntry =
        WebvhLogEntry(
            versionId = versionId,
            versionTime = "2026-04-30T12:00:00Z",
            parameters =
                WebvhParameters(
                    method = "did:webvh:1.0",
                    scid = "QmExampleSCIDvalue000000000000000000000000000",
                    updateKeys = listOf("z6MkExample"),
                ),
            state = DidDocument(id = "did:webvh:QmExampleSCIDvalue000000000000000000000000000:example.com"),
            proof = listOf(proof()),
        )

    @Test
    fun writerEmitsOneLinePerEntryTerminatedByNewline() {
        val jsonl = WebvhLogWriter.write(listOf(entry("1-Qmaaa"), entry("2-Qmbbb")))
        val lines = jsonl.split('\n')
        // Trailing \n yields an empty final element.
        assertEquals(3, lines.size)
        assertTrue(lines[0].isNotEmpty())
        assertTrue(lines[1].isNotEmpty())
        assertTrue(lines[2].isEmpty())
    }

    @Test
    fun writerThenReaderPreservesEntries() {
        val original = listOf(entry("1-Qmaaa"), entry("2-Qmbbb"))
        val jsonl = WebvhLogWriter.write(original)
        val read = WebvhLogReader.read(jsonl)
        assertTrue(read.isOk, "read should succeed: ${if (read.isErr) read.error.message else ""}")
        assertEquals(original, read.value)
    }

    @Test
    fun readerSkipsBlankLines() {
        val original = listOf(entry("1-Qmaaa"))
        val jsonl = "\n" + WebvhLogWriter.write(original) + "   \n\n"
        val read = WebvhLogReader.read(jsonl)
        assertTrue(read.isOk)
        assertEquals(1, read.value.size)
    }

    @Test
    fun readerReturnsErrorOnMalformedLine() {
        val read = WebvhLogReader.read("not-json\n")
        assertTrue(read.isErr)
        assertEquals("PARSING_ERROR", read.error.code)
    }
}
