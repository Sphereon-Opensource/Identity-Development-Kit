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
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.generateJwkThumbprintUri
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.impl.signing.IssuerKeyIdResolver
import com.sphereon.statuslist.spi.CredentialStatusEnricher
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock

/**
 * JWT VC JSON format handler (jwt_vc_json).
 *
 * Issues W3C Verifiable Credentials as JWTs using [JwtService].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<CredentialFormatHandler>())
class JwtVcJsonFormatHandler(
    private val jwtService: JwtService,
    private val kms: KeyManagerService,
    private val issuerKeyIdResolver: IssuerKeyIdResolver,
    private val statusEnricherProvider: Provider<CredentialStatusEnricher>? = null,
) : CredentialFormatHandler {
    override val supportedFormat: String = CredentialFormat.JWT_VC_JSON.value

    override suspend fun canHandle(
        request: CredentialRequest,
        configuration: CredentialConfigurationSupported,
    ): Boolean = configuration.format == CredentialFormat.JWT_VC_JSON.value

    override suspend fun issueCredential(
        request: CredentialRequest,
        context: IssuanceContext,
    ): IdkResult<CredentialEnvelope, IdkError> {
        val credentialTypes =
            context.credentialConfiguration.credentialDefinition?.type
                ?: listOf("VerifiableCredential")

        val now = Clock.System.now()
        val nowEpochSeconds = now.epochSeconds

        // Pre-sign: reserve a status-list entry (if configured) so its `credentialStatus` reference
        // is embedded into the signed credential.
        val reservedStatus =
            reserveCredentialStatus(statusEnricherProvider?.invoke(), context).getOrElse { return Err(it) }

        // Build the W3C VC payload
        val vcPayload =
            buildJsonObject {
                putJsonArray("@context") {
                    add(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))
                }
                putJsonArray("type") {
                    credentialTypes.forEach { add(JsonPrimitive(it)) }
                }
                put("issuer", context.issuerIdentifier)
                put("issuanceDate", now.toString())
                reservedStatus?.let { put("credentialStatus", it.claim) }
                putJsonObject("credentialSubject") {
                    put("id", context.subject)
                    for ((name, value) in context.attributes) {
                        put(name, value)
                    }
                }
            }

        // Build the full JWT payload with vc claim
        val jwtPayload =
            buildJsonObject {
                put("vc", vcPayload)
                put("iss", context.issuerIdentifier)
                val holderKid = context.holderKeyId
                val isDid = holderKid != null && holderKid.startsWith("did:")
                // Prefer the holder DID as subject when the proof was bound via a DID URL.
                // Otherwise fall back to the token context subject (e.g. user account id).
                val sub = if (isDid) holderKid!!.substringBefore('#') else context.subject
                put("sub", sub)
                // Shift iat backward by the configured clock-skew so wallets with slightly-
                // ahead clocks still accept the credential on receipt.
                val iat = nowEpochSeconds - context.issuanceClockSkewInSeconds
                put("iat", iat)
                // Only emit `exp` when the configuration specifies a validity window.
                context.expirationInDays?.let { days ->
                    put("exp", iat + days.toLong() * 24L * 60L * 60L)
                }
                // Holder binding: cnf carries EXACTLY ONE of `kid` (DID VM URL) or `jwk`,
                // matching the binding method the wallet used in its proof. Wallet libs
                // like credo-ts pick `jwk` over `kid` when both are present and then fail
                // to locate their internal key-map entry (keyed by the DID, not by
                // thumbprint).
                context.holderBindingKey?.let { key ->
                    putJsonObject("cnf") {
                        if (isDid && holderKid != null) {
                            put("kid", JsonPrimitive(holderKid))
                        } else {
                            put("jwk", key)
                        }
                    }
                }
            }

        // Resolve the issuer signing key. Prefer the per-credential `signingKeyAlias` from the
        // issuer configuration; fall back to the configuration ID for embedders that key the
        // signing key by config id. The signing-key identifier header (kid / x5c) is derived
        // from `signingKeyMode` so verifiers can discover the public key — mirrors
        // SdJwtDcFormatHandler.
        val keyAlias = context.signingKeyAlias ?: context.credentialConfigurationId
        val issuerKey = ManagedOptsAlias(identifier = keyAlias)
        val keyIdentifierHeader = resolveSigningHeader(keyAlias, context.signingKeyMode)

        val result =
            jwtService
                .createJwsCompact(
                    CreateJwsArgs(
                        issuer = issuerKey,
                        payload = jwtPayload,
                        opts =
                            CreateJwsOpts(
                                protectedHeader = keyIdentifierHeader,
                                noIdentifierInHeader = keyIdentifierHeader != null,
                            ),
                    ),
                ).getOrElse { return Err(it) }

        return Ok(
            CredentialEnvelope(
                credential = JsonPrimitive(result.jwt),
                format = context.credentialConfiguration.format,
            ),
        )
    }

    /**
     * Resolves the signing-key identifier header (kid / x5c) for the JWT protected header based
     * on [mode]. Returns null when no identifier is requested (SigningKeyMode.None), in which case
     * the credential is verifiable via the issuer's `/.well-known/jwt-vc-issuer` JWKS.
     */
    private suspend fun resolveSigningHeader(
        keyAlias: String,
        mode: SigningKeyMode,
    ): JsonObject? =
        when (mode) {
            is SigningKeyMode.None -> null

            is SigningKeyMode.Did -> resolveDidKid(keyAlias, mode.method)

            is SigningKeyMode.X5c -> resolveX5cHeader(keyAlias)

            is SigningKeyMode.JwkThumbprint -> resolveJwkThumbprintKid(keyAlias)

            is SigningKeyMode.Federation -> throw UnsupportedOperationException(
                "OpenID Federation signing mode is not yet implemented",
            )
        }

    private suspend fun resolveDidKid(
        keyAlias: String,
        method: String,
    ): JsonObject? {
        val vmId =
            issuerKeyIdResolver
                .resolveDidVerificationMethodId(keyAlias = keyAlias, didMethod = method)
                .getOrElse { return null }
        return buildJsonObject { put("kid", JsonPrimitive(vmId)) }
    }

    private suspend fun resolveX5cHeader(keyAlias: String,): JsonObject? {
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = keyAlias))
        if (keyResult.isErr) return null
        val jwk = keyResult.value.key as? Jwk ?: return null
        val chain = jwk.x5c ?: return null
        return buildJsonObject {
            put("x5c", JsonArray(chain.map { JsonPrimitive(it) }))
        }
    }

    private suspend fun resolveJwkThumbprintKid(keyAlias: String): JsonObject? {
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = keyAlias))
        if (keyResult.isErr) return null
        val jwk = keyResult.value.key as? Jwk ?: return null
        val publicJwk = jwk.toPublicKey()
        val thumbprintUri = generateJwkThumbprintUri(publicJwk)
        return buildJsonObject {
            put("kid", JsonPrimitive(thumbprintUri))
        }
    }
}
