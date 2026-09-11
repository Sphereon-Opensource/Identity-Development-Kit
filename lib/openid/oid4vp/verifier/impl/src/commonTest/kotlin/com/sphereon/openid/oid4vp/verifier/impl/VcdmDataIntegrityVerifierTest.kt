package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.dataintegrity.command.AddProofInput
import com.sphereon.crypto.dataintegrity.command.AddProofOutput
import com.sphereon.crypto.dataintegrity.command.VerifyProofInput
import com.sphereon.crypto.dataintegrity.facade.DataIntegrityServiceFacade
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.DataIntegrityVerificationResult
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolution
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolver
import com.sphereon.crypto.dataintegrity.registry.CryptosuiteRegistry
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteCreator
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteVerifier
import com.sphereon.crypto.dataintegrity.cryptosuite.CryptosuiteVerification
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerificationArgs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VcdmDataIntegrityVerifierTest {
    private val issuerDid = "did:example:issuer"
    private val holderDid = "did:example:holder"
    private val verificationMethod = "$issuerDid#assertion-key"

    @Test
    fun validVcdm2CredentialRequiresVerifiedAssertionProofAndIssuerController() = runTest {
        val document = credentialDocument()
        val verifier = verifier(document, verified = true)

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.ASSERTION_METHOD,
                expectedController = issuerDid,
            ),
        )

        assertTrue(result.isOk)
    }

    @Test
    fun dataIntegrityCreatedIsOptional() = runTest {
        val document = credentialDocument(includeCreated = false)
        val verifier = verifier(document, verified = true, includeCreated = false)

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.ASSERTION_METHOD,
                expectedController = issuerDid,
            ),
        )

        assertTrue(result.isOk)
    }

    @Test
    fun wrongProofPurposeIsRejectedEvenWhenSignatureResultIsVerified() = runTest {
        val document = credentialDocument(proofPurpose = "authentication")
        val verifier = verifier(document, verified = true)

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.ASSERTION_METHOD,
                expectedController = issuerDid,
            ),
        )

        assertFalse(result.isOk)
    }

    @Test
    fun wrongVerificationMethodControllerIsRejected() = runTest {
        val document = credentialDocument()
        val verifier = verifier(document, verified = true, controller = holderDid)

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.ASSERTION_METHOD,
                expectedController = issuerDid,
            ),
        )

        assertFalse(result.isOk)
    }

    @Test
    fun missingResolutionControllerIsRejectedEvenWhenSignatureIsVerified() = runTest {
        val document = credentialDocument()
        val verifier = verifier(document, verified = true, controller = null)

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.ASSERTION_METHOD,
                expectedController = issuerDid,
            ),
        )

        assertFalse(result.isOk)
    }

    @Test
    fun vpRequiresOid4vpDomainAndChallengeBinding() = runTest {
        val document = presentationDocument()
        val verifier = verifier(document, verified = true, controller = holderDid, vmDid = holderDid)

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.AUTHENTICATION,
                expectedController = holderDid,
                expectedDomain = "redirect_uri:https://verifier.example/callback",
                expectedChallenge = "oid4vp-nonce",
                requireDomainAndChallenge = true,
            ),
        )

        assertFalse(result.isOk)
    }

    @Test
    fun presentationWithoutHolderUsesAuthenticatedProofController() = runTest {
        val domain = "https://verifier.example"
        val challenge = "oid4vp-nonce"
        val vm = "$holderDid#authentication-key"
        val document =
            buildJsonObject {
                putJsonArray("@context") { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) }
                putJsonArray("type") { add(JsonPrimitive("VerifiablePresentation")) }
                putJsonObject("proof") {
                    put("type", "DataIntegrityProof")
                    put("cryptosuite", "eddsa-jcs-2022")
                    put("proofPurpose", "authentication")
                    put("verificationMethod", vm)
                    put("proofValue", "zsignature")
                    put("domain", domain)
                    put("challenge", challenge)
                }
            }
        val proof =
            DataIntegrityProof(
                cryptosuite = "eddsa-jcs-2022",
                proofPurpose = ProofPurpose.AUTHENTICATION,
                verificationMethod = vm,
                proofValue = "zsignature",
                domain = domain,
                challenge = challenge,
            )
        val verifier =
            VcdmDataIntegrityVerifierImpl(
                facade =
                    FakeDataIntegrityFacade(
                        DataIntegrityVerificationResult(
                            verified = true,
                            verifiedDocument = JsonObject(document - "proof"),
                            proofs = listOf(proof),
                        ),
                    ),
                verificationMethodResolver =
                    FakeVerificationMethodResolver(
                        controller = holderDid,
                        purposes = setOf(ProofPurpose.AUTHENTICATION),
                    ),
                cryptosuiteRegistry = AcceptingCryptosuiteRegistry,
            )

        val result =
            verifier.verify(
                VcdmDataIntegrityVerificationArgs(
                    document = document,
                    expectedProofPurpose = ProofPurpose.AUTHENTICATION,
                    expectedDomain = domain,
                    expectedChallenge = challenge,
                    requireDomainAndChallenge = true,
                ),
            )

        assertTrue(result.isOk)
        assertEquals(setOf(holderDid), result.value.authenticatedControllers)
    }

    @Test
    fun presentationWithoutHolderRejectsMissingAuthenticatedProofController() = runTest {
        val domain = "https://verifier.example"
        val challenge = "oid4vp-nonce"
        val vm = "$holderDid#authentication-key"
        val document = buildJsonObject {
            putJsonArray("@context") { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) }
            putJsonArray("type") { add(JsonPrimitive("VerifiablePresentation")) }
            put("proof", authenticationProof(vm, domain, challenge))
        }
        val proof = DataIntegrityProof(
            cryptosuite = "eddsa-jcs-2022",
            proofPurpose = ProofPurpose.AUTHENTICATION,
            verificationMethod = vm,
            proofValue = "zsignature",
            domain = domain,
            challenge = challenge,
        )
        val verifier = VcdmDataIntegrityVerifierImpl(
            facade = FakeDataIntegrityFacade(
                DataIntegrityVerificationResult(
                    verified = true,
                    verifiedDocument = JsonObject(document - "proof"),
                    proofs = listOf(proof),
                ),
            ),
            verificationMethodResolver = MappingVerificationMethodResolver(mapOf(vm to null)),
            cryptosuiteRegistry = AcceptingCryptosuiteRegistry,
        )

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.AUTHENTICATION,
                expectedDomain = domain,
                expectedChallenge = challenge,
                requireDomainAndChallenge = true,
            ),
        )

        assertFalse(result.isOk)
    }

    @Test
    fun presentationWithoutHolderAcceptsMultipleAuthenticatedProofControllers() = runTest {
        val domain = "https://verifier.example"
        val challenge = "oid4vp-nonce"
        val firstVm = "$holderDid#authentication-key"
        val secondVm = "did:example:other#authentication-key"
        val document = buildJsonObject {
            putJsonArray("@context") { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) }
            putJsonArray("type") { add(JsonPrimitive("VerifiablePresentation")) }
            putJsonArray("proof") {
                add(authenticationProof(firstVm, domain, challenge))
                add(authenticationProof(secondVm, domain, challenge))
            }
        }
        val proofs = listOf(
            DataIntegrityProof(
                cryptosuite = "eddsa-jcs-2022",
                proofPurpose = ProofPurpose.AUTHENTICATION,
                verificationMethod = firstVm,
                proofValue = "zsignature-1",
                domain = domain,
                challenge = challenge,
            ),
            DataIntegrityProof(
                cryptosuite = "eddsa-jcs-2022",
                proofPurpose = ProofPurpose.AUTHENTICATION,
                verificationMethod = secondVm,
                proofValue = "zsignature-2",
                domain = domain,
                challenge = challenge,
            ),
        )
        val verifier = VcdmDataIntegrityVerifierImpl(
            facade = FakeDataIntegrityFacade(
                DataIntegrityVerificationResult(
                    verified = true,
                    verifiedDocument = JsonObject(document - "proof"),
                    proofs = proofs,
                ),
            ),
            verificationMethodResolver = MappingVerificationMethodResolver(
                mapOf(firstVm to holderDid, secondVm to "did:example:other"),
            ),
            cryptosuiteRegistry = AcceptingCryptosuiteRegistry,
        )

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.AUTHENTICATION,
                expectedDomain = domain,
                expectedChallenge = challenge,
                requireDomainAndChallenge = true,
            ),
        )

        assertTrue(result.isOk)
        assertEquals(setOf(holderDid, "did:example:other"), result.value.authenticatedControllers)
    }

    private fun authenticationProof(
        verificationMethod: String,
        domain: String,
        challenge: String,
    ): JsonObject = buildJsonObject {
        put("type", "DataIntegrityProof")
        put("cryptosuite", "eddsa-jcs-2022")
        put("proofPurpose", "authentication")
        put("verificationMethod", verificationMethod)
        put("proofValue", "zsignature")
        put("domain", domain)
        put("challenge", challenge)
    }

    @Test
    fun oid4vpPresentationRejectsDomainSetEvenWhenItContainsExpectedClientId() = runTest {
        val expectedDomain = "https://verifier.example"
        val challenge = "oid4vp-nonce"
        val vm = "$holderDid#authentication-key"
        val document =
            buildJsonObject {
                putJsonArray("@context") { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) }
                putJsonArray("type") { add(JsonPrimitive("VerifiablePresentation")) }
                putJsonObject("proof") {
                    put("type", "DataIntegrityProof")
                    put("cryptosuite", "eddsa-jcs-2022")
                    put("proofPurpose", "authentication")
                    put("verificationMethod", vm)
                    put("proofValue", "zsignature")
                    putJsonArray("domain") {
                        add(JsonPrimitive(expectedDomain))
                        add(JsonPrimitive("https://other-verifier.example"))
                    }
                    put("challenge", challenge)
                }
            }
        val proof =
            DataIntegrityProof(
                cryptosuite = "eddsa-jcs-2022",
                proofPurpose = ProofPurpose.AUTHENTICATION,
                verificationMethod = vm,
                proofValue = "zsignature",
                domainSet = listOf(expectedDomain, "https://other-verifier.example"),
                challenge = challenge,
            )
        val verifier =
            VcdmDataIntegrityVerifierImpl(
                facade =
                    FakeDataIntegrityFacade(
                        DataIntegrityVerificationResult(
                            verified = true,
                            verifiedDocument = JsonObject(document - "proof"),
                            proofs = listOf(proof),
                        ),
                    ),
                verificationMethodResolver =
                    FakeVerificationMethodResolver(
                        controller = holderDid,
                        purposes = setOf(ProofPurpose.AUTHENTICATION),
                    ),
                cryptosuiteRegistry = AcceptingCryptosuiteRegistry,
            )

        val result =
            verifier.verify(
                VcdmDataIntegrityVerificationArgs(
                    document = document,
                    expectedProofPurpose = ProofPurpose.AUTHENTICATION,
                    expectedDomain = expectedDomain,
                    expectedChallenge = challenge,
                    requireDomainAndChallenge = true,
                ),
            )

        assertFalse(result.isOk)
    }

    @Test
    fun cryptographicallyUnverifiedProofIsRejected() = runTest {
        val document = credentialDocument()
        val verifier = verifier(document, verified = false)

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.ASSERTION_METHOD,
                expectedController = issuerDid,
            ),
        )

        assertFalse(result.isOk)
    }

    @Test
    fun verifierOwnedResolutionPolicyIsForwardedInsteadOfInferringFromProofReference() = runTest {
        val document = credentialDocument()
        val proof = DataIntegrityProof(
            cryptosuite = "eddsa-jcs-2022",
            proofPurpose = ProofPurpose.ASSERTION_METHOD,
            verificationMethod = verificationMethod,
            proofValue = "zsignature",
            created = "2026-08-25T12:00:00Z",
        )
        val resolver = FakeVerificationMethodResolver(
            controller = issuerDid,
            purposes = setOf(ProofPurpose.ASSERTION_METHOD),
        )
        val policy = VerificationMethodResolutionPolicy.empty()
        val verifier = VcdmDataIntegrityVerifierImpl(
            facade = FakeDataIntegrityFacade(
                DataIntegrityVerificationResult(
                    verified = true,
                    verifiedDocument = JsonObject(document - "proof"),
                    proofs = listOf(proof),
                ),
            ),
            verificationMethodResolver = resolver,
            cryptosuiteRegistry = AcceptingCryptosuiteRegistry,
        )

        val result = verifier.verify(
            VcdmDataIntegrityVerificationArgs(
                document = document,
                expectedProofPurpose = ProofPurpose.ASSERTION_METHOD,
                expectedController = issuerDid,
                verificationMethodResolutionPolicy = policy,
            ),
        )

        assertTrue(result.isOk)
        assertTrue(resolver.lastPolicy === policy)
    }

    private fun verifier(
        document: JsonObject,
        verified: Boolean,
        controller: String? = issuerDid,
        vmDid: String = issuerDid,
        includeCreated: Boolean = true,
    ): VcdmDataIntegrityVerifierImpl {
        val proof = DataIntegrityProof(
            cryptosuite = "eddsa-jcs-2022",
            proofPurpose = if (document["holder"] != null) ProofPurpose.AUTHENTICATION else ProofPurpose.ASSERTION_METHOD,
            verificationMethod = "${vmDid}#assertion-key",
            proofValue = "zsignature",
            created = "2026-08-25T12:00:00Z".takeIf { includeCreated },
            domain = document["holder"]?.let { null },
            challenge = document["holder"]?.let { null },
        )
        return VcdmDataIntegrityVerifierImpl(
            facade = FakeDataIntegrityFacade(
                DataIntegrityVerificationResult(
                    verified = verified,
                    verifiedDocument = JsonObject(document - "proof"),
                    proofs = listOf(proof),
                ),
            ),
            verificationMethodResolver = FakeVerificationMethodResolver(
                controller = controller,
                purposes = setOf(proof.proofPurpose),
            ),
            cryptosuiteRegistry = AcceptingCryptosuiteRegistry,
        )
    }

    private fun credentialDocument(
        proofPurpose: String = "assertionMethod",
        includeCreated: Boolean = true,
    ): JsonObject = buildJsonObject {
        putJsonArray("@context") { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) }
        putJsonArray("type") { add(JsonPrimitive("VerifiableCredential")) }
        put("issuer", issuerDid)
        putJsonObject("credentialSubject") { put("id", "did:example:subject"); put("name", "Alice") }
        putJsonObject("proof") {
            put("type", "DataIntegrityProof")
            put("cryptosuite", "eddsa-jcs-2022")
            put("proofPurpose", proofPurpose)
            put("verificationMethod", verificationMethod)
            put("proofValue", "zsignature")
            if (includeCreated) put("created", "2026-08-25T12:00:00Z")
        }
    }

    private fun presentationDocument(): JsonObject = buildJsonObject {
        putJsonArray("@context") { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) }
        putJsonArray("type") { add(JsonPrimitive("VerifiablePresentation")) }
        put("holder", holderDid)
        putJsonArray("verifiableCredential") { }
        putJsonObject("proof") {
            put("type", "DataIntegrityProof")
            put("cryptosuite", "eddsa-jcs-2022")
            put("proofPurpose", "authentication")
            put("verificationMethod", "$holderDid#assertion-key")
            put("proofValue", "zsignature")
            put("created", "2026-08-25T12:00:00Z")
        }
    }
}

private object AcceptingCryptosuiteRegistry : CryptosuiteRegistry {
    private val ids = setOf("eddsa-jcs-2022", "eddsa-rdfc-2022", "ecdsa-rdfc-2019")

    override fun getCreator(cryptosuiteId: String): DataIntegrityCryptosuiteCreator? = null
    override fun getVerifier(cryptosuiteId: String): DataIntegrityCryptosuiteVerifier? =
        if (cryptosuiteId in ids) AcceptingCryptosuiteVerifier(cryptosuiteId) else null

    override fun supportedCreators(): Set<String> = emptySet()
    override fun supportedVerifiers(): Set<String> = ids
}

private class AcceptingCryptosuiteVerifier(
    override val cryptosuiteId: String,
) : DataIntegrityCryptosuiteVerifier {
    override suspend fun verifyProof(
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
    ): IdkResult<CryptosuiteVerification, IdkError> = error("unused")
}

private class FakeDataIntegrityFacade(
    private val result: DataIntegrityVerificationResult,
) : DataIntegrityServiceFacade {
    override suspend fun addProof(input: AddProofInput): IdkResult<AddProofOutput, IdkError> = error("unused")

    override suspend fun verifyProof(input: VerifyProofInput): IdkResult<DataIntegrityVerificationResult, IdkError> = Ok(result)
}

private class FakeVerificationMethodResolver(
    private val controller: String?,
    private val purposes: Set<ProofPurpose>,
) : VerificationMethodResolver {
    var lastPolicy: VerificationMethodResolutionPolicy? = null

    override suspend fun resolve(reference: String): IdkResult<VerificationMethodResolution, com.sphereon.core.api.error.IdkErrorType> =
        Ok(
            VerificationMethodResolution(
                reference = reference,
                key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = "AQ"),
                controller = controller,
                authorizedProofPurposes = purposes,
            ),
        )

    override suspend fun resolve(
        reference: String,
        policy: VerificationMethodResolutionPolicy,
    ): IdkResult<VerificationMethodResolution, com.sphereon.core.api.error.IdkErrorType> {
        lastPolicy = policy
        return resolve(reference)
    }
}

private class MappingVerificationMethodResolver(
    private val controllers: Map<String, String?>,
) : VerificationMethodResolver {
    override suspend fun resolve(reference: String): IdkResult<VerificationMethodResolution, com.sphereon.core.api.error.IdkErrorType> =
        Ok(
            VerificationMethodResolution(
                reference = reference,
                key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = "AQ"),
                controller = controllers[reference],
                authorizedProofPurposes = setOf(ProofPurpose.AUTHENTICATION),
            ),
        )

    override suspend fun resolve(
        reference: String,
        policy: VerificationMethodResolutionPolicy,
    ): IdkResult<VerificationMethodResolution, com.sphereon.core.api.error.IdkErrorType> = resolve(reference)
}
