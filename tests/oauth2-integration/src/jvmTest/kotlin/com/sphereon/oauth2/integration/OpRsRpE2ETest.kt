/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.integration

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.ValidateIdTokenArgs
import com.sphereon.oauth2.common.command.ValidateIdTokenCommand
import com.sphereon.oauth2.common.config.AuthorizationServerMode
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Session-graph accessors for the OP/RP/RS commands the integration tests exercise. A single
 * graph carries them because in a "all-in-one" deployment — which is exactly what these tests
 * simulate — they live in one process and share the KMS and JwtService.
 */
@ContributesTo(SessionScope::class)
interface OAuth2IntegrationSessionGraph {
    val createAccessTokenCommand: CreateAccessTokenCommand
    val createIdTokenCommand: CreateIdTokenCommand
    val getJwksCommand: GetJwksCommand
    val validateIdTokenCommand: ValidateIdTokenCommand
    val buildServerMetadataCommand: BuildServerMetadataCommand
    val oauth2ServersConfigProvider: OAuth2ServersConfigProvider
    val jwtService: JwtService
}

/**
 * End-to-end integration tests across the three OAuth2 / OIDC roles the IDK ships:
 *
 *  - **OP**  (`lib-oauth2-server-authorization-impl` + `services-oauth2-as-rest`) —
 *            mints signed ID and access tokens via `CreateIdTokenCommand` /
 *            `CreateAccessTokenCommand`, publishes its JWKS via `GetJwksCommand`.
 *  - **RP**  (`lib-oauth2-client-impl` + `lib-oauth2-common-impl`) —
 *            validates OP-issued ID tokens through `ValidateIdTokenCommand`, pinning the
 *            signature to the OP's advertised JWKS via the `trustedJwks` option.
 *  - **RS**  (`lib-oauth2-server-resource-impl`) — verifies OP-issued access-token JWTs using
 *            the shared `JwtService` + KMS key (same signing key the OP used), replacing the
 *            HTTPS JWKS round-trip a cross-process RS would do.
 *
 * Scope: proves the three command stacks wire together and produce / consume consistent tokens
 * against a single shared signing key. The full HTTP dance (/.well-known + /authorize + /token
 * over MockEngine) is out of scope here — it belongs in a follow-up test that layers an
 * in-process HTTP router on top of these same commands.
 */
class OpRsRpE2ETest {
    private val ctx = OAuth2IntegrationTestContext(this)
    private val graph = ctx.session.graph as OAuth2IntegrationSessionGraph
    private val jwtServiceGraph = ctx.session.graph as JwtServiceImpl.Graph

    private val json = Json { ignoreUnknownKeys = true }

    private val clientId = "test-client"
    private val subject = "test-subject"

    @Test
    fun op_idToken_rpValidates_claimsAndSignature() =
        runTest {
            ctx.ensureOpSigningKey()

            val nonce = "nonce-abc"

            val idTokenResult =
                graph.createIdTokenCommand.execute(
                    CreateIdTokenArgs(
                        subject = subject,
                        clientId = clientId,
                        nonce = nonce,
                    ),
                )
            assertTrue(
                idTokenResult.isOk,
                "OP must mint id_token: ${if (idTokenResult.isErr) idTokenResult.error.message.defaultMessage else ""}",
            )
            val idToken = idTokenResult.value.value

            val validation =
                graph.validateIdTokenCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = TestOAuth2ServersConfigProvider.ISSUER_URL,
                                expectedAudience = clientId,
                                expectedNonce = nonce,
                            ),
                    ),
                )
            assertTrue(
                validation.isOk,
                "RP must accept OP-signed id_token: ${if (validation.isErr) validation.error.message.defaultMessage else ""}",
            )
            val payload = validation.value.payload
            assertEquals(TestOAuth2ServersConfigProvider.ISSUER_URL, payload.iss)
            assertEquals(subject, payload.sub)
            assertEquals(listOf(clientId), payload.aud)
            assertEquals(nonce, payload.nonce)
            assertTrue(validation.value.nonceMatched == true)
        }

    @Test
    fun op_idToken_rpValidates_pinnedToOpJwks() =
        runTest {
            ctx.ensureOpSigningKey()

            val idToken =
                graph.createIdTokenCommand
                    .execute(CreateIdTokenArgs(subject = subject, clientId = clientId))
                    .let {
                        check(it.isOk)
                        it.value.value
                    }

            val jwks =
                com.sphereon.crypto.core.jose.JwkSet(
                    graph.getJwksCommand.execute(GetJwksArgs()).let {
                        check(it.isOk)
                        it.value.keys.toTypedArray()
                    },
                )
            assertTrue(jwks.keys.isNotEmpty(), "JWKS must carry at least one key")
            assertNotNull(
                jwks.keys.first().kid,
                "JWKS-published key must expose a kid for kid-bound RP validation",
            )

            val validation =
                graph.validateIdTokenCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = TestOAuth2ServersConfigProvider.ISSUER_URL,
                                expectedAudience = clientId,
                                trustedJwks = jwks,
                            ),
                    ),
                )
            assertTrue(
                validation.isOk,
                "RP must validate OP-issued id_token against OP's advertised JWKS; got: " +
                    "${if (validation.isErr) validation.error.message.defaultMessage else ""}",
            )
        }

    @Test
    fun op_advertisesJwks_withPublicSigningKey() =
        runTest {
            ctx.ensureOpSigningKey()

            val jwksResult = graph.getJwksCommand.execute(GetJwksArgs())
            assertTrue(jwksResult.isOk, "OP must expose JWKS")

            val keys = jwksResult.value.keys
            assertTrue(keys.isNotEmpty(), "JWKS must include at least one public key")
            assertNotNull(keys.first().kty, "JWKS entry must carry a key type")
            // Private-key components MUST NOT leak into the public JWKS.
            assertTrue(keys.first().d == null, "JWKS must not carry the EC private component `d`")
        }

    @Test
    fun op_accessToken_rsVerifiesSignature_againstOpSigningKey() =
        runTest {
            ctx.ensureOpSigningKey()

            val accessTokenResult =
                graph.createAccessTokenCommand.execute(
                    CreateAccessTokenArgs(
                        subject = subject,
                        clientId = clientId,
                        scope = "read",
                        audience = listOf("https://api.test/resource"),
                    ),
                )
            assertTrue(
                accessTokenResult.isOk,
                "OP must mint access_token: ${if (accessTokenResult.isErr) accessTokenResult.error.message.defaultMessage else ""}",
            )
            val accessToken = accessTokenResult.value.value

            // RS-side: verify the JWT signature against the OP's signing key. In an in-process
            // all-in-one deployment the RS shares the KMS with the OP, so the verifier can pin
            // to the same alias instead of fetching JWKS over HTTPS. A cross-process RS would
            // swap this for a `jwksUri` — that path is exercised by an HTTP-level test layered
            // on top (follow-up).
            val verifyResult =
                graph.jwtService.verifyJws(
                    VerifyJwsArgs(
                        jws = JwsCompact(accessToken),
                        identifier =
                            ManagedOptsAlias(
                                identifier = OAuth2IntegrationTestContext.OP_SIGNING_KEY_ALIAS,
                            ),
                    ),
                )
            assertTrue(
                verifyResult.isOk,
                "RS must verify access_token signature: ${if (verifyResult.isErr) verifyResult.error.message.defaultMessage else ""}",
            )
            assertTrue(verifyResult.value.isValid, "signature must validate")

            // Spot-check payload claims the OP promised to embed.
            val payloadJson = verifyResult.value.parsedPayload.toString()
            val claims = json.parseToJsonElement(payloadJson) as JsonObject
            assertEquals(
                TestOAuth2ServersConfigProvider.ISSUER_URL,
                claims["iss"]?.jsonPrimitive?.content,
                "access_token iss claim must match configured issuer",
            )
            assertEquals(subject, claims["sub"]?.jsonPrimitive?.content)
            assertNotNull(claims["exp"], "access_token must carry exp")
        }

    @Test
    fun rp_rejectsIdToken_signedByWrongKey() =
        runTest {
            ctx.ensureOpSigningKey()

            // Mint a valid id_token.
            val idToken =
                graph.createIdTokenCommand
                    .execute(CreateIdTokenArgs(subject = subject, clientId = clientId))
                    .let {
                        check(it.isOk)
                        it.value.value
                    }

            // Build a JWKS that deliberately contains a different key.
            val foreignAlias = "foreign-signing-key"
            ctx.ensureOpSigningKey(alias = foreignAlias)
            val foreignKeyPair = ctx.keyManagerService.generateKeyAsync(alg = com.sphereon.crypto.core.generic.SignatureAlgorithm.ECDSA_SHA256)
            val foreignJwks =
                com.sphereon.crypto.core.jose
                    .JwkSet(arrayOf(foreignKeyPair.jose.publicJwk))

            val validation =
                graph.validateIdTokenCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = TestOAuth2ServersConfigProvider.ISSUER_URL,
                                expectedAudience = clientId,
                                trustedJwks = foreignJwks,
                            ),
                    ),
                )
            assertTrue(
                validation.isErr,
                "RP must reject id_token whose signing kid is absent from the trusted JWKS",
            )
        }

    /**
     * OIDF Conformance Group F: an ID token whose signature was made with a key NOT in the
     * issuer's advertised JWKS must be rejected, even though the token itself is otherwise
     * well-formed (valid issuer, audience, claims). Mirrors
     * `OIDCCClientTestInvalidIdTokenSignatureWithRS256` from the OIDF Basic RP plan: the JWS
     * verifier MUST verify the signature against the trusted JWKS and reject material that
     * doesn't bind.
     *
     * Approach: mint an OP-issued id_token with the OP's signing key, then validate it under a
     * trusted JWKS that ONLY contains an unrelated key. The verifier MUST refuse — earlier
     * builds passed because the kid-presence pre-check would short-circuit; the signature was
     * never actually checked against the JWKS key.
     */
    @Test
    fun rp_rejectsIdToken_signedByKeyNotInTrustedJwks() =
        runTest {
            ctx.ensureOpSigningKey()

            val idToken =
                graph.createIdTokenCommand
                    .execute(CreateIdTokenArgs(subject = subject, clientId = clientId))
                    .let {
                        check(it.isOk)
                        it.value.value
                    }

            // A fresh key the OP did NOT sign with — the trusted JWKS publishes only this one.
            val untrustedKeyPair = ctx.keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val untrustedJwks =
                com.sphereon.crypto.core.jose
                    .JwkSet(arrayOf(untrustedKeyPair.jose.publicJwk))

            val validation =
                graph.validateIdTokenCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = TestOAuth2ServersConfigProvider.ISSUER_URL,
                                expectedAudience = clientId,
                                trustedJwks = untrustedJwks,
                            ),
                    ),
                )
            assertTrue(
                validation.isErr,
                "RP must reject an id_token whose signing key is absent from the trusted JWKS, " +
                    "even when the token's claims would otherwise validate",
            )
        }

    @Test
    fun rp_rejectsIdToken_withWrongAudience() =
        runTest {
            ctx.ensureOpSigningKey()

            val idToken =
                graph.createIdTokenCommand
                    .execute(CreateIdTokenArgs(subject = subject, clientId = clientId))
                    .let {
                        check(it.isOk)
                        it.value.value
                    }

            val validation =
                graph.validateIdTokenCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = TestOAuth2ServersConfigProvider.ISSUER_URL,
                                expectedAudience = "different-client",
                            ),
                    ),
                )
            assertTrue(validation.isErr, "RP must reject id_token whose aud doesn't match expectedAudience")
            assertTrue(
                validation.error.message.defaultMessage
                    ?.contains("audience") == true ||
                    validation.error.message.defaultMessage
                        ?.contains("aud") == true,
                "rejection must cite audience; got: ${validation.error.message.defaultMessage}",
            )
        }

    @Test
    fun rp_rejectsIdToken_withWrongIssuer() =
        runTest {
            ctx.ensureOpSigningKey()

            val idToken =
                graph.createIdTokenCommand
                    .execute(CreateIdTokenArgs(subject = subject, clientId = clientId))
                    .let {
                        check(it.isOk)
                        it.value.value
                    }

            val validation =
                graph.validateIdTokenCommand.execute(
                    ValidateIdTokenArgs(
                        idToken = idToken,
                        options =
                            IdTokenValidationOptions(
                                expectedIssuer = "https://evil.example.com",
                                expectedAudience = clientId,
                            ),
                    ),
                )
            assertTrue(validation.isErr)
            assertTrue(
                validation.error.message.defaultMessage
                    ?.contains("ssuer") == true,
                "rejection must cite issuer; got: ${validation.error.message.defaultMessage}",
            )
        }

    /**
     * OIDF Conformance Group D end-to-end: an RSA-2048 KMS key drives the entire id-token /
     * discovery / JWKS round-trip without any explicit `idTokenSigningAlgValuesSupported`
     * pin in config. Verifies that:
     *  - discovery metadata advertises `RS256` (key-derived, was historically `ES256`),
     *  - the issued id-token JWS header carries `alg=RS256` and a `kid` that's present in
     *    the published JWKS, with the matching JWK also reporting `alg=RS256`.
     *
     * Mirrors the OIDF Basic OP `OIDCCIdTokenSignature` test expectation: the id-token's
     * JWS `alg` matches what discovery metadata advertises.
     */
    @Test
    fun rs256IdTokenEndToEnd() =
        runTest {
            val rsaAlias = "rsa-2048-test"
            ctx.ensureOpSigningKey(alias = rsaAlias, alg = SignatureAlgorithm.RSA_SHA256)

            // Swap the per-session config to point at the RSA key with NO pinned alg list, so
            // both discovery and id-token signing have to derive the alg from the resolved key.
            val testProvider = graph.oauth2ServersConfigProvider as TestOAuth2ServersConfigProvider
            testProvider.overrideServer(
                OAuth2ServerInstanceConfig(
                    mode = AuthorizationServerMode.HOSTED,
                    issuer = TestOAuth2ServersConfigProvider.ISSUER_URL,
                    oidc = FeaturePolicy.SUPPORTED,
                    idTokenSigningAlgValuesSupported = null,
                ),
            )

            try {
                val discoveryResult = graph.buildServerMetadataCommand.execute(BuildServerMetadataArgs())
                assertTrue(
                    discoveryResult.isOk,
                    "discovery must succeed for RSA key: ${if (discoveryResult.isErr) discoveryResult.error.message.defaultMessage else ""}",
                )
                assertEquals(
                    listOf("RS256"),
                    discoveryResult.value.idTokenSigningAlgValuesSupported,
                    "discovery must advertise RS256 derived from the RSA signing key, not the historical ES256 fallback",
                )

                val idTokenResult =
                    graph.createIdTokenCommand.execute(
                        CreateIdTokenArgs(
                            subject = subject,
                            clientId = clientId,
                            nonce = "rs256-nonce",
                        ),
                    )
                assertTrue(
                    idTokenResult.isOk,
                    "OP must mint RS256 id_token: ${if (idTokenResult.isErr) idTokenResult.error.message.defaultMessage else ""}",
                )
                val idToken = idTokenResult.value.value

                // Decode the JWS header without verifying — we want to inspect alg/kid.
                val headerSegment = idToken.substringBefore('.')
                val headerJsonBytes =
                    java.util.Base64
                        .getUrlDecoder()
                        .decode(headerSegment.padBase64Url())
                val headerJson = json.parseToJsonElement(headerJsonBytes.decodeToString()) as JsonObject
                assertEquals(
                    "RS256",
                    headerJson["alg"]?.jsonPrimitive?.content,
                    "id_token JWS header alg must match discovery-advertised RS256",
                )
                val kid = headerJson["kid"]?.jsonPrimitive?.content
                assertNotNull(kid, "RS256 id_token must carry a kid")

                // Published JWKS must include the same kid, and the published JWK must report RS256.
                val jwksResult = graph.getJwksCommand.execute(GetJwksArgs())
                assertTrue(jwksResult.isOk)
                val publishedKey =
                    jwksResult.value.keys.firstOrNull { it.kid == kid }
                        ?: error("JWKS does not contain id_token kid '$kid'; published kids=${jwksResult.value.keys.map { it.kid }}")
                assertEquals(
                    "RS256",
                    publishedKey.alg?.value,
                    "JWKS-published key must report alg=RS256 to match the id_token signature",
                )
            } finally {
                testProvider.resetToDefault()
            }
        }

    @Test
    fun op_idToken_emitsAuthTimeWhenSuppliedByCaller() =
        runTest {
            // Group I (OIDC `auth_time`): when the standard authorize flow has an active
            // OidcLoginSession, it propagates the session's authTime through
            // CreateAuthorizationCodeArgs -> AuthorizationCodeData.authTime ->
            // CreateIdTokenArgs.authTime, where it lands as the id_token `auth_time` claim. This
            // assertion locks in the leaf step of that chain so the OIDF `OIDCCMaxAge*` suite
            // sees a stable, non-zero value when the suite presets `max_age`.
            ctx.ensureOpSigningKey()
            val authTime = 1_700_000_000L

            val idTokenResult =
                graph.createIdTokenCommand.execute(
                    CreateIdTokenArgs(
                        subject = subject,
                        clientId = clientId,
                        authTime = authTime,
                    ),
                )
            assertTrue(idTokenResult.isOk)

            val payload =
                idTokenResult.value.value
                    .substringAfter('.')
                    .substringBefore('.')
                    .let {
                        java.util.Base64
                            .getUrlDecoder()
                            .decode(it.padBase64Url())
                            .decodeToString()
                    }.let { json.parseToJsonElement(it) as JsonObject }
            assertEquals(authTime, payload["auth_time"]?.jsonPrimitive?.content?.toLong())
        }

    /** Right-pad a base64url segment to a multiple of 4 so `Base64.getUrlDecoder()` accepts it. */
    private fun String.padBase64Url(): String =
        when (length % 4) {
            0 -> this
            2 -> "$this=="
            3 -> "$this="
            else -> error("Invalid base64url length: $length")
        }
}
