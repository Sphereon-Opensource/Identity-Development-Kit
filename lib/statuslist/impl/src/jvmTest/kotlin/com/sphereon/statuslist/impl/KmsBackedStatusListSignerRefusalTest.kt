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

package com.sphereon.statuslist.impl

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.CoseCryptoService
import com.sphereon.crypto.core.CryptoServices
import com.sphereon.crypto.core.cose.CoseSign1CborCodecImpl
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.impl.sign.CwtStatusListSigner
import com.sphereon.statuslist.impl.sign.JwsStatusListSigner
import com.sphereon.statuslist.impl.sign.LocalStatusListJwsSigningService
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the fail-closed refusal lives: in the signers that actually consume the key name.
 *
 * The drivers hand a null key name straight through, so a signer that signs with a KMS key is the
 * one that must refuse. Every reason a binding is unusable reaches this point identically, so the
 * refusal must reveal nothing about which lists hold which key material. A signer that derives its
 * key from its own durable material never reaches this code and keeps signing; that shape is locked
 * in by the driver tests, which use a signer that ignores the key name.
 */
class KmsBackedStatusListSignerRefusalTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jwtService: JwtService
    private lateinit var coseCryptoService: CoseCryptoService

    private val app = createJvmStatusListTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("statuslist-signer-refusal", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "statuslist-signer-refusal-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        val provider =
            (app as SoftwareKmsProviderFactoryImpl.Graph)
                .softwareKmsProvider
                .create(config, session.asCoreApiServiceGraph().serviceExecution)
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(provider, makeDefaultKms = true)
        jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
        coseCryptoService = (session.graph as CryptoServices.Graph).cryptoServices.cose
    }

    private fun signer(): JwsStatusListSigner =
        JwsStatusListSigner(
            LocalStatusListJwsSigningService(jwtService, keyManagerService),
            NoopDidProviderRegistry,
            NoopDidResolverRegistry,
            CwtStatusListSigner(coseCryptoService, CoseSign1CborCodecImpl(), keyManagerService, NoopDidProviderRegistry),
        )

    private fun args(
        signingKeyName: String?,
        proofFormat: StatusProofFormat = StatusProofFormat.JWT,
    ) = SignStatusListTokenArgs(
        spec = StatusListSpec.TOKEN_STATUS_LIST,
        proofFormat = proofFormat,
        issuer = "did:example:issuer",
        statusListUri = "https://issuer.example/statuslists/refusal",
        signingKeyName = signingKeyName,
        bitsPerStatus = 1,
        length = 256,
        purposes = listOf(StatusPurpose.REVOCATION),
        encodedList = "eNrbuRgAAhcBXQ",
        issuedAtEpochSeconds = 1_700_000_000L,
    )

    @Test
    fun everyUnusableBindingRefusesIdentically() =
        runTest {
            // Absent, detached, cross-tenant, inactive, and unmapped bindings all arrive here as a
            // null key name, so the refusal must be byte-for-byte the same for each.
            val refusals =
                List(5) { signer().signStatusListToken(args(signingKeyName = null)) }.map { result ->
                    assertTrue(result.isErr)
                    result.error.code to result.error.message.defaultMessage
                }

            assertEquals("STATUSLIST_SIGNING_KEY_UNRESOLVABLE", refusals.first().first)
            assertEquals(1, refusals.toSet().size, "an unusable binding must not reveal why it is unusable")
        }

    @Test
    fun aBlankKeyNameRefusesRatherThanFallingBackToAKmsDefault() =
        runTest {
            val result = signer().signStatusListToken(args(signingKeyName = "  "))

            assertTrue(result.isErr)
            assertEquals("STATUSLIST_SIGNING_KEY_UNRESOLVABLE", result.error.code)
        }

    @Test
    fun theCwtFormRefusesTheSameWay() =
        runTest {
            val result = signer().signStatusListToken(args(signingKeyName = null, proofFormat = StatusProofFormat.CWT))

            assertTrue(result.isErr)
            assertEquals("STATUSLIST_SIGNING_KEY_UNRESOLVABLE", result.error.code)
        }
}
