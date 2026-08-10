/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.Ok
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.holder.CreatedProof
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Oid4vciIssuanceLaunchOptionsTest {
    @Test
    fun appliesPerInteractionProtocolOptionsWithoutReplacingKeyOwnership() {
        val baseline =
            Oid4vciHolderIssuanceOptions(
                signingKeyId = "wallet-unit-owned-key",
                operationBinding = "attended-operation",
                clientId = "default-client",
            )

        val applied =
            baseline.withLaunchAttributes(
                mapOf(
                    Oid4vciInteractionLaunchAttributes.CLIENT_ID to "module-client",
                    Oid4vciInteractionLaunchAttributes.REDIRECT_URI to "https://suite.example/callback",
                    Oid4vciInteractionLaunchAttributes.CREDENTIAL_CONFIGURATION_ID to "pid",
                    Oid4vciInteractionLaunchAttributes.CREDENTIAL_BATCH_SIZE to "2",
                    Oid4vciInteractionLaunchAttributes.USE_PAR to "true",
                    Oid4vciInteractionLaunchAttributes.ENCRYPT_CREDENTIAL_REQUEST to "true",
                ),
            )

        assertEquals("wallet-unit-owned-key", applied.signingKeyId)
        assertEquals("attended-operation", applied.operationBinding)
        assertEquals("module-client", applied.clientId)
        assertEquals("https://suite.example/callback", applied.redirectUri)
        assertEquals("pid", applied.credentialConfigurationId)
        assertEquals(2, applied.batchSize)
        assertEquals(true, applied.usePar)
        assertEquals(true, applied.encryptCredentialRequest)
    }

    @Test
    fun rejectsAmbiguousParAttribute() {
        assertFailsWith<IllegalArgumentException> {
            Oid4vciHolderIssuanceOptions(signingKeyId = "key").withLaunchAttributes(
                mapOf(Oid4vciInteractionLaunchAttributes.USE_PAR to "sometimes"),
            )
        }
    }

    @Test
    fun rejectsNonPositiveOrNonNumericBatchSize() {
        listOf("0", "-1", "two").forEach { value ->
            assertFailsWith<IllegalArgumentException> {
                Oid4vciHolderIssuanceOptions(signingKeyId = "key").withLaunchAttributes(
                    mapOf(Oid4vciInteractionLaunchAttributes.CREDENTIAL_BATCH_SIZE to value),
                )
            }
        }
    }

    @Test
    fun composesBatchProofsFromDistinctProofRequests() =
        runTest {
            val seenAliases = mutableListOf<String>()
            val provider =
                object : Oid4vciCredentialRequestProofProvider {
                    override suspend fun createProof(request: Oid4vciCredentialRequestProofRequest) =
                        Ok(
                            CreatedProof(
                                CredentialRequestProofs.jwt("proof-${request.signingKeyId}"),
                            ),
                        ).also { seenAliases += request.signingKeyId }
                }

            val result =
                provider.createProofs(
                    listOf("holder-key", "holder-key-batch-2").map { alias ->
                        Oid4vciCredentialRequestProofRequest(
                            walletUnitId = "wallet-unit",
                            issuerUrl = "https://issuer.example",
                            signingKeyId = alias,
                        )
                    },
                )

            assertTrue(result.isOk)
            assertEquals(listOf("holder-key", "holder-key-batch-2"), seenAliases)
            assertEquals(
                listOf(JsonPrimitive("proof-holder-key"), JsonPrimitive("proof-holder-key-batch-2")),
                result.value.proofs.proofValues,
            )
        }
}
