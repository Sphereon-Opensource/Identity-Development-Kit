package com.sphereon.jsonld.processor

import com.sphereon.core.api.Ok
import com.sphereon.jsonld.LinkedDataDocument
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import com.sphereon.jsonld.rdfcanon.RdfNQuads
import com.sphereon.jsonld.rdfcanon.RdfDatasetCanonicalizer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Offline checks against the vendored W3C JSON-LD API toRdf vectors. */
class JsonLdProcessorOfficialCorpusTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val loader = LinkedDataDocumentLoader { iri ->
        Ok(LinkedDataDocument(iri, json.parseToJsonElement("{}")))
    }

    @Test
    fun representativeToRdfVectorsMatch() = runTest {
        for (id in listOf("0001", "0002", "0003", "0004", "0015", "0026")) {
            val input = resource("w3c-json-ld-api-ffdb326/toRdf/$id-in.jsonld")
            val expected = resource("w3c-json-ld-api-ffdb326/toRdf/$id-out.nq")
            val actual = JsonLdProcessor(loader).toRdf(json.parseToJsonElement(input))
            val actualCanonical = RdfDatasetCanonicalizer().canonicalize(actual)
            val expectedCanonical = RdfDatasetCanonicalizer().canonicalize(RdfNQuads.parse(expected))
            assertEquals(expectedCanonical, actualCanonical, "W3C toRdf vector $id")
        }
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "missing vendored resource $name" }
            .bufferedReader()
            .use { it.readText() }
}
