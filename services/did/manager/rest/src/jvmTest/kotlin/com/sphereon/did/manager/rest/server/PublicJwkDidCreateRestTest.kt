/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.sphereon.did.manager.rest.server

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.did.manager.rest.server.adapter.DidManagerHttpAdapter
import com.sphereon.did.manager.rest.server.ktor.createDidManagerAppGraph
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicJwkDidCreateRestTest {
    private lateinit var app: com.sphereon.di.app.AppGraph
    private lateinit var adapter: DidManagerHttpAdapter
    private lateinit var keyManager: KeyManagerService
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "public-jwk-rest-test",
                "kms.providers.softwaretest.keystore.keyVisibility" to "private",
            ),
        )
        app = createDidManagerAppGraph(application = this, appId = "public-jwk-did-create-rest")
        app.userContextManager.destroyAll()
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("public-jwk-did-create-rest", principalType = com.sphereon.di.context.PrincipalType.USER)
        TestSessionGraph.fromSession(session).also {
            adapter = it.adapter
            keyManager = it.keyManager
        }
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) app.userContextManager.destroyAll()
    }

    @Test
    fun createBodyIsStrictEvenThoughEndpointJsonIgnoresUnknownKeys() =
        runTest {
            assertEquals(201, post(validBody()).statusCode)

            val privateMemberBodies =
                listOf("d", "p", "q", "dp", "dq", "qi", "k").map { member ->
                    validBody().replace(
                        "\"kid\":\"wscd-public-key-1\"",
                        "\"kid\":\"wscd-public-key-1\",\"$member\":\"private-material\"",
                    )
                }
            val rejected =
                listOf(
                    validBody().replace("\"method\":\"jwk\"", "\"method\":\"jwk\",\"unexpected\":true"),
                    validBody().replace("\"kind\":\"PUBLIC_JWK\"", "\"kind\":\"PUBLIC_JWK\",\"providerId\":\"must-not-cross\""),
                    validBody().replace("\"kid\":\"wscd-public-key-1\"", "\"kid\":\"wscd-public-key-1\",\"unexpected\":\"member\""),
                    validBody().replace("\"kid\":\"wscd-public-key-1\"", "\"kid\":\"wscd-public-key-1\",\"oth\":[]"),
                    validBody().replace("\"kid\":\"wscd-public-key-1\"", "\"kid\":\" \""),
                    validBody().replace("\"kind\":\"PUBLIC_JWK\"", "\"kind\":\"KMS_REFERENCE\""),
                ) + privateMemberBodies

            rejected.forEach { body ->
                val response = post(body)
                assertEquals(400, response.statusCode, "body should fail closed: $body; response=${response.body}")
            }
        }

    @Test
    fun publicJwkVerificationMethodOmitsKeyInfoInsteadOfSerializingAnEmptyObject() =
        runTest {
            val created = post(validBody())
            assertEquals(201, created.statusCode)
            val did = json.parseToJsonElement(created.body!!).jsonObject["did"]!!.jsonPrimitive.content

            val methods =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/api/did/v1/identifiers/$did/verification-methods",
                    ),
                )

            assertEquals(200, methods.statusCode)
            val verificationMethod =
                json.parseToJsonElement(methods.body!!).jsonObject["items"]!!.jsonArray.single().jsonObject
            assertEquals(null, verificationMethod["keyInfo"])
        }

    @Test
    fun kmsCreateByAliasPersistsBoundVmAndEnrichedKeyMapping() =
        runTest {
            val alias = "kms-create-rest-key"
            keyManager.generateKey(providerId = "softwaretest", alias = alias, alg = SignatureAlgorithm.ECDSA_SHA256)

            val created = post(kmsBody(alias))
            assertEquals(201, created.statusCode, created.body)
            val did = json.parseToJsonElement(created.body!!).jsonObject["did"]!!.jsonPrimitive.content

            val methods =
                adapter.handleRequest(
                    GenericHttpRequest(method = "GET", path = "/api/did/v1/identifiers/$did/verification-methods"),
                )
            assertEquals(200, methods.statusCode, methods.body)
            val keyInfo =
                json.parseToJsonElement(methods.body!!).jsonObject["items"]!!.jsonArray.single().jsonObject["keyInfo"]!!.jsonObject
            assertEquals("softwaretest", keyInfo["providerId"]!!.jsonPrimitive.content)
            assertEquals(alias, keyInfo["alias"]!!.jsonPrimitive.content)
            assertEquals("EC", keyInfo["keyType"]!!.jsonPrimitive.content)
            assertEquals("ECDSA_SHA256", keyInfo["signatureAlgorithm"]!!.jsonPrimitive.content)

            val mappings =
                adapter.handleRequest(GenericHttpRequest(method = "GET", path = "/api/did/v1/identifiers/$did/key-mappings"))
            assertEquals(200, mappings.statusCode, mappings.body)
            val mapping = json.parseToJsonElement(mappings.body!!).jsonObject["items"]!!.jsonArray.single().jsonObject
            val mappedKey = mapping["keyInfo"]!!.jsonObject
            assertEquals("softwaretest", mappedKey["providerId"]!!.jsonPrimitive.content)
            assertEquals(alias, mappedKey["alias"]!!.jsonPrimitive.content)
            assertEquals("EC", mappedKey["keyType"]!!.jsonPrimitive.content)
            assertEquals("ECDSA_SHA256", mappedKey["signatureAlgorithm"]!!.jsonPrimitive.content)
        }

    private suspend fun post(body: String) =
        adapter.handleRequest(
            GenericHttpRequest(
                method = "POST",
                path = "/api/did/v1/identifiers",
                headers = mapOf("Content-Type" to "application/json"),
                bodySupplier = { body },
            ),
        )

    private fun validBody(): String =
        """
        {
          "method":"jwk",
          "keyInfo":{
            "kind":"PUBLIC_JWK",
            "publicJwk":{
              "kty":"EC",
              "crv":"P-256",
              "x":"f83OJ3D2xF4yVPs6k2lE0_C3lq8GG5GpQ1GkGvI0zGY",
              "y":"x_FEzRu9m0cN5yZKkH9VqxcWxLb5Y7EFYqmP9FxbnTc",
              "kid":"wscd-public-key-1",
              "alg":"ES256",
              "use":"sig"
            }
          }
        }
        """.trimIndent()

    private fun kmsBody(alias: String): String =
        """
        {
          "method":"jwk",
          "keyInfo":{"kind":"KMS","providerId":"softwaretest","alias":"$alias"}
        }
        """.trimIndent()
}
