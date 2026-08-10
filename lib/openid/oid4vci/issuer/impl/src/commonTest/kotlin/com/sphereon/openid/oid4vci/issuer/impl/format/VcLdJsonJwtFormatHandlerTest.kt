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

package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.jsonld.LinkedDataDocument
import com.sphereon.jsonld.command.JsonLdContextValidator
import com.sphereon.jsonld.command.JsonLdSchemaValidator
import com.sphereon.jsonld.command.MapBackedJsonLdSchemaRegistry
import com.sphereon.jsonld.command.jsonLdSchemaEntry
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VcLdJsonJwtFormatHandlerTest {
    private val vcdm2Body =
        buildJsonObject {
            put("@context", buildJsonObject { put("name", JsonPrimitive("https://schema.org/name")) })
        }
    private val untpBody =
        buildJsonObject {
            put("@context", buildJsonObject { put("description", JsonPrimitive("https://schema.org/description")) })
        }
    private val bundleLoader =
        LinkedDataDocumentLoader { iri ->
            when (iri) {
                "https://www.w3.org/ns/credentials/v2" -> {
                    Ok(
                        LinkedDataDocument(
                            documentUrl = iri,
                            content = vcdm2Body,
                            contentType = "application/ld+json",
                        ),
                    )
                }

                "https://vocabulary.uncefact.org/untp/" -> {
                    Ok(
                        LinkedDataDocument(
                            documentUrl = iri,
                            content = untpBody,
                            contentType = "application/ld+json",
                        ),
                    )
                }

                else -> {
                    Err(
                        com.sphereon.jsonld.JsonLdError.LoadingDocumentFailed(
                            iri = iri,
                            reason = "no fixture for $iri",
                        ),
                    )
                }
            }
        }

    private val widgetSchema =
        buildJsonObject {
            put("\$schema", JsonPrimitive("https://json-schema.org/draft/2020-12/schema"))
            put("type", JsonPrimitive("object"))
        }

    private val schemaRegistry =
        MapBackedJsonLdSchemaRegistry(
            mapOf(
                "DigitalProductPassport" to
                    jsonLdSchemaEntry(
                        schemaUri = "https://test.example/dpp.json",
                        schema = widgetSchema,
                    ),
            ),
        )

    private fun newHandler(capturedPayload: ((JsonObject) -> Unit)? = null,): VcLdJsonJwtFormatHandler =
        VcLdJsonJwtFormatHandler(
            jwtService = FakeJwtService(capturedPayload),
            contextValidator = JsonLdContextValidator(bundleLoader),
            schemaValidator = JsonLdSchemaValidator(schemaRegistry),
        )

    private fun makeContext(
        types: List<String>?,
        attributes: Map<String, JsonElement> = emptyMap(),
        extraContexts: List<String>? = listOf("https://vocabulary.uncefact.org/untp/"),
    ) = IssuanceContext(
        subject = "did:example:holder123",
        clientId = "client-1",
        issuerIdentifier = "https://issuer.example.com",
        credentialConfigurationId = "untp-dpp",
        credentialConfiguration =
            CredentialConfigurationSupported(
                format = CredentialFormat.VC_LD_JSON_JWT.value,
                credentialDefinition = CredentialDefinition(type = types, context = extraContexts),
            ),
        holderBindingKey = null,
        attributes = attributes,
        signingKeyAlias = "issuer-signing-untp",
    )

    @Test
    fun canHandleReturnsTrueForVcLdJsonJwt() =
        runTest {
            val handler = newHandler()
            val request = CredentialRequest(format = CredentialFormat.VC_LD_JSON_JWT.value)
            assertTrue(
                handler.canHandle(
                    request,
                    CredentialConfigurationSupported(format = CredentialFormat.VC_LD_JSON_JWT.value),
                ),
            )
        }

    @Test
    fun canHandleReturnsFalseForJwtVcJson() =
        runTest {
            val handler = newHandler()
            val request = CredentialRequest(format = CredentialFormat.JWT_VC_JSON.value)
            assertFalse(
                handler.canHandle(
                    request,
                    CredentialConfigurationSupported(format = CredentialFormat.JWT_VC_JSON.value),
                ),
            )
        }

    @Test
    fun issueCredentialBuildsVcdm2BodyWithUntpContext() =
        runTest {
            var captured: JsonObject? = null
            val handler = newHandler { captured = it }
            val request = CredentialRequest(format = CredentialFormat.VC_LD_JSON_JWT.value)
            val context =
                makeContext(
                    types = listOf("VerifiableCredential", "DigitalProductPassport"),
                    attributes =
                        mapOf(
                            "productName" to JsonPrimitive("Sample Widget"),
                            "weightKg" to JsonPrimitive(2.5),
                        ),
                )

            val result = handler.issueCredential(request, context)
            assertTrue(result.isOk, "issuance must succeed: ${if (result.isErr) result.error else ""}")
            assertEquals(CredentialFormat.VC_LD_JSON_JWT.value, result.value.format)

            val payload = assertNotNull(captured, "JwtService must have been invoked")

            // VC body fields are at the JWT root (no `vc` wrapper, per VC-JOSE-COSE §3.1.1).
            val contexts =
                payload["@context"]?.jsonArray
                    ?: error("@context must be present at root")
            assertEquals(
                listOf("https://www.w3.org/ns/credentials/v2", "https://vocabulary.uncefact.org/untp/"),
                contexts.map { it.jsonPrimitive.content },
            )

            val types = payload["type"]?.jsonArray ?: error("type must be present")
            assertEquals(
                listOf("VerifiableCredential", "DigitalProductPassport"),
                types.map { it.jsonPrimitive.content },
            )

            // VCDM 2.0 uses validFrom (not issuanceDate).
            assertNotNull(payload["validFrom"])
            assertEquals(
                "https://issuer.example.com",
                payload["issuer"]?.jsonPrimitive?.content,
            )

            // JWT registered claims are at the same level.
            assertEquals("https://issuer.example.com", payload["iss"]?.jsonPrimitive?.content)
            assertEquals("did:example:holder123", payload["sub"]?.jsonPrimitive?.content)
            assertNotNull(payload["iat"])

            // Custom attributes flow into credentialSubject, not the root.
            val cs = payload["credentialSubject"]?.jsonObject ?: error("credentialSubject")
            assertEquals("Sample Widget", cs["productName"]?.jsonPrimitive?.content)
        }

    @Test
    fun issuanceFailsWhenContextValidatorRejectsVocab() =
        runTest {
            // Inject a loader that returns a context body containing @vocab.
            val poisonedLoader =
                LinkedDataDocumentLoader { iri ->
                    when (iri) {
                        "https://www.w3.org/ns/credentials/v2" -> {
                            Ok(
                                LinkedDataDocument(
                                    documentUrl = iri,
                                    content =
                                        buildJsonObject {
                                            put(
                                                "@context",
                                                buildJsonObject { put("@vocab", JsonPrimitive("https://example.com/")) },
                                            )
                                        },
                                    contentType = "application/ld+json",
                                ),
                            )
                        }

                        else -> {
                            Err(
                                com.sphereon.jsonld.JsonLdError.LoadingDocumentFailed(
                                    iri = iri,
                                    reason = "fixture absent",
                                ),
                            )
                        }
                    }
                }
            val handler =
                VcLdJsonJwtFormatHandler(
                    jwtService = FakeJwtService(),
                    contextValidator = JsonLdContextValidator(poisonedLoader),
                    schemaValidator = JsonLdSchemaValidator(schemaRegistry),
                )
            val context = makeContext(types = listOf("VerifiableCredential"), extraContexts = null)
            val result =
                handler.issueCredential(
                    CredentialRequest(format = CredentialFormat.VC_LD_JSON_JWT.value),
                    context,
                )
            assertTrue(result.isErr, "@vocab must abort issuance")
            assertEquals("JSONLD_INVALID_VOCAB_MAPPING", result.error.code)
        }

    @Test
    fun issuanceProceedsWhenSchemaIsNotBundled() =
        runTest {
            // Use the empty default-style registry; schema validator returns the
            // structured "no schema is registered" violation, which Track A treats
            // as a soft pass.
            val handler =
                VcLdJsonJwtFormatHandler(
                    jwtService = FakeJwtService(),
                    contextValidator = JsonLdContextValidator(bundleLoader),
                    schemaValidator = JsonLdSchemaValidator(MapBackedJsonLdSchemaRegistry(emptyMap())),
                )
            val context = makeContext(types = listOf("VerifiableCredential", "UnknownType"))
            val result =
                handler.issueCredential(
                    CredentialRequest(format = CredentialFormat.VC_LD_JSON_JWT.value),
                    context,
                )
            assertTrue(result.isOk, "missing-schema must not block issuance under Track A policy")
        }

    /**
     * Fake JwtService that captures the payload it was asked to sign and
     * emits a deterministic compact-JWS string.
     */
    private class FakeJwtService(
        private val onPayload: ((JsonObject) -> Unit)? = null,
    ) : JwtService {
        override val commands: JwtService.Commands
            get() = throw UnsupportedOperationException("not used in tests")

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
            val payload =
                args.payload as? JsonObject
                    ?: error("VcLdJsonJwtFormatHandler must pass a JsonObject payload")
            onPayload?.invoke(payload)
            // Encode the payload deterministically (no signing) so tests can
            // also decode the JWT body when needed.
            val encoded = Json.encodeToString(JsonObject.serializer(), payload)
            return Ok(JwtCompactResult(jwt = "eyJhbGciOiJFUzI1NiJ9.${encoded.encodeToByteArray().encodeToBase64Url()}.fakesig"))
        }

        override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = throw UnsupportedOperationException("not used in tests")

        override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = throw UnsupportedOperationException("not used in tests")

        override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = throw UnsupportedOperationException("not used in tests")

        override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> = throw UnsupportedOperationException("not used in tests")

        override fun assembleJwsGeneral(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray
        ) = throw UnsupportedOperationException("not used in tests")

        override fun assembleJwsFlattened(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray
        ) = throw UnsupportedOperationException("not used in tests")

        override fun assembleJwsCompact(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray
        ) = throw UnsupportedOperationException("not used in tests")
    }
}
