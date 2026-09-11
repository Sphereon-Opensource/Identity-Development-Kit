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
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.vcdm.VcdmProfiles
import com.sphereon.openid.oid4vc.common.vcdm.VcdmUris
import com.sphereon.openid.oid4vc.common.vcdm.VcdmVersion
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * JWT VC JSON format handler (jwt_vc_json).
 *
 * Issues W3C Verifiable Credentials as JWTs using [JwtService].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<CredentialFormatHandler>())
@OptIn(ExperimentalUuidApi::class)
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
        val suppliedProperties =
            mergeVcdmIssuanceProperties(context, VcdmVersion.V1_1)
                .getOrElse { return Err(it) }
        if (context.credentialSubjects.isNotEmpty() && context.attributes.isNotEmpty()) {
            return Err(
                IdkError.fromString(
                    code = "invalid_vcdm_credential",
                    message = "credentialSubjects and attributes cannot both be supplied",
                ),
            )
        }
        context.credentialId?.let {
            if (!VcdmUris.isValid(it)) {
                return Err(IdkError.fromString(code = "invalid_vcdm_identifier", message = "credentialId must be a URI"))
            }
        }
        val credentialSubjects = context.credentialSubjects.ifEmpty { listOf(JsonObject(context.attributes)) }
        if (credentialSubjects.size != 1) {
            return Err(
                IdkError.fromString(
                    code = "invalid_vcdm_credential",
                    message = "VCDM 1.1 jwt_vc_json requires exactly one credentialSubject object",
                ),
            )
        }
        val credentialSubject = credentialSubjects.single()

        val nowEpochSeconds = Clock.System.now().epochSeconds
        val validityStart = context.validFrom ?: Instant.fromEpochSeconds(nowEpochSeconds)
        val validityEnd = context.validUntil ?: context.expirationInDays?.let { days ->
            Instant.fromEpochSeconds(validityStart.epochSeconds + days.toLong() * 24L * 60L * 60L)
        }
        // NumericDate has second precision. Use that same instant for the semantic issuanceDate
        // so the mandatory VCDM 1.1 nbf mapping is exact rather than approximately equal.
        val issuanceInstant = Instant.fromEpochSeconds(validityStart.epochSeconds)

        // Pre-sign: reserve a status-list entry (if configured) so its `credentialStatus` reference
        // is embedded into the signed credential.
        val statusEnricher = statusEnricherProvider?.invoke()
        val reservedStatus =
            reserveCredentialStatus(statusEnricher, context).getOrElse { return Err(it) }
        var statusBound = reservedStatus == null
        try {
        // VCDM credential identifiers are URIs. Use the same URI for the semantic VC id, the JWT
        // jti mapping, and the status-store binding so all three identify the issued credential.
        val credentialId = context.credentialId ?: reservedStatus?.let { "urn:uuid:${Uuid.random()}" }

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
                put("issuanceDate", issuanceInstant.toString())
                validityEnd?.let { put("expirationDate", it.toString()) }
                credentialId?.let { put("id", it) }
                suppliedProperties.forEach { (name, value) -> put(name, value) }
                reservedStatus?.let { put("credentialStatus", it.claim) }
                put("credentialSubject", credentialSubject)
            }

        val profileValidation = VcdmProfiles.v1_1.validateCredential(vcPayload)
        if (!profileValidation.valid) {
            val error = profileValidation.errors.firstOrNull()
            return Err(
                if (error != null) IdkError.fromDTO(error)
                else IdkError.fromString(
                    code = "invalid_vcdm_credential",
                    message = "VCDM 1.1 credential profile validation failed",
                ),
            )
        }

        // Build the full JWT payload with vc claim
        val jwtPayload =
            buildJsonObject {
                put("vc", vcPayload)
                put("iss", context.issuerIdentifier)
                put("nbf", validityStart.epochSeconds)
                credentialId?.let { put("jti", it) }
                val holderKid = context.holderKeyId
                val isDid = holderKid != null && holderKid.startsWith("did:")
                (credentialSubject["id"] as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.let { put("sub", it.content) }
                // Shift iat backward by the configured clock-skew so wallets with slightly-
                // ahead clocks still accept the credential on receipt.
                val iat = nowEpochSeconds - context.issuanceClockSkewInSeconds
                put("iat", iat)
                // Only emit `exp` when the configuration specifies a validity window.
                validityEnd?.let { put("exp", it.epochSeconds) }
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

        // Server-resolved signing key; a credential is never signed under a name derived from a
        // caller-visible identifier. The signing-key identifier header (kid / x5c) is derived from
        // `signingKeyMode` so verifiers can discover the public key — mirrors SdJwtVcFormatHandler.
        val keyAlias = context.requireSigningKeyName().getOrElse { return Err(it) }
        val issuerKey = ManagedOptsAlias(identifier = keyAlias)
        val signingVerificationMethodId =
            if (context.signingKeyMode is SigningKeyMode.Did) {
                val selected =
                    context.signingVerificationMethodId
                        ?: return Err(
                            IdkError.fromString(
                                code = "invalid_signing_verification_method",
                                message = "DID signing requires an exact assertionMethod verification method",
                            ),
                        )
                issuerKeyIdResolver.validateDidVerificationMethodId(keyAlias, selected).getOrElse { return Err(it) }
            } else {
                null
            }
        val keyIdentifierHeader =
            resolveIssuerSigningHeader(
                kms = kms,
                issuerKeyIdResolver = issuerKeyIdResolver,
                keyAlias = keyAlias,
                mode = context.signingKeyMode,
                signingVerificationMethodId = signingVerificationMethodId,
                configuredX5c = context.signingX5c,
            ).getOrElse { return Err(it) }

        val protectedHeader =
            buildJsonObject {
                // VCDM 1.1 requires typ=JWT when typ is present. Emit it consistently so generic
                // JOSE processors do not confuse this legacy wrapper with VCDM 2.0 vc+jwt.
                put("typ", JsonPrimitive("JWT"))
                keyIdentifierHeader?.forEach { (name, value) -> put(name, value) }
            }

        val result =
            jwtService
                .createJwsCompact(
                    CreateJwsArgs(
                        issuer = issuerKey,
                        payload = jwtPayload,
                        opts =
                            CreateJwsOpts(
                                protectedHeader = protectedHeader,
                                noIdentifierInHeader = true,
                            ),
                    ),
                ).getOrElse { return Err(it) }

        reservedStatus?.let { reserved ->
            checkNotNull(statusEnricher)
                .bind(reserved.handle, credentialId = credentialId, credentialHash = null)
                .getOrElse { return Err(it) }
        }
        statusBound = true

        return Ok(
            CredentialEnvelope(
                credential = JsonPrimitive(result.jwt),
                format = context.credentialConfiguration.format,
            ),
        )
        } finally {
            if (!statusBound && reservedStatus != null) {
                try {
                    withContext(NonCancellable) { checkNotNull(statusEnricher).cancel(reservedStatus.handle) }
                } catch (_: Exception) {
                    // Cleanup must never replace the validation/signing/bind error that caused it.
                }
            }
        }
    }

}
