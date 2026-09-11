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

package com.sphereon.statuslist.impl.sign

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.Log
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.x509.x5cWithoutTerminalSelfSignedRoot
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListErrors
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.impl.envelope.BitstringStatusListEnvelope
import com.sphereon.statuslist.impl.envelope.TokenStatusListEnvelope
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import com.sphereon.statuslist.spi.StatusListSigner
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Default [StatusListSigner]: builds the spec envelope and signs it with the issuer's key via the
 * shared [JwtService]. Token Status List → `statuslist+jwt`; W3C Bitstring → `vc+jwt`; generic and
 * ISO/IEC 18013-5 mdoc CWTs use the dedicated COSE signers.
 *
 * The JOSE key-reference header is built to MATCH how the credentials that reference the list are
 * signed (`signingKeyMode`), because many wallets reject a status list whose trust mechanism or
 * anchor differs from the credential's:
 * - `did:<method>` → emit the DID verification-method id as `kid` and root the token `iss` in the
 *   same DID (so `iss`/`kid` are consistent, as the credential issuance does).
 * - `x5c` → embed the signing key's certificate chain and its public JWK. The certificate chain
 *   remains the configured trust mechanism; the redundant public JWK lets non-HAIP Token Status
 *   List verifiers validate the same signature without changing signing keys or profile behavior.
 * - otherwise → let the KMS attach the key identifier (a cert-bearing key yields `x5c` automatically).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<StatusListSigner>())
class JwsStatusListSigner(
    private val jwsSigningService: StatusListJwsSigningService,
    private val didProviderRegistry: DidProviderRegistry,
    private val didResolverRegistry: DidResolverRegistry,
    private val cwtSigner: CwtStatusListSigner,
    /** Required in every deployed graph: an mdoc profile must never silently downgrade to generic CWT. */
    private val mdocCwtSigner: MdocCwtStatusListSigner,
) : StatusListSigner {
    private val log = Log.app().withTag("JwsStatusListSigner")

    override suspend fun signStatusListToken(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError> {
        // This signer signs with a KMS key, so the server must have resolved one. A missing name is
        // refused outright: it is never substituted, defaulted, or derived from the list identity.
        // The CWT branch guards itself, since it is also reachable directly.
        if (args.mdocProfile != null) {
            if (args.spec != StatusListSpec.TOKEN_STATUS_LIST || args.proofFormat != StatusProofFormat.CWT) {
                return Err(StatusListErrors.unsupportedProofFormat(args.spec, args.proofFormat))
            }
            return mdocCwtSigner?.sign(args)
                ?: Err(StatusListErrors.unsupportedProofFormat(args.spec, args.proofFormat))
        }
        if (args.spec == StatusListSpec.TOKEN_STATUS_LIST && args.proofFormat == StatusProofFormat.CWT) {
            return cwtSigner.sign(args)
        }
        val keyName =
            args.signingKeyName?.takeIf { it.isNotBlank() }
                ?: return Err(StatusListErrors.signingKeyUnresolvable(args.statusListUri))
        return when (args.spec to args.proofFormat) {
            StatusListSpec.TOKEN_STATUS_LIST to StatusProofFormat.JWT -> {
                sign(args, keyName, typ = "statuslist+jwt", contentType = StatusListContentTypes.STATUSLIST_JWT) {
                    TokenStatusListEnvelope.buildPayload(it)
                }
            }

            StatusListSpec.BITSTRING_STATUS_LIST to StatusProofFormat.VC_JWT -> {
                sign(args, keyName, typ = "vc+jwt", contentType = StatusListContentTypes.VC_JWT) {
                    BitstringStatusListEnvelope.buildCredential(it)
                }
            }

            else -> {
                Err(StatusListErrors.unsupportedProofFormat(args.spec, args.proofFormat))
            }
        }
    }

    private suspend fun sign(
        args: SignStatusListTokenArgs,
        keyName: String,
        typ: String,
        contentType: String,
        buildPayload: (SignStatusListTokenArgs) -> JsonObject,
    ): IdkResult<StatusListToken, IdkError> {
        val (effectiveArgs, identifierHeader) = resolveKeyReference(args, keyName).getOrElse { return Err(it) }
        val payload = buildPayload(effectiveArgs)
        val header =
            buildJsonObject {
                put("typ", typ)
                identifierHeader?.forEach { (k, v) -> put(k, v) }
            }
        val result =
            jwsSigningService
                .createCompactJws(
                    StatusListJwsSigningRequest(
                        statusListArgs = effectiveArgs,
                        keyName = keyName,
                        payload = payload,
                        mode = identifierMode(effectiveArgs.signingKeyMode),
                        // When we built the identifier ourselves, tell the KMS not to add its own
                        // (otherwise a cert-bearing key would also inject x5c, contradicting a DID kid).
                        opts = CreateJwsOpts(protectedHeader = header, noIdentifierInHeader = identifierHeader != null),
                    ),
                ).getOrElse { return Err(it) }
        return Ok(StatusListToken(token = result.jwt, contentType = contentType, ttlSeconds = effectiveArgs.ttlSeconds))
    }

    /**
     * Resolve the JOSE key-reference header for [args]'s [SignStatusListTokenArgs.signingKeyMode],
     * returning the (possibly DID-rooted) args plus the header fragment to merge (or null to let the
     * KMS attach the identifier). DID and x5c resolution failures fail closed and never switch the
     * trust mechanism selected by the credential configuration.
     */
    private suspend fun resolveKeyReference(
        args: SignStatusListTokenArgs,
        keyName: String,
    ): IdkResult<Pair<SignStatusListTokenArgs, JsonObject?>, IdkError> {
        val mode = args.signingKeyMode ?: return Ok(args to null)
        return when {
            mode.startsWith("did:") -> {
                val vmId = resolveDidKid(args, keyName, mode.removePrefix("did:")).getOrElse { return Err(it) }
                val did = vmId.substringBefore('#')
                Ok(args.copy(issuer = did) to buildJsonObject { put("kid", JsonPrimitive(vmId)) })
            }

            mode.equals("x5c", ignoreCase = true) -> {
                val header =
                    resolveX5cHeader(args, keyName)
                        ?: return Err(
                            IdkError.fromString(
                                code = "statuslist_x5c_unresolved",
                                message =
                                    "Cannot resolve an x5c certificate chain for status-list signing key '$keyName'. " +
                                        "Refusing to fall back to a kid or another trust mechanism.",
                            ),
                        )
                Ok(args to header)
            }

            // jwk-thumbprint / unknown modes: let the KMS attach the identifier.
            else -> {
                Ok(args to null)
            }
        }
    }

    /**
     * DID verification-method id ("`<did>#<fragment>`") used as the token `kid`, or a stage-specific
     * error if it cannot be resolved. Priority: an explicit configured [SignStatusListTokenArgs.signingVerificationMethodId]
     * (required form for did:web/webvh), else for web/webvh `did:web:<host>#<keyName>` (host from
     * the list's hosting URI), else the key-derived id for did:jwk/did:key.
     */
    private suspend fun resolveDidKid(
        args: SignStatusListTokenArgs,
        keyName: String,
        method: String,
    ): IdkResult<String, IdkError> {
        val totalStarted = TimeSource.Monotonic.markNow()
        val publicKeyStarted = TimeSource.Monotonic.markNow()
        // A configured kid MUST be a full absolute DID URL (`did:<method>:...#<fragment>`) — never a
        // hostname or a bare fragment. Anything else is ignored in favour of the derived absolute kid.
        val jwk =
            publicJwk(args, keyName)
                ?: return didKidFailure(
                    code = "statuslist_did_public_key_unavailable",
                    message = "The exact status-list signing key has no resolvable public JWK.",
                    method = method,
                    totalStarted = totalStarted,
                    publicKeyMs = publicKeyStarted.elapsedNow().inWholeMilliseconds,
                )
        val publicKeyMs = publicKeyStarted.elapsedNow().inWholeMilliseconds
        args.signingVerificationMethodId?.let { configuredId ->
            val did = configuredId.substringBefore('#')
            if (!configuredId.startsWith("did:") || '#' !in configuredId || did.substringAfter("did:").substringBefore(':') != method) {
                return didKidFailure(
                    code = "statuslist_did_verification_method_invalid",
                    message = "The configured status-list verification-method ID is not an absolute did:$method URL.",
                    method = method,
                    totalStarted = totalStarted,
                    publicKeyMs = publicKeyMs,
                )
            }
            val didResolutionStarted = TimeSource.Monotonic.markNow()
            val resolutionResult = didResolverRegistry.resolve(did)
            val didResolutionMs = didResolutionStarted.elapsedNow().inWholeMilliseconds
            if (resolutionResult.isErr) {
                return didKidFailure(
                    code = "statuslist_did_resolution_failed",
                    message = "The configured status-list DID could not be resolved (${resolutionResult.error.code}).",
                    method = method,
                    totalStarted = totalStarted,
                    publicKeyMs = publicKeyMs,
                    didResolutionMs = didResolutionMs,
                    diagnostic = resolutionResult.error.message.defaultMessage,
                )
            }
            val matchStarted = TimeSource.Monotonic.markNow()
            val match = matchStatusListAssertionMethod(resolutionResult.value, jwk, requiredId = configuredId)
            val matchMs = matchStarted.elapsedNow().inWholeMilliseconds
            val vmId = match.id
            if (vmId == null) {
                return didKidFailure(
                    code = "statuslist_did_assertion_method_mismatch",
                    message =
                        "The configured status-list verification method '$configuredId' is not the unique assertionMethod " +
                            "bound to the exact signing key in the resolved DID document " +
                            "(assertionMethods=${match.assertionMethodCount}, keyMatches=${match.keyMatchCount}).",
                    method = method,
                    totalStarted = totalStarted,
                    publicKeyMs = publicKeyMs,
                    didResolutionMs = didResolutionMs,
                    matchMs = matchMs,
                    assertionMethodCount = match.assertionMethodCount,
                    keyMatchCount = match.keyMatchCount,
                )
            }
            didKidSuccess(
                method = method,
                totalStarted = totalStarted,
                publicKeyMs = publicKeyMs,
                didResolutionMs = didResolutionMs,
                matchMs = matchMs,
                assertionMethodCount = match.assertionMethodCount,
                keyMatchCount = match.keyMatchCount,
            )
            return Ok(vmId)
        }
        val provider =
            didProviderRegistry.getProvider(method)
                ?: return didKidFailure(
                    code = "statuslist_did_provider_unavailable",
                    message = "No DID provider is registered for status-list signing method '$method'.",
                    method = method,
                    totalStarted = totalStarted,
                    publicKeyMs = publicKeyMs,
                )
        val webMethod = method in WEB_RESOLVED_METHODS
        val createdResult =
            provider
                .create(
                    DidCreateOptions(
                        method = method,
                        publicKeyJwk = jwk.toPublicKey(),
                        domain = if (webMethod) statusListWebAuthorityOf(args.statusListUri) else null,
                        purposes = if (webMethod) listOf(VerificationPurpose.ASSERTION_METHOD) else null,
                        verificationMethodId = if (webMethod) keyName else null,
                    ),
                )
        if (createdResult.isErr) {
            return didKidFailure(
                code = "statuslist_did_creation_failed",
                message = "The status-list signing DID could not be created: ${createdResult.error.code}.",
                method = method,
                totalStarted = totalStarted,
                publicKeyMs = publicKeyMs,
            )
        }
        val didResolutionStarted = TimeSource.Monotonic.markNow()
        val resolutionResult = didResolverRegistry.resolve(createdResult.value.did)
        val didResolutionMs = didResolutionStarted.elapsedNow().inWholeMilliseconds
        if (resolutionResult.isErr) {
            return didKidFailure(
                code = "statuslist_did_resolution_failed",
                message = "The status-list signing DID could not be resolved: ${resolutionResult.error.code}.",
                method = method,
                totalStarted = totalStarted,
                publicKeyMs = publicKeyMs,
                didResolutionMs = didResolutionMs,
            )
        }
        val matchStarted = TimeSource.Monotonic.markNow()
        val match = matchStatusListAssertionMethod(resolutionResult.value, jwk)
        val matchMs = matchStarted.elapsedNow().inWholeMilliseconds
        val vmId =
            match.id
                ?: return didKidFailure(
                    code = "statuslist_did_assertion_method_mismatch",
                    message =
                        "The resolved DID document does not contain one unique assertionMethod bound to the exact " +
                            "status-list signing key (assertionMethods=${match.assertionMethodCount}, " +
                            "keyMatches=${match.keyMatchCount}).",
                    method = method,
                    totalStarted = totalStarted,
                    publicKeyMs = publicKeyMs,
                    didResolutionMs = didResolutionMs,
                    matchMs = matchMs,
                    assertionMethodCount = match.assertionMethodCount,
                    keyMatchCount = match.keyMatchCount,
                )
        didKidSuccess(
            method = method,
            totalStarted = totalStarted,
            publicKeyMs = publicKeyMs,
            didResolutionMs = didResolutionMs,
            matchMs = matchMs,
            assertionMethodCount = match.assertionMethodCount,
            keyMatchCount = match.keyMatchCount,
        )
        return Ok(vmId)
    }

    private suspend fun didKidFailure(
        code: String,
        message: String,
        method: String,
        totalStarted: TimeMark,
        publicKeyMs: Long,
        didResolutionMs: Long = 0L,
        matchMs: Long = 0L,
        assertionMethodCount: Int = 0,
        keyMatchCount: Int = 0,
        diagnostic: String? = null,
    ): IdkResult<String, IdkError> {
        val diagnosticSuffix =
            diagnostic
                ?.replace('\n', ' ')
                ?.replace('\r', ' ')
                ?.take(500)
                ?.let { " diagnostic=$it" }
                .orEmpty()
        log.warn(
            "VDX_STATUSLIST_DID_KID_RESOLUTION outcome=failure reason=$code method=$method " +
                "publicKeyMs=$publicKeyMs didResolutionMs=$didResolutionMs matchMs=$matchMs " +
                "assertionMethods=$assertionMethodCount keyMatches=$keyMatchCount " +
                "durationMs=${totalStarted.elapsedNow().inWholeMilliseconds}$diagnosticSuffix",
        )
        return Err(
            IdkError.fromString(
                code = code,
                message = "$message Refusing to fall back to another trust mechanism.",
            ),
        )
    }

    private suspend fun didKidSuccess(
        method: String,
        totalStarted: TimeMark,
        publicKeyMs: Long,
        didResolutionMs: Long,
        matchMs: Long,
        assertionMethodCount: Int,
        keyMatchCount: Int,
    ) {
        log.info(
            "VDX_STATUSLIST_DID_KID_RESOLUTION outcome=success method=$method " +
                "publicKeyMs=$publicKeyMs didResolutionMs=$didResolutionMs matchMs=$matchMs " +
                "assertionMethods=$assertionMethodCount keyMatches=$keyMatchCount " +
                "durationMs=${totalStarted.elapsedNow().inWholeMilliseconds}",
        )
    }

    private companion object {
        val WEB_RESOLVED_METHODS = setOf("web", "webvh")

        /**
         * An explicit `jwk` mode embeds the public signing key in the protected header. This is the
         * self-contained verification mechanism used by non-HAIP Token Status List deployments.
         * DID and x5c modes build their own header fragment above; all other modes retain the KMS
         * auto-detection behaviour.
         */
        fun identifierMode(signingKeyMode: String?): JwsIdentifierMode =
            if (signingKeyMode.equals("jwk", ignoreCase = true)) JwsIdentifierMode.JWK else JwsIdentifierMode.AUTO

    }

    /**
     * `{ x5c: [...], jwk: {...} }` from the same signing key, or null when the key carries no chain.
     *
     * The JWK is public-only. Keeping both identifiers on the one signature is intentional: HAIP
     * validates the CA-backed `x5c` chain while the Final Token Status List path uses the embedded
     * JWK. Credential format does not affect this publication behavior.
     */
    private suspend fun resolveX5cHeader(
        args: SignStatusListTokenArgs,
        keyAlias: String,
    ): JsonObject? {
        val signingJwk = publicJwk(args, keyAlias) ?: return null
        val chain = signingJwk.x5c?.let(::x5cWithoutTerminalSelfSignedRoot) ?: return null
        val publicJwk = Json.encodeToJsonElement(Jwk.serializer(), signingJwk.toPublicKey() as Jwk).jsonObject
        return buildJsonObject {
            put("x5c", JsonArray(chain.map { JsonPrimitive(it) }))
            put("jwk", publicJwk)
        }
    }

    private suspend fun publicJwk(
        args: SignStatusListTokenArgs,
        keyAlias: String,
    ): Jwk? = jwsSigningService.publicJwk(keyAlias, args.signingKeyInstanceId)
}

/**
 * Public authority of a hosted status-list URL. A non-default port is part of the corresponding
 * did:web identifier and must reach the DID provider so it can be encoded as `%3A<port>`.
 */
internal fun statusListWebAuthorityOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val authority =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    if ('@' in authority) return null
    return authority.takeIf { it.isNotBlank() }
}

internal fun findStatusListAssertionMethodId(
    resolution: DidResolutionResult,
    signingKey: Jwk,
    requiredId: String? = null,
): String? = matchStatusListAssertionMethod(resolution, signingKey, requiredId).id

internal data class StatusListAssertionMethodMatch(
    val id: String?,
    val assertionMethodCount: Int,
    val keyMatchCount: Int,
)

internal fun matchStatusListAssertionMethod(
    resolution: DidResolutionResult,
    signingKey: Jwk,
    requiredId: String? = null,
): StatusListAssertionMethodMatch {
    if (!resolution.isSuccess()) return StatusListAssertionMethodMatch(null, 0, 0)
    val did =
        resolution.didDocument?.id?.takeIf { it.startsWith("did:") }
            ?: return StatusListAssertionMethodMatch(null, 0, 0)
    val expected = signingKey.publicMaterial()
    val assertionMethods = resolution.getAssertionMethods()
    val matches =
        assertionMethods
            .filter { it.publicKeyJwk?.publicMaterial() == expected }
            .mapNotNull { method ->
                when {
                    method.id.startsWith("$did#") && method.id.length > did.length + 1 -> method.id
                    method.id.startsWith("#") && method.id.length > 1 -> "$did${method.id}"
                    else -> null
                }
            }.distinct()
    val id = matches.singleOrNull()?.takeIf { requiredId == null || it == requiredId }
    return StatusListAssertionMethodMatch(id, assertionMethods.size, matches.size)
}

private fun Jwk.publicMaterial(): JsonObject {
    val full = Json.encodeToJsonElement(Jwk.serializer(), this).jsonObject
    return buildJsonObject {
        full.forEach { (key, value) ->
            if (key in setOf("kty", "crv", "x", "y", "n", "e")) put(key, value)
        }
    }
}
