/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WalletMetadataSerializationTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun ldpVcUsesFinalProofTypeAndCryptosuiteWireNames() {
        val metadata =
            WalletMetadata(
                vpFormatsSupported =
                    mapOf(
                        "ldp_vc" to
                            VpFormatSupport(
                                proofTypeValues = listOf("DataIntegrityProof"),
                                cryptosuiteValues = listOf("eddsa-rdfc-2022"),
                            ),
                    ),
            )

        val ldpVc =
            json.encodeToJsonElement(WalletMetadata.serializer(), metadata)
                .jsonObject["vp_formats_supported"]!!.jsonObject["ldp_vc"]!!.jsonObject

        assertEquals("[\"DataIntegrityProof\"]", ldpVc["proof_type_values"].toString())
        assertEquals("[\"eddsa-rdfc-2022\"]", ldpVc["cryptosuite_values"].toString())
        assertFalse(ldpVc.containsKey("proof_types_supported"))
    }

    @Test
    fun ldpVcBuilderPopulatesFinalDataIntegrityFields() {
        val metadata =
            buildWalletMetadata {
                supportLdpVc(
                    proofTypeValues = listOf("DataIntegrityProof"),
                    cryptosuiteValues = listOf("eddsa-rdfc-2022"),
                )
            }

        val ldpVc = metadata.vpFormatsSupported.getValue("ldp_vc")
        assertEquals(listOf("DataIntegrityProof"), ldpVc.proofTypeValues)
        assertEquals(listOf("eddsa-rdfc-2022"), ldpVc.cryptosuiteValues)
    }
}

class WalletMetadataValidationTest {
    @Test
    fun `ldp_vc final proof type and cryptosuite lists must not be empty`() {
        val emptyProofTypes =
            validateWalletMetadata(
                WalletMetadata(
                    vpFormatsSupported =
                        mapOf("ldp_vc" to VpFormatSupport(proofTypeValues = emptyList())),
                ),
            )
        val emptyCryptosuites =
            validateWalletMetadata(
                WalletMetadata(
                    vpFormatsSupported =
                        mapOf("ldp_vc" to VpFormatSupport(cryptosuiteValues = emptyList())),
                ),
            )

        assertFalse(emptyProofTypes.isValid)
        assertFalse(emptyCryptosuites.isValid)
    }
}
