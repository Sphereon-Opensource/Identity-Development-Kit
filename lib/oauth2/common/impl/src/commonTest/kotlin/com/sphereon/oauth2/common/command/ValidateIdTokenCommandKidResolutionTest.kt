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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.testutil.createOauth2CommonTestAppGraph
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Verifies the kid-resolution + JWKS-bound signature behaviour the IDK ID token validator must
 * provide for OIDF Basic RP conformance: kid-absent single-jwks acceptance,
 * wrong-material rejection, embedded-jwk refusal under trustedJwks pinning. The pre-validation
 * rejection paths (alg allow-list, embedded-key rejection) live in
 * [com.sphereon.oauth2.common.ValidateIdTokenJwksBindingTest].
 */
class ValidateIdTokenCommandKidResolutionTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jwtService: JwtService
    private lateinit var validateCommand: ValidateIdTokenCommand

    private val app = createOauth2CommonTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("id-token-kid-resolution")

    @BeforeTest
    fun setUp() {
        val providerConfig =
            SoftwareKmsProviderConfig(
                id = "kid-resolution-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        val provider =
            (app as SoftwareKmsProviderFactoryImpl.Graph)
                .softwareKmsProvider
                .create(providerConfig, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(provider, makeDefaultKms = true)

        jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
        validateCommand = ValidateIdTokenCommandImpl(session.asCoreApiServiceGraph().serviceExecution, jwtService)
    }

    private suspend fun mintIdToken(
        issuer: ManagedOptsKeyInfo,
        omitKid: Boolean = false,
    ): String {
        val now = Clock.System.now().epochSeconds
        val payload =
            buildJsonObject {
                put("iss", "https://example.com")
                put("sub", "user-1")
                put("aud", JsonArray(listOf(JsonPrimitive("client-1"))))
                put("exp", now + 3600)
                put("iat", now)
            }
        val opts =
            if (omitKid) {
                // noIdentifierInHeader skips the entire identifier block (kid AND alg),
                // so we pre-seed the protected header with alg ourselves.
                CreateJwsOpts(
                    noIdentifierInHeader = true,
                    protectedHeader = buildJsonObject { put("alg", JsonPrimitive("ES256")) },
                )
            } else {
                CreateJwsOpts()
            }
        val signed = jwtService.createJwsCompact(CreateJwsArgs(issuer = issuer, payload = payload, opts = opts))
        check(signed.isOk) { "Failed to mint id_token: ${if (signed.isErr) signed.error else "<unknown>"}" }
        return signed.value.jwt
    }

    private val baseOptions =
        IdTokenValidationOptions(
            expectedIssuer = "https://example.com",
            expectedAudience = "client-1",
        )

    /** kid absent + a single matching JWK in the trusted set: verifier accepts. */
    @Test
    fun absentKidSingleMatchingKeyAccepts() =
        runTest {
            val keyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val idToken = mintIdToken(issuer, omitKid = true)
            val trustedJwks = JwkSet(keys = arrayOf(keyPair.jose.publicJwk))

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options = baseOptions.copy(trustedJwks = trustedJwks),
                    ),
                )
            assertTrue(
                result.isOk,
                "kid-absent + single matching key must verify; got: " +
                    "${if (result.isErr) result.error.message.defaultMessage else ""}",
            )
        }

    /** kid absent + multiple matching keys in the trusted set: verifier MUST refuse. */
    @Test
    fun absentKidMultipleCandidatesRejects() =
        runTest {
            val keyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val secondPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )

            val idToken = mintIdToken(issuer, omitKid = true)
            val trustedJwks =
                JwkSet(
                    keys = arrayOf(keyPair.jose.publicJwk, secondPair.jose.publicJwk),
                )

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options = baseOptions.copy(trustedJwks = trustedJwks),
                    ),
                )
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("No matching trusted JWK") == true,
                "ambiguous kid-absent JWKS lookup must reject; got: ${result.error.message.defaultMessage}",
            )
        }

    /** kid in header matches a JWKS entry, but the signature was made with a DIFFERENT key. */
    @Test
    fun presentKidWrongMaterialRejects() =
        runTest {
            // Sign with key A; advertise a foreign key under the SAME kid in the trusted JWKS.
            val signingPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val signingInfo: ManagedKeyInfoType<*> = signingPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = signingInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )
            val idToken = mintIdToken(issuer)
            val signingKid =
                signingPair.kid ?: signingPair.jose.publicJwk.kid
                    ?: error("Signing key has no kid; cannot run wrong-material test")

            val foreignPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val foreignAsObject =
                foreignPair.jose.publicJwk
                    .toJsonObject()
                    .jsonObject
            val forgedJwk =
                Jwk.fromJsonObject(
                    buildJsonObject {
                        foreignAsObject.forEach { (k, v) -> put(k, v) }
                        put("kid", JsonPrimitive(signingKid))
                    },
                )
            val trustedJwks = JwkSet(keys = arrayOf(forgedJwk))

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options = baseOptions.copy(trustedJwks = trustedJwks),
                    ),
                )
            assertTrue(result.isErr, "signature must fail when material differs from advertised JWK")
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("signature") == true ||
                    result.error.message.defaultMessage
                        ?.contains("Invalid") == true,
                "rejection must cite signature; got: ${result.error.message.defaultMessage}",
            )
        }

    /** kid in header but no matching entry in JWKS at all. */
    @Test
    fun presentKidNoMatchInJwksRejects() =
        runTest {
            val signingPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val signingInfo: ManagedKeyInfoType<*> = signingPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val issuer =
                ManagedOptsKeyInfo(
                    identifier = signingInfo,
                    context = IdentifierContext(clientId = "https://example.com"),
                )
            val idToken = mintIdToken(issuer)

            // Trusted JWKS holds an unrelated key with a DIFFERENT kid.
            val unrelatedPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val trustedJwks = JwkSet(keys = arrayOf(unrelatedPair.jose.publicJwk))

            val result =
                validateCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options = baseOptions.copy(trustedJwks = trustedJwks),
                    ),
                )
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("No matching trusted JWK") == true,
                "missing-kid-in-JWKS must reject; got: ${result.error.message.defaultMessage}",
            )
        }
}
