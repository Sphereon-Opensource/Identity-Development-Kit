/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.Err
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsJsonGeneralWithIdentifiers
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerificationArgs
import com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerificationResult
import com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerifier
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationProvenance
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationResult
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.IssuanceDiagnosticCode
import com.sphereon.wallet.impl.CredentialSubjectExtractorImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Focused unit coverage for [Oid4vciIssuedCredentialAcceptance], for format-specific paths not
 * exercised end-to-end by [WalletStoreOid4vciCredentialResponseReceiverTest] (JWT/W3C type
 * derivation, mdoc mismatch diagnostics, malformed-mdoc rejection). SD-JWT
 * verification/reconciliation/subject-population are covered at the receiver entry point instead;
 * see that test class for the corresponding coverage (verification rejection, type-ref mismatch,
 * missing payload type refs, format-scoped expected refs, subject population, append equivalence).
 */
class Oid4vciIssuedCredentialAcceptanceTest {
    @Test
    fun verifyVcdm11JwtRejectsMalformedCredentialAfterClassification() =
        runTest {
            val verify = RecordingVerifyJwsCommand()
            val acceptance = testAcceptance(verifyJws = verify)
            val issuer = "https://issuer.example"
            // This is compact-JWS shaped and classifies as a VCDM 1.1 credential, but its
            // semantic VC is missing the required credentialSubject property.
            val raw = buildTestJwtVc(
                """{"iss":"$issuer","nbf":1767225600,"vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"$issuer"}}""",
            )

            val result = acceptance.verify(
                credentialConfigurationId = "employee",
                credentialFormat = CredentialFormat.JWT_VC_JSON,
                instances = listOf(testInstance(raw, CredentialFormat.JWT_VC_JSON)),
                issuerAuthentication = WalletIssuerAuthenticationResult(
                    issuer = issuer,
                    trustedJwks = Json.parseToJsonElement("""{"keys":[{"kty":"EC","crv":"P-256","x":"x","y":"y","kid":"issuer-1"}]}""").jsonObject,
                    provenance = listOf(WalletIssuerAuthenticationProvenance("trust-domain", "issuer.example")),
                ),
            )

            assertTrue(result.isErr, "a malformed VCDM 1.1 credential must be rejected at acceptance")
            assertEquals(null, verify.lastArgs, "profile-invalid credentials must not reach JWS verification")
        }

    @Test
    fun verifyVcdm20JwtRejectsMalformedCredentialAfterClassification() =
        runTest {
            val verify = RecordingVerifyJwsCommand()
            val acceptance = testAcceptance(verifyJws = verify)
            val issuer = "https://issuer.example"
            // This is compact-JWS shaped and classifies as a VCDM 2.0 credential, but its
            // semantic VC is missing the required issuer property.
            val raw = buildTestJwtVc(
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","EmployeeCredential"],"credentialSubject":{"id":"did:example:holder"}}""",
                headerJson = """{"alg":"ES256","typ":"vc+jwt"}""",
            )

            val result = acceptance.verify(
                credentialConfigurationId = "employee",
                credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                instances = listOf(testInstance(raw, CredentialFormat.JWT_VC_JSON_LD)),
                issuerAuthentication = WalletIssuerAuthenticationResult(
                    issuer = issuer,
                    trustedJwks = Json.parseToJsonElement("""{"keys":[{"kty":"EC","crv":"P-256","x":"x","y":"y","kid":"issuer-1"}]}""").jsonObject,
                    provenance = listOf(WalletIssuerAuthenticationProvenance("trust-domain", "issuer.example")),
                ),
            )

            assertTrue(result.isErr, "a malformed VCDM 2.0 credential must be rejected at acceptance")
            assertEquals(null, verify.lastArgs, "profile-invalid credentials must not reach JWS verification")
        }

    @Test
    fun verifyVcdm11JwtUsesIssuerPinnedJwksAndChecksIssuerBinding() =
        runTest {
            val issuer = "https://issuer.example"
            val trustedJwks = Json.parseToJsonElement("""{"keys":[{"kty":"EC","crv":"P-256","x":"x","y":"y","kid":"issuer-1"}]}""").jsonObject
            val verify = RecordingVerifyJwsCommand()
            val acceptance = testAcceptance(verifyJws = verify)
            val raw = buildTestJwtVc(
                """{"iss":"$issuer","sub":"did:example:holder","nbf":1767225600,"vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"$issuer","credentialSubject":{"id":"did:example:holder","role":"employee"}}}""",
            )

            val result = acceptance.verify(
                credentialConfigurationId = "employee",
                credentialFormat = CredentialFormat.JWT_VC_JSON,
                instances = listOf(testInstance(raw, CredentialFormat.JWT_VC_JSON)),
                issuerAuthentication = WalletIssuerAuthenticationResult(
                    issuer = issuer,
                    trustedJwks = trustedJwks,
                    provenance = listOf(WalletIssuerAuthenticationProvenance("trust-domain", "issuer.example")),
                ),
            )

            assertTrue(result.isOk, "pinned issuer-authentication should admit a valid VCDM 1.1 JWT VC: $result")
            assertEquals(trustedJwks, verify.lastArgs?.trustedJwks)
            assertEquals(raw, (verify.lastArgs?.jws as JwsCompact).value)
        }

    @Test
    fun verifyVcdm20JwtFailsClosedWithoutIssuerAuthentication() =
        runTest {
            val acceptance = testAcceptance(verifyJws = RecordingVerifyJwsCommand())
            val raw = buildTestJwtVc(
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"https://issuer.example","credentialSubject":{"id":"did:example:holder","role":"employee"}}""",
                headerJson = """{"alg":"ES256","typ":"vc+jwt"}""",
            )

            val result = acceptance.verify(
                credentialConfigurationId = "employee",
                credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                instances = listOf(testInstance(raw, CredentialFormat.JWT_VC_JSON_LD)),
            )

            assertTrue(result.isErr, "a JWT VC must not be accepted without resolved issuer keys")
        }

    @Test
    fun verifyVcdm20JwtUsesIssuerPinnedJwks() =
        runTest {
            val issuer = "https://issuer.example"
            val trustedJwks = Json.parseToJsonElement(
                """{"keys":[{"kty":"EC","crv":"P-256","x":"x","y":"y","kid":"issuer-2"}]}""",
            ).jsonObject
            val verify = RecordingVerifyJwsCommand()
            val acceptance = testAcceptance(verifyJws = verify)
            val raw = buildTestJwtVc(
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"$issuer","credentialSubject":{"id":"did:example:holder","role":"employee"}}""",
                headerJson = """{"alg":"ES256","typ":"vc+jwt"}""",
            )

            val result = acceptance.verify(
                credentialConfigurationId = "employee",
                credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                instances = listOf(testInstance(raw, CredentialFormat.JWT_VC_JSON_LD)),
                issuerAuthentication = WalletIssuerAuthenticationResult(
                    issuer = issuer,
                    trustedJwks = trustedJwks,
                    provenance = listOf(WalletIssuerAuthenticationProvenance("pinned-jwks", "issuer-policy")),
                ),
                expectedIssuer = issuer,
            )

            assertTrue(result.isOk, "VCDM 2.0 JWT VC should verify with issuer-pinned keys: $result")
            assertEquals(trustedJwks, verify.lastArgs?.trustedJwks)
        }

    @Test
    fun verifyJwtRejectsPrivateIssuerKeyEvenWhenTrustResultShapeIsValid() =
        runTest {
            val issuer = "https://issuer.example"
            val acceptance = testAcceptance(verifyJws = RecordingVerifyJwsCommand())
            val raw = buildTestJwtVc(
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential"],"issuer":"$issuer","credentialSubject":{"id":"did:example:holder"}}""",
                headerJson = """{"alg":"ES256","typ":"vc+jwt"}""",
            )
            val result = acceptance.verify(
                credentialConfigurationId = "employee",
                credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                instances = listOf(testInstance(raw, CredentialFormat.JWT_VC_JSON_LD)),
                issuerAuthentication = WalletIssuerAuthenticationResult(
                    issuer = issuer,
                    trustedJwks = Json.parseToJsonElement(
                        """{"keys":[{"kty":"EC","crv":"P-256","x":"x","y":"y","d":"private","kid":"issuer-1"}]}""",
                    ).jsonObject,
                    provenance = listOf(WalletIssuerAuthenticationProvenance("trust-domain", issuer)),
                ),
            )
            assertTrue(result.isErr, "issuer trust results must pass the same public signing-key admission")
        }

    @Test
    fun verifyJwtRejectsCredentialIssuerDifferentFromPinnedIssuer() =
        runTest {
            val verify = RecordingVerifyJwsCommand()
            val acceptance = testAcceptance(verifyJws = verify)
            val raw = buildTestJwtVc(
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"https://attacker.example","credentialSubject":{"id":"did:example:holder","role":"employee"}}""",
                headerJson = """{"alg":"ES256","typ":"vc+jwt"}""",
            )
            val auth = WalletIssuerAuthenticationResult(
                issuer = "https://issuer.example",
                trustedJwks = Json.parseToJsonElement("""{"keys":[{"kty":"EC","crv":"P-256","x":"x","y":"y"}]}""").jsonObject,
                provenance = listOf(WalletIssuerAuthenticationProvenance("did", "did:example:issuer")),
            )

            val result = acceptance.verify(
                credentialConfigurationId = "employee",
                credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                instances = listOf(testInstance(raw, CredentialFormat.JWT_VC_JSON_LD)),
                issuerAuthentication = auth,
            )

            assertTrue(result.isErr, "credential issuer must match the pinned issuer identity")
        }

    @Test
    fun verifySkipsNonSdJwtFormatsWithoutCallingTheVerificationCommand() =
        runTest {
            val verify = FakeVerifySdJwtVcCommand(accept = false)
            val acceptance =
                Oid4vciIssuedCredentialAcceptance(
                    verifySdJwtVcCommand = verify,
                    subjectExtractor = CredentialSubjectExtractorImpl(),
                    identityResolver = TestPassThroughWalletIdentityResolver,
                )
            val instance = testInstance(raw = "mdoc-raw-does-not-matter", format = CredentialFormat.MSO_MDOC)

            val result = acceptance.verify("SomeConfig", CredentialFormat.MSO_MDOC, listOf(instance))

            assertTrue(result.isOk, "non-SD-JWT formats must not be gated by issuer-signature verification")
            assertEquals(null, verify.lastVerified, "the verification command must never be invoked for a non-SD-JWT format")
        }

    @Test
    fun verifyLdpVcClassifiesBareVcdmAndDelegatesCryptographicVerification() =
        runTest {
            val verifier = RecordingVcdmDataIntegrityVerifier(accept = true)
            val acceptance = testAcceptance(vcdmDataIntegrityVerifier = verifier)
            val issuer = "https://issuer.example"
            val raw =
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"$issuer","credentialSubject":{"id":"https://holder.example/subject"},"proof":{"type":"DataIntegrityProof","cryptosuite":"eddsa-jcs-2022","proofPurpose":"assertionMethod","verificationMethod":"https://issuer.example/keys#assertion","created":"2026-08-26T10:00:00Z","proofValue":"zvalid"}}"""

            val result = acceptance.verify(
                credentialConfigurationId = "employee-ldp-vc",
                credentialFormat = CredentialFormat.LDP_VC,
                instances = listOf(testInstance(raw, CredentialFormat.LDP_VC)),
                expectedIssuer = issuer,
            )

            assertTrue(result.isOk, "bare ldp_vc must be delegated to the DI verifier: $result")
            assertEquals(issuer, verifier.lastArgs?.expectedController)
            assertEquals(ProofPurpose.ASSERTION_METHOD, verifier.lastArgs?.expectedProofPurpose)
            assertEquals("https://holder.example/subject", verifier.lastArgs?.document?.get("credentialSubject")?.jsonObject?.get("id")?.jsonPrimitive?.content)
        }

    @Test
    fun verifyLdpVcAcceptsVcdm11BareCredential() =
        runTest {
            val issuer = "did:example:issuer"
            val acceptance = testAcceptance(vcdmDataIntegrityVerifier = RecordingVcdmDataIntegrityVerifier(accept = true))
            val raw =
                """{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"$issuer","credentialSubject":{"id":"did:example:holder"},"proof":{"type":"DataIntegrityProof","cryptosuite":"eddsa-jcs-2022","proofPurpose":"assertionMethod","verificationMethod":"$issuer#assertion","proofValue":"zvalid"}}"""

            val result = acceptance.verify(
                credentialConfigurationId = "employee-ldp-vc-11",
                credentialFormat = CredentialFormat.LDP_VC,
                instances = listOf(testInstance(raw, CredentialFormat.LDP_VC)),
                expectedIssuer = issuer,
            )

            assertTrue(result.isOk, "bare VCDM 1.1 ldp_vc must be accepted after DI verification: $result")
        }

    @Test
    fun verifyLdpVcRejectsTamperWrongIssuerAndUnsupportedContextBeforeStorage() =
        runTest {
            val issuer = "https://issuer.example"
            val tamperVerifier = RecordingVcdmDataIntegrityVerifier(accept = false)
            val acceptance = testAcceptance(vcdmDataIntegrityVerifier = tamperVerifier)
            val valid =
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"$issuer","credentialSubject":{"id":"https://holder.example/subject"},"proof":{"type":"DataIntegrityProof","cryptosuite":"eddsa-jcs-2022","proofPurpose":"assertionMethod","verificationMethod":"https://issuer.example/keys#assertion","created":"2026-08-26T10:00:00Z","proofValue":"ztampered"}}"""
            val wrongIssuer = valid.replace(issuer, "https://attacker.example")
            val unsupported = valid.replace("https://www.w3.org/ns/credentials/v2", "https://example.com/unsupported-vcdm")

            val tamperResult = acceptance.verify("employee-ldp-vc", CredentialFormat.LDP_VC, listOf(testInstance(valid, CredentialFormat.LDP_VC)), expectedIssuer = issuer)
            val wrongIssuerResult = acceptance.verify("employee-ldp-vc", CredentialFormat.LDP_VC, listOf(testInstance(wrongIssuer, CredentialFormat.LDP_VC)), expectedIssuer = issuer)
            val unsupportedResult = acceptance.verify("employee-ldp-vc", CredentialFormat.LDP_VC, listOf(testInstance(unsupported, CredentialFormat.LDP_VC)), expectedIssuer = issuer)

            assertTrue(tamperResult.isErr, "cryptographic DI rejection must block storage")
            assertTrue(wrongIssuerResult.isErr, "credential issuer must match the resolved issuer")
            assertTrue(unsupportedResult.isErr, "unsupported VCDM context must be rejected")
        }

    @Test
    fun ldpVcDerivesPayloadTypeAndSubjectReferencesFromBareJson() =
        runTest {
            val acceptance = testAcceptance(vcdmDataIntegrityVerifier = RecordingVcdmDataIntegrityVerifier(accept = true))
            val raw =
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"https://issuer.example","credentialSubject":{"id":"https://holder.example/subject"},"proof":{"type":"DataIntegrityProof","cryptosuite":"eddsa-jcs-2022","proofPurpose":"assertionMethod","verificationMethod":"https://issuer.example/keys#assertion","created":"2026-08-26T10:00:00Z","proofValue":"zvalid"}}"""
            val instance = testInstance(raw, CredentialFormat.LDP_VC)

            val refs = acceptance.actualTypeRefs("employee-ldp-vc", CredentialFormat.LDP_VC, listOf(instance))
            val subjects = acceptance.resolveSubjects(CredentialFormat.LDP_VC, raw)

            assertTrue(refs.isOk)
            assertEquals(setOf("VerifiableCredential", "EmployeeCredential"), refs.value.map { it.value }.toSet())
            assertTrue(subjects.isOk)
            assertEquals(listOf("https://holder.example/subject"), subjects.value.map { it.value })
        }

    @Test
    fun actualTypeRefsDerivesW3cTypesFromJwtVcPayloadAndDiagnosticsRecordsMismatch() =
        runTest {
            val expectedType = "EmployeeCredential"
            val actualType = "EmployeeBadgeCredential"
            val acceptance = testAcceptance()
            val raw =
                buildTestJwtVc(
                    """{"iss":"https://issuer.example","sub":"did:example:holder-jwt-vc","nbf":1767225600,"vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","$actualType"],"credentialSubject":{"id":"did:example:holder-jwt-vc"}}}""",
                )
            val instance = testInstance(raw = raw, format = CredentialFormat.JWT_VC_JSON)

            val actualResult = acceptance.actualTypeRefs("JwtConfig", CredentialFormat.JWT_VC_JSON, listOf(instance))
            assertTrue(actualResult.isOk, "actualTypeRefs should succeed: ${if (actualResult.isErr) actualResult.error else ""}")
            val actual = actualResult.value
            assertEquals(setOf("VerifiableCredential", actualType), actual.map { it.value }.toSet())
            assertTrue(actual.all { it.source == CredentialTypeRefSource.CREDENTIAL_PAYLOAD })

            val expected =
                setOf(
                    CredentialTypeRef(
                        format = CredentialFormat.JWT_VC_JSON,
                        kind = CredentialTypeRefKind.W3C_VC_TYPE,
                        value = "VerifiableCredential",
                        source = CredentialTypeRefSource.ISSUER_METADATA,
                    ),
                    CredentialTypeRef(
                        format = CredentialFormat.JWT_VC_JSON,
                        kind = CredentialTypeRefKind.W3C_VC_TYPE,
                        value = expectedType,
                        source = CredentialTypeRefSource.ISSUER_METADATA,
                        primary = true,
                    ),
                )
            val diagnostics = acceptance.diagnostics(expected, actual, Clock.System.now())
            assertEquals(1, diagnostics.size)
            assertEquals(IssuanceDiagnosticCode.CREDENTIAL_TYPE_REF_MISMATCH, diagnostics.single().code)
            assertEquals(setOf("VerifiableCredential", actualType), diagnostics.single().actualCredentialTypeRefs.map { it.value }.toSet())
        }

    @Test
    fun actualTypeRefsDistinguishesVcdm20RootAndVcdm11WrappedCredentials() =
        runTest {
            val acceptance = testAcceptance()
            val v2Type = "UniversityDegreeCredential"
            val v1Type = "EmployeeCredential"
            val v2 =
                buildTestJwtVc(
                    """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","$v2Type"],"issuer":"did:example:issuer","validFrom":"2026-01-01T00:00:00Z","credentialSubject":{"id":"did:example:v2-holder","degree":"BSc"}}""",
                    headerJson = """{"alg":"ES256","typ":"vc+jwt"}""",
                )
            val v1 =
                buildTestJwtVc(
                    """{"iss":"did:example:issuer","sub":"did:example:v1-holder","nbf":1767225600,"vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","$v1Type"],"issuer":"did:example:issuer","issuanceDate":"2026-01-01T00:00:00Z","credentialSubject":{"id":"did:example:v1-holder","role":"employee"}}}""",
                )

            val v2Result = acceptance.actualTypeRefs("v2", CredentialFormat.JWT_VC_JSON_LD, listOf(testInstance(v2, CredentialFormat.JWT_VC_JSON_LD)))
            val v1Result = acceptance.actualTypeRefs("v1", CredentialFormat.JWT_VC_JSON, listOf(testInstance(v1, CredentialFormat.JWT_VC_JSON)))

            assertTrue(v2Result.isOk, "VCDM 2.0 root credential should be recognized")
            assertTrue(v1Result.isOk, "VCDM 1.1 wrapped credential should be recognized")
            assertEquals(setOf("VerifiableCredential", v2Type), v2Result.value.map { it.value }.toSet())
            assertEquals(setOf("VerifiableCredential", v1Type), v1Result.value.map { it.value }.toSet())
            assertTrue(v2Result.value.all { it.format == CredentialFormat.JWT_VC_JSON_LD })
            assertTrue(v1Result.value.all { it.format == CredentialFormat.JWT_VC_JSON })
        }

    @Test
    fun actualTypeRefsRejectsMalformedNoneAlgorithmAndWrongVcdmShape() =
        runTest {
            val acceptance = testAcceptance()
            val malformed = "not-a-jwt"
            val noneAlgorithm = buildTestJwtVc(
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","EmployeeCredential"],"credentialSubject":{"id":"did:example:holder"}}""",
                headerJson = """{"alg":"none","typ":"vc+jwt"}""",
            )
            val v2WithV1Wrapper = buildTestJwtVc(
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"vc":{"type":["VerifiableCredential","EmployeeCredential"],"credentialSubject":{"id":"did:example:holder"}}}""",
            )
            val v1WithoutWrapper = buildTestJwtVc(
                """{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","EmployeeCredential"],"credentialSubject":{"id":"did:example:holder"}}""",
            )

            listOf(
                CredentialFormat.JWT_VC_JSON_LD to malformed,
                CredentialFormat.JWT_VC_JSON_LD to noneAlgorithm,
                CredentialFormat.JWT_VC_JSON_LD to v2WithV1Wrapper,
                CredentialFormat.JWT_VC_JSON to v1WithoutWrapper,
            ).forEach { (format, raw) ->
                val result = acceptance.actualTypeRefs("invalid", format, listOf(testInstance(raw, format)))
                assertTrue(result.isErr, "invalid VCDM shape must be rejected for $format")
            }
        }

    @Test
    fun resolveSubjectsPreservesVcdm20RootAndVcdm11WrappedSubjectIds() =
        runTest {
            val acceptance = testAcceptance()
            val v2 = buildTestJwtVc(
                """{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","EmployeeCredential"],"issuer":"did:example:issuer","credentialSubject":{"id":"did:example:v2-holder"}}""",
                headerJson = """{"alg":"ES256","typ":"vc+jwt"}""",
            )
            val v1 = buildTestJwtVc(
                """{"iss":"did:example:issuer","sub":"did:example:v1-holder","nbf":1767225600,"vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","EmployeeCredential"],"credentialSubject":{"id":"did:example:v1-holder"}}}""",
            )

            val v2Result = acceptance.resolveSubjects(CredentialFormat.JWT_VC_JSON_LD, v2)
            val v1Result = acceptance.resolveSubjects(CredentialFormat.JWT_VC_JSON, v1)

            assertTrue(v2Result.isOk)
            assertTrue(v1Result.isOk)
            assertEquals(listOf("did:example:v2-holder"), v2Result.value.map { it.value })
            assertEquals(listOf("did:example:v1-holder"), v1Result.value.map { it.value })
        }

    @Test
    fun actualTypeRefsDerivesMdocDoctypeFromIssuedPayloadAndDiagnosticsRecordsMismatch() =
        runTest {
            val expectedDoctype = "org.iso.18013.5.1.mDL"
            val actualDoctype = "eu.europa.ec.eudi.pid.1"
            val acceptance = testAcceptance()
            val raw = buildTestMdocCredential(actualDoctype)
            val instance = testInstance(raw = raw, format = CredentialFormat.MSO_MDOC)

            val actualResult = acceptance.actualTypeRefs("MdocConfig", CredentialFormat.MSO_MDOC, listOf(instance))
            assertTrue(actualResult.isOk, "actualTypeRefs should succeed: ${if (actualResult.isErr) actualResult.error else ""}")
            val actual = actualResult.value
            assertEquals(setOf(actualDoctype), actual.map { it.value }.toSet())

            val expected =
                setOf(
                    CredentialTypeRef(
                        format = CredentialFormat.MSO_MDOC,
                        kind = CredentialTypeRefKind.MDOC_DOCTYPE,
                        value = expectedDoctype,
                        source = CredentialTypeRefSource.ISSUER_METADATA,
                        primary = true,
                    ),
                )
            val diagnostics = acceptance.diagnostics(expected, actual, Clock.System.now())
            assertEquals(1, diagnostics.size)
            assertEquals(setOf(expectedDoctype), diagnostics.single().expectedCredentialTypeRefs.map { it.value }.toSet())
            assertEquals(setOf(actualDoctype), diagnostics.single().actualCredentialTypeRefs.map { it.value }.toSet())
        }

    @Test
    fun actualTypeRefsRejectsUnparseableMdocPayload() =
        runTest {
            // Defensive coverage: a payload that is not valid base64url CBOR must be REJECTED
            // (Err), not silently accepted with zero type refs.
            val acceptance = testAcceptance()
            val instance = testInstance(raw = "not-a-valid-mdoc-cbor-payload", format = CredentialFormat.MSO_MDOC)

            val result = acceptance.actualTypeRefs("MdocConfig", CredentialFormat.MSO_MDOC, listOf(instance))

            assertTrue(result.isErr, "an unparseable mdoc payload must not silently produce zero type refs")
        }

    @Test
    fun resolveSubjectsReturnsEmptyForMdocFormat() =
        runTest {
            // ISO 18013-5 carries no globally scoped subject URI (CredentialSubjectExtractorImpl).
            val acceptance = testAcceptance()

            val result = acceptance.resolveSubjects(CredentialFormat.MSO_MDOC, "some-cbor-bytes")

            assertTrue(result.isOk)
            assertEquals(emptyList(), result.value)
        }
}

private fun buildTestJwtVc(payloadJson: String, headerJson: String = """{"alg":"ES256","typ":"JWT"}"""): String {
    val header = headerJson.encodeToByteArray().encodeToBase64Url()
    val payload = payloadJson.encodeToByteArray().encodeToBase64Url()
    return "$header.$payload.fakesig"
}

private fun testInstance(
    raw: String,
    format: CredentialFormat,
    instanceId: String = "instance-1",
): CredentialInstance {
    val now = Clock.System.now()
    return CredentialInstance(
        id = instanceId,
        walletUnitId = "wallet-unit-acceptance-test",
        credentialRecordId = "record-1",
        format = format,
        raw = raw,
        bodyStorageRef =
            BodyStorageRef(
                kind = BodyStorageKind.WALLET_STORE,
                path = "wallet-units/wallet-unit-acceptance-test/credentials/record-1/instances/$instanceId/body",
            ),
        lifecycleState = CredentialLifecycleState.ACTIVE,
        storedAt = now,
        updatedAt = now,
    )
}

private fun testAcceptance(
    verifyJws: VerifyJwsCommand = RecordingVerifyJwsCommand(),
    vcdmDataIntegrityVerifier: VcdmDataIntegrityVerifier? = null,
): Oid4vciIssuedCredentialAcceptance =
    Oid4vciIssuedCredentialAcceptance(
        verifySdJwtVcCommand = FakeVerifySdJwtVcCommand(accept = true),
        verifyJwsCommand = verifyJws,
        subjectExtractor = CredentialSubjectExtractorImpl(),
        identityResolver = TestPassThroughWalletIdentityResolver,
        vcdmDataIntegrityVerifier = vcdmDataIntegrityVerifier,
    )

private class RecordingVerifyJwsCommand : VerifyJwsCommand {
    override val commandId: String = VerifyJwsCommand.COMMAND_ID
    override val inputTypeToken: TypeToken<VerifyJwsArgs> = typeToken<VerifyJwsArgs>()
    override val outputTypeToken: TypeToken<JwsValidationResult> = typeToken<JwsValidationResult>()
    override val isEnabled: Boolean = true
    var lastArgs: VerifyJwsArgs? = null

    override suspend fun supports(args: Any): Boolean = args is VerifyJwsArgs

    override suspend fun execute(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> {
        lastArgs = args
        val jws = args.jws ?: return IdkResult.err(IdkError.fromString("missing jws"))
        return Ok(
            JwsValidationResult(
                jws = JwsJsonGeneralWithIdentifiers(payload = "", signatures = emptyList()),
                isValid = true,
                parsedPayload = Json.parseToJsonElement((jws as JwsCompact).value.split(".")[1].decodeFromBase64Url().decodeToString()).jsonObject,
            ),
        )
    }
}

private class RecordingVcdmDataIntegrityVerifier(
    private val accept: Boolean,
) : VcdmDataIntegrityVerifier {
    var lastArgs: VcdmDataIntegrityVerificationArgs? = null

    override suspend fun verify(
        args: VcdmDataIntegrityVerificationArgs,
    ): IdkResult<VcdmDataIntegrityVerificationResult, IdkError> {
        lastArgs = args
        return if (accept) {
            Ok(VcdmDataIntegrityVerificationResult(verifiedDocument = JsonObject(args.document - "proof"), proofCount = 1))
        } else {
            Err(IdkError.fromString(code = "DATA_INTEGRITY_VERIFICATION_FAILED", message = "fake: signature mismatch"))
        }
    }
}
