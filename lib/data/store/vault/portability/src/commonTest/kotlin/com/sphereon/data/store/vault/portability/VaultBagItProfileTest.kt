/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.vault.portability

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultBagItProfileTest {
    private val assembler = VaultBagItProfileAssembler()

    @Test
    fun `assembler fixes required tag and payload paths and covers them with checksums`() =
        runTest {
            val packageSource = assembler.assemble(profileSource(dpv = "{\"@context\":\"https://w3id.org/dpv\"}"))
            val paths = packageSource.entries.mapTo(mutableSetOf()) { it.path }

            assertTrue(paths.containsAll(VaultBagItProfile.REQUIRED_ARCHIVE_ENTRIES))
            assertTrue("data/${VaultBagItProfile.DPV_JSON_LD_PAYLOAD}" in paths)
            assertTrue(packageSource.payloadManifest.any { it.path == "data/${VaultBagItProfile.POLICY_SNAPSHOT_PAYLOAD}" })
            assertTrue(packageSource.payloadManifest.any { it.path == "data/${VaultBagItProfile.PROVENANCE_PAYLOAD}" })

            val tagManifest =
                requireNotNull(packageSource.entry(BagItImportValidator.TAG_MANIFEST))
                    .content
                    .open()
                    .readAll()
                    .decodeToString()
            assertTrue(tagManifest.lines().any { it.endsWith("  ${VaultBagItProfile.SIGNED_VAULT_MANIFEST_TAG}") })

            val verified = BagItImportValidator().verify(packageSource.asArchive())
            assertTrue(verified.valid, verified.issues.joinToString())
            assertEquals(4, verified.verifiedPayloadCount)
        }

    @Test
    fun `required profile documents cannot be empty or replaced by caller payloads`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                assembler.assemble(profileSource().copy(signedVaultManifest = TestReplayableContent(byteArrayOf())))
            }
            assertFailsWith<IllegalArgumentException> {
                assembler.assemble(
                    profileSource().copy(
                        payloads = listOf(TestPayload(VaultBagItProfile.POLICY_SNAPSHOT_PAYLOAD, "override".encodeToByteArray())),
                    ),
                )
            }
            assertFailsWith<IllegalArgumentException> {
                assembler.assemble(
                    profileSource().copy(bagInfo = mapOf("vault-profile" to "caller-defined")),
                )
            }
        }

    @Test
    fun `profile verifier rejects wrong marker and incomplete reserved entry coverage`() =
        runTest {
            val packageSource = assembler.assemble(profileSource())
            val verifier = VaultBagItStructuralValidator()

            val valid = verifier.verify(packageSource.asArchive())
            assertTrue(valid.valid, valid.issues.joinToString())

            val wrongMarker =
                verifier.verify(
                    packageSource.asArchive(
                        mapOf(
                            "bag-info.txt" to
                                "External-Identifier: vault-1\nVault-Profile: urn:example:wrong\nPayload-Oxum: 1.1\n".encodeToByteArray(),
                        ),
                    ),
                )
            assertFalse(wrongMarker.valid)
            assertTrue(wrongMarker.issues.any { it.code == VaultVerificationIssueCode.INVALID_PROFILE })

            val withoutManifestTag =
                TestArchiveSource(
                    packageSource.asArchiveEntries().filterNot { it.path == VaultBagItProfile.SIGNED_VAULT_MANIFEST_TAG },
                )
            val missingManifest = verifier.verify(withoutManifestTag)
            assertFalse(missingManifest.valid)
            assertTrue(missingManifest.issues.any { it.code == VaultVerificationIssueCode.MISSING_PROFILE_ENTRY })

            val withoutPolicy =
                TestArchiveSource(
                    packageSource.asArchiveEntries().filterNot { it.path == "data/${VaultBagItProfile.POLICY_SNAPSHOT_PAYLOAD}" },
                )
            val missingPolicy = verifier.verify(withoutPolicy)
            assertFalse(missingPolicy.valid)
            assertTrue(missingPolicy.issues.any { it.code == VaultVerificationIssueCode.MISSING_PROFILE_ENTRY })
        }

    @Test
    fun `profile verifier invokes optional signed-manifest verification port`() =
        runTest {
            val packageSource = assembler.assemble(profileSource())
            val missingProvider = VaultBagItProfileVerifier().verify(packageSource.asArchive())
            assertFalse(missingProvider.valid)
            assertTrue(missingProvider.issues.any { it.code == VaultVerificationIssueCode.SIGNATURE_VERIFIER_REQUIRED })

            val accepting = VaultBagItProfileVerifier(signedManifestVerifier = SignedVaultManifestVerifier { true })
            val accepted = accepting.verify(packageSource.asArchive())
            assertTrue(accepted.valid, accepted.issues.joinToString())

            val rejecting = VaultBagItProfileVerifier(signedManifestVerifier = SignedVaultManifestVerifier { false })
            val result = rejecting.verify(packageSource.asArchive())
            assertFalse(result.valid)
            assertTrue(result.issues.any { it.code == VaultVerificationIssueCode.INVALID_SIGNED_MANIFEST })
        }

    private fun profileSource(dpv: String? = null) =
        VaultBagItProfileSource(
            payloads = listOf(TestPayload("documents/credential.bin", byteArrayOf(1, 2, 3))),
            signedVaultManifest = TestReplayableContent("{\"signature\":\"test\"}".encodeToByteArray()),
            policySnapshot = TestReplayableContent("{\"policy\":\"snapshot\"}".encodeToByteArray()),
            provenance = TestReplayableContent("{\"source\":\"vault\"}".encodeToByteArray()),
            dpvJsonLd = dpv?.encodeToByteArray()?.let(::TestReplayableContent),
            bagInfo = mapOf("External-Identifier" to "vault-1"),
        )
}
