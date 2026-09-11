/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import com.sphereon.jsonld.processor.JsonLdProcessor
import com.sphereon.openid.oid4vc.common.vcdm.VcdmClassifier
import com.sphereon.openid.oid4vc.common.PresentationFormat
import com.sphereon.openid.oid4vc.common.vcdm.VcdmDocumentKind
import com.sphereon.openid.oid4vc.common.vcdm.VcdmProfiles
import com.sphereon.openid.oid4vc.common.vcdm.VcdmVersion
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.PreparedPresentation
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaSigningRequest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock

data class Oid4vpDataIntegrityHolderBindingRequest(
    val walletUnitId: String,
    val operationBinding: String?,
    val request: ResolvedOid4vpRequest,
    val selectedCredentials: List<SelectedCredential>,
)

/** Explicit holder-binding output: selected credentials and independently prepared VP artifacts. */
data class Oid4vpDataIntegrityHolderBindingResult(
    val selectedCredentials: List<SelectedCredential>,
    val preparedPresentations: List<PreparedPresentation> = emptyList(),
)

/**
 * Applies holder Data Integrity proofs before authorization-response assembly. Implementations
 * return explicit already-secured VP artifacts for ldp_vc selections; the generic holder must
 * only serialize those values and never select a KMS or sign them again.
 */
fun interface Oid4vpDataIntegrityHolderBindingProvider {
    suspend fun applyHolderBinding(request: Oid4vpDataIntegrityHolderBindingRequest): IdkResult<Oid4vpDataIntegrityHolderBindingResult, IdkError>

    companion object {
        val none: Oid4vpDataIntegrityHolderBindingProvider =
            Oid4vpDataIntegrityHolderBindingProvider { request -> Ok(Oid4vpDataIntegrityHolderBindingResult(request.selectedCredentials)) }
    }
}

/**
 * WSCA/WSCD-backed holder binding for JSON-LD VCDM credentials.
 *
 * [linkedDataDocumentLoaderProvider] is intentionally mandatory. RDF cryptosuites must use the
 * application's session-scoped loader chain so custom contexts receive the configured caching,
 * integrity-pin, network, and SSRF policy; this layer must never install its own fallback loader.
 */
class SecureComponentOid4vpDataIntegrityHolderBindingProvider(
    private val secureComponentCryptoSurfaceProvider: () -> Wsca,
    private val linkedDataDocumentLoaderProvider: () -> LinkedDataDocumentLoader,
    private val signingAlgorithmResolver: Oid4vpDataIntegritySigningAlgorithmResolver =
        Oid4vpDataIntegritySigningAlgorithmResolver.strict,
) : Oid4vpDataIntegrityHolderBindingProvider {
    constructor(
        secureComponentCryptoSurface: Wsca,
        linkedDataDocumentLoader: LinkedDataDocumentLoader,
        signingAlgorithmResolver: Oid4vpDataIntegritySigningAlgorithmResolver =
            Oid4vpDataIntegritySigningAlgorithmResolver.strict,
    ) : this({ secureComponentCryptoSurface }, { linkedDataDocumentLoader }, signingAlgorithmResolver)

    private val secureComponentCryptoSurface: Wsca by lazy { secureComponentCryptoSurfaceProvider() }
    private val cryptosuiteRegistry: Oid4vpDataIntegrityCryptosuiteSignerRegistry by lazy {
        Oid4vpDataIntegrityCryptosuiteSignerRegistry(JsonLdProcessor(linkedDataDocumentLoaderProvider()))
    }

    override suspend fun applyHolderBinding(
        request: Oid4vpDataIntegrityHolderBindingRequest,
    ): IdkResult<Oid4vpDataIntegrityHolderBindingResult, IdkError> {
        if (request.selectedCredentials.none { it.credentialFormat == CredentialFormat.LDP_VC }) {
            return Ok(Oid4vpDataIntegrityHolderBindingResult(request.selectedCredentials))
        }
        val operationBinding = request.operationBinding?.takeIf { it.isNotBlank() }
            ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "OID4VP Data Integrity holder binding requires attended operation binding",
                ),
            )
        val nonce = request.request.request.nonce?.takeIf { it.isNotBlank() }
            ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "OID4VP Data Integrity holder binding requires authorization request nonce",
                ),
            )
        val audience = request.request.verifierInfo.clientId.takeIf { it.isNotBlank() }
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP Data Integrity holder binding requires verifier client_id"))

        val verifierFormats = request.request.clientMetadata?.vpFormatsSupported
        if (verifierFormats != null && CredentialFormat.LDP_VC.value !in verifierFormats) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Verifier metadata does not advertise the ldp_vc presentation format",
                ),
            )
        }
        val ldpVcFormat = verifierFormats?.get(CredentialFormat.LDP_VC.value)
        ldpVcFormat?.proofTypeValues?.let { requestedProofTypes ->
            if (DataIntegrityProof.TYPE_DATA_INTEGRITY !in requestedProofTypes) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Verifier does not accept DataIntegrityProof for ldp_vc",
                    ),
                )
            }
        }
        val requestedCryptosuites = ldpVcFormat?.cryptosuiteValues
        val ldpCredentials = request.selectedCredentials.filter { it.credentialFormat == CredentialFormat.LDP_VC }
        val preparedPresentations = mutableListOf<PreparedPresentation>()
        for ((_, compatibleCredentials) in ldpCredentials.groupBy { credential ->
            val vc = credential.presentation as? JsonObject
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Selected ldp_vc credential '${credential.credentialId}' must be a JSON object"))
            VcdmClassifier.classifyDocument(vc).getOrElse { return Err(it) }.version
        }) {
            val secured = signPresentation(
                walletUnitId = request.walletUnitId,
                operationBinding = operationBinding,
                nonce = nonce,
                audience = audience,
                credentials = compatibleCredentials,
                verifierCryptosuiteAllowlist = requestedCryptosuites,
            ).getOrElse { return Err(it) }
            val prepared = PreparedPresentation(
                presentation = secured,
                presentationFormat = PresentationFormat.LDP_VP,
                credentialQueryIds = compatibleCredentials.map { it.credentialQueryId }.distinct(),
                credentialIds = compatibleCredentials.map { it.credentialId },
            )
            preparedPresentations += prepared
        }
        // Rebuild in original selection order so query and audit semantics remain stable.
        return Ok(Oid4vpDataIntegrityHolderBindingResult(request.selectedCredentials, preparedPresentations))
    }

    private suspend fun signPresentation(
        walletUnitId: String,
        operationBinding: String,
        nonce: String,
        audience: String,
        credentials: List<SelectedCredential>,
        verifierCryptosuiteAllowlist: List<String>?,
    ): IdkResult<JsonObject, IdkError> {
        if (credentials.isEmpty()) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "At least one ldp_vc credential is required"))
        val prepared = credentials.map { credential ->
            val vc = credential.presentation as? JsonObject
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Selected ldp_vc credential '${credential.credentialId}' must be a JSON object"))
            val classification = VcdmClassifier.classifyDocument(vc).getOrElse { return Err(it) }
            if (classification.kind != VcdmDocumentKind.CREDENTIAL) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Selected ldp_vc credential '${credential.credentialId}' must be a VerifiableCredential"))
            }
            val holderId = credential.holderId?.takeIf { it.isNotBlank() }
            val verificationMethod = credential.holderVerificationMethod?.takeIf { it.isNotBlank() }
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP Data Integrity verificationMethod is missing"))
            val cryptosuite = credential.dataIntegrityCryptosuite?.takeIf { it.isNotBlank() }
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Selected ldp_vc credential '${credential.credentialId}' has no explicit Data Integrity cryptosuite"))
            if (verifierCryptosuiteAllowlist != null && cryptosuite !in verifierCryptosuiteAllowlist) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Selected ldp_vc cryptosuite '$cryptosuite' is not accepted by the verifier"))
            }
            val cryptosuiteSigner = cryptosuiteRegistry.require(cryptosuite).getOrElse { return Err(it) }
            val signatureAlgorithm = signingAlgorithmResolver.resolve(credential, cryptosuite).getOrElse { return Err(it) }
            if (!cryptosuiteSigner.supports(signatureAlgorithm)) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Cryptosuite '$cryptosuite' does not support selected holder-key algorithm $signatureAlgorithm"))
            }
            val keyAlias = credential.holderKeyRef?.takeIf { it.isNotBlank() }
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP Data Integrity holder key reference is missing"))
            val keyRef = secureComponentCryptoSurface.ensureKey(walletUnitId, SecureComponentUsage.WALLET_CREDENTIAL_PROOF, signatureAlgorithm, keyAlias).getOrElse { return Err(it) }
            val expectedJoseAlgorithm = signatureAlgorithm.expectedJoseAlgorithm()
            if (!keyRef.algorithm.equals(expectedJoseAlgorithm, ignoreCase = true)) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WSCA holder key '${keyRef.keyId}' reports algorithm '${keyRef.algorithm}', expected '$expectedJoseAlgorithm'"))
            }
            if (keyRef.walletUnitId != null && keyRef.walletUnitId != walletUnitId) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WSCA holder key '${keyRef.keyId}' is bound to a different wallet unit"))
            }
            PreparedData(credential, vc, classification.version, holderId, verificationMethod, cryptosuite, cryptosuiteSigner, signatureAlgorithm, keyRef)
        }
        val first = prepared.first()
        val holderIds = prepared.mapNotNull { it.holderId }.distinct()
        val unsecured = buildJsonObject {
            putJsonArray("@context") { add(JsonPrimitive(if (first.version == VcdmVersion.V1_1) VcdmProfiles.V1_1_CONTEXT else VcdmProfiles.V2_0_CONTEXT)) }
            putJsonArray("type") { add(JsonPrimitive("VerifiablePresentation")) }
            if (holderIds.size == 1 && prepared.all { it.holderId != null }) put("holder", holderIds.single())
            putJsonArray("verifiableCredential") { prepared.forEach { add(it.vc) } }
        }
        val proofs = mutableListOf<JsonElement>()
        val seenBindings = mutableSetOf<Pair<String, String>>()
        for (item in prepared) {
            val binding = item.keyRef.keyId to item.verificationMethod
            if (!seenBindings.add(binding)) continue
            val created = Clock.System.now().toString()
            val proofConfig = DataIntegrityProof(
                cryptosuite = item.cryptosuite,
                proofPurpose = ProofPurpose.AUTHENTICATION,
                verificationMethod = item.verificationMethod,
                proofValue = "",
                created = created,
                domain = audience,
                challenge = nonce,
            )
            val signingInput = try { item.cryptosuiteSigner.hashData(unsecured, proofConfig, item.signatureAlgorithm) } catch (expected: Exception) {
                return Err(IdkError.fromString(message = "OID4VP Data Integrity '${item.cryptosuite}' transformation failed: ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
            }
            val signingRequest =
                WscaSigningRequest(
                    walletUnitId = walletUnitId,
                    keyRef = item.keyRef,
                    signingInput = signingInput,
                    operationBinding = operationBinding,
                    audience = audience,
                    nonce = nonce,
                )
            val prepared = secureComponentCryptoSurface.prepareSign(signingRequest).getOrElse { return Err(it) }
            val signature = secureComponentCryptoSurface.sign(prepared, signingRequest).getOrElse { return Err(it) }
            val proofValue = try { item.cryptosuiteSigner.encodeProofValue(signature, item.signatureAlgorithm) } catch (expected: IllegalArgumentException) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WSCA returned an invalid '${item.cryptosuite}' raw signature: ${expected.message}", throwable = expected))
            }
            proofs += encodeProof(proofConfig.copy(proofValue = proofValue))
        }
        return Ok(JsonObject(unsecured + ("proof" to if (credentials.size == 1) proofs.single() else kotlinx.serialization.json.JsonArray(proofs))))
    }

    private data class PreparedData(
        val credential: SelectedCredential,
        val vc: JsonObject,
        val version: VcdmVersion,
        val holderId: String?,
        val verificationMethod: String,
        val cryptosuite: String,
        val cryptosuiteSigner: Oid4vpDataIntegrityCryptosuiteSigner,
        val signatureAlgorithm: SignatureAlgorithm,
        val keyRef: com.sphereon.wallet.unit.WalletAttestedKeyRef,
    )

    private fun encodeProof(proof: DataIntegrityProof): JsonElement =
        buildJsonObject {
            put("type", proof.type)
            put("cryptosuite", proof.cryptosuite)
            put("proofPurpose", proof.proofPurpose.value)
            put("verificationMethod", proof.verificationMethod)
            put("proofValue", proof.proofValue)
            proof.created?.let { put("created", it) }
            proof.domain?.let { put("domain", it) }
            proof.challenge?.let { put("challenge", it) }
        }

    private fun SignatureAlgorithm.expectedJoseAlgorithm(): String =
        when (this) {
            SignatureAlgorithm.ED25519 -> "EdDSA"
            SignatureAlgorithm.ECDSA_SHA256 -> "ES256"
            SignatureAlgorithm.ECDSA_SHA384 -> "ES384"
            else -> throw IllegalArgumentException("Unsupported OID4VP Data Integrity holder-key algorithm $this")
        }
}
