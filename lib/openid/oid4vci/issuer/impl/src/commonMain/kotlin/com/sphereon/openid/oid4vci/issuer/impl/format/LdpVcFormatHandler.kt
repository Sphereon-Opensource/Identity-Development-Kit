/*
 * Copyright 2026 Sphereon International B.V.
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
import com.sphereon.crypto.dataintegrity.command.AddProofInput
import com.sphereon.crypto.dataintegrity.command.AddProofServiceCommand
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.vcdm.VcdmProfile
import com.sphereon.openid.oid4vc.common.vcdm.VcdmProfiles
import com.sphereon.openid.oid4vc.common.vcdm.VcdmUris
import com.sphereon.openid.oid4vc.common.vcdm.VcdmVersion
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.spi.CredentialStatusEnricher
import com.sphereon.statuslist.spi.StatusClaimMergeTarget
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * OID4VCI 1.0 Final `ldp_vc` issuer handler.
 *
 * The credential is a bare JSON-LD VCDM credential. Its `credential_definition` supplies the
 * complete context/type contract; the shared VCDM profile validates that contract for either
 * VCDM 1.1 or VCDM 2.0. Proof creation is delegated to the Data Integrity service command so
 * cryptosuite selection and KMS key resolution remain explicit and provider-neutral.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<CredentialFormatHandler>())
@OptIn(ExperimentalUuidApi::class)
class LdpVcFormatHandler(
    private val addProofCommand: AddProofServiceCommand,
    private val statusEnricherProvider: Provider<CredentialStatusEnricher>? = null,
) : CredentialFormatHandler {
    override val supportedFormat: String = CredentialFormat.LDP_VC.value

    override suspend fun canHandle(
        request: CredentialRequest,
        configuration: CredentialConfigurationSupported,
    ): Boolean = configuration.format == supportedFormat

    override suspend fun issueCredential(
        request: CredentialRequest,
        context: IssuanceContext,
    ): IdkResult<CredentialEnvelope, IdkError> {
        if (context.credentialConfiguration.format != supportedFormat) {
            return Err(invalidConfiguration("credential configuration format must be ldp_vc"))
        }
        if (context.statusListBinding?.spec == StatusListSpec.TOKEN_STATUS_LIST) {
            return Err(invalidConfiguration("ldp_vc credentials require a Bitstring Status List credentialStatus"))
        }

        val profile = resolveProfile(context.credentialConfiguration.credentialDefinition).getOrElse { return Err(it) }
        val suppliedProperties =
            mergeVcdmIssuanceProperties(context, profile.version)
                .getOrElse { return Err(it) }
        val issuer = requireUri(context.issuerIdentifier, "issuer identifier").getOrElse { return Err(it) }
        val verificationMethod =
            requireUri(context.signingVerificationMethodId, "signing verification method").getOrElse { return Err(it) }
        val signingKeyRef =
            context.signingKeyAlias?.takeIf { it.isNotBlank() }
                ?: return Err(invalidSigning("an explicit signing key/KMS reference is required"))
        val cryptosuite =
            context.dataIntegrityCryptosuite?.takeIf { it.isNotBlank() }
                ?: return Err(invalidSigning("an explicit Data Integrity cryptosuite is required"))
        if (context.credentialSubjects.isNotEmpty() && context.attributes.isNotEmpty()) {
            return Err(invalidConfiguration("credentialSubjects and attributes cannot both be supplied"))
        }
        if (context.attributes.containsKey("id")) {
            return Err(invalidConfiguration("credentialSubject.id must not be supplied as an ordinary attribute"))
        }
        context.credentialId?.let {
            if (!VcdmUris.isValid(it)) {
                return Err(invalidConfiguration("credentialId must be an absolute URI"))
            }
        }
        val credentialSubjects = context.credentialSubjects.ifEmpty { listOf(JsonObject(context.attributes)) }
        credentialSubjects.forEachIndexed { index, credentialSubject ->
            credentialSubject["id"]?.let { id ->
                val primitive = id as? JsonPrimitive
                val value = if (primitive?.isString == true) primitive.content else null
                if (value == null || !VcdmUris.isValid(value)) {
                    return Err(invalidConfiguration("credentialSubjects[$index].id must be an absolute URI string"))
                }
            }
        }

        val now = Clock.System.now()
        val validFrom = context.validFrom ?: now
        val validUntil = context.validUntil ?: expiry(context.expirationInDays, validFrom)
        val credential =
            buildCredential(
                profile = profile,
                definition = context.credentialConfiguration.credentialDefinition!!,
                issuer = issuer,
                credentialSubjects = credentialSubjects,
                credentialId = context.credentialId,
                validFrom = validFrom,
                validUntil = validUntil,
                properties = suppliedProperties,
            )
        validateCredential(profile, credential).getOrElse { return Err(it) }

        val statusEnricher = statusEnricherProvider?.invoke()
        val reservedStatus =
            reserveCredentialStatus(statusEnricher, context).getOrElse { return Err(it) }
        var statusBound = reservedStatus == null
        try {
            if (reservedStatus?.mergeTarget != null &&
                reservedStatus.mergeTarget != StatusClaimMergeTarget.VC_CREDENTIAL_STATUS
            ) {
                return Err(invalidConfiguration("ldp_vc credentials require a credentialStatus-compatible status list"))
            }

            // VCDM credential identifiers are URIs. Use the same URI for the semantic credential
            // id and the status-store binding so both identify the issued credential.
            val credentialId = context.credentialId ?: reservedStatus?.let { "urn:uuid:${Uuid.random()}" }
            val credentialWithStatus =
                if (reservedStatus != null) {
                    JsonObject(
                        credential +
                            mapOf(
                                "id" to JsonPrimitive(checkNotNull(credentialId)),
                                "credentialStatus" to reservedStatus.claim,
                            ),
                    )
                } else {
                    credential
                }

            // Status is part of the signed VCDM document, so validate the complete document again
            // before handing it to AddProof. This catches malformed enricher output and ensures
            // VCDM 1.1 and 2.0 cardinality/required-property rules are both enforced.
            if (reservedStatus != null) {
                validateCredential(profile, credentialWithStatus).getOrElse { return Err(it) }
            }

            val secured =
                addProofCommand
                    .execute(
                        AddProofInput(
                            unsecuredDocument = credentialWithStatus,
                            proofs =
                                listOf(
                                    ProofOptions(
                                        cryptosuite = cryptosuite,
                                        verificationMethod = verificationMethod,
                                        proofPurpose = ProofPurpose.ASSERTION_METHOD,
                                        signingKeyRef = signingKeyRef,
                                        created = now.toString(),
                                    ),
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
                    credential = secured.securedDocument,
                    format = supportedFormat,
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

    private fun resolveProfile(definition: CredentialDefinition?): IdkResult<VcdmProfile, IdkError> {
        if (definition == null) {
            return Err(invalidConfiguration("credential_definition is required for ldp_vc"))
        }
        val contexts = definition.context
            ?: return Err(invalidConfiguration("credential_definition.@context is required for ldp_vc"))
        if (contexts.isEmpty() || contexts.any { it.isBlank() || !VcdmUris.isValid(it) }) {
            return Err(invalidConfiguration("credential_definition.@context must be a non-empty list of IRIs"))
        }
        val types = definition.type
            ?: return Err(invalidConfiguration("credential_definition.type is required for ldp_vc"))
        if (types.isEmpty() || types.any { it.isBlank() }) {
            return Err(invalidConfiguration("credential_definition.type must be a non-empty list"))
        }

        val baseContexts = contexts.filter { it == VcdmProfiles.V1_1_CONTEXT || it == VcdmProfiles.V2_0_CONTEXT }
        return when {
            baseContexts.size != 1 ->
                Err(invalidConfiguration("credential_definition.@context must identify exactly one VCDM 1.1 or 2.0 base context"))
            baseContexts.single() == VcdmProfiles.V1_1_CONTEXT -> Ok(VcdmProfiles.v1_1)
            else -> Ok(VcdmProfiles.v2_0)
        }
    }

    private fun buildCredential(
        profile: VcdmProfile,
        definition: CredentialDefinition,
        issuer: String,
        credentialSubjects: List<JsonObject>,
        credentialId: String?,
        validFrom: Instant,
        validUntil: Instant?,
        properties: kotlinx.serialization.json.JsonObject,
    ): JsonObject =
        buildJsonObject {
            putJsonArray("@context") {
                definition.context!!.forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("type") {
                definition.type!!.forEach { add(JsonPrimitive(it)) }
            }
            put("issuer", issuer)
            credentialId?.let { put("id", it) }
            if (profile.version == VcdmVersion.V1_1) {
                put("issuanceDate", validFrom.toString())
                validUntil?.let { put("expirationDate", it.toString()) }
            } else {
                put("validFrom", validFrom.toString())
                validUntil?.let { put("validUntil", it.toString()) }
            }
            properties.forEach { (name, value) -> put(name, value) }
            if (credentialSubjects.size == 1) {
                put("credentialSubject", credentialSubjects.single())
            } else {
                putJsonArray("credentialSubject") {
                    credentialSubjects.forEach(::add)
                }
            }
        }

    private fun validateCredential(
        profile: VcdmProfile,
        credential: JsonObject,
    ): IdkResult<Unit, IdkError> {
        val validation = profile.validateCredential(credential)
        if (!validation.valid) {
            val error = validation.errors.firstOrNull()
            return Err(
                if (error != null) IdkError.fromDTO(error)
                else invalidConfiguration("credential_definition does not describe a valid ${profile.version.value} credential"),
            )
        }
        return Ok(Unit)
    }

    private fun requireUri(
        value: String?,
        label: String,
    ): IdkResult<String, IdkError> {
        val identifier = value?.takeIf { it.isNotBlank() }
            ?: return Err(invalidSigning("$label is required"))
        return if (VcdmUris.isValid(identifier)) Ok(identifier)
        else Err(invalidSigning("$label must be an absolute URI"))
    }

    private fun expiry(days: Int?, now: Instant): Instant? =
        days?.let { Instant.fromEpochSeconds(now.epochSeconds + it.toLong() * SECONDS_PER_DAY) }

    private companion object {
        const val SECONDS_PER_DAY = 24L * 60L * 60L

        fun invalidConfiguration(message: String): IdkError =
            IdkError.fromString(code = "invalid_ldp_vc_configuration", message = message)

        fun invalidSigning(message: String): IdkError =
            IdkError.fromString(code = "invalid_ldp_vc_signing", message = message)
    }
}
