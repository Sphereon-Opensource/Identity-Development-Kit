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

package com.sphereon.wallet.impl

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.wallet.credential.CredentialFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Builds a minimal compact JWT (header.payload.signature) whose payload is the
 * given JSON object encoded as base64url. The signature part is a placeholder —
 * no cryptographic verification is performed by [CredentialSubjectExtractorImpl].
 */
private fun buildJwt(payloadJson: String): String {
    val header = """{"alg":"ES256","typ":"JWT"}""".encodeToByteArray().encodeToBase64Url()
    val payload = payloadJson.encodeToByteArray().encodeToBase64Url()
    return "$header.$payload.fakesignature"
}

/**
 * Builds a minimal SD-JWT compact serialization:
 * <issuer-jwt>~<disclosure>~
 * The issuer JWT payload contains the given claims. No real signatures or disclosures
 * are used — [SdJwtCodec.parse] only needs the structure to be valid.
 */
private fun buildSdJwt(payloadJson: String): String {
    val header = """{"alg":"ES256","typ":"dc+sd-jwt"}""".encodeToByteArray().encodeToBase64Url()
    val payload = payloadJson.encodeToByteArray().encodeToBase64Url()
    // No disclosures, trailing ~ marks it as SD-JWT presentation format
    return "$header.$payload.fakesignature~"
}

class CredentialSubjectExtractorTest {
    private val extractor = CredentialSubjectExtractorImpl()

    // -----------------------------------------------------------------------
    // SD-JWT (dc+sd-jwt)
    // -----------------------------------------------------------------------

    @Test
    fun sdJwtWithSubClaimReturnsDIDRef() {
        val raw = buildSdJwt("""{"iss":"https://issuer.example","sub":"did:example:subject123","vct":"EmployeeCredential"}""")
        val subjects = extractor.extractSubjects(CredentialFormat.SD_JWT_DC, raw)
        assertEquals(1, subjects.size)
        assertEquals(IdentifierType.DID, subjects[0].type)
        assertEquals("did:example:subject123", subjects[0].value)
    }

    @Test
    fun sdJwtWithUriSubClaimReturnsUriRef() {
        val raw = buildSdJwt("""{"iss":"https://issuer.example","sub":"https://subject.example/users/42","vct":"SomeCredential"}""")
        val subjects = extractor.extractSubjects(CredentialFormat.SD_JWT_DC, raw)
        assertEquals(1, subjects.size)
        assertEquals(IdentifierType("uri"), subjects[0].type)
        assertEquals("https://subject.example/users/42", subjects[0].value)
    }

    @Test
    fun sdJwtWithoutSubClaimReturnsEmpty() {
        val raw = buildSdJwt("""{"iss":"https://issuer.example","vct":"AnonCredential"}""")
        val subjects = extractor.extractSubjects(CredentialFormat.SD_JWT_DC, raw)
        assertTrue(subjects.isEmpty())
    }

    @Test
    fun sdJwtVcFormatAlsoExtractsSub() {
        val raw = buildSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder","vct":"PID"}""")
        val subjects = extractor.extractSubjects(CredentialFormat.SD_JWT_VC, raw)
        assertEquals(1, subjects.size)
        assertEquals("did:example:holder", subjects[0].value)
    }

    // -----------------------------------------------------------------------
    // JWT (jwt_vc_json) — single credentialSubject object
    // -----------------------------------------------------------------------

    @Test
    fun jwtVcJsonWithSingleCredentialSubjectObjectExtractsId() {
        val payload =
            """{"iss":"https://issuer.example","sub":"did:example:s1","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],""" +
                """"type":["VerifiableCredential"],"credentialSubject":{"id":"did:example:s1","name":"Alice"}}}"""
        val raw = buildJwt(payload)
        val subjects = extractor.extractSubjects(CredentialFormat.JWT_VC_JSON, raw)
        assertEquals(1, subjects.size)
        assertEquals(IdentifierType.DID, subjects[0].type)
        assertEquals("did:example:s1", subjects[0].value)
    }

    @Test
    fun jwtVcJsonWithArrayCredentialSubjectsExtractsBoth() {
        val payload =
            """{"iss":"https://issuer.example","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],""" +
                """"type":["VerifiableCredential"],"credentialSubject":[{"id":"did:example:s1","name":"Alice"},{"id":"did:example:s2","name":"Bob"}]}}"""
        val raw = buildJwt(payload)
        val subjects = extractor.extractSubjects(CredentialFormat.JWT_VC_JSON, raw)
        assertEquals(2, subjects.size)
        assertEquals("did:example:s1", subjects[0].value)
        assertEquals("did:example:s2", subjects[1].value)
    }

    @Test
    fun jwtVcJsonCredentialSubjectWithoutIdReturnsEmpty() {
        val payload = """{"iss":"https://issuer.example","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential"],"credentialSubject":{"name":"Anonymous"}}}"""
        val raw = buildJwt(payload)
        val subjects = extractor.extractSubjects(CredentialFormat.JWT_VC_JSON, raw)
        assertTrue(subjects.isEmpty())
    }

    @Test
    fun jwtVcJsonFallsBackToTopLevelSub() {
        // No vc.credentialSubject.id, but top-level sub is present
        val payload = """{"iss":"https://issuer.example","sub":"did:example:fallback","vc":{"@context":[],"type":["VerifiableCredential"]}}"""
        val raw = buildJwt(payload)
        val subjects = extractor.extractSubjects(CredentialFormat.JWT_VC_JSON, raw)
        assertEquals(1, subjects.size)
        assertEquals("did:example:fallback", subjects[0].value)
    }

    // -----------------------------------------------------------------------
    // mdoc — always empty
    // -----------------------------------------------------------------------

    @Test
    fun mdocAlwaysReturnsEmpty() {
        val subjects = extractor.extractSubjects(CredentialFormat.MSO_MDOC, "some-cbor-bytes")
        assertTrue(subjects.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Robustness — malformed input must not crash
    // -----------------------------------------------------------------------

    @Test
    fun malformedSdJwtReturnsEmpty() {
        val subjects = extractor.extractSubjects(CredentialFormat.SD_JWT_DC, "not-a-valid-sdjwt")
        assertTrue(subjects.isEmpty())
    }

    @Test
    fun malformedJwtReturnsEmpty() {
        val subjects = extractor.extractSubjects(CredentialFormat.JWT_VC_JSON, "only-one-part")
        assertTrue(subjects.isEmpty())
    }
}
