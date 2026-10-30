/* Copyright 2026 Sphereon International B.V. Licensed under Apache-2.0. */
package com.sphereon.authenticsource

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticSourceBoundaryRegressionTest {
    private val endpoint = "https://source.example/api"
    private val binding = SourceEndpointBinding(endpoint, "p", "s", "v1", true)
    private val tx = SourceTransactionRef("tx", "subject", "v1")
    private val target = "$endpoint/verify"
    private val codec = DefaultAuthenticSourceWireCodec()
    private val options = HttpClientOptions(enableLogging = false, followRedirects = false,
        urlValidation = UrlValidationPolicy(allowedSchemes = setOf("https"), exactTargetUri = target, requireResolvedAddressEnforcement = true))
    private class ThrowingFactory(private val error: Exception) : HttpClientFactory {
        var calls = 0
        override fun createClient(options: HttpClientOptions): HttpClient { calls++; throw error }
        override fun isSupportedOptions(options: HttpClientOptions) = true
        override fun getEngineTypesSupported() = listOf(HttpClientEngineType.CIO)
        override fun getEngineTypeDefault() = HttpClientEngineType.CIO
    }
    private fun clientWith(owner: SourceAuthorizationOwner) = AuthenticSourceHttpClient(codec, owner)
    private fun throwingOwner(error: Exception): SourceAuthorizationOwner = object : SourceAuthorizationOwner {
        override suspend fun authorize(binding: SourceEndpointBinding, transaction: SourceTransactionRef): IdkResult<SourceAuthorizedCall, IdkError> = throw error
    }
    private fun throwingCall(error: Exception): SourceAuthorizationOwner = object : SourceAuthorizationOwner {
        override suspend fun authorize(binding: SourceEndpointBinding, transaction: SourceTransactionRef): IdkResult<SourceAuthorizedCall, IdkError> =
            IdkResult.ok(object : SourceAuthorizedCall {
                override suspend fun postJson(url: String, body: String): IdkResult<SourceHttpResponse, IdkError> = throw error
            })
    }
    @Test fun arbitraryFactoryCallbacksAreRejectedBeforeFactoryInvocation() = runTest {
        val factory = ThrowingFactory(IllegalStateException("factory must not run"))
        val configured = AuthorizedBearerSourceCall(factory, target, "token", options.copy(additionalConfig = {}))
        assertTrue(configured.postJson(target, "{}").isErr)
        val defaults = AuthorizedBearerSourceCall(factory, target, "token", options.copy(defaultRequest = {}))
        assertTrue(defaults.postJson(target, "{}").isErr)
        assertEquals(0, factory.calls)
    }
    @Test fun ownerAndHttpExceptionsAreSanitizedAndCancellationPropagates() = runTest {
        val secret = "do-not-disclose-token"
        val ownerFailure = clientWith(throwingOwner(IllegalStateException(secret))).verify(binding, tx, WireVerifyRequest())
        val callFailure = clientWith(throwingCall(IllegalStateException(secret))).verify(binding, tx, WireVerifyRequest())
        assertTrue(ownerFailure.isErr); assertTrue(callFailure.isErr)
        assertFalse(ownerFailure.error.toString().contains(secret)); assertFalse(callFailure.error.toString().contains(secret))
        assertFailsWith<CancellationException> { clientWith(throwingOwner(CancellationException(secret))).verify(binding, tx, WireVerifyRequest()) }
        assertFailsWith<CancellationException> { clientWith(throwingCall(CancellationException(secret))).verify(binding, tx, WireVerifyRequest()) }
    }
    @Test fun factoryFailureIsSanitizedAndCancellationPropagates() = runTest {
        val secret = "do-not-disclose-token"
        val failed = AuthorizedBearerSourceCall(ThrowingFactory(IllegalStateException(secret)), target, "token", options).postJson(target, "{}")
        assertTrue(failed.isErr); assertFalse(failed.error.toString().contains(secret))
        val factory = ThrowingFactory(CancellationException(secret))
        assertFailsWith<CancellationException> { AuthorizedBearerSourceCall(factory, target, "token", options).postJson(target, "{}") }
        assertEquals(1, factory.calls)
    }
    @Test fun concreteHttpCancellationPropagatesWithoutRenderingToken() = runTest {
        var authorization: String? = null
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            throw CancellationException("http cancelled")
        }
        val factory = object : HttpClientFactory {
            override fun createClient(options: HttpClientOptions) = HttpClient(engine)
            override fun isSupportedOptions(options: HttpClientOptions) = true
            override fun getEngineTypesSupported() = listOf(HttpClientEngineType.CIO)
            override fun getEngineTypeDefault() = HttpClientEngineType.CIO
        }
        val call = AuthorizedBearerSourceCall(factory, target, "secret-token", options)
        assertFailsWith<CancellationException> { call.postJson(target, "{}") }
        assertEquals("Bearer secret-token", authorization)
        assertFalse(call.toString().contains("secret-token"))
    }
    @Test fun retrieveRetainsVerificationNamedExtensionWithoutDecodingIt() = runTest {
        val body = """{"provider":{"legalName":"P"},"attributes":[{"attributeIdentifier":"urn:example:a","attributeValue":{ "n" : 1.00 }}],"attributeVerificationResults":"extension","fragmentVerificationResults":null}"""
        val owner = object : SourceAuthorizationOwner {
            override suspend fun authorize(binding: SourceEndpointBinding, transaction: SourceTransactionRef): IdkResult<SourceAuthorizedCall, IdkError> =
                IdkResult.ok(object : SourceAuthorizedCall {
                    override suspend fun postJson(url: String, body: String): IdkResult<SourceHttpResponse, IdkError> =
                        IdkResult.ok(SourceHttpResponse(200, body = this@AuthenticSourceBoundaryRegressionTest.retrieveFixture(), contentType = "application/json"))
                })
        }
        val result = clientWith(owner).retrieve(binding, tx, WireRetrieveRequest(listOf("urn:example:a")))
        assertTrue(result.isOk); assertEquals(body, result.value.payload.originalJson)
        assertEquals("{ \"n\" : 1.00 }", result.value.payload.attributes!!.single().attributeValueJson)
    }
    private fun retrieveFixture() = """{"provider":{"legalName":"P"},"attributes":[{"attributeIdentifier":"urn:example:a","attributeValue":{ "n" : 1.00 }}],"attributeVerificationResults":"extension","fragmentVerificationResults":null}"""
}
