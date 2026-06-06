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
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.format.SdPolicy
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.sdjwt.IssueSdJwtArgs
import com.sphereon.sdjwt.SdJwtService
import com.sphereon.sdjwt.dsl.sdJwtPayload
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
import kotlin.time.Clock

/**
 * SD-JWT DC format handler (dc+sd-jwt and vc+sd-jwt).
 *
 * Issues SD-JWT credentials using [SdJwtService].
 * Applies selective disclosure policies from the issuance context.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<CredentialFormatHandler>())
class SdJwtDcFormatHandler(
    private val sdJwtService: SdJwtService,
    private val kms: KeyManagerService,
    private val didProviderRegistry: DidProviderRegistry,
    private val issuerKeyIdResolver: com.sphereon.openid.oid4vci.issuer.impl.signing.IssuerKeyIdResolver,
    private val statusEnricherProvider: Provider<CredentialStatusEnricher>? = null,
) : CredentialFormatHandler {
    override val supportedFormat: String = CredentialFormat.SD_JWT_DC.value

    override suspend fun canHandle(
        request: CredentialRequest,
        configuration: CredentialConfigurationSupported,
    ): Boolean = configuration.format == CredentialFormat.SD_JWT_DC.value || configuration.format == CredentialFormat.SD_JWT_VC.value

    override suspend fun issueCredential(
        request: CredentialRequest,
        context: IssuanceContext,
    ): IdkResult<CredentialEnvelope, IdkError> {
        val vct =
            context.credentialConfiguration.vct
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "vct is required for SD-JWT DC format"))

        // iat is shifted backward by the configured issuance clock-skew so wallets with
        // slightly-ahead clocks still accept the credential as valid on receipt.
        val iat = Clock.System.now().epochSeconds - context.issuanceClockSkewInSeconds

        // The SD-JWT `iss` claim: per SD-JWT VC §3.5 it MUST be resolvable to the signing
        // key. It MAY be an HTTPS URL (resolved via /.well-known/jwt-vc-issuer) OR a DID
        // URL (resolved via the DID method). When the issuer is configured to sign with a
        // DID-based key mode (`did:jwk`, `did:key`, etc.), we emit the DID URL as `iss` so
        // `iss` and the header `kid` are consistently rooted in the same DID. Wallets
        // like credo-ts that only support did-based issuer resolution require this. The
        // OID4VCI Credential Issuer Identifier (used in `credential_issuer`, offers, and
        // metadata discovery) remains the HTTPS URL and is unaffected.
        val signingMode = context.signingKeyMode
        val issClaim: String =
            if (signingMode is SigningKeyMode.Did) {
                val keyAlias = context.signingKeyAlias ?: context.credentialConfigurationId
                issuerKeyIdResolver
                    .resolveDidVerificationMethodId(keyAlias, signingMode.method)
                    .getOrElse { return Err(it) }
                    .substringBefore('#')
            } else {
                context.issuerIdentifier
            }
        // exp is only emitted when the credential configuration specifies a validity window.
        // Per SD-JWT VC §3.2.2 / RFC 7519 §4.1.4 `exp` is OPTIONAL — absence means no
        // expiration check applies.
        val exp: Long? = context.expirationInDays?.let { iat + it.toLong() * SECONDS_PER_DAY }

        // Pre-sign: reserve a Token Status List entry (if configured) so the `status.status_list`
        // reference is embedded into the signed SD-JWT.
        val reservedStatus =
            reserveCredentialStatus(statusEnricherProvider?.invoke(), context).getOrElse { return Err(it) }

        // Build the SD-JWT payload using the DSL
        val payload =
            sdJwtPayload {
                // Standard JWT claims (never SD per RFC 9901 Section 9.7)
                iss(issClaim)
                claim("vct", vct)
                iat(iat)
                if (exp != null) claim("exp", exp)
                // Status list reference — a standard, never-selectively-disclosed claim.
                reservedStatus?.let { claim("status", it.claim) }
                // Holder binding: cnf carries EXACTLY ONE of `kid` (DID VM URL) or `jwk`,
                // matching the method the wallet used in its proof. Wallet libraries such
                // as credo-ts pick the first branch they find — including both with `jwk`
                // first makes them treat the binding as `method: jwk` and then fail to
                // locate the wallet's internal key (the wallet keyed its key by the DID,
                // not by thumbprint).
                //
                //   - DID-bound proof (kid = did:...#vm)  → cnf = { kid: <did-url> }
                //                                           sub = <did without fragment>
                //   - jwk-bound proof                     → cnf = { jwk: <public jwk> }
                //                                           sub omitted (proof's `iss`
                //                                           is wallet client_id, not the
                //                                           credential subject)
                context.holderBindingKey?.let { key ->
                    val holderKid = context.holderKeyId
                    val isDid = holderKid != null && holderKid.startsWith("did:")
                    if (isDid) {
                        claim("sub", holderKid!!.substringBefore('#'))
                        claim(
                            "cnf",
                            buildJsonObject { put("kid", JsonPrimitive(holderKid)) },
                        )
                    } else {
                        claim(
                            "cnf",
                            buildJsonObject { put("jwk", key) },
                        )
                    }
                }

                // Add attributes, respecting SD policies
                for ((name, value) in context.attributes) {
                    val policy = context.sdPolicies[name] ?: SdPolicy.SELECTIVELY_DISCLOSABLE
                    when (policy) {
                        SdPolicy.ALWAYS_DISCLOSED -> {
                            claim(name, value)
                        }

                        SdPolicy.SELECTIVELY_DISCLOSABLE -> {
                            claimSd(name, value)
                        }

                        SdPolicy.NEVER_DISCLOSED -> { /* skip entirely */ }
                    }
                }
            }

        val keyAlias = context.signingKeyAlias ?: context.credentialConfigurationId
        val issuerKey = ManagedOptsAlias(identifier = keyAlias)

        // Resolve signing key identifier for the JWT protected header, then merge in the
        // SD-JWT VC `typ` header. Per draft-ietf-oauth-sd-jwt-vc §3.1 the JWT MUST carry
        // `typ: dc+sd-jwt` (or `vc+sd-jwt` for the older profile). Wallets that don't find
        // this reject the credential (e.g. credo-ts `SdJwtVcService.ts:339-341`).
        val keyIdentifierHeader = resolveSigningHeader(keyAlias, context.signingKeyMode, context.signingCertChainPath)
        val headerType: String =
            when (context.credentialConfiguration.format) {
                CredentialFormat.SD_JWT_DC.value -> "dc+sd-jwt"
                CredentialFormat.SD_JWT_VC.value -> "vc+sd-jwt"
                else -> "dc+sd-jwt"
            }
        val signingHeader: JsonObject =
            buildJsonObject {
                put("typ", JsonPrimitive(headerType))
                keyIdentifierHeader?.forEach { (k, v) -> put(k, v) }
            }

        val result =
            sdJwtService
                .issueSdJwt(
                    IssueSdJwtArgs(
                        payload = payload,
                        issuer = issuerKey,
                        opts =
                            CreateJwsOpts(
                                protectedHeader = signingHeader,
                                noIdentifierInHeader = keyIdentifierHeader != null,
                            ),
                    ),
                ).getOrElse { return Err(it) }

        return Ok(
            CredentialEnvelope(
                credential = JsonPrimitive(result.sdJwt),
                format = context.credentialConfiguration.format,
            ),
        )
    }

    /**
     * Resolves the signing key identifier for the JWT protected header based on [mode].
     */
    private suspend fun resolveSigningHeader(
        keyAlias: String,
        mode: SigningKeyMode,
        certChainPath: String?,
    ): JsonObject? =
        when (mode) {
            is SigningKeyMode.None -> null

            is SigningKeyMode.Did -> resolveDidKid(keyAlias, mode.method)

            is SigningKeyMode.X5c -> resolveX5cHeader(keyAlias, certChainPath)

            is SigningKeyMode.JwkThumbprint -> resolveJwkThumbprintKid(keyAlias)

            is SigningKeyMode.Federation -> throw UnsupportedOperationException(
                "OpenID Federation signing mode is not yet implemented",
            )
        }

    /**
     * Creates a DID using the specified [method] from the signing key's public component
     * and returns the assertionMethod verification method ID as the kid.
     */
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

    /**
     * Resolves the X.509 certificate chain for the x5c JWT header.
     * Primary source: KMS key's x5c field. Fallback: PEM file from [certChainPath].
     */
    private suspend fun resolveX5cHeader(
        keyAlias: String,
        certChainPath: String?,
    ): JsonObject? {
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = keyAlias))
        if (keyResult.isErr) {
            return null
        }
        val jwk = keyResult.value.key as? Jwk ?: return null

        // Primary: certificate chain from the JWK's x5c field (KMS-stored)
        // Fallback: PEM file from config (would require file I/O service injection)
        val chain = jwk.x5c ?: return null

        return buildJsonObject {
            put("x5c", JsonArray(chain.map { JsonPrimitive(it) }))
        }
    }

    /**
     * Computes a JWK Thumbprint URI (RFC 9278) from the signing key's public component
     * and returns it as the kid in the JWT header.
     */
    private suspend fun resolveJwkThumbprintKid(keyAlias: String): JsonObject? {
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = keyAlias))
        if (keyResult.isErr) {
            return null
        }
        val jwk = keyResult.value.key as? Jwk ?: return null
        val publicJwk = jwk.toPublicKey()

        val thumbprintUri = generateJwkThumbprintUri(publicJwk)
        return buildJsonObject {
            put("kid", JsonPrimitive(thumbprintUri))
        }
    }

    companion object {
        private const val SECONDS_PER_DAY: Long = 24L * 60L * 60L
    }
}
