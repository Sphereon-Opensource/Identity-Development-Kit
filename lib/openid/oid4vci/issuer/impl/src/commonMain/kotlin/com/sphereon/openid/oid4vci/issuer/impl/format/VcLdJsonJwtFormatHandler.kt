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
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.WellKnownContexts
import com.sphereon.jsonld.WellKnownCredentialTypes
import com.sphereon.jsonld.command.JsonLdContextValidator
import com.sphereon.jsonld.command.JsonLdSchemaValidator
import com.sphereon.jsonld.command.ValidateJsonLdContextInput
import com.sphereon.jsonld.command.ValidateJsonLdSchemaInput
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
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
import kotlin.time.Instant

/**
 * `vc+ld+json+jwt` format handler — VCDM 2.0 + JSON-LD body enveloped via
 * JOSE per W3C VC JOSE/COSE Section 3.1.1.
 *
 * Pipeline on issuance:
 *
 * 1. Build the VCDM 2.0 credential body (JSON-LD with VCDM 2.0 vocabulary —
 *    `validFrom`/`validUntil`, no `iat`/`exp` shadow inside the VC).
 * 2. Validate the credential's `@context` chain against UNTP 0.7.0
 *    `@vocab` MUST-NOT, resolving remote references through the IDK
 *    `LinkedDataDocumentLoader` chain (built-in → cache → integrity-pin
 *    → HTTP). Failure aborts issuance.
 * 3. Validate the credential body against the JSON Schema registered for its
 *    `type` (when one is bundled). Failure aborts issuance with
 *    structured per-pointer violations.
 * 4. Merge JWT registered claims (`iss`/`iat`/`exp`/`sub`/`cnf`) at the root of
 *    the JOSE payload, alongside the VC body fields. VC-JOSE-COSE §3.1.1
 *    treats the entire VC document as the JWT payload — there is no `vc`
 *    wrapper claim, unlike the VC 1.1 `jwt_vc_json` shape.
 * 5. Sign with JWS; emit the compact serialization.
 *
 * UNTP 0.7.0 mandates this format for issued credentials.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<CredentialFormatHandler>())
class VcLdJsonJwtFormatHandler(
    private val jwtService: JwtService,
    private val contextValidator: JsonLdContextValidator,
    private val schemaValidator: JsonLdSchemaValidator,
) : CredentialFormatHandler {
    override val supportedFormat: String = CredentialFormat.VC_LD_JSON_JWT.value

    override suspend fun canHandle(
        request: CredentialRequest,
        configuration: CredentialConfigurationSupported,
    ): Boolean = configuration.format == CredentialFormat.VC_LD_JSON_JWT.value

    override suspend fun issueCredential(
        request: CredentialRequest,
        context: IssuanceContext,
    ): IdkResult<CredentialEnvelope, IdkError> {
        // Fail closed: this handler does not embed a credentialStatus entry, so a credential
        // configuration bound to a status list must not issue through it.
        unsupportedStatusListBinding(context)?.let { return Err(it) }

        val credentialTypes = resolveCredentialTypes(context)
        val primaryType = pickPrimaryType(credentialTypes)
        val now = Clock.System.now()
        val nowEpochSeconds = now.epochSeconds
        val expiry: Instant? = computeExpiry(context, nowEpochSeconds)

        val vcBody =
            buildVcBody(
                BuildVcBodyInputs(
                    credentialTypes = credentialTypes,
                    issuer = context.issuerIdentifier,
                    subject = context.subject,
                    attributes = context.attributes,
                    validFrom = now,
                    validUntil = expiry,
                    extraContexts = additionalContextsFor(context),
                ),
            )

        validateContextChain(vcBody).getOrElse { return Err(it) }
        validateAgainstSchema(vcBody, primaryType).getOrElse { return Err(it) }

        val jwtPayload = buildJwtPayload(vcBody, context, nowEpochSeconds)
        return signEnvelope(jwtPayload, context)
    }

    private fun resolveCredentialTypes(context: IssuanceContext): List<String> =
        context.credentialConfiguration.credentialDefinition?.type
            ?: listOf(WellKnownCredentialTypes.VERIFIABLE_CREDENTIAL)

    private fun pickPrimaryType(credentialTypes: List<String>): String =
        credentialTypes.firstOrNull { it != WellKnownCredentialTypes.VERIFIABLE_CREDENTIAL }
            ?: credentialTypes.first()

    private fun computeExpiry(
        context: IssuanceContext,
        nowEpochSeconds: Long,
    ): Instant? =
        context.expirationInDays?.let { days ->
            Instant.fromEpochSeconds(nowEpochSeconds + days.toLong() * SECONDS_PER_DAY)
        }

    /**
     * Validate @context chain (UNTP 0.7.0 @vocab MUST-NOT).
     */
    private suspend fun validateContextChain(vcBody: JsonObject): IdkResult<Unit, IdkError> {
        val verdict =
            contextValidator.validate(
                ValidateJsonLdContextInput(context = vcBody["@context"] ?: JsonArray(emptyList())),
            )
        if (verdict.isErr) {
            return Err(IdkError.fromDTO(verdict.error))
        }
        return Ok(Unit)
    }

    /**
     * Validate JSON Schema for the primary credential type. Unknown types (no schema in
     * the registry) are a soft pass: experimental or non-UNTP credentials issue without
     * schema enforcement. Schema mismatches and malformed schemas abort issuance.
     */
    private suspend fun validateAgainstSchema(
        vcBody: JsonObject,
        primaryType: String,
    ): IdkResult<Unit, IdkError> {
        val verdict =
            schemaValidator.validate(
                ValidateJsonLdSchemaInput(payload = vcBody, credentialType = primaryType),
            )
        return when {
            verdict.isOk -> Ok(Unit)
            verdict.error is JsonLdError.NoSchemaRegistered -> Ok(Unit)
            else -> Err(IdkError.fromDTO(verdict.error))
        }
    }

    /**
     * Build JWT payload: VC body fields at the root + JWT registered claims
     * (`iss`/`iat`/`exp`/`sub`/`cnf`). VC-JOSE-COSE §3.1.1 treats the entire VC document
     * as the JWT payload — there is no `vc` wrapper claim.
     */
    private fun buildJwtPayload(
        vcBody: JsonObject,
        context: IssuanceContext,
        nowEpochSeconds: Long,
    ): JsonObject =
        buildJsonObject {
            // Spread VC body keys at the root.
            for ((k, v) in vcBody) put(k, v)

            put("iss", context.issuerIdentifier)
            val didKid = context.holderKeyId?.takeIf { it.startsWith("did:") }
            val sub = didKid?.substringBefore('#') ?: context.subject
            put("sub", sub)
            // iat shifted backward by clock-skew tolerance.
            val iat = nowEpochSeconds - context.issuanceClockSkewInSeconds
            put("iat", iat)
            context.expirationInDays?.let { days ->
                put("exp", iat + days.toLong() * SECONDS_PER_DAY)
            }
            // Holder binding: cnf carries EITHER kid (DID VM URL) OR jwk,
            // never both — wallet libs (credo-ts and others) misbehave when
            // both are present.
            context.holderBindingKey?.let { key ->
                putJsonObject("cnf") {
                    if (didKid != null) {
                        put("kid", JsonPrimitive(didKid))
                    } else {
                        put("jwk", key)
                    }
                }
            }
        }

    private suspend fun signEnvelope(
        jwtPayload: JsonObject,
        context: IssuanceContext,
    ): IdkResult<CredentialEnvelope, IdkError> {
        val issuerKey = ManagedOptsAlias(identifier = context.credentialConfigurationId)
        val signed =
            jwtService
                .createJwsCompact(
                    CreateJwsArgs(issuer = issuerKey, payload = jwtPayload),
                ).getOrElse { return Err(it) }
        return Ok(
            CredentialEnvelope(
                credential = JsonPrimitive(signed.jwt),
                format = context.credentialConfiguration.format,
            ),
        )
    }

    /**
     * Grouped inputs for [buildVcBody] — avoids LongParameterList while keeping the
     * builder a pure function over its inputs. Internal-only, not serialized.
     */
    private data class BuildVcBodyInputs(
        val credentialTypes: List<String>,
        val issuer: String,
        val subject: String,
        val attributes: Map<String, kotlinx.serialization.json.JsonElement>,
        val validFrom: Instant,
        val validUntil: Instant?,
        val extraContexts: List<String>,
    )

    private fun buildVcBody(inputs: BuildVcBodyInputs): JsonObject =
        buildJsonObject {
            putJsonArray("@context") {
                // VCDM 2.0 base @context is mandatory and first.
                add(JsonPrimitive(WellKnownContexts.VCDM_2_0))
                for (ctx in inputs.extraContexts) {
                    if (ctx != WellKnownContexts.VCDM_2_0) {
                        add(JsonPrimitive(ctx))
                    }
                }
            }
            putJsonArray("type") {
                inputs.credentialTypes.forEach { add(JsonPrimitive(it)) }
            }
            put("issuer", inputs.issuer)
            put("validFrom", inputs.validFrom.toString())
            inputs.validUntil?.let { put("validUntil", it.toString()) }
            putJsonObject("credentialSubject") {
                put("id", inputs.subject)
                for ((name, value) in inputs.attributes) {
                    put(name, value)
                }
            }
        }

    /**
     * Pull additional `@context` IRIs from
     * `credentialConfiguration.credentialDefinition.@context`. The
     * configuration may declare extra contexts (e.g. UNTP DPP) that should
     * appear after the VCDM 2.0 base.
     */
    private fun additionalContextsFor(context: IssuanceContext): List<String> =
        context.credentialConfiguration.credentialDefinition
            ?.context
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private companion object {
        const val SECONDS_PER_DAY = 24L * 60L * 60L
    }
}
