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
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.models.VerificationPurpose
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Default [StatusListSigner]: builds the spec envelope and signs it with the issuer's key via the
 * shared [JwtService]. Token Status List → `statuslist+jwt`; W3C Bitstring → `vc+jwt`. CWT/COSE is
 * not yet wired (returns an unsupported-format error).
 *
 * The JOSE key-reference header is built to MATCH how the credentials that reference the list are
 * signed (`signingKeyMode`), because many wallets reject a status list whose trust mechanism or
 * anchor differs from the credential's:
 * - `did:<method>` → emit the DID verification-method id as `kid` and root the token `iss` in the
 *   same DID (so `iss`/`kid` are consistent, as the credential issuance does).
 * - `x5c` → embed the signing key's certificate chain.
 * - otherwise → let the KMS attach the key identifier (a cert-bearing key yields `x5c` automatically).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<StatusListSigner>())
class JwsStatusListSigner(
    private val jwtService: JwtService,
    private val kms: KeyManagerService,
    private val didProviderRegistry: DidProviderRegistry,
    private val cwtSigner: CwtStatusListSigner,
) : StatusListSigner {
    override suspend fun signStatusListToken(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError> =
        when (args.spec to args.proofFormat) {
            StatusListSpec.TOKEN_STATUS_LIST to StatusProofFormat.JWT -> {
                sign(args, typ = "statuslist+jwt", contentType = StatusListContentTypes.STATUSLIST_JWT) {
                    TokenStatusListEnvelope.buildPayload(it)
                }
            }

            StatusListSpec.BITSTRING_STATUS_LIST to StatusProofFormat.VC_JWT -> {
                sign(args, typ = "vc+jwt", contentType = StatusListContentTypes.VC_JWT) {
                    BitstringStatusListEnvelope.buildCredential(it)
                }
            }

            // IETF Token Status List in CWT form → COSE_Sign1 over a CWT claim set (delegated).
            StatusListSpec.TOKEN_STATUS_LIST to StatusProofFormat.CWT -> {
                cwtSigner.sign(args)
            }

            else -> {
                Err(StatusListErrors.unsupportedProofFormat(args.spec, args.proofFormat))
            }
        }

    private suspend fun sign(
        args: SignStatusListTokenArgs,
        typ: String,
        contentType: String,
        buildPayload: (SignStatusListTokenArgs) -> JsonObject,
    ): IdkResult<StatusListToken, IdkError> {
        val (effectiveArgs, identifierHeader) = resolveKeyReference(args).getOrElse { return Err(it) }
        val payload = buildPayload(effectiveArgs)
        val header =
            buildJsonObject {
                put("typ", typ)
                identifierHeader?.forEach { (k, v) -> put(k, v) }
            }
        val result =
            jwtService
                .createJwsCompact(
                    CreateJwsArgs(
                        issuer = ManagedOptsAlias(identifier = effectiveArgs.signingKeyAlias),
                        payload = payload,
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
     * KMS attach the identifier). Resolution failures fall back to the KMS default rather than failing
     * the whole list, mirroring the credential issuance handlers.
     */
    private suspend fun resolveKeyReference(args: SignStatusListTokenArgs): IdkResult<Pair<SignStatusListTokenArgs, JsonObject?>, IdkError> {
        val mode = args.signingKeyMode ?: return Ok(args to null)
        return when {
            mode.startsWith("did:") -> {
                val vmId =
                    resolveDidKid(args, mode.removePrefix("did:"))
                        // SECURITY: never silently fall back to another trust mechanism (x5c / KMS
                        // default) when a DID kid can't be resolved — that would issue the list under
                        // an x509 cert instead of the DID. Fail loudly. did:web/did:webvh kids are not
                        // derivable from the key, so configure `verification-method-id` for the list.
                        ?: return Err(
                            IdkError.fromString(
                                code = "statuslist_did_kid_unresolved",
                                message =
                                    "Cannot resolve a '$mode' kid for status-list signing key '${args.signingKeyAlias}'. " +
                                        "Set the list's 'verification-method-id' (did:web/did:webvh kids are not derivable " +
                                        "from the key). Refusing to fall back to x5c or any other trust mechanism.",
                            ),
                        )
                val did = vmId.substringBefore('#')
                Ok(args.copy(issuer = did) to buildJsonObject { put("kid", JsonPrimitive(vmId)) })
            }

            mode.equals("x5c", ignoreCase = true) -> {
                Ok(args to resolveX5cHeader(args.signingKeyAlias))
            }

            // jwk-thumbprint / unknown modes: let the KMS attach the identifier.
            else -> {
                Ok(args to null)
            }
        }
    }

    /**
     * DID verification-method id ("`<did>#<fragment>`") used as the token `kid`, or `null` if it
     * cannot be resolved. Priority: an explicit configured [SignStatusListTokenArgs.signingVerificationMethodId]
     * (required form for did:web/webvh), else for web/webvh `did:web:<host>#<signingKeyAlias>` (host from
     * the list's hosting URI), else the key-derived id for did:jwk/did:key.
     */
    private suspend fun resolveDidKid(
        args: SignStatusListTokenArgs,
        method: String,
    ): String? {
        // A configured kid MUST be a full absolute DID URL (`did:<method>:...#<fragment>`) — never a
        // hostname or a bare fragment. Anything else is ignored in favour of the derived absolute kid.
        args.signingVerificationMethodId?.takeIf { it.startsWith("did:") && it.contains('#') }?.let { return it }
        val jwk = publicJwk(args.signingKeyAlias) ?: return null
        val provider = didProviderRegistry.getProvider(method) ?: return null
        val webMethod = method in WEB_RESOLVED_METHODS
        val created =
            provider
                .create(
                    DidCreateOptions(
                        method = method,
                        publicKeyJwk = jwk.toPublicKey(),
                        domain = if (webMethod) hostOf(args.statusListUri) else null,
                        purposes = if (webMethod) listOf(VerificationPurpose.ASSERTION_METHOD) else null,
                        verificationMethodId = if (webMethod) args.signingKeyAlias else null,
                    ),
                ).getOrNull() ?: return null
        return created.verificationMethodsByPurpose[VerificationPurpose.ASSERTION_METHOD]
            ?.firstOrNull()
            ?.id
            ?: if (webMethod) "${created.did}#${args.signingKeyAlias}" else "${created.did}#0"
    }

    private companion object {
        val WEB_RESOLVED_METHODS = setOf("web", "webvh")

        /** Host (authority without scheme/port/path) of an absolute http(s) URL, or null. */
        fun hostOf(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val authority =
                url
                    .substringAfter("://", "")
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
            val host = authority.substringBefore('@').substringBefore(':')
            return host.takeIf { it.isNotBlank() }
        }
    }

    /** `{ x5c: [...] }` from the signing key's certificate chain, or null when the key carries none. */
    private suspend fun resolveX5cHeader(keyAlias: String): JsonObject? {
        val chain = publicJwk(keyAlias)?.x5c ?: return null
        return buildJsonObject { put("x5c", JsonArray(chain.map { JsonPrimitive(it) })) }
    }

    private suspend fun publicJwk(keyAlias: String): Jwk? =
        kms
            .getKeyResult(KeyInfo<Nothing>(alias = keyAlias))
            .getOrNull()
            ?.key
            ?.key as? Jwk
}
