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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BagItPackageTest {
    private val builder = BagItPackageBuilder()

    @Test
    fun `logical BagIt package is deterministic and canonically ordered`() =
        runTest {
            val first =
                builder.build(
                    payloads =
                        listOf(
                            TestPayload("z-last.txt", "last".encodeToByteArray()),
                            TestPayload("a-first.txt", "hello".encodeToByteArray()),
                        ),
                    bagInfo = mapOf("Source-Organization" to "Sphereon", "External-Identifier" to "vault-1"),
                )
            val second =
                builder.build(
                    payloads =
                        listOf(
                            TestPayload("a-first.txt", "hello".encodeToByteArray()),
                            TestPayload("z-last.txt", "last".encodeToByteArray()),
                        ),
                    bagInfo = mapOf("External-Identifier" to "vault-1", "Source-Organization" to "Sphereon"),
                )

            assertEquals(first.entries.map { it.path }, second.entries.map { it.path })
            assertEquals(first.entries.map { it.path }.sortedWith(VaultPortablePath.canonicalUtf8Comparator()), first.entries.map { it.path })
            first.entries.zip(second.entries).forEach { (left, right) ->
                assertContentEquals(left.content.open().readAll(), right.content.open().readAll())
            }
            assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                first.payloadManifest.first { it.path == "data/a-first.txt" }.sha256,
            )
        }

    @Test
    fun `streaming verifier accepts generated package and detects payload and tag tampering`() =
        runTest {
            val packageSource = builder.build(listOf(TestPayload("docs/file.txt", "authentic".encodeToByteArray())))
            val valid = BagItImportValidator().verify(packageSource.asArchive())
            assertTrue(valid.valid)
            assertEquals(1, valid.verifiedPayloadCount)

            val payloadTamper =
                BagItImportValidator().verify(
                    packageSource.asArchive(mapOf("data/docs/file.txt" to "tampered".encodeToByteArray())),
                )
            assertFalse(payloadTamper.valid)
            assertTrue(payloadTamper.issues.any { it.code == VaultVerificationIssueCode.CHECKSUM_MISMATCH })

            val tagTamper =
                BagItImportValidator().verify(
                    packageSource.asArchive(mapOf("bag-info.txt" to "Payload-Oxum: 999.1\n".encodeToByteArray())),
                )
            assertTrue(tagTamper.issues.any { it.code == VaultVerificationIssueCode.TAG_CHECKSUM_MISMATCH })
        }

    @Test
    fun `normal export rejects secret classes and excluded paths`() =
        runTest {
            assertFailsWith<VaultPortabilityError.ExcludedContent> {
                builder.build(listOf(TestPayload("credentials/key", byteArrayOf(1), VaultExportContentClass.PRIVATE_KEY)))
            }
            assertFailsWith<VaultPortabilityError.ExcludedContent> {
                builder.build(listOf(TestPayload(".secrets/refresh-token", byteArrayOf(1))))
            }
        }
}
