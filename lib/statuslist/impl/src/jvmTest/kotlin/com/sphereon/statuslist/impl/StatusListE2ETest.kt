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
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.CryptoServices
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1CborCodecImpl
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.DidProvider
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.statuslist.AllocateEntryArgs
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.CredentialStatusAction
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.RevokeCredentialStatusArgs
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.UpdateEntryStatusArgs
import com.sphereon.statuslist.impl.codec.StatusListCodec
import com.sphereon.statuslist.impl.command.RevokeCredentialStatusCommandImpl
import com.sphereon.statuslist.impl.driver.InMemoryStatusListDriver
import com.sphereon.statuslist.impl.driver.InMemoryStatusListStore
import com.sphereon.statuslist.impl.envelope.BitstringStatusListEnvelope
import com.sphereon.statuslist.impl.envelope.TokenStatusListEnvelope
import com.sphereon.statuslist.impl.sign.CwtStatusListSigner
import com.sphereon.statuslist.impl.sign.JwsStatusListSigner
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * End-to-end status-list flow through the REAL crypto stack (software KMS + [JwtService]): create a
 * list, allocate an entry, mint a genuinely-signed status-list token, then decode that token's
 * payload and assert the status bit — before and after revocation — for both specs. This exercises
 * the [JwsStatusListSigner] ↔ crypto integration that the fake-signer driver tests could not.
 */
class StatusListE2ETest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jwtService: JwtService
    private lateinit var coseCryptoService: CoseCryptoService

    private val app = createJvmStatusListTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("statuslist-e2e")

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "statuslist-e2e-provider",
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

    private suspend fun newDriverWithKey(): Pair<InMemoryStatusListDriver, String> {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val alias = keyInfo.alias ?: fail("generated key has no alias")
        return InMemoryStatusListDriver(
            InMemoryStatusListStore(),
            JwsStatusListSigner(
                jwtService,
                keyManagerService,
                NoopDidProviderRegistry,
                CwtStatusListSigner(coseCryptoService, CoseSign1CborCodecImpl(), keyManagerService, NoopDidProviderRegistry),
            ),
        ) to alias
    }

    /** Public COSE key info for [alias], for verifying a signed CWT. */
    private suspend fun coseVerifyKeyFor(alias: String): KeyInfoType<CoseKeyType> {
        val managed =
            keyManagerService.getKeyResult(KeyInfo<Nothing>(alias = alias)).getOrElse { fail("getKey: $it") }.key
                ?: fail("no key for $alias")
        return CoseJoseKeyMappingService.toCoseKeyInfo(managed)
    }

    /** Decode the status bit at [index] from a signed status-list token's JWT payload. */
    private suspend fun decodeBit(
        token: String,
        spec: StatusListSpec,
        bitsPerStatus: Int,
        index: Int,
    ): Int {
        val segments = token.split(".")
        assertEquals(3, segments.size, "status-list token must be a signed compact JWS (header.payload.signature)")
        val payload = JwsUtils.decodeBase64UrlToJson(segments[1])
        val encodedList =
            when (spec) {
                StatusListSpec.TOKEN_STATUS_LIST -> TokenStatusListEnvelope.parse(payload).encodedList
                StatusListSpec.BITSTRING_STATUS_LIST -> BitstringStatusListEnvelope.parse(payload).encodedList
            }
        return StatusListCodec.decode(encodedList, bitsPerStatus, spec).get(index)
    }

    private suspend fun tokenFor(
        driver: InMemoryStatusListDriver,
        correlationId: String,
    ): String =
        (driver.getStatusListToken(StatusListRef(correlationId = correlationId)).getOrElse { fail("getToken: $it") })
            ?.token ?: fail("no token for $correlationId")

    @Test
    fun tokenStatusListIssueRevokeAndVerify() =
        runTest {
            val (driver, alias) = newDriverWithKey()
            val correlationId = "e2e-token"
            val created =
                driver
                    .createStatusList(
                        CreateStatusListArgs(
                            correlationId = correlationId,
                            spec = StatusListSpec.TOKEN_STATUS_LIST,
                            purposes = listOf(StatusPurpose.REVOCATION),
                            proofFormat = StatusProofFormat.JWT,
                            issuer = "did:example:issuer",
                            statusListUri = "https://issuer.example/statuslists/$correlationId",
                            length = 256,
                            bitsPerStatus = 1,
                            signingKeyAlias = alias,
                        ),
                    ).getOrElse { fail("create: $it") }
            // The token is a genuinely signed compact JWS.
            assertEquals(3, created.signedToken.split(".").size)

            driver
                .allocateEntry(
                    AllocateEntryArgs(StatusListRef(correlationId = correlationId), explicitIndex = 42, credentialId = "cred-42"),
                ).getOrElse { fail("allocate: $it") }

            // Freshly issued: index 42 is VALID in the signed token.
            assertEquals(StatusValues.VALID, decodeBit(tokenFor(driver, correlationId), StatusListSpec.TOKEN_STATUS_LIST, 1, 42))

            // Revoke by credentialId (no need to know the index).
            driver
                .updateEntryStatus(
                    UpdateEntryStatusArgs(EntryRef(correlationId = correlationId, credentialId = "cred-42"), StatusValues.INVALID),
                ).getOrElse { fail("revoke: $it") }

            // The re-signed token now shows index 42 INVALID, while an untouched index stays VALID.
            val revokedToken = tokenFor(driver, correlationId)
            assertEquals(StatusValues.INVALID, decodeBit(revokedToken, StatusListSpec.TOKEN_STATUS_LIST, 1, 42))
            assertEquals(StatusValues.VALID, decodeBit(revokedToken, StatusListSpec.TOKEN_STATUS_LIST, 1, 0))
        }

    @Test
    fun bitstringStatusListIssueRevokeAndVerify() =
        runTest {
            val (driver, alias) = newDriverWithKey()
            val correlationId = "e2e-bitstring"
            driver
                .createStatusList(
                    CreateStatusListArgs(
                        correlationId = correlationId,
                        spec = StatusListSpec.BITSTRING_STATUS_LIST,
                        purposes = listOf(StatusPurpose.REVOCATION),
                        proofFormat = StatusProofFormat.VC_JWT,
                        issuer = "did:example:issuer",
                        statusListUri = "https://issuer.example/statuslists/$correlationId",
                        length = 131_072,
                        bitsPerStatus = 1,
                        signingKeyAlias = alias,
                    ),
                ).getOrElse { fail("create: $it") }

            driver
                .allocateEntry(
                    AllocateEntryArgs(
                        StatusListRef(correlationId = correlationId),
                        explicitIndex = 9000,
                        entryCorrelationId = "order-9000",
                    ),
                ).getOrElse { fail("allocate: $it") }

            assertEquals(
                StatusValues.VALID,
                decodeBit(tokenFor(driver, correlationId), StatusListSpec.BITSTRING_STATUS_LIST, 1, 9000),
            )

            // Revoke by business key (entryCorrelationId).
            driver
                .updateEntryStatus(
                    UpdateEntryStatusArgs(
                        EntryRef(correlationId = correlationId, entryCorrelationId = "order-9000"),
                        StatusValues.INVALID,
                    ),
                ).getOrElse { fail("revoke: $it") }

            val revokedToken = tokenFor(driver, correlationId)
            assertEquals(StatusValues.INVALID, decodeBit(revokedToken, StatusListSpec.BITSTRING_STATUS_LIST, 1, 9000))
            assertEquals(StatusValues.VALID, decodeBit(revokedToken, StatusListSpec.BITSTRING_STATUS_LIST, 1, 0))
        }

    /** The ergonomic [RevokeCredentialStatusCommand] — the simple IDK entry point for an example issuer. */
    @Test
    fun revokeCommandRevokesThenReactivatesByCredentialId() =
        runTest {
            val (driver, alias) = newDriverWithKey()
            val correlationId = "e2e-revoke-cmd"
            driver
                .createStatusList(
                    CreateStatusListArgs(
                        correlationId = correlationId,
                        spec = StatusListSpec.TOKEN_STATUS_LIST,
                        purposes = listOf(StatusPurpose.REVOCATION),
                        proofFormat = StatusProofFormat.JWT,
                        issuer = "did:example:issuer",
                        statusListUri = "https://issuer.example/statuslists/$correlationId",
                        length = 256,
                        bitsPerStatus = 1,
                        signingKeyAlias = alias,
                    ),
                ).getOrElse { fail("create: $it") }
            driver
                .allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = correlationId), explicitIndex = 7, credentialId = "cred-7"))
                .getOrElse { fail("allocate: $it") }

            val execution = session.asCoreApiServiceGraph().serviceExecution
            val revoke = RevokeCredentialStatusCommandImpl(execution, driver)
            val entryRef = EntryRef(correlationId = correlationId, credentialId = "cred-7")

            // Revoke by credentialId via the command — no raw status values, no REST.
            revoke
                .execute(RevokeCredentialStatusArgs(entryRef, CredentialStatusAction.REVOKE))
                .getOrElse { fail("revoke: $it") }
            assertEquals(StatusValues.INVALID, decodeBit(tokenFor(driver, correlationId), StatusListSpec.TOKEN_STATUS_LIST, 1, 7))

            // Reactivate via the command flips it back.
            revoke
                .execute(RevokeCredentialStatusArgs(entryRef, CredentialStatusAction.REACTIVATE))
                .getOrElse { fail("reactivate: $it") }
            assertEquals(StatusValues.VALID, decodeBit(tokenFor(driver, correlationId), StatusListSpec.TOKEN_STATUS_LIST, 1, 7))
        }

    /** Token Status List in CWT form: a real COSE_Sign1 that decodes, carries the typ header, and verifies. */
    @Test
    fun tokenStatusListCwtSignsDecodesAndVerifies() =
        runTest {
            val (driver, alias) = newDriverWithKey()
            val correlationId = "e2e-cwt"
            driver
                .createStatusList(
                    CreateStatusListArgs(
                        correlationId = correlationId,
                        spec = StatusListSpec.TOKEN_STATUS_LIST,
                        purposes = listOf(StatusPurpose.REVOCATION),
                        proofFormat = StatusProofFormat.CWT,
                        issuer = "https://issuer.example",
                        statusListUri = "https://issuer.example/statuslists/$correlationId",
                        length = 256,
                        bitsPerStatus = 1,
                        signingKeyAlias = alias,
                        ttlSeconds = 300,
                    ),
                ).getOrElse { fail("create: $it") }
            driver
                .allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = correlationId), explicitIndex = 42, credentialId = "cred-42"))
                .getOrElse { fail("allocate: $it") }

            val token =
                driver.getStatusListToken(StatusListRef(correlationId = correlationId)).getOrElse { fail("getToken: $it") }
                    ?: fail("no token")
            // A CWT is binary: it carries raw bytes, not a JWS string.
            assertEquals(StatusListContentTypes.STATUSLIST_CWT, token.contentType)
            val bytes = token.tokenBytes ?: fail("CWT token must carry binary bytes")

            // Decodes as a COSE_Sign1 whose protected header carries typ (label 16) = the CWT media type.
            val coseSign1 = CoseSign1CborCodecImpl().decode(bytes).getOrElse { fail("decode COSE_Sign1: $it") }.value
            assertEquals(StatusListContentTypes.STATUSLIST_CWT, coseSign1.protectedHeader.typ?.value)
            assertNotNull(coseSign1.protectedHeader.alg, "CWT must carry a signing alg")
            assertNotNull(coseSign1.payload, "CWT must carry the CWT claims payload")

            // The COSE signature verifies against the issuer key.
            val verify = coseCryptoService.verify1(coseSign1, coseVerifyKeyFor(alias), requireX5Chain = false)
            assertFalse(verify.error, "CWT signature must verify: ${verify.message}")
        }
}

/** No DID providers — these e2e tests sign with a key alias only (no `did:` signing mode). */
internal object NoopDidProviderRegistry : DidProviderRegistry {
    override fun getProvider(method: String): DidProvider? = null

    override fun getCapabilities(method: String): DidMethodCapabilities? = null

    override fun getSupportedMethods(): List<String> = emptyList()
}
