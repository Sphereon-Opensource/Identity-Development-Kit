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

package com.sphereon.oauth2.server.authorization.impl.command.clientauth

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsJsonGeneralWithIdentifiers
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.crypto.resolution.IdentifierService
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.ClientAuthenticationEndpoint
import com.sphereon.oauth2.server.authorization.command.clientauth.VerifyAttestationClientAuthArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAttestationChallengeStorage
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAttestationPopJtiStorage
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.storage.AttestationChallengeStorage
import com.sphereon.oauth2.server.authorization.storage.AttestationPopJtiStorage
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceAttestationEnforcementRequest
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceAttestationEnforcer
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceAttestationEvidence
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceClientStatusEvidence
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceTrustEvidence
import com.sphereon.trust.x509.X509TrustAnchorLoader
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Unit tests for [VerifyAttestationClientAuthCommandImpl] that exercise every step of the
 * draft-ietf-oauth-attestation-based-client-auth-07 verification path with stubbed JwtService /
 * ClientRegistry / challenge storage. These run in milliseconds because they do not boot Ktor;
 * they replace the test-coverage gap previously only filled by the OIDF harness.
 */
class VerifyAttestationClientAuthCommandImplTest {
    private val ctx = OAuth2ServerTestContext("attestation-client-auth-test", this)
    private val tokenEndpointUrl = "https://auth.example.com/token"
    private val asIssuer = "https://auth.example.com"

    private fun ecJwk(kid: String) =
        Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "x-coord-$kid",
            y = "y-coord-$kid",
            kid = kid,
        )

    private fun cnfJwkObject(kid: String): JsonObject =
        buildJsonObject {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", "x-coord-$kid")
            put("y", "y-coord-$kid")
            put("kid", kid)
        }

    /**
     * Build a compact-form attestation JWT whose base64url payload is the supplied JSON. The
     * lightweight `JwtClaimsParser` inside the impl decodes this segment directly, so claim
     * values flow from this string into the production code path (the stub JwtService also
     * feeds the same JSON back through `parsedPayload` for the PoP claim re-read).
     */
    private fun attestationJwt(
        claims: JsonObject,
        typ: String = "oauth-client-attestation+jwt",
        kid: String = "att-key",
    ): String {
        val header =
            buildJsonObject {
                put("alg", "ES256")
                put("typ", typ)
                put("kid", kid)
            }
        return header.toString().encodeToByteArray().encodeToBase64Url() + "." +
            claims.toString().encodeToByteArray().encodeToBase64Url() + "." +
            "stub-att-sig"
    }

    private fun popJwt(
        claims: JsonObject = popClaims(),
        typ: String = "oauth-client-attestation-pop+jwt",
    ): String {
        val header =
            buildJsonObject {
                put("alg", "ES256")
                put("typ", typ)
            }
        return header.toString().encodeToByteArray().encodeToBase64Url() + "." +
            claims.toString().encodeToByteArray().encodeToBase64Url() + "." +
            "stub-pop-sig"
    }

    private fun attestationClaims(
        iss: String = "trusted-attester",
        sub: String = "client-att",
        expSecondsFromNow: Long = 300,
        iatSecondsFromNow: Long? = 0,
        cnfKid: String = "instance-key-1",
        cnfJwk: JsonObject? = null,
    ): JsonObject =
        buildJsonObject {
            put("iss", iss)
            put("sub", sub)
            put("exp", kotlinx.serialization.json.JsonPrimitive(Clock.System.now().epochSeconds + expSecondsFromNow))
            if (iatSecondsFromNow != null) {
                put("iat", kotlinx.serialization.json.JsonPrimitive(Clock.System.now().epochSeconds + iatSecondsFromNow))
            }
            put(
                "cnf",
                buildJsonObject {
                    put("jwk", cnfJwk ?: cnfJwkObject(cnfKid))
                },
            )
        }

    private fun popClaims(
        iss: String? = "client-att",
        aud: String = "https://auth.example.com/token",
        iatSecondsFromNow: Long? = 0,
        jti: String? = "pop-jti-${kotlin.random.Random.nextLong()}",
        challenge: String? = null,
        challengeClaimName: String = "challenge",
    ): JsonObject =
        buildJsonObject {
            if (iss != null) put("iss", iss)
            put("aud", aud)
            if (iatSecondsFromNow != null) {
                put("iat", kotlinx.serialization.json.JsonPrimitive(Clock.System.now().epochSeconds + iatSecondsFromNow))
            }
            if (jti != null) put("jti", jti)
            if (challenge != null) put(challengeClaimName, challenge)
        }

    private fun clientRegistration(
        clientId: String = "client-att",
        method: ClientAuthenticationMethod = ClientAuthenticationMethod.ATTEST_JWT_CLIENT_AUTH,
        trustedAttesterJwks: List<Jwk>? = listOf(ecJwk("att-key")),
        trustedAttesterIssuers: List<String>? = null,
    ): ClientRegistration =
        ClientRegistration(
            clientId = clientId,
            tokenEndpointAuthMethod = method,
            grantTypes = listOf(GrantType.CLIENT_CREDENTIALS),
            trustedAttesterJwks = trustedAttesterJwks,
            trustedAttesterIssuers = trustedAttesterIssuers,
        )

    private fun trustedWalletInstanceEvidence(
        evidenceId: String = "persisted-wia-1",
        profile: String = "TS03_JWT",
        format: String = "JWT",
        signerCertificateProfile: String? = "HARDWARE_SECURE",
        status: WalletInstanceClientStatusEvidence =
            WalletInstanceClientStatusEvidence(
                statusListUri = "https://status.example.com/wia/status.jwt",
                index = "42",
                status = "VALID",
                revoked = false,
                maintenanceExpiresAtEpochSeconds = Clock.System.now().epochSeconds + 600,
            ),
        trust: WalletInstanceTrustEvidence =
            WalletInstanceTrustEvidence(
                trusted = true,
                decision = "TRUSTED",
                expiresAtEpochSeconds = Clock.System.now().epochSeconds + 600,
                signerCertificateProfile = signerCertificateProfile,
            ),
    ): WalletInstanceAttestationEvidence =
        WalletInstanceAttestationEvidence(
            evidenceId = evidenceId,
            profile = profile,
            format = format,
            attestationExpiresAtEpochSeconds = Clock.System.now().epochSeconds + 600,
            clientStatus = status,
            trust = trust,
            walletInstanceId = "wallet-instance-1",
            walletProvider = "wallet-provider",
            walletSolution = "wallet-solution",
            signerCertificateProfile = signerCertificateProfile,
        )

    private fun newCommand(
        clientRegistry: ClientRegistry = StubClientRegistry(clientRegistration()),
        jwtService: JwtService,
        config: OAuth2ServerInstanceConfig =
            OAuth2ServerInstanceConfig(
                issuer = asIssuer,
                attestation = FeaturePolicy.SUPPORTED,
            ),
        challengeStorage: AttestationChallengeStorage = InMemoryAttestationChallengeStorage(defaultSecureRandom()),
        jtiStorage: AttestationPopJtiStorage = InMemoryAttestationPopJtiStorage(),
        x509TrustAnchorLoader: X509TrustAnchorLoader = EmptyX509TrustAnchorLoader,
        identifierService: IdentifierService = UnreachableIdentifierService,
        walletInstanceAttestationEnforcer: WalletInstanceAttestationEnforcer? = null,
    ): VerifyAttestationClientAuthCommandImpl =
        VerifyAttestationClientAuthCommandImpl(
            ctx.execution,
            clientRegistry,
            jwtService,
            TestOAuth2ServersConfigProvider(OAuth2ServersConfig(servers = mapOf("default" to config))),
            challengeStorage,
            jtiStorage,
            x509TrustAnchorLoader,
            identifierService,
            walletInstanceAttestationEnforcer,
        )

    private fun args(
        clientId: String = "client-att",
        attestation: String = attestationJwt(attestationClaims()),
        pop: String = popJwt(popClaims()),
        endpoint: ClientAuthenticationEndpoint = ClientAuthenticationEndpoint.TOKEN,
    ) = VerifyAttestationClientAuthArgs(
        clientId = clientId,
        attestationJwt = attestation,
        popJwt = pop,
        tokenEndpointUrl = tokenEndpointUrl,
        endpoint = endpoint,
    )

    @Test
    fun happyPath_withValidAttestationAndPop_accepts() =
        runTest {
            val popPayload = popClaims()
            val jwt =
                StubJwtService(
                    parsedPayloads =
                        listOf(
                            JsonObject(emptyMap()),
                            popPayload,
                        ),
                )
            val result =
                newCommand(jwtService = jwt).execute(
                    args(pop = popJwt(popPayload)),
                )

            assertTrue(result.isOk, "expected accept; got: ${if (result.isErr) result.error.message.defaultMessage else ""}")
            assertEquals("client-att", result.value.clientId)
            assertEquals(ClientAuthenticationMethod.ATTEST_JWT_CLIENT_AUTH, result.value.method)
            assertEquals("instance-key-1", result.value.clientInstanceKey?.kid)
        }

    @Test
    fun untrustedAttesterIssuer_rejectsWithInvalidClientAttestation() =
        runTest {
            val registry =
                StubClientRegistry(
                    clientRegistration(
                        trustedAttesterIssuers = listOf("only-this-attester"),
                    ),
                )
            val attClaims = attestationClaims(iss = "rogue-attester")
            val popPayload = popClaims()
            val jwt =
                StubJwtService(
                    parsedPayloads = listOf(JsonObject(emptyMap()), popPayload),
                )
            val result =
                newCommand(clientRegistry = registry, jwtService = jwt).execute(
                    args(
                        attestation = attestationJwt(attClaims),
                        pop = popJwt(popPayload),
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun stalePopIat_rejectsWithInvalidClientAttestation() =
        runTest {
            // popMaxAgeSeconds default is 60s; iat 600 seconds in the past blows the freshness window.
            val popPayload = popClaims(iatSecondsFromNow = -600)
            val jwt =
                StubJwtService(
                    parsedPayloads = listOf(JsonObject(emptyMap()), popPayload),
                )
            val result = newCommand(jwtService = jwt).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun replayedChallenge_rejectsWithInvalidClientAttestation() =
        runTest {
            val challengeStorage = InMemoryAttestationChallengeStorage(defaultSecureRandom())
            val challenge =
                challengeStorage.generateChallenge().getOrElse { error("could not generate challenge") }
            val config =
                OAuth2ServerInstanceConfig(
                    issuer = asIssuer,
                    attestation = FeaturePolicy.SUPPORTED,
                    attestationChallengeRequired = true,
                )
            val popPayload = popClaims(challenge = challenge)

            val command =
                newCommand(
                    jwtService =
                        StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload)),
                    config = config,
                    challengeStorage = challengeStorage,
                )
            val firstResult = command.execute(args(pop = popJwt(popPayload)))
            assertTrue(firstResult.isOk, "first redemption should succeed")

            // Second use of the same challenge with a fresh PoP must be rejected because the
            // challenge is one-time-use even though every other claim is fine.
            val replayCommand =
                newCommand(
                    jwtService =
                        StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload)),
                    config = config,
                    challengeStorage = challengeStorage,
                )
            val replayResult = replayCommand.execute(args(pop = popJwt(popPayload)))
            assertTrue(replayResult.isErr)
        }

    @Test
    fun missingCnfJwk_rejectsWithInvalidClientAttestation() =
        runTest {
            val claimsWithoutCnfJwk =
                buildJsonObject {
                    put("iss", "trusted-attester")
                    put("sub", "client-att")
                    put("exp", kotlinx.serialization.json.JsonPrimitive(Clock.System.now().epochSeconds + 300))
                    put("cnf", buildJsonObject { put("not_jwk", "value") })
                }
            val popPayload = popClaims()
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result =
                newCommand(jwtService = jwt).execute(
                    args(
                        attestation = attestationJwt(claimsWithoutCnfJwk),
                        pop = popJwt(popPayload),
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun popVerifyFails_rejectsWithInvalidClientAttestation() =
        runTest {
            // Two verifyJws calls: attestation succeeds, PoP fails. Stub returns isValid=false on
            // call index 1 to model "PoP signed by wrong key".
            val popPayload = popClaims()
            val jwt =
                StubJwtService(
                    parsedPayloads = listOf(JsonObject(emptyMap()), popPayload),
                    isValidPerCall = listOf(true, false),
                )
            val result = newCommand(jwtService = jwt).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun popIssDoesNotMatchAttestationSub_rejectsWithInvalidClientAttestation() =
        runTest {
            val popPayload = popClaims(iss = "different-client")
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result = newCommand(jwtService = jwt).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun popAudMismatch_rejectsWithInvalidClientAttestation() =
        runTest {
            val popPayload = popClaims(aud = "https://other.example.com/somewhere")
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result = newCommand(jwtService = jwt).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun expiredAttestation_rejectsWithUseFreshAttestation() =
        runTest {
            val attClaims = attestationClaims(expSecondsFromNow = -60)
            val popPayload = popClaims()
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result =
                newCommand(jwtService = jwt).execute(
                    args(attestation = attestationJwt(attClaims), pop = popJwt(popPayload)),
                )

            assertTrue(result.isErr)
            assertEquals("use_fresh_attestation", result.error.code)
        }

    @Test
    fun challengeRequiredButMissing_rejectsWithUseAttestationChallenge() =
        runTest {
            val popPayload = popClaims(challenge = null)
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val config =
                OAuth2ServerInstanceConfig(
                    issuer = asIssuer,
                    attestation = FeaturePolicy.SUPPORTED,
                    attestationChallengeRequired = true,
                )
            val result =
                newCommand(jwtService = jwt, config = config).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("use_attestation_challenge", result.error.code)
            assertTrue(
                result.error.meta["attestation_challenge"] is String,
                "expected attestation_challenge to be returned in error meta",
            )
        }

    @Test
    fun staleAttestationIat_rejectsWithUseFreshAttestation() =
        runTest {
            // attestationMaxLifetimeSeconds default is 600s; iat 1 day ago blows the lifetime window.
            val attClaims = attestationClaims(iatSecondsFromNow = -86_400)
            val popPayload = popClaims()
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result =
                newCommand(jwtService = jwt).execute(
                    args(attestation = attestationJwt(attClaims), pop = popJwt(popPayload)),
                )

            assertTrue(result.isErr)
            assertEquals("use_fresh_attestation", result.error.code)
        }

    @Test
    fun attestationDisabled_rejectsWithInvalidClient() =
        runTest {
            val config =
                OAuth2ServerInstanceConfig(
                    issuer = asIssuer,
                    attestation = FeaturePolicy.DISABLED,
                )
            val result = newCommand(jwtService = StubJwtService(), config = config).execute(args())

            assertTrue(result.isErr)
            assertEquals("invalid_client", result.error.code)
        }

    @Test
    fun clientNotRegisteredForAttestation_rejectsWithInvalidClient() =
        runTest {
            val registry =
                StubClientRegistry(
                    clientRegistration(method = ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
                )
            val popPayload = popClaims()
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result =
                newCommand(clientRegistry = registry, jwtService = jwt).execute(
                    args(pop = popJwt(popPayload)),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_client", result.error.code)
        }

    @Test
    fun missingPopJti_rejectsWithInvalidClientAttestation() =
        runTest {
            // §5.2 jti is REQUIRED in both draft-07 and draft-08. Missing → reject.
            val popPayload = popClaims(jti = null)
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result = newCommand(jwtService = jwt).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun missingPopIat_rejectsWithInvalidClientAttestation() =
        runTest {
            // §5.2 iat is REQUIRED in both draft-07 and draft-08. Missing → reject.
            val popPayload = popClaims(iatSecondsFromNow = null)
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result = newCommand(jwtService = jwt).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun replayedPopJti_rejectsWithInvalidClientAttestation() =
        runTest {
            // §10.5 / §12.1: the AS keeps a sliding window of seen jti values and rejects any
            // replay. Two commands sharing one jtiStorage exercise that path.
            val sharedJtiStorage = InMemoryAttestationPopJtiStorage()
            val popPayload = popClaims()
            val firstCommand =
                newCommand(
                    jwtService = StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload)),
                    jtiStorage = sharedJtiStorage,
                )
            val firstResult = firstCommand.execute(args(pop = popJwt(popPayload)))
            assertTrue(firstResult.isOk, "first submission must succeed")

            val replayCommand =
                newCommand(
                    jwtService = StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload)),
                    jtiStorage = sharedJtiStorage,
                )
            val replayResult = replayCommand.execute(args(pop = popJwt(popPayload)))
            assertTrue(replayResult.isErr)
            assertEquals("invalid_client_attestation", replayResult.error.code)
        }

    @Test
    fun popWithoutIss_accepted_perDraft08() =
        runTest {
            // Draft-08 dropped `iss` from the PoP claim set. A spec-compliant PoP without `iss`
            // must be accepted (the cnf.jwk binding is the only required link to the attestation).
            val popPayload = popClaims(iss = null)
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result = newCommand(jwtService = jwt).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isOk, "expected accept; got: ${if (result.isErr) result.error.message.defaultMessage else ""}")
        }

    @Test
    fun attestationWithoutIss_accepted_perDraft08() =
        runTest {
            // Draft-08 dropped `iss` from the attestation claim set. Trust is enforced through
            // the pinned trustedAttesterJwks; missing `iss` no longer triggers a hard reject.
            val claimsWithoutIss =
                buildJsonObject {
                    put("sub", "client-att")
                    put("exp", kotlinx.serialization.json.JsonPrimitive(Clock.System.now().epochSeconds + 300))
                    put("iat", kotlinx.serialization.json.JsonPrimitive(Clock.System.now().epochSeconds))
                    put("cnf", buildJsonObject { put("jwk", cnfJwkObject("instance-key-1")) })
                }
            val popPayload = popClaims()
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result =
                newCommand(jwtService = jwt).execute(
                    args(attestation = attestationJwt(claimsWithoutIss), pop = popJwt(popPayload)),
                )

            assertTrue(result.isOk, "expected accept; got: ${if (result.isErr) result.error.message.defaultMessage else ""}")
        }

    @Test
    fun productionWalletInstanceAttestationWithoutEnforcer_rejects() =
        runTest {
            val popPayload = popClaims()
            val config =
                OAuth2ServerInstanceConfig(
                    issuer = asIssuer,
                    attestation = FeaturePolicy.SUPPORTED,
                    walletInstanceAttestation = FeaturePolicy.REQUIRED,
                )
            val result =
                newCommand(
                    jwtService = StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload)),
                    config = config,
                ).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun localWalletInstanceAttestationEvidence_rejects() =
        runTest {
            val popPayload = popClaims()
            val config =
                OAuth2ServerInstanceConfig(
                    issuer = asIssuer,
                    attestation = FeaturePolicy.SUPPORTED,
                    walletInstanceAttestation = FeaturePolicy.REQUIRED,
                )
            val result =
                newCommand(
                    jwtService = StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload)),
                    config = config,
                    walletInstanceAttestationEnforcer =
                        StubWalletInstanceAttestationEnforcer(
                            trustedWalletInstanceEvidence(
                                profile = "LOCAL_TEST_REFERENCE",
                                signerCertificateProfile = "SOFTWARE_TEST",
                            ),
                        ),
                ).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun revokedWalletInstanceAttestationEvidence_rejects() =
        runTest {
            val popPayload = popClaims()
            val config =
                OAuth2ServerInstanceConfig(
                    issuer = asIssuer,
                    attestation = FeaturePolicy.SUPPORTED,
                    walletInstanceAttestation = FeaturePolicy.REQUIRED,
                )
            val result =
                newCommand(
                    jwtService = StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload)),
                    config = config,
                    walletInstanceAttestationEnforcer =
                        StubWalletInstanceAttestationEnforcer(
                            trustedWalletInstanceEvidence(
                                status =
                                    WalletInstanceClientStatusEvidence(
                                        statusListUri = "https://status.example.com/wia/status.jwt",
                                        index = "42",
                                        status = "REVOKED",
                                        revoked = true,
                                    ),
                            ),
                        ),
                ).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isErr)
            assertEquals("invalid_client_attestation", result.error.code)
        }

    @Test
    fun trustedWalletInstanceAttestationEvidence_acceptsAndPropagates() =
        runTest {
            val popPayload = popClaims()
            val config =
                OAuth2ServerInstanceConfig(
                    issuer = asIssuer,
                    attestation = FeaturePolicy.SUPPORTED,
                    walletInstanceAttestation = FeaturePolicy.REQUIRED,
                )
            val enforcer = StubWalletInstanceAttestationEnforcer(trustedWalletInstanceEvidence())
            val result =
                newCommand(
                    jwtService = StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload)),
                    config = config,
                    walletInstanceAttestationEnforcer = enforcer,
                ).execute(args(pop = popJwt(popPayload), endpoint = ClientAuthenticationEndpoint.PAR))

            assertTrue(result.isOk, "expected accept; got: ${if (result.isErr) result.error.message.defaultMessage else ""}")
            assertEquals("persisted-wia-1", result.value.walletInstanceAttestation?.evidenceId)
            assertEquals(ClientAuthenticationEndpoint.PAR, enforcer.requests.single().endpoint)
            assertEquals(setOf(asIssuer, tokenEndpointUrl), enforcer.requests.single().acceptedAudiences)
        }

    @Test
    fun challengeClaimNamedChallenge_accepted() =
        runTest {
            // Normative §5.2 names the OPTIONAL challenge claim `challenge`; verify we read it
            // under that name (the §6.1 `nonce` example is also accepted as a fallback).
            val challengeStorage = InMemoryAttestationChallengeStorage(defaultSecureRandom())
            val challenge =
                challengeStorage.generateChallenge().getOrElse { error("could not generate challenge") }
            val config =
                OAuth2ServerInstanceConfig(
                    issuer = asIssuer,
                    attestation = FeaturePolicy.SUPPORTED,
                    attestationChallengeRequired = true,
                )
            val popPayload = popClaims(challenge = challenge, challengeClaimName = "challenge")
            val jwt =
                StubJwtService(parsedPayloads = listOf(JsonObject(emptyMap()), popPayload))
            val result =
                newCommand(
                    jwtService = jwt,
                    config = config,
                    challengeStorage = challengeStorage,
                ).execute(args(pop = popJwt(popPayload)))

            assertTrue(result.isOk, "expected accept; got: ${if (result.isErr) result.error.message.defaultMessage else ""}")
        }

    // ========================================================================
    // Stubs
    // ========================================================================

    /**
     * Stub identifier service used by tests that exercise the JWK-pinning path. The HAIP x5c
     * fallback only fires when both an x5c JOSE header and at least one trusted cert are
     * present; with [EmptyX509TrustAnchorLoader] returning an empty list, this stub is
     * unreachable in the JWK-pinning tests and never has to resolve anything real.
     */
    private object UnreachableIdentifierService : IdentifierService {
        override val supportedIdentifierMethods: List<IIdentifierMethod> = emptyList()

        override suspend fun isSupportedIdentifier(identifier: Any): Boolean = false

        override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean = false

        override suspend fun isSupportedOpts(opts: IdentifierOptsOrResult): Boolean = false

        override suspend fun asSupportedOpts(opts: IdentifierOptsOrResult): IdkResult<IdentifierOptsOrResult, IdkErrorType> = error("IdentifierService.asSupportedOpts is not exercised by these tests")

        override suspend fun resolve(opts: IdentifierOptsOrResult): IdkResult<out IdentifierOptsOrResult, IdkErrorType> = error("IdentifierService.resolve is not exercised by these tests")
    }

    private object EmptyX509TrustAnchorLoader : X509TrustAnchorLoader {
        override suspend fun loadTrustedCerts(): List<String> = emptyList()
    }

    private class StubWalletInstanceAttestationEnforcer(
        private val evidence: WalletInstanceAttestationEvidence,
    ) : WalletInstanceAttestationEnforcer {
        val requests = mutableListOf<WalletInstanceAttestationEnforcementRequest>()

        override suspend fun enforce(request: WalletInstanceAttestationEnforcementRequest): IdkResult<WalletInstanceAttestationEvidence, IdkError> {
            requests += request
            return Ok(evidence)
        }
    }

    private class StubClientRegistry(
        private val client: ClientRegistration? = null,
    ) : ClientRegistry {
        override suspend fun getClient(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError> = Ok(client)

        override suspend fun registerClient(registration: ClientRegistration): IdkResult<ClientRegistration, AuthorizationServerError.StorageError> = Ok(registration)

        override suspend fun updateClient(
            clientId: String,
            registration: ClientRegistration,
        ): IdkResult<ClientRegistration, AuthorizationServerError> = Ok(registration)

        override suspend fun deleteClient(clientId: String): IdkResult<Unit, AuthorizationServerError> = Ok(Unit)

        override suspend fun listClients(
            limit: Int,
            offset: Int,
        ): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

        override suspend fun findClientsByName(name: String): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

        override suspend fun clientExists(clientId: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(client != null)

        override suspend fun verifyClientCredentials(
            clientId: String,
            clientSecret: String,
        ): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(false)
    }

    /**
     * JwtService stub that returns deterministic [JwsValidationResult]s. Each call to
     * [verifyJws] consumes one entry from [parsedPayloads] and one from [isValidPerCall].
     * The first call corresponds to the attestation JWT, the second to the PoP JWT.
     */
    private class StubJwtService(
        private val parsedPayloads: List<JsonObject> = emptyList(),
        private val isValidPerCall: List<Boolean> = parsedPayloads.map { true },
    ) : JwtService {
        private val notImpl =
            IdkError(
                code = "not_implemented",
                message = IdkError.Message(i18nKey = "", defaultMessage = "Not implemented"),
            )
        private var callIndex = 0

        override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = Err(notImpl)

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> = Err(notImpl)

        override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = Err(notImpl)

        override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = Err(notImpl)

        override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> {
            val idx = callIndex
            callIndex += 1
            val payload = parsedPayloads.getOrElse(idx) { JsonObject(emptyMap()) }
            val isValid = isValidPerCall.getOrElse(idx) { true }
            return Ok(
                JwsValidationResult(
                    jws = JwsJsonGeneralWithIdentifiers(payload = "", signatures = emptyList()),
                    isValid = isValid,
                    parsedPayload = payload,
                ),
            )
        }

        override fun assembleJwsGeneral(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ): JwsJsonGeneral = throw NotImplementedError()

        override fun assembleJwsFlattened(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ): JwsJsonFlattened = throw NotImplementedError()

        override fun assembleJwsCompact(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ): JwtCompactResult = throw NotImplementedError()

        override val commands: JwtService.Commands
            get() = throw NotImplementedError()
    }
}
