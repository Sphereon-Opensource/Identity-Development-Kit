/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.data.store.party.model.IdentityRole
import com.sphereon.mdoc.data.device.IssuerSignedCborCodec
import com.sphereon.mdoc.data.device.IssuerSignedCborCodecImpl
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodec
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.sdjwt.vc.IssuerMetadataResolutionError
import com.sphereon.sdjwt.vc.IssuerMetadataResolutionResult
import com.sphereon.sdjwt.vc.IssuerMetadataResolver
import com.sphereon.sdjwt.vc.SdJwtVcVerificationOpts
import com.sphereon.sdjwt.vc.VerifySdJwtVcArgs
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.wallet.WalletIdentityResolver
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialSubjectExtractor
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.IssuanceDiagnostic
import com.sphereon.wallet.credential.IssuanceDiagnosticCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

private val acceptanceIssuerSignedCborCodec: IssuerSignedCborCodec = IssuerSignedCborCodecImpl()
private val acceptanceMobileSecurityObjectCborCodec: MobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl()

/**
 * Receive-time verification must stay hermetic with respect to the issuer: signature,
 * disclosure-integrity, and typ/vct checks all work from the credential itself, while the
 * best-effort `/.well-known/jwt-vc-issuer` metadata fetch is an OUTBOUND trust lookup that
 * belongs to the flow's trust-review stage (which has already run by the time the receiver
 * stores). The OIDF conformance suite enforces this: an issuance test is interrupted with
 * "unexpected HTTP call to .well-known/jwt-vc-issuer" if the wallet phones back mid-issuance.
 */
private object ReceiveTimeNoFetchIssuerMetadataResolver : IssuerMetadataResolver {
    override suspend fun resolve(issuer: String): IssuerMetadataResolutionResult =
        IssuerMetadataResolutionResult.Failure(
            IssuerMetadataResolutionError.NetworkError(
                message = "Issuer metadata resolution is deliberately skipped during receive-time acceptance",
            ),
        )

    override fun supports(issuer: String): Boolean = true
}

/**
 * Storage-side acceptance checks the OID4VCI interaction-engine receiver runs on newly issued
 * credential instances before they are persisted, before `putCredential`.
 *
 * Three storage-side value-adds live here as a focused, independently testable collaborator
 * (rather than inlined into the receiver) so the receiver's `receiveCredentialResponse` stays
 * readable:
 *  1. VERIFY: issuer-signature verification of newly issued SD-JWT VC instances ([verify]).
 *  2. RECONCILE: actual (payload-derived) credential type-ref derivation, kept canonical on the
 *     record, reconciled against the issuer-metadata-derived expected refs with a mismatch
 *     diagnostic when they differ ([actualTypeRefs] / [diagnostics]).
 *  3. SUBJECTS: credential-subject extraction + identity resolution for newly created records
 *     ([resolveSubjects]).
 */
class Oid4vciIssuedCredentialAcceptance(
    private val verifySdJwtVcCommand: VerifySdJwtVcCommand,
    private val subjectExtractor: CredentialSubjectExtractor,
    private val identityResolver: WalletIdentityResolver,
) {
    /**
     * VERIFY: for SD-JWT VC formats, runs [VerifySdJwtVcCommand] per instance; a failed
     * verification rejects the whole store operation. Non-SD-JWT formats are not gated.
     */
    suspend fun verify(
        credentialConfigurationId: String,
        credentialFormat: CredentialFormat,
        instances: List<CredentialInstance>,
    ): IdkResult<Unit, IdkError> {
        if (!credentialFormat.isSdJwt) return Ok(Unit)
        for (instance in instances) {
            val verifyResult =
                verifySdJwtVcCommand.execute(
                    VerifySdJwtVcArgs(
                        sdJwt = instance.requireRaw(),
                        opts =
                            SdJwtVcVerificationOpts(
                                validateTypeMetadata = false,
                                validateStatus = false,
                                issuerMetadataResolver = ReceiveTimeNoFetchIssuerMetadataResolver,
                            ),
                    ),
                )
            if (verifyResult.isErr) {
                return Err(
                    IdkError.fromString(
                        code = "ISSUED_CREDENTIAL_VERIFICATION_FAILED",
                        message =
                            "Refusing to store issued credential '$credentialConfigurationId': " +
                                "issuer-signature verification failed: ${verifyResult.error.message.defaultMessage}",
                    ),
                )
            }
        }
        return Ok(Unit)
    }

    /**
     * RECONCILE (actual side): derives ACTUAL type refs from each issued payload (vct / docType /
     * W3C types per format). Issued credentials with NO derivable payload type refs are rejected.
     */
    fun actualTypeRefs(
        credentialConfigurationId: String,
        credentialFormat: CredentialFormat,
        instances: List<CredentialInstance>,
    ): IdkResult<Set<CredentialTypeRef>, IdkError> {
        val actual = mutableSetOf<CredentialTypeRef>()
        for (instance in instances) {
            val instanceRefs = credentialPayloadTypeRefs(credentialFormat, instance.requireRaw())
            if (instanceRefs.isEmpty()) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "No credentialTypeRefs could be derived from issued credential payload '${instance.id}' " +
                                "for '$credentialConfigurationId'",
                    ),
                )
            }
            actual += instanceRefs
        }
        return Ok(actual)
    }

    /**
     * RECONCILE (diagnostic side): expected (issuer-metadata) refs stay canonical on the record;
     * a mismatch against the actual refs appends a diagnostic, including the empty-expected
     * short-circuit (an issuer that declares no type constraints cannot mismatch).
     */
    fun diagnostics(
        expected: Set<CredentialTypeRef>,
        actual: Set<CredentialTypeRef>,
        observedAt: Instant,
    ): List<IssuanceDiagnostic> {
        if (expected.isEmpty()) return emptyList()
        if (expected.referenceKeys() == actual.referenceKeys()) return emptyList()
        return listOf(
            IssuanceDiagnostic(
                code = IssuanceDiagnosticCode.CREDENTIAL_TYPE_REF_MISMATCH,
                message = "Issued credential type references differ from issuer metadata expectations",
                expectedCredentialTypeRefs = expected,
                actualCredentialTypeRefs = actual,
                observedAt = observedAt,
            ),
        )
    }

    /**
     * SUBJECTS: extracts subject identifiers from an issued instance's raw payload and resolves
     * each through the identity book (extractSubjects + identityResolver.resolve(...,
     * IdentityRole.HOLDER) per subject). Callers populate this only once, when a NEW record is
     * created - existing records are not re-populated on append.
     */
    suspend fun resolveSubjects(
        credentialFormat: CredentialFormat,
        raw: String,
    ): IdkResult<List<IdentifierRef>, IdkError> {
        val resolved = mutableListOf<IdentifierRef>()
        for (subjectRef in subjectExtractor.extractSubjects(credentialFormat, raw)) {
            val resolvedResult = identityResolver.resolve(subjectRef, IdentityRole.HOLDER)
            if (resolvedResult.isErr) return Err(resolvedResult.error)
            resolved += resolvedResult.value
        }
        return Ok(resolved)
    }

    private fun credentialPayloadTypeRefs(
        format: CredentialFormat,
        raw: String,
    ): Set<CredentialTypeRef> =
        when {
            format.isSdJwt -> sdJwtVct(raw, format)
            format.isMdoc -> mdocDoctype(raw, format)
            format.isJwt || format == CredentialFormat.VC_LD_JSON_JWT -> w3cTypeRefsFromPayload(format, w3cTypesFromJwt(raw))
            else -> emptySet()
        }

    private fun sdJwtVct(
        raw: String,
        format: CredentialFormat,
    ): Set<CredentialTypeRef> {
        val parseResult = SdJwtCodec.parse(raw)
        if (parseResult.isErr) return emptySet()
        val vct =
            parseResult.value.payload.fullPayload["vct"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: return emptySet()
        return setOf(
            CredentialTypeRef(
                format = format,
                kind = CredentialTypeRefKind.SD_JWT_VCT,
                value = vct,
                source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                primary = true,
            ),
        )
    }

    private fun mdocDoctype(
        raw: String,
        format: CredentialFormat,
    ): Set<CredentialTypeRef> {
        val issuerSignedBytes =
            try {
                raw.decodeFromBase64Url()
            } catch (_: Exception) {
                return emptySet()
            }
        val issuerSignedResult = acceptanceIssuerSignedCborCodec.decode(issuerSignedBytes)
        if (issuerSignedResult.isErr) return emptySet()

        val msoPayload =
            issuerSignedResult.value.value.issuerAuth.payload
                ?.value ?: return emptySet()
        val msoResult = acceptanceMobileSecurityObjectCborCodec.decode(msoPayload)
        if (msoResult.isErr) return emptySet()

        val doctype =
            msoResult.value.value.docType
                .toString()
                .takeIf { it.isNotBlank() } ?: return emptySet()
        return setOf(
            CredentialTypeRef(
                format = format,
                kind = CredentialTypeRefKind.MDOC_DOCTYPE,
                value = doctype,
                source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                primary = true,
            ),
        )
    }

    private fun w3cTypesFromJwt(raw: String): List<String> {
        val parts = raw.split(".")
        if (parts.size < 2) return emptyList()
        return try {
            val payload = JwsUtils.decodeBase64UrlToJson(parts[1])
            val vcClaim = payload["vc"]
            if (vcClaim is JsonObject) {
                val vcTypes = jsonStringList(vcClaim["type"])
                if (vcTypes.isNotEmpty()) return vcTypes
            }
            jsonStringList(payload["type"])
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun w3cTypeRefsFromPayload(
        format: CredentialFormat,
        types: List<String>,
    ): Set<CredentialTypeRef> {
        val cleanTypes = types.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val primaryType = cleanTypes.firstOrNull { it != "VerifiableCredential" } ?: cleanTypes.firstOrNull()
        return cleanTypes
            .map {
                CredentialTypeRef(
                    format = format,
                    kind = CredentialTypeRefKind.W3C_VC_TYPE,
                    value = it,
                    source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                    primary = it == primaryType,
                )
            }.toSet()
    }

    private fun jsonStringList(element: JsonElement?): List<String> =
        when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> element.contentOrNull?.let { listOf(it) }.orEmpty()
            else -> emptyList()
        }

    private fun Set<CredentialTypeRef>.referenceKeys(): Set<Triple<CredentialFormat, CredentialTypeRefKind, String>> = map { Triple(it.format, it.kind, it.value) }.toSet()
}
