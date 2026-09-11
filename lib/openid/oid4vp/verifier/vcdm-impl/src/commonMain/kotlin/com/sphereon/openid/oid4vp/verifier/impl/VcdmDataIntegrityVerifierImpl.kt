package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.dataintegrity.command.VerifyProofInput
import com.sphereon.crypto.dataintegrity.facade.DataIntegrityServiceFacade
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.dataintegrity.registry.CryptosuiteRegistry
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolver
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import com.sphereon.openid.oid4vc.common.vcdm.VcdmDocumentKind
import com.sphereon.openid.oid4vc.common.vcdm.VcdmProfile
import com.sphereon.openid.oid4vc.common.vcdm.VcdmProfiles
import com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerificationArgs
import com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerificationResult
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * VCDM JSON Data Integrity boundary used by OID4VP. This intentionally wires
 * only the `DataIntegrityProof` profile. Cryptosuite-specific canonicalization
 * and signing remain delegated to the Data Integrity facade.
 */
@Inject
@SingleIn(com.sphereon.di.session.SessionScope::class)
@ContributesBinding(
    com.sphereon.di.session.SessionScope::class,
    binding = binding<com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerifier>(),
)
class VcdmDataIntegrityVerifierImpl(
    private val facade: DataIntegrityServiceFacade,
    private val verificationMethodResolver: VerificationMethodResolver,
    private val cryptosuiteRegistry: CryptosuiteRegistry,
) : com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerifier {
    override suspend fun verify(
        args: VcdmDataIntegrityVerificationArgs,
    ): IdkResult<VcdmDataIntegrityVerificationResult, IdkError> =
        verifyDocument(args)

    private suspend fun verifyDocument(
        args: VcdmDataIntegrityVerificationArgs,
    ): IdkResult<VcdmDataIntegrityVerificationResult, IdkError> {
        val profileAndKind = detectProfileAndKind(args.document)
            ?: return failure("document is not a supported VCDM 1.1 or 2.0 JSON object")
        val profile = profileAndKind.first
        val kind = profileAndKind.second
        val purposeFailure = when (kind) {
            VcdmDocumentKind.CREDENTIAL -> if (args.expectedProofPurpose != ProofPurpose.ASSERTION_METHOD) {
                "VCDM credentials require proofPurpose assertionMethod"
            } else {
                null
            }

            VcdmDocumentKind.PRESENTATION -> if (args.expectedProofPurpose != ProofPurpose.AUTHENTICATION) {
                "VCDM presentations require proofPurpose authentication"
            } else {
                null
            }
        }
        if (purposeFailure != null) return failure(purposeFailure)
        val securedDocument = securedDocument(args.document, profile)
        val profileValidation = when (kind) {
            VcdmDocumentKind.CREDENTIAL -> profile.validateCredential(args.document)
            VcdmDocumentKind.PRESENTATION -> profile.validatePresentation(args.document)
        }
        if (!profileValidation.valid) {
            return failure(
                profileValidation.errors.firstOrNull()?.message?.defaultMessage
                    ?: "VCDM profile validation failed",
            )
        }

        val rawProofs = extractProofObjects(securedDocument)
            ?: return failure("Data Integrity proof must be an object or a non-empty array of objects")
        val expectedPurpose = args.expectedProofPurpose.value
        for (proof in rawProofs) {
            val shapeFailure = validateProofShape(proof, expectedPurpose)
            if (shapeFailure != null) return failure(shapeFailure)
        }
        if (kind == VcdmDocumentKind.PRESENTATION && args.requireDomainAndChallenge &&
            (args.expectedDomain == null || args.expectedChallenge == null)
        ) {
            return failure("OID4VP-bound VCDM presentation requires expected domain and challenge")
        }

        val verified = try {
            facade.verifyProof(
                VerifyProofInput(
                    securedDocument = securedDocument,
                    expectedProofPurpose = expectedPurpose,
                    verificationMethodResolutionPolicy = args.verificationMethodResolutionPolicy,
                ),
            )
        } catch (expected: Exception) {
            return failure("Data Integrity proof verification failed: ${expected.message ?: "unexpected verifier error"}")
        }
        if (verified.isErr) return failure("Data Integrity proof verification failed: ${verified.error.message.defaultMessage}")
        val verification = verified.value
        val verifiedDocument = verification.verifiedDocument
        if (!verification.verified || verifiedDocument == null) {
            val detail = verification.errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
            return failure("Data Integrity cryptographic verification failed${detail?.let { ": $it" } ?: ""}")
        }
        if (verifiedDocument.containsKey(PROOF)) {
            return failure("Data Integrity verifier returned a secured document")
        }
        if (verification.proofs.size != rawProofs.size) {
            return failure("Data Integrity verifier did not verify every proof")
        }

        val declaredController = controllerFor(kind, securedDocument)
        val controller = args.expectedController ?: declaredController
        if (kind == VcdmDocumentKind.CREDENTIAL && controller == null) {
            return failure("VCDM credential has no issuer controller")
        }
        val authenticatedControllers = linkedSetOf<String>()
        for ((index, proof) in verification.proofs.withIndex()) {
            val metadataFailure = validateProofMetadata(
                proof = proof,
                expectedDomain = args.expectedDomain,
                expectedChallenge = args.expectedChallenge,
                requireDomainAndChallenge = kind == VcdmDocumentKind.PRESENTATION && args.requireDomainAndChallenge,
            )
            if (metadataFailure != null) return failure("proof[$index] $metadataFailure")
            val vmValidation = verifyVerificationMethod(
                verificationMethod = proof.verificationMethod,
                purpose = args.expectedProofPurpose,
                expectedController = controller,
                policy = args.verificationMethodResolutionPolicy,
            )
            if (vmValidation.failure != null) return failure("proof[$index] ${vmValidation.failure}")
            authenticatedControllers += requireNotNull(vmValidation.controller)
        }
        if (profile.version == com.sphereon.openid.oid4vc.common.vcdm.VcdmVersion.V2_0 &&
            kind == VcdmDocumentKind.PRESENTATION && declaredController == null
        ) {
            if (authenticatedControllers.isEmpty()) {
                return failure("VCDM 2.0 presentation without holder requires an authenticated controller")
            }
        }

        return com.sphereon.core.api.Ok(
            VcdmDataIntegrityVerificationResult(
                verifiedDocument = verifiedDocument,
                proofCount = verification.proofs.size,
                authenticatedControllers = authenticatedControllers,
            ),
        )
    }

    private fun detectProfileAndKind(document: JsonObject): Pair<VcdmProfile, VcdmDocumentKind>? {
        for (profile in listOf(VcdmProfiles.v2_0, VcdmProfiles.v1_1)) {
            val detected = profile.detect(document)
            if (detected.isOk) return profile to detected.value
        }
        return null
    }

    private fun securedDocument(document: JsonObject, profile: VcdmProfile): JsonObject {
        if (profile.version != com.sphereon.openid.oid4vc.common.vcdm.VcdmVersion.V1_1) return document
        val wrapper = document["vc"] ?: document["vp"]
        return wrapper as? JsonObject ?: document
    }

    private fun extractProofObjects(document: JsonObject): List<JsonObject>? = when (val proof = document[PROOF]) {
        is JsonObject -> listOf(proof)
        is JsonArray -> proof.map { it as? JsonObject ?: return null }.takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun validateProofShape(proof: JsonObject, expectedPurpose: String): String? {
        val type = proof.stringField("type") ?: return "proof.type must be a string"
        if (type != DataIntegrityProof.TYPE_DATA_INTEGRITY) return "proof.type must be DataIntegrityProof"
        val cryptosuite = proof.stringField("cryptosuite") ?: return "proof.cryptosuite must be a string"
        if (cryptosuiteRegistry.getVerifier(cryptosuite) == null) {
            return "unsupported or unconfigured Data Integrity cryptosuite '$cryptosuite'"
        }
        val purpose = proof.stringField("proofPurpose") ?: return "proof.proofPurpose must be a string"
        if (purpose != expectedPurpose) return "proof.proofPurpose must be '$expectedPurpose'"
        if (proof.stringField("verificationMethod").isNullOrBlank()) return "proof.verificationMethod must be a non-empty string"
        if (proof.stringField("proofValue").isNullOrBlank()) return "proof.proofValue must be a non-empty string"
        if (proof.containsKey("created") && proof.stringField("created").isNullOrBlank()) {
            return "proof.created must be a non-empty string when present"
        }
        return null
    }

    private fun validateProofMetadata(
        proof: DataIntegrityProof,
        expectedDomain: String?,
        expectedChallenge: String?,
        requireDomainAndChallenge: Boolean,
    ): String? {
        val created = proof.created?.let { parseInstant(it) ?: return "created must be a valid ISO-8601 instant" }
        val now = Clock.System.now()
        if (created != null && created > now) return "created must not be in the future"
        val expires = proof.expires?.let { parseInstant(it) ?: return "expires must be a valid ISO-8601 instant" }
        if (expires != null && created != null && expires < created) return "expires must not be before created"
        if (expires != null && expires < now) return "expires is in the past"
        if (expectedDomain != null && proof.domain != expectedDomain) return "domain does not match the OID4VP client_id"
        if (expectedChallenge != null && proof.challenge != expectedChallenge) return "challenge does not match the OID4VP nonce"
        if (requireDomainAndChallenge && (proof.domain == null || proof.challenge == null)) {
            return "domain and challenge are required for OID4VP holder binding"
        }
        return null
    }

    private suspend fun verifyVerificationMethod(
        verificationMethod: String,
        purpose: ProofPurpose,
        expectedController: String?,
        policy: VerificationMethodResolutionPolicy,
    ): VerificationMethodValidation {
        val resolution = try {
            verificationMethodResolver.resolve(verificationMethod, policy)
        } catch (expected: Exception) {
            return VerificationMethodValidation(failure = "verificationMethod resolution failed: ${expected.message ?: "unexpected resolver error"}")
        }
        if (resolution.isErr) return VerificationMethodValidation(failure = "verificationMethod resolution failed: ${resolution.error.message.defaultMessage}")
        val metadata = resolution.value
        if (metadata.reference != verificationMethod) return VerificationMethodValidation(failure = "resolved verificationMethod ID does not match proof.verificationMethod")
        val authenticatedController = metadata.controller?.takeIf { it.isNotBlank() }
            ?: return VerificationMethodValidation(failure = "verificationMethod controller could not be established")
        if (expectedController != null && authenticatedController != expectedController) {
            return VerificationMethodValidation(failure = "verificationMethod controller '$authenticatedController' does not match '$expectedController'")
        }
        if (purpose !in metadata.authorizedProofPurposes) {
            return VerificationMethodValidation(failure = "verificationMethod is not authorized for proofPurpose '${purpose.value}'")
        }
        return VerificationMethodValidation(controller = authenticatedController)
    }

    private data class VerificationMethodValidation(
        val controller: String? = null,
        val failure: String? = null,
    )

    private fun controllerFor(kind: VcdmDocumentKind, document: JsonObject): String? {
        val name = if (kind == VcdmDocumentKind.CREDENTIAL) "issuer" else "holder"
        return identifier(document[name])
    }

    private fun identifier(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.takeIf { it.isString }?.content
        is JsonObject -> element.stringField("id")
        else -> null
    }

    private fun JsonObject.stringField(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun parseInstant(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun <T : Any> failure(message: String): IdkResult<T, IdkError> =
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = message))

    companion object {
        private const val PROOF = "proof"
    }
}
