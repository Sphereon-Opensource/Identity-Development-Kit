package com.sphereon.did.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class DidDocumentContextTest {
    private val omittingDefaults = Json { encodeDefaults = false }

    private fun jwkMethod(did: String) = VerificationMethod(id = "$did#key-1", type = "JsonWebKey2020", controller = did)

    @Test
    fun contextIsWrittenEvenByAnEncoderThatOmitsDefaults() {
        val encoded = omittingDefaults.encodeToString(DidDocument.serializer(), DidDocument(id = "did:web:example.com"))

        val context = Json.parseToJsonElement(encoded).jsonObject.getValue("@context").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf(DidDocument.DEFAULT_CONTEXT), context)
    }

    @Test
    fun requiredContextsAddTheSuiteOfEveryVerificationMethodType() {
        val did = "did:web:example.com"
        val document =
            DidDocument(
                id = did,
                verificationMethod = listOf(jwkMethod(did)),
                assertionMethod = listOf(VerificationMethodOrReference.fromReference("$did#key-1")),
            ).withRequiredContexts()

        assertEquals(listOf(DidDocument.DEFAULT_CONTEXT, DidDocument.JWK_2020_CONTEXT), document.context)
    }

    @Test
    fun requiredContextsKeepTheDidContextFirstAndOtherContextsAfter() {
        val did = "did:web:example.com"
        val document =
            DidDocument(
                context = listOf("https://example.com/custom/v1"),
                id = did,
                verificationMethod = listOf(jwkMethod(did)),
            ).withRequiredContexts()

        assertEquals(
            listOf(DidDocument.DEFAULT_CONTEXT, DidDocument.JWK_2020_CONTEXT, "https://example.com/custom/v1"),
            document.context,
        )
    }
}
