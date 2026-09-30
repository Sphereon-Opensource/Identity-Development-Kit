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

package com.sphereon.catalog.eu.impl

import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.impl.chain.ChainFindingCodes
import com.sphereon.catalog.eu.impl.chain.ChainValidationRequest
import com.sphereon.catalog.eu.impl.chain.DirectValidationRequest
import com.sphereon.catalog.eu.impl.chain.EuCatalogueChainValidator
import com.sphereon.catalog.eu.impl.chain.LOC_IDENTITY_PREFIX
import com.sphereon.catalog.eu.impl.chain.LastSeenCatalogue
import com.sphereon.catalog.eu.impl.fetch.CatalogueFetcher
import com.sphereon.catalog.eu.impl.testutil.CatalogueFixtures
import com.sphereon.catalog.eu.impl.testutil.CatalogueTestContext
import com.sphereon.catalog.eu.impl.testutil.MapCatalogueHttpClient
import com.sphereon.catalog.eu.impl.testutil.SyntheticCatalogueSet
import com.sphereon.catalog.eu.impl.testutil.SyntheticCatalogueSigner
import com.sphereon.catalog.eu.model.CatalogueFinding
import com.sphereon.catalog.eu.model.CatalogueKind
import com.sphereon.catalog.eu.model.FindingSeverity
import com.sphereon.catalog.eu.validation.CatalogueProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class EuCatalogueChainValidatorTest {
    private val context = CatalogueTestContext("chain-validator", this)

    private fun validator(server: MapCatalogueHttpClient) = EuCatalogueChainValidator(CatalogueFetcher(server), context.signatureVerifier, context.digestVerifier)

    private fun newSet() = SyntheticCatalogueSet(context.digestVerifier)

    private fun request(
        set: SyntheticCatalogueSet,
        lastSeen: Map<CatalogueKind, LastSeenCatalogue> = emptyMap(),
    ) = ChainValidationRequest(
        locUrl = CatalogueFixtures.LOC_URL,
        locSignerCertificates = listOf(set.locSigner.certificateDer),
        lastSeen = lastSeen,
        now = Instant.parse("2026-09-29T00:00:00Z"),
    )

    private fun errors(findings: List<CatalogueFinding>) = findings.filter { it.severity == FindingSeverity.ERROR }.map { it.code }

    @Test
    fun signedSyntheticChainIsAcceptedEndToEnd() =
        runTest {
            val synthetic = newSet()
            val result = validator(synthetic.server()).validate(request(synthetic))
            assertTrue(result.isOk, "chain failed: ${if (result.isErr) result.error.reason else ""}")
            val outcome = result.value
            assertEquals(emptyList(), errors(outcome.set.findings))
            assertNotNull(outcome.set.coa)
            assertNotNull(outcome.set.cos)
            assertEquals(setOf("family_name", "birth_date"), outcome.set.attributeEntries.map { it.attributeIdentifier }.toSet())
            assertEquals(1, outcome.set.schemeEntries.size)
            assertEquals(setOf(CatalogueKind.LOC, CatalogueKind.COA, CatalogueKind.COS), outcome.documents.keys)
            assertEquals(3, outcome.set.signerEvidence.size)
            assertEquals(3, outcome.entryFiles.size)
        }

    @Test
    fun tamperedEntryFileFailsTheWholeCatalogueWithADigestMismatch() =
        runTest {
            val synthetic = newSet()
            val server = synthetic.server()
            server.files[CatalogueFixtures.FAMILY_NAME_URL] = synthetic.familyName.decodeToString().replace("family_name", "family_nome").encodeToByteArray()
            val outcome = validator(server).validate(request(synthetic)).value
            assertNull(outcome.set.coa)
            assertTrue(ChainFindingCodes.DIGEST_MISMATCH in errors(outcome.set.findings))
            assertNotNull(outcome.set.cos, "the other catalogue is judged independently")
        }

    @Test
    fun coaSignedByACertificateNotListedInItsPointerIsRejected() =
        runTest {
            val listed = SyntheticCatalogueSigner()
            val synthetic = SyntheticCatalogueSet(context.digestVerifier, coaPointerSigner = listed)
            val outcome = validator(synthetic.server()).validate(request(synthetic)).value
            assertNull(outcome.set.coa)
            assertTrue(ChainFindingCodes.SIGNER_NOT_AUTHORISED in errors(outcome.set.findings), outcome.set.findings.toString())
            assertNotNull(outcome.set.cos)
        }

    @Test
    fun tamperedMainFileFailsTheSignature() =
        runTest {
            val synthetic = newSet()
            val server = synthetic.server()
            server.files[CatalogueFixtures.COS_URL] = synthetic.cos.decodeToString().replace("Synthetic", "Syntheticx").encodeToByteArray()
            val outcome = validator(server).validate(request(synthetic)).value
            assertNull(outcome.set.cos)
            assertTrue(ChainFindingCodes.SIGNATURE_INVALID in errors(outcome.set.findings), outcome.set.findings.toString())
        }

    @Test
    fun sequenceRegressionIsRejected() =
        runTest {
            val synthetic = newSet()
            val lastSeen = mapOf(CatalogueKind.COA to LastSeenCatalogue(null, sequenceNumber = 5))
            val outcome = validator(synthetic.server()).validate(request(synthetic, lastSeen)).value
            assertNull(outcome.set.coa)
            assertTrue(ChainFindingCodes.SEQUENCE_REGRESSION in errors(outcome.set.findings))
        }

    @Test
    fun equalSequenceWithDifferentContentIsAConflictAndWithSameContentIsAccepted() =
        runTest {
            val synthetic = newSet()
            val first = validator(synthetic.server()).validate(request(synthetic)).value
            val sha = first.documents.getValue(CatalogueKind.COA).sha256
            val seq = first.documents.getValue(CatalogueKind.COA).sequenceNumber

            val same = validator(synthetic.server()).validate(request(synthetic, mapOf(CatalogueKind.COA to LastSeenCatalogue(null, seq, sha)))).value
            assertNotNull(same.set.coa)

            val other = validator(synthetic.server()).validate(request(synthetic, mapOf(CatalogueKind.COA to LastSeenCatalogue(null, seq, ByteArray(32))))).value
            assertNull(other.set.coa)
            assertTrue(ChainFindingCodes.SEQUENCE_CONFLICT in errors(other.set.findings))
        }

    @Test
    fun higherSequenceThanLastSeenIsAccepted() =
        runTest {
            val synthetic = newSet()
            val outcome = validator(synthetic.server()).validate(request(synthetic, mapOf(CatalogueKind.COA to LastSeenCatalogue(null, 1)))).value
            assertNotNull(outcome.set.coa)
        }

    @Test
    fun changedCatalogueIdentifierIsRejected() =
        runTest {
            val synthetic = newSet()
            val outcome = validator(synthetic.server()).validate(request(synthetic, mapOf(CatalogueKind.COA to LastSeenCatalogue("urn:other", 1)))).value
            assertNull(outcome.set.coa)
            assertTrue(ChainFindingCodes.IDENTIFIER_CHANGED in errors(outcome.set.findings))
        }

    @Test
    fun unknownCriticalExtensionInAnEntryFailsTheCatalogue() =
        runTest {
            val bad = CatalogueFixtures.bytes("negative/attribute-unknown-critical-extension.xml")
            val synthetic = SyntheticCatalogueSet(context.digestVerifier, familyName = bad)
            val outcome = validator(synthetic.server()).validate(request(synthetic)).value
            assertNull(outcome.set.coa)
            assertTrue(ChainFindingCodes.UNKNOWN_CRITICAL_EXTENSION in errors(outcome.set.findings), outcome.set.findings.toString())
        }

    @Test
    fun locSignedByAnUnknownCertificateIsRejected() =
        runTest {
            val synthetic = newSet()
            val forged = ChainValidationRequest(CatalogueFixtures.LOC_URL, listOf(SyntheticCatalogueSigner().certificateDer))
            val result = validator(synthetic.server()).validate(forged)
            assertTrue(result.isErr)
            assertEquals(CatalogErrorCode.SIGNATURE_INVALID, result.error.errorCode)
        }

    @Test
    fun expiredLocIsRejectedWithItsDiagnostic() =
        runTest {
            val synthetic = newSet()
            val late = ChainValidationRequest(CatalogueFixtures.LOC_URL, listOf(synthetic.locSigner.certificateDer), now = Instant.parse("2030-01-01T00:00:00Z"))
            val result = validator(synthetic.server()).validate(late)
            assertTrue(result.isErr)
            assertEquals(CatalogErrorCode.SCHEMA_VIOLATION, result.error.errorCode)
            assertTrue(result.error.reason.startsWith(ChainFindingCodes.LOC_NEXT_UPDATE_PASSED), result.error.reason)
        }

    @Test
    fun theLiveListOfCataloguesGetsAShortStableIdentity() =
        runTest {
            val synthetic = newSet()
            val first = validator(synthetic.server()).validate(request(synthetic)).value.documents.getValue(CatalogueKind.LOC).identifier
            val second = validator(synthetic.server()).validate(request(synthetic)).value.documents.getValue(CatalogueKind.LOC).identifier
            // The live list names its operator and scheme in every official language; a plain join is several kilobytes and
            // cannot be indexed as a catalogue identifier.
            assertTrue(Regex("^" + Regex.escape(LOC_IDENTITY_PREFIX) + "[0-9a-f]{64}$").matches(first), first)
            assertEquals(first, second)
            val accepted = validator(synthetic.server()).validate(request(synthetic, mapOf(CatalogueKind.LOC to LastSeenCatalogue(first, 1))))
            assertTrue(accepted.isOk, "the stored identity is recognised on the next sync: ${if (accepted.isErr) accepted.error.reason else ""}")
        }

    @Test
    fun locIdentityChangeIsDetectedAcrossSyncs() =
        runTest {
            val synthetic = newSet()
            val last = mapOf(CatalogueKind.LOC to LastSeenCatalogue("urn:another-operator#list", 1))
            val result = validator(synthetic.server()).validate(request(synthetic, last))
            assertTrue(result.isErr)
            assertTrue(result.error.reason.startsWith(ChainFindingCodes.IDENTIFIER_CHANGED), result.error.reason)
        }

    @Test
    fun unreachableCatalogueIsReportedPerCatalogue() =
        runTest {
            val synthetic = newSet()
            val server = synthetic.server()
            server.files.remove(CatalogueFixtures.SCHEME_URL)
            val outcome = validator(server).validate(request(synthetic)).value
            assertNull(outcome.set.cos)
            assertNotNull(outcome.set.coa)
            assertTrue(ChainFindingCodes.FETCH_FAILED in errors(outcome.set.findings))
        }

    private fun directServer(synthetic: SyntheticCatalogueSet): MapCatalogueHttpClient {
        val server = MapCatalogueHttpClient()
        server.files["https://catalogue.example.org/coa.xml"] = synthetic.coa
        server.files["https://catalogue.example.org/cos.xml"] = synthetic.cos
        server.files["https://catalogue.example.org/attributes/eu.europa.ec.eudi.pid.1/family_name.xml"] = synthetic.familyName
        server.files["https://catalogue.example.org/attributes/eu.europa.ec.eudi.pid.1/birth_date.xml"] = synthetic.birthDate
        server.files["https://catalogue.example.org/schemes/eu-pid.xml"] = synthetic.scheme
        return server
    }

    @Test
    fun directCatalogueOfAttributesWithACustomIdentifierIsAcceptedFromItsSigner() =
        runTest {
            val synthetic = SyntheticCatalogueSet(context.digestVerifier, coaIdentifier = "urn:example:catalogue-of-attributes")
            val result =
                validator(directServer(synthetic)).validateDirect(
                    DirectValidationRequest(coaUrl = "https://catalogue.example.org/coa.xml", signerCertificates = listOf(synthetic.coaSigner.certificateDer)),
                )
            assertTrue(result.isOk, "direct validation failed: ${if (result.isErr) result.error.reason else ""}")
            val outcome = result.value
            assertEquals(emptyList(), errors(outcome.findings))
            assertNotNull(outcome.coa)
            assertNull(outcome.cos)
            assertEquals(setOf("family_name", "birth_date"), outcome.attributeEntries.map { it.attributeIdentifier }.toSet())
            assertEquals(setOf(CatalogueKind.COA), outcome.documents.keys)
            assertEquals(1, outcome.signerEvidence.size)
        }

    @Test
    fun directCatalogueRefusesTheReservedEuIdentifiersUnderTheCustomProfile() =
        runTest {
            val synthetic = newSet()
            val outcome =
                validator(directServer(synthetic)).validateDirect(
                    DirectValidationRequest(coaUrl = "https://catalogue.example.org/coa.xml", signerCertificates = listOf(synthetic.coaSigner.certificateDer)),
                ).value
            assertNull(outcome.coa)
            assertTrue("CATALOGUE_IDENTIFIER_RESERVED" in errors(outcome.findings), outcome.findings.toString())
        }

    @Test
    fun directCatalogueSignedByAnUnlistedCertificateIsRejected() =
        runTest {
            val synthetic = SyntheticCatalogueSet(context.digestVerifier, coaIdentifier = "urn:example:catalogue-of-attributes")
            val outcome =
                validator(directServer(synthetic)).validateDirect(
                    DirectValidationRequest(coaUrl = "https://catalogue.example.org/coa.xml", signerCertificates = listOf(SyntheticCatalogueSigner().certificateDer)),
                ).value
            assertNull(outcome.coa)
            assertTrue(ChainFindingCodes.SIGNER_NOT_AUTHORISED in errors(outcome.findings), outcome.findings.toString())
        }

    @Test
    fun directCatalogueOfSchemesIsValidatedWithItsOwnProfileAndSigner() =
        runTest {
            val synthetic = newSet()
            val outcome =
                validator(directServer(synthetic)).validateDirect(
                    DirectValidationRequest(
                        cosUrl = "https://catalogue.example.org/cos.xml",
                        signerCertificates = listOf(synthetic.cosSigner.certificateDer),
                        cosProfile = CatalogueProfile(requireSignature = true),
                    ),
                ).value
            assertEquals(emptyList(), errors(outcome.findings))
            assertNotNull(outcome.cos)
            assertEquals(1, outcome.schemeEntries.size)
            assertNull(outcome.coa)
        }

    @Test
    fun directCatalogueAppliesTheSequenceRules() =
        runTest {
            val synthetic = SyntheticCatalogueSet(context.digestVerifier, coaIdentifier = "urn:example:catalogue-of-attributes")
            val outcome =
                validator(directServer(synthetic)).validateDirect(
                    DirectValidationRequest(
                        coaUrl = "https://catalogue.example.org/coa.xml",
                        signerCertificates = listOf(synthetic.coaSigner.certificateDer),
                        lastSeen = mapOf(CatalogueKind.COA to LastSeenCatalogue("urn:example:catalogue-of-attributes", sequenceNumber = 9)),
                    ),
                ).value
            assertNull(outcome.coa)
            assertTrue(ChainFindingCodes.SEQUENCE_REGRESSION in errors(outcome.findings))
        }

    @Test
    fun directValidationNeedsAtLeastOneUrl() =
        runTest {
            val result = validator(MapCatalogueHttpClient()).validateDirect(DirectValidationRequest(signerCertificates = emptyList()))
            assertTrue(result.isErr)
        }
}
