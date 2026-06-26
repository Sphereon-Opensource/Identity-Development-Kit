/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.LogoProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.config.CredentialSigningConfig
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetJwtVcIssuerMetadataRootEndpointCommandImpl
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetJwtVcIssuerMetadataScopedEndpointCommandImpl
import com.sphereon.openid.oid4vci.issuer.impl.signing.IssuerKeyIdResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the SD-JWT VC Issuer Metadata endpoint (/.well-known/jwt-vc-issuer[/…]).
 *
 * The tests construct the endpoint commands directly with fake dependencies and
 * dispatch synthetic [GenericHttpRequest]s — they do NOT spin up an HTTP server.
 */
class JwtVcIssuerMetadataEndpointTest {
    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------

    /** Fake resolver that returns a predictable VM id + public JWK per alias. */
    private class FakeKeyIdResolver : IssuerKeyIdResolver {
        override suspend fun resolveDidVerificationMethodId(
            keyAlias: String,
            didMethod: String,
        ): IdkResult<String, IdkError> = Ok("did:$didMethod:fake-jwk-for-$keyAlias#0")

        override suspend fun resolvePublicJwk(keyAlias: String): IdkResult<JsonObject, IdkError> =
            Ok(
                buildJsonObject {
                    put("kty", JsonPrimitive("EC"))
                    put("crv", JsonPrimitive("P-256"))
                    put("x", JsonPrimitive("x-of-$keyAlias"))
                    put("y", JsonPrimitive("y-of-$keyAlias"))
                },
            )
    }

    private fun fakeConfig(
        issuerIdentifier: String = "https://issuer.example.com/oid4vci",
        aliases: List<String> = listOf("TestCredential", "PID"),
        displaySupplier: () -> List<DisplayProperties>? = { null },
        onPrepare: () -> Unit = {},
    ) = object : com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider {
        override val issuerIdentifier: String = issuerIdentifier
        override val credentialConfigurations: Map<String, CredentialConfigurationSupported> =
            aliases.associateWith { CredentialConfigurationSupported(format = "dc+sd-jwt") }
        override val authorizationServers: List<String>? = null
        override val display: List<DisplayProperties>? get() = displaySupplier()
        override val credentialSigningConfigs: Map<String, CredentialSigningConfig> =
            aliases.associateWith {
                CredentialSigningConfig(signingKeyAlias = it, signingKeyMode = SigningKeyMode.Did("jwk"))
            }

        override suspend fun prepare() {
            onPrepare()
        }
    }

    private fun buildCommands(
        issuerIdentifier: String = "https://issuer.example.com/oid4vci",
        aliases: List<String> = listOf("TestCredential", "PID"),
    ): Pair<GetJwtVcIssuerMetadataRootEndpointCommandImpl, GetJwtVcIssuerMetadataScopedEndpointCommandImpl> {
        val execution = TestSessionExecution()
        val config = fakeConfig(issuerIdentifier, aliases)
        val resolver = FakeKeyIdResolver()
        return Pair(
            GetJwtVcIssuerMetadataRootEndpointCommandImpl(execution, config, resolver),
            GetJwtVcIssuerMetadataScopedEndpointCommandImpl(execution, config, resolver),
        )
    }

    // ------------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------------

    @Test
    fun scopedEndpointReturnsIssuerMetadataForConfiguredPath() =
        runTest {
            val (_, scoped) = buildCommands()
            val req =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/jwt-vc-issuer/oid4vci",
                    pathParameters = mapOf("issuer_path" to "oid4vci"),
                )
            val response = scoped.execute(req).getOrThrow()
            assertEquals(200, response.statusCode)
            assertEquals("application/json", response.headers["Content-Type"])
            val body = json.parseToJsonElement(response.body!!).jsonObject
            assertEquals("https://issuer.example.com/oid4vci", body["issuer"]!!.jsonPrimitive.content)
            val keys = body["jwks"]!!.jsonObject["keys"]!!.jsonArray
            assertEquals(2, keys.size, "Two signing aliases configured, two entries expected")
        }

    @Test
    fun scopedEndpointRejectsWrongPath() =
        runTest {
            val (_, scoped) = buildCommands(issuerIdentifier = "https://issuer.example.com/oid4vci")
            val req =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/jwt-vc-issuer/wrong-path",
                    pathParameters = mapOf("issuer_path" to "wrong-path"),
                )
            val response = scoped.execute(req)
            assertTrue(response.isErr, "Mismatched path must error, not silently serve")
        }

    @Test
    fun rootEndpointRejectsPathScopedIssuer() =
        runTest {
            val (root, _) = buildCommands(issuerIdentifier = "https://issuer.example.com/oid4vci")
            val req = GenericHttpRequest(method = "GET", path = "/.well-known/jwt-vc-issuer")
            val response = root.execute(req)
            assertTrue(
                response.isErr,
                "Path-scoped issuer must not respond at the bare well-known (spec insert form only)",
            )
        }

    @Test
    fun rootEndpointServesWhenIssuerHasNoPath() =
        runTest {
            val (root, _) = buildCommands(issuerIdentifier = "https://host-only-issuer.example.com")
            val req = GenericHttpRequest(method = "GET", path = "/.well-known/jwt-vc-issuer")
            val response = root.execute(req).getOrThrow()
            assertEquals(200, response.statusCode)
            val body = json.parseToJsonElement(response.body!!).jsonObject
            assertEquals("https://host-only-issuer.example.com", body["issuer"]!!.jsonPrimitive.content)
        }

    @Test
    fun publishedEntriesCarryDidVmKidAndBarePublicJwk() =
        runTest {
            val (_, scoped) =
                buildCommands(
                    issuerIdentifier = "https://issuer.example.com/oid4vci",
                    aliases = listOf("TestCredential"),
                )
            val req =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/jwt-vc-issuer/oid4vci",
                    pathParameters = mapOf("issuer_path" to "oid4vci"),
                )
            val response = scoped.execute(req).getOrThrow()
            val entry =
                json
                    .parseToJsonElement(response.body!!)
                    .jsonObject["jwks"]!!
                    .jsonObject["keys"]!!
                    .jsonArray[0]
                    .jsonObject
            assertEquals("did:jwk:fake-jwk-for-TestCredential#0", entry["kid"]!!.jsonPrimitive.content)
            // JWK members — no kid inside the JWK itself (cf. canonical did:jwk rule)
            val allowedMembers = setOf("kty", "crv", "x", "y", "n", "e", "kid")
            val jwkMembers = entry.keys
            val leaks = jwkMembers - allowedMembers
            assertTrue(leaks.isEmpty(), "JWK entry must not expose metadata; leaked: $leaks")
            assertEquals("EC", entry["kty"]!!.jsonPrimitive.content)
        }

    @Test
    fun issuerDesignBrandingIsPreparedAndPublishedAsSdJwtIssuerMetadataDisplay() =
        runTest {
            var prepared = false
            val designDisplay =
                listOf(
                    DisplayProperties(
                        name = "Acme Workforce Issuer",
                        locale = "en-US",
                        logo = LogoProperties(uri = "/public/assets/design/acme-issuer-logo.png", altText = "Acme"),
                    ),
                    DisplayProperties(name = "Acme Werkgever", locale = "nl-NL"),
                )
            val execution = TestSessionExecution()
            val config =
                fakeConfig(
                    displaySupplier = { if (prepared) designDisplay else null },
                    onPrepare = { prepared = true },
                )
            val scoped = GetJwtVcIssuerMetadataScopedEndpointCommandImpl(execution, config, FakeKeyIdResolver())
            val req =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/jwt-vc-issuer/oid4vci",
                    pathParameters = mapOf("issuer_path" to "oid4vci"),
                )

            val response = scoped.execute(req).getOrThrow()
            assertTrue(prepared, "SD-JWT issuer metadata must prepare the config provider before reading issuer branding")
            val body = json.parseToJsonElement(response.body!!).jsonObject
            val display = body["display"]!!.jsonArray
            assertEquals(2, display.size)
            val en = display[0].jsonObject
            assertEquals("Acme Workforce Issuer", en["name"]!!.jsonPrimitive.content)
            assertEquals("en-US", en["locale"]!!.jsonPrimitive.content)
            assertEquals("/public/assets/design/acme-issuer-logo.png", en["logo"]!!.jsonObject["uri"]!!.jsonPrimitive.content)
            assertEquals("Acme", en["logo"]!!.jsonObject["alt_text"]!!.jsonPrimitive.content)
            val nl = display[1].jsonObject
            assertEquals("Acme Werkgever", nl["name"]!!.jsonPrimitive.content)
            assertEquals("nl-NL", nl["locale"]!!.jsonPrimitive.content)
        }
}
