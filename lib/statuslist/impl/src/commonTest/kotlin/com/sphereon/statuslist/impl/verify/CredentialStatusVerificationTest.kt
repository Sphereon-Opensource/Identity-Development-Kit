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

package com.sphereon.statuslist.impl.verify

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.x509.X509VerificationRequestType
import com.sphereon.crypto.core.x509.X509VerificationResult
import com.sphereon.crypto.core.x509.X509VerificationResultType
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.core.x509.certificateFromPem
import com.sphereon.statuslist.CredentialStatusReference
import com.sphereon.statuslist.CredentialStatusInput
import com.sphereon.statuslist.CredentialStatusMetadata
import com.sphereon.statuslist.MdocCredentialStatusMetadata
import com.sphereon.statuslist.CredentialStatusDecision
import com.sphereon.statuslist.CredentialStatusPolicy
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.ResolvedStatus
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.evaluateCredentialStatus
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import com.sphereon.statuslist.spi.StatusListResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Resolver stub returning a fixed status value (or a failure) so the evaluation matrix is deterministic. */
private class FakeResolver(
    private val value: Int,
    private val fail: Boolean = false,
) : StatusListResolver {
    var lastArgs: ResolveStatusArgs? = null

    override suspend fun resolveStatus(args: ResolveStatusArgs): IdkResult<ResolvedStatus, IdkError> =
        if (fail) {
            Err(IdkError.UNKNOWN_ERROR(message = "boom"))
        } else {
            lastArgs = args
            Ok(ResolvedStatus(value = value, valid = value == StatusValues.VALID, statusListUri = args.uri))
        }
}

private object FakeMdocTrustService : X509VerifyService {
    override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType =
        X509VerificationResult(
            certificateChain = emptyArray(),
            critical = true,
            message = "test stub",
            error = true,
        )

    override fun setTrustedCerts(trustedCerts: Array<String>?): X509VerifyService = this

    override fun getTrustedCerts(): Array<String>? = arrayOf("configured-root")
}

class CredentialStatusVerificationTest {
    private fun claims(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun tokenVerifiers(
        value: Int,
        fail: Boolean = false,
    ): Set<CredentialStatusVerifier> = setOf(TokenStatusListCredentialStatusVerifier(FakeResolver(value, fail)))

    @Test
    fun tokenReferenceExtraction() {
        val verifier = TokenStatusListCredentialStatusVerifier(FakeResolver(0))
        val refs = verifier.references(claims("""{"status":{"status_list":{"uri":"https://x/sl","idx":5}}}"""))
        assertEquals(1, refs.size)
        assertEquals("https://x/sl", refs[0].uri)
        assertEquals(5, refs[0].index)
        assertTrue(verifier.references(claims("""{"foo":1}""")).isEmpty())
    }

    @Test
    fun mdocReferenceExtractionKeepsStatusMechanismsDistinct() {
        val verifier = MdocCredentialStatusVerifier(FakeResolver(0), FakeMdocTrustService)
        val statusList = verifier.references(claims("""{"status":{"mdoc_status_list":{"uri":"https://x/mdoc","idx":5}}}"""))
        assertEquals(1, statusList.size)
        assertEquals("mdoc_status", statusList.single().mechanism)
        assertEquals(5, statusList.single().index)
        assertEquals("https://x/mdoc", statusList.single().uri)

        val identifierList = verifier.references(claims("""{"status":{"mdoc_identifier_list":{"uri":"https://x/ids","id":"AQID"}}}"""))
        assertEquals(1, identifierList.size)
        assertEquals(0, identifierList.single().index)
        assertEquals("https://x/ids", identifierList.single().uri)
        assertTrue(identifierList.single().identifier!!.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun mdocVerifierConsumesAuthenticatedMetadataWithoutReadingOrdinaryClaims() {
        val verifier = MdocCredentialStatusVerifier(FakeResolver(0), FakeMdocTrustService)
        val authenticatedReference =
            CredentialStatusReference(
                mechanism = MdocCredentialStatusVerifier.MECHANISM,
                uri = "https://x/authenticated",
                index = 7,
            )
        val input =
            CredentialStatusInput(
                // A conflicting legacy-shaped claim must not override typed MSO metadata.
                claims = claims("""{"status":{"mdoc_status_list":{"uri":"https://x/legacy","idx":1}}}"""),
                metadata = CredentialStatusMetadata(MdocCredentialStatusMetadata(listOf(authenticatedReference))),
            )

        val references = verifier.references(input)

        assertEquals(listOf(authenticatedReference), references)
    }

    @Test
    fun mdocReferenceExtractionHandlesEveryDocumentInAResponse() {
        val verifier = MdocCredentialStatusVerifier(FakeResolver(0), FakeMdocTrustService)
        val references = verifier.references(
            claims(
                """{"status":{"mdoc_status_list":[{"uri":"https://x/one","idx":1},{"uri":"https://x/two","idx":2}]}}""",
            ),
        )

        assertEquals(2, references.size)
        assertEquals(listOf("https://x/one", "https://x/two"), references.map { it.uri })
        assertEquals(listOf(1, 2), references.map { it.index })
    }

    @Test
    fun malformedMdocCertificateDoesNotFallBackToConfiguredTrustRoots() =
        runTest {
            val resolver = FakeResolver(StatusValues.VALID)
            val verifier = MdocCredentialStatusVerifier(resolver, FakeMdocTrustService)
            val reference =
                verifier.references(
                    claims("""{"status":{"mdoc_status_list":{"uri":"https://x/mdoc","idx":1,"certificate":"%%%"}}}"""),
                ).single()

            val result = verifier.resolve(reference)

            assertTrue(result.isErr)
            assertNull(resolver.lastArgs, "an invalid explicit certificate must stop before resolver fallback")
        }

    @Test
    fun malformedMdocIdentifierCannotResolveAsAnEmptyIdentifier() {
        val verifier = MdocCredentialStatusVerifier(FakeResolver(StatusValues.VALID), FakeMdocTrustService)
        val reference =
            verifier.references(
                claims("""{"status":{"mdoc_identifier_list":{"uri":"https://x/ids","id":"%%%"}}}"""),
            ).single()

        assertEquals("", reference.uri)
        assertTrue(reference.identifier!!.isEmpty())
    }

    @Test
    fun mdocIdentifierListDoesNotDeclareGenericTokenStatusList() =
        runTest {
            val resolver = FakeResolver(StatusValues.VALID)
            val verifier = MdocCredentialStatusVerifier(resolver, FakeMdocTrustService)
            val reference = verifier.references(claims("""{"status":{"mdoc_identifier_list":{"uri":"https://x/ids","id":"AQID"}}}""")).single()

            verifier.resolve(reference)

            assertEquals(null, resolver.lastArgs?.expectedSpec)
            assertEquals(StatusProofFormat.CWT, resolver.lastArgs?.expectedFormat)
            assertTrue(resolver.lastArgs?.identifier!!.contentEquals(byteArrayOf(1, 2, 3)))
        }

    @Test
    fun mdocCertificatePinsTheCwtChainWithoutReplacingConfiguredTrustRoots() =
        runTest {
            val resolver = FakeResolver(StatusValues.VALID)
            val verifier = MdocCredentialStatusVerifier(resolver, FakeMdocTrustService)
            val certificate = certificateFromPem(TEST_CERTIFICATE_PEM).der
            val reference =
                CredentialStatusReference(
                    mechanism = MdocCredentialStatusVerifier.MECHANISM,
                    uri = "https://x/mdoc",
                    index = 1,
                    certificate = certificate,
                )

            val result = verifier.resolve(reference)

            assertTrue(result.isOk)
            assertTrue(resolver.lastArgs?.trustedCerts!!.contentEquals(arrayOf("configured-root")))
            assertTrue(resolver.lastArgs?.expectedCertificate!!.contentEquals(certificate))
        }

    @Test
    fun bitstringReferenceExtraction() {
        val verifier = BitstringStatusListCredentialStatusVerifier(FakeResolver(0))
        val refs =
            verifier.references(
                claims(
                    """{"credentialStatus":{"type":"BitstringStatusListEntry","statusListCredential":"https://x/bs","statusListIndex":"7","statusPurpose":"revocation"}}""",
                ),
            )
        assertEquals(1, refs.size)
        assertEquals(7, refs[0].index)
        assertEquals("https://x/bs", refs[0].uri)

        // Also recognized under a `vc` envelope.
        val nested =
            verifier.references(
                claims(
                    """{"vc":{"credentialStatus":{"type":"BitstringStatusListEntry","statusListCredential":"https://x/bs","statusListIndex":"3"}}}""",
                ),
            )
        assertEquals(3, nested.single().index)

        // A non-Bitstring credentialStatus is ignored.
        assertTrue(
            verifier.references(claims("""{"credentialStatus":{"type":"OtherEntry","statusListCredential":"https://x/bs","statusListIndex":"1"}}""")).isEmpty(),
        )
    }

    @Test
    fun acceptanceMatrix() =
        runTest {
            val claims = claims("""{"status":{"status_list":{"uri":"https://x/sl","idx":1}}}""")

            // Active is accepted by default.
            assertEquals(CredentialStatusDecision.ACCEPT, evaluateCredentialStatus(tokenVerifiers(StatusValues.VALID), claims, CredentialStatusPolicy()).decision)
            // Revoked / suspended are rejected by default, accepted only when explicitly allowed.
            assertEquals(CredentialStatusDecision.REJECT, evaluateCredentialStatus(tokenVerifiers(StatusValues.INVALID), claims, CredentialStatusPolicy()).decision)
            assertEquals(
                CredentialStatusDecision.ACCEPT,
                evaluateCredentialStatus(tokenVerifiers(StatusValues.INVALID), claims, CredentialStatusPolicy(acceptRevoked = true)).decision,
            )
            assertEquals(CredentialStatusDecision.REJECT, evaluateCredentialStatus(tokenVerifiers(StatusValues.SUSPENDED), claims, CredentialStatusPolicy()).decision)
            assertEquals(
                CredentialStatusDecision.ACCEPT,
                evaluateCredentialStatus(tokenVerifiers(StatusValues.SUSPENDED), claims, CredentialStatusPolicy(acceptSuspended = true)).decision,
            )
            // Unresolvable fails closed by default; accepted only when rejectOnUnresolvable is off.
            assertEquals(CredentialStatusDecision.REJECT, evaluateCredentialStatus(tokenVerifiers(0, fail = true), claims, CredentialStatusPolicy()).decision)
            assertEquals(
                CredentialStatusDecision.ACCEPT,
                evaluateCredentialStatus(tokenVerifiers(0, fail = true), claims, CredentialStatusPolicy(rejectOnUnresolvable = false)).decision,
            )
        }

    @Test
    fun requireStatusAndEmptySet() =
        runTest {
            val noRef = claims("""{"foo":1}""")
            val verifiers = tokenVerifiers(StatusValues.VALID)

            // No reference + not required → accept (nothing to check).
            assertEquals(CredentialStatusDecision.ACCEPT, evaluateCredentialStatus(verifiers, noRef, CredentialStatusPolicy()).decision)
            // No reference + requireStatus → reject.
            assertEquals(CredentialStatusDecision.REJECT, evaluateCredentialStatus(verifiers, noRef, CredentialStatusPolicy(requireStatus = true)).decision)
            // Empty verifier set → skipped, even with requireStatus (nothing wired to enforce it).
            assertEquals(CredentialStatusDecision.SKIPPED, evaluateCredentialStatus(emptySet(), noRef, CredentialStatusPolicy(requireStatus = true)).decision)
        }

    private companion object {
        private val TEST_CERTIFICATE_PEM = """
-----BEGIN CERTIFICATE-----
MIID6jCCAtKgAwIBAgIUZDoXRc6UwR/4DSF119w7ZYM2UrQwDQYJKoZIhvcNAQEL
BQAwfjELMAkGA1UEBhMCTkwxFjAUBgNVBAgMDU5vcnRoIEhvbGxhbmQxEjAQBgNV
BAcMCUFtc3RlcmRhbTEZMBcGA1UECgwQU3BoZXJlb24gSUQgVGVjaDERMA8GA1UE
CwwISWRlbnRpdHkxFTATBgNVBAMMDHNwaGVyZW9uLmNvbTAeFw0yNTA0MjIxMTUw
NDhaFw0zNTA0MjAxMTUwNDhaMH4xCzAJBgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0
aCBIb2xsYW5kMRIwEAYDVQQHDAlBbXN0ZXJkYW0xGTAXBgNVBAoMEFNwaGVyZW9u
IElEIFRlY2gxETAPBgNVBAsMCElkZW50aXR5MRUwEwYDVQQDDAxzcGhlcmVvbi5j
b20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDTPZOWQJQCJRMh1eSB
HS9LurbxQMgwYHgiip0ioMz3vxNEB/fJ7yt03StY/h3IHNfxfxQ3VSzzqzWlaAmF
LrOHMOIlwXS8WikUViB1i/IaLd5R5g+eYTA9ReclNnT3+0+1oZIyZ5dHKtWZUIgR
37ieH0ZvLWydWr7MpqUkSk5yCsl0CMSvbGdjf+eDTgmiYiNVH4HkB3TYahwOjptE
rib7qwNWP7doXx22WjMxbsnsPdx8I5VtbMlZtrzMbLxKf74HptrNd6ZzQ/0OXDH1
MkYGu4E/bbVHCJERUXbVnwIDKpoArhSBoE8iroiYuW3aRqsJJuzPinsPp7Q+Uvrn
vrExAgMBAAGjYDBeMB0GA1UdDgQWBBQpQI0OZ367uIV3MtEQQCt0kvyQRTAfBgNV
HSMEGDAWgBQpQI0OZ367uIV3MtEQQCt0kvyQRTAPBgNVHRMBAf8EBTADAQH/MAsG
A1UdDwQEAwIDODANBgkqhkiG9w0BAQsFAAOCAQEAgWlUg9cgIyzM2fyu5yRcAheY
pQm7dKFDxdzuy2YzNiWXCRAhsT3YDxiTyoBBOCdrA2eYRqkLlFzA1aFVcRhCPobM
ENgyEeVN7SGlLRZ1b0XbmViVafuG+WCP4Lh9Q14accpsEcOAM98coVAoskrs6HgR
441AzVJJEaf7NqLv7SjHw/T4meS4mdB+wyLQnrZwRPfPO63kVDZiuiM04OwAhbk5
3kMxqLt0TV63iimNnjLhmqP+ktXhueWKHqPbwEO4wdkVkQx/W8Y5sk/l/1STsdYE
AoC+D6BQFQ4qD5dRu4JbNaH78Fhw7wEoDplA6k9R2t39KuqlPJHAanRDsVN1gQ==
-----END CERTIFICATE-----
""".trimIndent()
    }
}
