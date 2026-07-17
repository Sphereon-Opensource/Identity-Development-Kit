/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.encodeToBase64Url
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
import com.sphereon.wallet.impl.NoOpWalletIdentityResolver
import kotlinx.coroutines.test.runTest
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
    fun verifySkipsNonSdJwtFormatsWithoutCallingTheVerificationCommand() =
        runTest {
            val verify = FakeVerifySdJwtVcCommand(accept = false)
            val acceptance =
                Oid4vciIssuedCredentialAcceptance(
                    verifySdJwtVcCommand = verify,
                    subjectExtractor = CredentialSubjectExtractorImpl(),
                    identityResolver = NoOpWalletIdentityResolver(),
                )
            val instance = testInstance(raw = "mdoc-raw-does-not-matter", format = CredentialFormat.MSO_MDOC)

            val result = acceptance.verify("SomeConfig", CredentialFormat.MSO_MDOC, listOf(instance))

            assertTrue(result.isOk, "non-SD-JWT formats must not be gated by issuer-signature verification")
            assertEquals(null, verify.lastVerified, "the verification command must never be invoked for a non-SD-JWT format")
        }

    @Test
    fun actualTypeRefsDerivesW3cTypesFromJwtVcPayloadAndDiagnosticsRecordsMismatch() =
        runTest {
            val expectedType = "EmployeeCredential"
            val actualType = "EmployeeBadgeCredential"
            val acceptance = testAcceptance()
            val raw =
                buildTestJwtVc(
                    """{"iss":"https://issuer.example","sub":"did:example:holder-jwt-vc","vc":{"type":["VerifiableCredential","$actualType"]}}""",
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

private fun buildTestJwtVc(payloadJson: String): String {
    val header = """{"alg":"ES256","typ":"JWT"}""".encodeToByteArray().encodeToBase64Url()
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

private fun testAcceptance(): Oid4vciIssuedCredentialAcceptance =
    Oid4vciIssuedCredentialAcceptance(
        verifySdJwtVcCommand = FakeVerifySdJwtVcCommand(accept = true),
        subjectExtractor = CredentialSubjectExtractorImpl(),
        identityResolver = NoOpWalletIdentityResolver(),
    )
