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
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.defaults.context.JwtClaimsParser
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.resolution.IdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication
import com.sphereon.oauth2.server.authorization.command.clientauth.VerifyAttestationClientAuthArgs
import com.sphereon.oauth2.server.authorization.command.clientauth.VerifyAttestationClientAuthCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.storage.AttestationChallengeStorage
import com.sphereon.oauth2.server.authorization.storage.AttestationPopJtiStorage
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.trust.x509.X509TrustAnchorLoader
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.putJsonArray
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * Implementation of [VerifyAttestationClientAuthCommand]. Runs the verification path defined by
 * draft-ietf-oauth-attestation-based-client-auth, written to satisfy both draft-07 (referenced
 * by OpenID for Verifiable Credential Issuance 1.0 final) and draft-08 (latest):
 *
 * 1. Parse + validate the attestation header `typ` (`oauth-client-attestation+jwt`).
 * 2. Decode attestation claims and read `sub` (REQUIRED in both drafts), `exp` (REQUIRED),
 *    `cnf` (REQUIRED), and the OPTIONAL `iss`/`iat` claims when present. Draft-07 placed
 *    `iss` in the JWT body; draft-08 dropped it. We accept either: signature trust is the
 *    primary gate, and a configured `trustedAttesterIssuers` allow-list only applies when the
 *    attestation actually carries an `iss`.
 * 3. Resolve the [ClientRegistration]; the `sub` is the client_id when the request did not
 *    supply one.
 * 4. Enforce the registered `token_endpoint_auth_method = attest_jwt_client_auth` and the
 *    per-client trusted attester allow-list when the attestation has an `iss`.
 * 5. Verify the attestation signature against the client's pinned `trustedAttesterJwks`. The
 *    AS MUST refuse to defer to embedded `jwk`/`x5c` headers or external resolvers per draft §4.
 * 6. Enforce attestation expiration and `attestationMaxLifetimeSeconds` freshness.
 * 7. Extract the client instance public key from `cnf.jwk`.
 * 8. Parse + validate the PoP header `typ` (`oauth-client-attestation-pop+jwt`).
 * 9. Verify the PoP signature against a single-key JWKS built from the attestation `cnf.jwk`.
 * 10. Validate PoP claims: `aud` references AS issuer or token URL (REQUIRED), `iat` within
 *     `attestationPopMaxAgeSeconds` (REQUIRED in both drafts), `jti` present (REQUIRED in both
 *     drafts). Draft-07 also requires `iss == attestation.sub`; we apply that check only when
 *     the PoP carries an `iss` so a draft-08-compliant PoP without `iss` is still accepted.
 * 11. Replay-detect the PoP `jti` against [AttestationPopJtiStorage] using the configured
 *     sliding window (§10.5 / §12.1). The dedup table is anchored on the PoP `iat`.
 * 12. When [com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig.attestationChallengeRequired]
 *     is on, verify the PoP `challenge` claim against [AttestationChallengeStorage]. Missing
 *     challenge yields [AuthorizationServerError.UseAttestationChallenge] carrying a freshly
 *     generated challenge so the client can retry. The §6.1 examples in both drafts encode the
 *     claim as `nonce` despite the §5.2 normative table naming it `challenge`; we read the
 *     normative `challenge` first and fall back to `nonce` for interop with libraries written
 *     against the example.
 * 13. On success, emit a [VerifiedClientAuthentication] with the resolved client_id and the
 *     extracted client instance key for downstream binding.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerifyAttestationClientAuthCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyAttestationClientAuthCommandImpl", exact = true)
class VerifyAttestationClientAuthCommandImpl(
    execution: SessionExecution,
    private val clientRegistry: ClientRegistry,
    private val jwtService: JwtService,
    private val configProvider: OAuth2ServersConfigProvider,
    private val challengeStorage: AttestationChallengeStorage,
    private val jtiStorage: AttestationPopJtiStorage,
    private val x509TrustAnchorLoader: X509TrustAnchorLoader,
    private val identifierService: IdentifierService,
) : TypedServiceCommandAdapter<VerifyAttestationClientAuthArgs, VerifiedClientAuthentication, IdkError>(
        commandId = VerifyAttestationClientAuthCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyAttestationClientAuthArgs>(),
        outputTypeToken = typeToken<VerifiedClientAuthentication>(),
    ),
    VerifyAttestationClientAuthCommand {
    override val commandId: String get() = VerifyAttestationClientAuthCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyAttestationClientAuthArgs

    override suspend fun doExecute(
        args: VerifyAttestationClientAuthArgs,
        applyDuring: (VerifyAttestationClientAuthArgs) -> VerifyAttestationClientAuthArgs,
    ): IdkResult<VerifiedClientAuthentication, IdkError> {
        val applied = applyDuring(args)
        return verify(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun verify(args: VerifyAttestationClientAuthArgs): IdkResult<VerifiedClientAuthentication, AuthorizationServerError> {
        val config = configProvider.serverConfig
        if (!config.attestation.isEnabled) {
            return Err(
                AuthorizationServerError.InvalidClient(
                    details = "Attestation-based client authentication is not enabled",
                ),
            )
        }

        val attestationJwt = args.attestationJwt
        val popJwt = args.popJwt

        // 1. Parse and validate attestation JWT header
        val attestationHeader =
            parseJwtHeader(attestationJwt)
                ?: return Err(
                    AuthorizationServerError.InvalidClientAttestation(
                        details = "Cannot parse attestation JWT header",
                    ),
                )

        val attestationTyp = attestationHeader["typ"]?.jsonPrimitive?.content
        if (attestationTyp != "oauth-client-attestation+jwt") {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details = "Attestation JWT typ must be 'oauth-client-attestation+jwt', got: $attestationTyp",
                ),
            )
        }

        // 2. Parse attestation JWT claims (lightweight decode)
        val attestationClaims =
            JwtClaimsParser.parseClaimsOrNull(attestationJwt)
                ?: return Err(
                    AuthorizationServerError.InvalidClientAttestation(
                        details = "Cannot parse attestation JWT claims",
                    ),
                )

        // `iss` is OPTIONAL: draft-07 listed it as a required body claim, draft-08 dropped it.
        // We accept either shape and only enforce the operator-configured trustedAttesterIssuers
        // allow-list when `iss` is actually present; trust is otherwise enforced through the
        // pinned `trustedAttesterJwks` signature check below.
        val attIss = attestationClaims["iss"]?.jsonPrimitive?.content
        val attSub =
            attestationClaims["sub"]?.jsonPrimitive?.content
                ?: return Err(AuthorizationServerError.InvalidClientAttestation(details = "Missing sub in attestation JWT"))
        val attExp =
            attestationClaims["exp"]?.jsonPrimitive?.long
                ?: return Err(AuthorizationServerError.InvalidClientAttestation(details = "Missing exp in attestation JWT"))

        // Use sub as client_id when no explicit client_id was supplied.
        val resolvedClientId = args.clientId.ifEmpty { attSub }

        // 3. Look up client registration. Per HAIP §4.4.1 the wallet attestation `sub` is shared
        // across wallet instances of the same Wallet Provider — it MAY not match a pre-registered
        // client. When the deployment carries x5c trust anchors (i.e. the Wallet Provider's CA),
        // we synthesize a transient registration so the attestation chain is the trust boundary
        // instead of a per-client JWK pinning. Without trust anchors, the missing client_id is a
        // hard ClientNotFound — matches the strict draft -07/-08 default.
        val attestationX5c = readX5cFromHeader(attestationHeader)
        val x509TrustedCerts = x509TrustAnchorLoader.loadTrustedCerts()
        val haipX5cTrustAvailable = attestationX5c != null && x509TrustedCerts.isNotEmpty()

        val client =
            clientRegistry
                .getClient(resolvedClientId)
                .getOrElse { return Err(it) }
                ?: if (haipX5cTrustAvailable) {
                    synthesizeHaipTransientClient(resolvedClientId)
                } else {
                    return Err(AuthorizationServerError.ClientNotFound(clientId = resolvedClientId))
                }

        if (client.tokenEndpointAuthMethod != ClientAuthenticationMethod.ATTEST_JWT_CLIENT_AUTH) {
            return Err(
                AuthorizationServerError.InvalidClient(
                    details = "Client is not configured for attestation-based authentication",
                ),
            )
        }

        // 4. Verify attester issuer is trusted (only when both the operator pinned an allow-list
        // and the attestation actually carries an `iss` — see iss-OPTIONAL note above).
        val trustedIssuers = client.trustedAttesterIssuers
        if (trustedIssuers != null && attIss != null && attIss !in trustedIssuers) {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details = "Attestation issuer '$attIss' is not trusted for client '$resolvedClientId'",
                ),
            )
        }

        // 5. Verify attestation JWT signature. Two trust modes are supported:
        //  (a) JWK pinning — the per-client `trustedAttesterJwks` allow-list (draft -07/-08 §4
        //      strict default; AS refuses embedded `jwk`/`x5c` headers).
        //  (b) HAIP §4.4.1 x5c — the attestation carries the wallet attester's leaf cert (and
        //      optional intermediates, MUST NOT include the trust anchor) in the JOSE `x5c`
        //      header. The AS verifies the chain against `trust.anchors.x509.*` CA bundles and
        //      uses the leaf's public key as the signer key.
        // Mode selection prefers (a) when configured; (b) requires both an x5c header and at
        // least one configured trust anchor.
        val trustedAttesterKeys = client.trustedAttesterJwks
        val attesterJwks: JsonObject =
            when {
                !trustedAttesterKeys.isNullOrEmpty() -> {
                    jwksDocument(trustedAttesterKeys.map { Json.encodeToJsonElement(Jwk.serializer(), it).jsonObject })
                }

                haipX5cTrustAvailable && attestationX5c != null -> {
                    val headerKid = attestationHeader["kid"]?.jsonPrimitive?.content
                    val haipResult = verifyAttestationX5cChain(attestationX5c, x509TrustedCerts, headerKid)
                    haipResult.getOrElse { return Err(it) }
                }

                else -> {
                    return Err(
                        AuthorizationServerError.InvalidClient(
                            details =
                                "Client '$resolvedClientId' is registered for attestation-based " +
                                    "authentication but has no trusted attester material configured. " +
                                    "Either pin attester JWKs at `oauth2.clients.<id>.trusted-attester-jwks.<n>.*` " +
                                    "or load the wallet-attester CA at `trust.anchors.x509.ca-bundle-paths.<n>` " +
                                    "(HAIP §4.4.1) and ensure the attestation carries an x5c header.",
                        ),
                    )
                }
            }
        val attestationVerifyResult =
            jwtService
                .verifyJws(VerifyJwsArgs(jws = JwsCompact(attestationJwt), trustedJwks = attesterJwks))
                .getOrElse {
                    return Err(
                        AuthorizationServerError.InvalidClientAttestation(
                            details = "Attestation JWT signature verification failed: ${it.message.defaultMessage}",
                        ),
                    )
                }

        if (!attestationVerifyResult.isValid) {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details = "Attestation JWT signature invalid: ${attestationVerifyResult.errorMessages.joinToString()}",
                ),
            )
        }

        // 6. Check expiration and freshness
        val now = Clock.System.now().epochSeconds
        if (attExp < now) {
            return Err(
                AuthorizationServerError.UseFreshAttestation(
                    details = "Attestation JWT has expired",
                ),
            )
        }

        val attIat = attestationClaims["iat"]?.jsonPrimitive?.long
        if (attIat != null && (now - attIat) > config.attestationMaxLifetimeSeconds) {
            return Err(
                AuthorizationServerError.UseFreshAttestation(
                    details = "Attestation JWT is too old (issued ${now - attIat}s ago, max ${config.attestationMaxLifetimeSeconds}s)",
                ),
            )
        }

        // 7. Extract client instance public key from cnf.jwk
        val cnf =
            attestationClaims["cnf"]?.jsonObject
                ?: return Err(
                    AuthorizationServerError.InvalidClientAttestation(
                        details = "Missing cnf claim in attestation JWT",
                    ),
                )

        val cnfJwkJson =
            cnf["jwk"]?.jsonObject
                ?: return Err(
                    AuthorizationServerError.InvalidClientAttestation(
                        details = "Missing jwk in cnf claim",
                    ),
                )

        val clientInstanceKey =
            try {
                Json.decodeFromJsonElement(Jwk.serializer(), cnfJwkJson)
            } catch (expected: Exception) {
                return Err(
                    AuthorizationServerError.InvalidClientAttestation(
                        details = "Invalid JWK in cnf claim: ${expected.message}",
                    ),
                )
            }

        // 8. Parse and validate PoP JWT header
        val popHeader =
            parseJwtHeader(popJwt)
                ?: return Err(
                    AuthorizationServerError.InvalidClientAttestation(
                        details = "Cannot parse attestation PoP JWT header",
                    ),
                )

        val popTyp = popHeader["typ"]?.jsonPrimitive?.content
        if (popTyp != "oauth-client-attestation-pop+jwt") {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details = "PoP JWT typ must be 'oauth-client-attestation-pop+jwt', got: $popTyp",
                ),
            )
        }

        // 9. Verify PoP JWT signature with the client instance key declared in the attestation's
        // `cnf.jwk`. The PoP MUST be signed by exactly that key
        // (draft-ietf-oauth-attestation-based-client-auth §5), so pin verification to a
        // single-key trusted JWKS document built from `cnfJwkJson`.
        val cnfJwks = jwksDocument(listOf(cnfJwkJson))
        val popVerifyResult =
            jwtService
                .verifyJws(VerifyJwsArgs(jws = JwsCompact(popJwt), trustedJwks = cnfJwks))
                .getOrElse {
                    return Err(
                        AuthorizationServerError.InvalidClientAttestation(
                            details = "PoP JWT signature verification failed: ${it.message.defaultMessage}",
                        ),
                    )
                }

        if (!popVerifyResult.isValid) {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details = "PoP JWT signature invalid: ${popVerifyResult.errorMessages.joinToString()}",
                ),
            )
        }

        // 10. Validate PoP JWT claims (already decoded during verification). `iss` is OPTIONAL —
        // draft-07 required `iss == attestation.sub`, draft-08 dropped the claim. We enforce the
        // match only when present so a draft-08-compliant PoP without `iss` is still accepted;
        // the cnf.jwk binding in step 9 already cryptographically links PoP → attestation.
        val popClaims = popVerifyResult.parsedPayload

        val popIss = popClaims["iss"]?.jsonPrimitive?.content
        if (popIss != null && popIss != attSub) {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details = "PoP JWT iss ('$popIss') must match attestation sub ('$attSub')",
                ),
            )
        }

        val popAud =
            popClaims["aud"]?.jsonPrimitive?.content
                ?: return Err(
                    AuthorizationServerError.InvalidClientAttestation(details = "Missing aud in PoP JWT"),
                )
        val configIssuer = config.issuer
        if (popAud != args.tokenEndpointUrl && popAud != configIssuer) {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details = "PoP JWT aud does not match AS issuer or token endpoint",
                ),
            )
        }

        // PoP `iat` is REQUIRED in both drafts (§5.2). Reject when missing or stale.
        val popIat =
            popClaims["iat"]?.jsonPrimitive?.long
                ?: return Err(
                    AuthorizationServerError.InvalidClientAttestation(details = "Missing iat in PoP JWT"),
                )
        if ((now - popIat) > config.attestationPopMaxAgeSeconds) {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details = "PoP JWT is too old (issued ${now - popIat}s ago, max ${config.attestationPopMaxAgeSeconds}s)",
                ),
            )
        }

        // PoP `jti` is REQUIRED in both drafts and is the AS-side replay-detection key (§10.5,
        // §12.1). Record-or-reject in a single atomic call so concurrent submissions of the same
        // jti can't both pass.
        val popJti =
            popClaims["jti"]?.jsonPrimitive?.content
                ?: return Err(
                    AuthorizationServerError.InvalidClientAttestation(details = "Missing jti in PoP JWT"),
                )
        jtiStorage
            .recordOrReject(
                jti = popJti,
                iat = popIat,
                windowSeconds = config.attestationPopJtiReplayWindowSeconds,
            ).getOrElse { return Err(it) }

        // 11. If challenge required, verify challenge claim. The §5.2 normative claim name is
        // `challenge`; the §6.1 encoded examples use `nonce` (a known editorial inconsistency
        // tracked in the document history). Read `challenge` first and fall back to `nonce`.
        if (config.attestationChallengeRequired) {
            val challengeClaim =
                popClaims["challenge"]?.jsonPrimitive?.content
                    ?: popClaims["nonce"]?.jsonPrimitive?.content
            if (challengeClaim == null) {
                val newChallenge =
                    challengeStorage
                        .generateChallenge()
                        .getOrElse { return Err(it) }
                return Err(AuthorizationServerError.UseAttestationChallenge(challenge = newChallenge))
            }

            challengeStorage
                .verifyAndConsumeChallenge(challengeClaim)
                .getOrElse { error -> return Err(error) }
        }

        // 12. All checks passed
        return Ok(
            VerifiedClientAuthentication(
                clientId = resolvedClientId,
                method = ClientAuthenticationMethod.ATTEST_JWT_CLIENT_AUTH,
                clientInstanceKey = clientInstanceKey,
            ),
        )
    }

    /** Build a JWKS document (`{"keys": [...]}`) so `verifyJws` accepts only those keys as signers. */
    private fun jwksDocument(keys: List<JsonObject>): JsonObject =
        buildJsonObject {
            putJsonArray("keys") {
                keys.forEach { add(it) }
            }
        }

    /** Parse the header portion of a JWT without full verification. */
    private fun parseJwtHeader(jwt: String): JsonObject? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) {
                return null
            }
            JwsUtils.decodeBase64UrlToJson(parts[0])
        } catch (_: Exception) {
            null
        }
    }

    /** Read the `x5c` JOSE header parameter as a list of base64-encoded DER certs (RFC 7515 §4.1.6). */
    private fun readX5cFromHeader(header: JsonObject): List<String>? {
        val x5cElement = header["x5c"] as? JsonArray ?: return null
        if (x5cElement.isEmpty()) return null
        return x5cElement.map { it.jsonPrimitive.content }
    }

    /**
     * HAIP §4.4.1 wallet-attestation x5c trust path. Uses the canonical
     * [IdentifierOptsOrResult][com.sphereon.crypto.resolution.IdentifierOptsOrResult] flow:
     * wrap the raw chain in [ExternalIdentifierX5cOpts] carrying the trust anchors loaded
     * from the trust libraries' single config source (`trust.anchors.x509.*` via
     * [X509TrustAnchorLoader]), resolve once to produce a fully-populated
     * [ExternalIdentifierResult.X5c] — the resolver parses the chain, runs the platform X.509
     * chain verify against those anchors, extracts the leaf JWK, and stores the result so any
     * subsequent caller (the JWKS build below; downstream signature verification) can read it
     * without re-parsing. The HAIP self-signed-leaf rule is enforced on top.
     *
     * Returns a single-key JWKS document built from the leaf cert's public key for downstream
     * `verifyJws` use, or an [AuthorizationServerError] if any rule fails.
     */
    private suspend fun verifyAttestationX5cChain(
        x5c: List<String>,
        trustedCerts: List<String>,
        headerKid: String?,
    ): IdkResult<JsonObject, AuthorizationServerError> {
        val opts =
            ExternalIdentifierX5cOpts(
                identifier = x5c,
                verify = true,
                trustAnchors = trustedCerts,
            )
        val resolved =
            identifierService.resolve(opts).getOrElse {
                return Err(
                    AuthorizationServerError.InvalidClientAttestation(
                        details = "Wallet attestation x5c resolution failed: ${it.message.defaultMessage}",
                    ),
                )
            } as? ExternalIdentifierResult.X5c
                ?: return Err(
                    AuthorizationServerError.InvalidClientAttestation(
                        details = "Wallet attestation x5c resolution did not return an X5c identifier",
                    ),
                )

        if (resolved.verificationResult.error) {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details =
                        "Wallet attestation x5c chain did not validate against configured X.509 trust anchors: " +
                            (resolved.verificationResult.message ?: "unknown error"),
                ),
            )
        }

        // HAIP §4.4.1 forbids self-signed leaves: a self-issued cert can't be anchored at an
        // independent trust root. The chain check above already rejects unanchored chains, but
        // a self-signed leaf could slip through if the operator mistakenly listed the leaf
        // itself as a trust anchor, so re-check explicitly.
        val leafCert = resolved.certificates.first()
        if (leafCert.subjectDN == leafCert.issuerDN) {
            return Err(
                AuthorizationServerError.InvalidClientAttestation(
                    details = "Wallet attestation leaf certificate is self-signed (HAIP §4.4.1 forbids self-signed signing certs)",
                ),
            )
        }

        // Stamp the JOSE header `kid` onto the leaf JWK before pinning. Trust is established by
        // the x5c chain we just validated; the verifier's strict `kid`-match lookup against the
        // single-key trustedJwks would otherwise fail when the cert-derived JWK has no kid (or a
        // different one). Asserting `kid` here lets the JWS verifier select this key cleanly.
        val leafJwk = leafCert.getPublicKeyJwk()
        val leafJwkJson = Json.encodeToJsonElement(JwkType.serializer(), leafJwk).jsonObject
        val pinnedJwkJson =
            if (headerKid != null) {
                JsonObject(leafJwkJson + ("kid" to kotlinx.serialization.json.JsonPrimitive(headerKid)))
            } else {
                leafJwkJson
            }
        return Ok(jwksDocument(listOf(pinnedJwkJson)))
    }

    /**
     * Construct a transient [ClientRegistration] for HAIP wallet attestation flows where the
     * attestation `sub` does not match a pre-registered client. The trust boundary is the x5c
     * chain validation against the operator's wallet-attester CA — there is no per-client JWK
     * pinning, since by HAIP §4.4.1 the `sub` is shared across all wallet instances of a given
     * Wallet Provider. Downstream code only reads `clientId`, `tokenEndpointAuthMethod` and the
     * attester allow-lists, all of which are populated to permit attestation flow.
     */
    private fun synthesizeHaipTransientClient(clientId: String): ClientRegistration =
        ClientRegistration(
            clientId = clientId,
            tokenEndpointAuthMethod = ClientAuthenticationMethod.ATTEST_JWT_CLIENT_AUTH,
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
            trustedAttesterIssuers = null,
            trustedAttesterJwks = null,
        )
}
