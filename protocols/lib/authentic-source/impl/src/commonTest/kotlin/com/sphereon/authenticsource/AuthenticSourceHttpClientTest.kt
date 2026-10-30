/* Copyright 2026 Sphereon International B.V. Licensed under Apache-2.0. */
package com.sphereon.authenticsource

import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AuthenticSourceHttpClientTest {
    private val binding = SourceEndpointBinding("https://source.example/api", "provider-1", "source-1", "v1", true)
    private val tx = SourceTransactionRef("tx-1", "subject-1", "v1")
    private val ok = """{"provider":{"legalName":"Example"},"attributeVerificationResults":[{"attributeIdentifier":"urn:example:a","attributeVerificationResult":"http://uri.etsi.org/19478/VerificationResult/Match"}],"fragmentVerificationResults":[{"attributeIdentifier":"urn:example:a","fragmentVerificationResult":"http://uri.etsi.org/19478/VerificationResult/Unknown"}]}"""
    private class Owner(var reply: SourceHttpResponse) : SourceAuthorizationOwner {
        var url: String? = null; var sent: String? = null; var allowed = true
        override suspend fun authorize(binding: SourceEndpointBinding, transaction: SourceTransactionRef): IdkResult<SourceAuthorizedCall, IdkError> {
            if (!allowed) return IdkErrorResult(IdkError.fromString("denied", "DENIED"))
            return IdkResult.ok(object : SourceAuthorizedCall {
                override suspend fun postJson(url: String, body: String): IdkResult<SourceHttpResponse, IdkError> {
                    this@Owner.url = url; sent = body; return IdkResult.ok(reply)
                }
            })
        }
    }
    @Test fun verifyRetainsUnverifiedPartialAndFragmentResults() = runTest {
        val owner = Owner(SourceHttpResponse(200, ok, "application/json"))
        val result = AuthenticSourceHttpClient(DefaultAuthenticSourceWireCodec(), owner).verify(binding, tx, WireVerifyRequest(listOf(WireAttribute("urn:example:a", "{}"))))
        assertTrue(result.isOk); assertEquals("https://source.example/api/verify", owner.url)
        assertEquals(tx, result.value.requestContext)
        assertEquals("http://uri.etsi.org/19478/VerificationResult/Unknown", result.value.payload.fragmentVerificationResults!!.single().fragmentVerificationResult)
        assertNull(result.value.payload.attributeVerificationResults!!.single().attributeValueJson)
        assertTrue(owner.sent!!.contains("\"attributeValue\":{}"))
    }
    @Test fun retrieveUsesAdvertisedCapabilityAndAnnexNames() = runTest {
        val owner = Owner(SourceHttpResponse(200, """{"provider":{"legalName":"P"},"attributes":[{"attributeIdentifier":"urn:example:a","attributeValue":{"x":1}}]}""", "application/json"))
        val client = AuthenticSourceHttpClient(DefaultAuthenticSourceWireCodec(), owner)
        val result = client.retrieve(binding, tx, WireRetrieveRequest(listOf("urn:example:a")))
        assertTrue(result.isOk); assertEquals("https://source.example/api/retrieve", owner.url)
        assertTrue(owner.sent!!.contains("\"attributeIdentifiers\"")); assertEquals("{\"x\":1}", result.value.payload.attributes!!.single().attributeValueJson)
        owner.url = null; assertTrue(client.retrieve(binding.copy(retrievalAdvertised = false), tx, WireRetrieveRequest(listOf("urn:example:a"))).isErr); assertNull(owner.url)
    }
    @Test fun deniedChangedBindingAndUnauthorizedNeverReturnBody() = runTest {
        val owner = Owner(SourceHttpResponse(401, "secret", "application/json"))
        val client = AuthenticSourceHttpClient(DefaultAuthenticSourceWireCodec(), owner)
        assertTrue(client.verify(binding, tx, WireVerifyRequest()).isErr)
        owner.url = null; assertTrue(client.verify(binding.copy(revision = "v2"), tx, WireVerifyRequest()).isErr); assertNull(owner.url)
        owner.allowed = false; assertTrue(client.verify(binding, tx, WireVerifyRequest()).isErr); assertNull(owner.url)
    }
}
