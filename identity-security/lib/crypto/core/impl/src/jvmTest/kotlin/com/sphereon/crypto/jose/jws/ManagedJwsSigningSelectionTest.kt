/* Copyright 2026 Sphereon International B.V. SPDX-License-Identifier: Apache-2.0 */
package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.kms.command.CreateRawSignatureArgs
import com.sphereon.crypto.core.kms.command.CreateRawSignatureCommand
import com.sphereon.crypto.core.kms.command.CreateRawSignatureResult
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonGeneralCommandImpl
import com.sphereon.crypto.jose.jws.command.PrepareJwsCommand
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import dev.whyoleg.cryptography.CryptographyProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManagedJwsSigningSelectionTest {
    private val app = createCryptoTestAppGraph(this)
    private val session = app.userContextManager.getAnonymous().sessionContextManager.createOrGetFromId(
        "managed-jws-selection", principalType = com.sphereon.di.context.PrincipalType.USER,
    )
    private val alias = "oauth2-as-platform"
    private val providerId = "managed-jws-test-provider"
    private lateinit var kms: KeyManagerService
    private lateinit var jwt: JwtService
    private lateinit var privateKey: ManagedKeyInfoType<*>
    private lateinit var publicKey: ManagedKeyInfoType<*>

    private suspend fun initialize() {
        val provider = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider.create(
            SoftwareKmsProviderConfig(id = providerId, cryptographyProvider = CryptographyProvider.Default.name),
            session.asCoreApiServiceGraph().serviceExecution,
        )
        kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        kms.registerProvider(provider, makeDefaultKms = true)
        jwt = (session.graph as JwtServiceImpl.Graph).jwtService
        val pair = kms.generateKeyAsync(alias = alias, alg = SignatureAlgorithm.ECDSA_SHA256)
        privateKey = pair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        publicKey = pair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
    }

    private fun args(kid: String = alias) = CreateJwsJsonArgs(
        issuer = ManagedOptsKeyInfo(identifier = KeyInfo<KeyType>(
            alias = alias, kid = kid, providerId = providerId, signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
        )),
        payload = JsonObject(mapOf("sub" to JsonPrimitive("test-subject"))),
    )

    @Test
    fun storedSelectorSignsWithExistingProtocolKid() = runTest {
        initialize()
        val prepared = jwt.prepareJws(args())
        assertTrue(prepared.isOk, "${prepared.errorOrNull()}")
        val result = jwt.createJwsJsonGeneral(args())
        assertTrue(result.isOk, "Managed alias signing failed: ${result.errorOrNull()}")
        assertEquals(alias, prepared.value.jws.protectedHeader?.kid)
    }

    @Test
    fun aliasOnlySelectorSignsUsingPrivateStoreAndPreservesResolvedKid() = runTest {
        initialize()
        val request = CreateJwsJsonArgs(
            issuer = ManagedOptsAlias(identifier = alias),
            payload = JsonObject(mapOf("sub" to JsonPrimitive("test-subject"))),
        )
        val prepared = jwt.prepareJws(request)
        assertTrue(prepared.isOk, "Alias preparation failed: ${prepared.errorOrNull()}")

        val result = jwt.createJwsJsonGeneral(request)

        assertTrue(result.isOk, "Alias signing must re-resolve private KMS material: ${result.errorOrNull()}")
        assertEquals(privateKey.kid, prepared.value.jws.protectedHeader?.kid)
    }

    @Test
    fun publicOnlyInlineMaterialDoesNotBorrowStoredPrivateKey() = runTest {
        initialize()
        val result = jwt.createJwsJsonGeneral(args().copy(issuer = ManagedOptsKeyInfo(identifier = publicKey)))
        assertTrue(result.isErr, "Inline public material must not select the stored private key")
    }

    @Test
    fun conflictingExplicitKidDoesNotUseAliasAsEscapeHatch() = runTest {
        initialize()
        val result = jwt.createJwsJsonGeneral(args(kid = "a-different-key"))
        assertTrue(result.isErr, "An explicit identity conflict must fail closed")
    }

    @Test
    fun nonExportableSignerReceivesSelectorAndSignatureIsBoundToPreparedPublicKey() = runTest {
        initialize()
        val request = args()
        val prepared = jwt.prepareJws(request)
        assertTrue(prepared.isOk)
        val prepare = mockk<PrepareJwsCommand>()
        val sign = mockk<CreateRawSignatureCommand>()
        coEvery { prepare.execute(any()) } returns prepared
        coEvery { sign.execute(any()) } coAnswers {
            val input = firstArg<CreateRawSignatureArgs>()
            val selector = assertNotNull(input.keyInfo)
            assertNull(selector.key, "The signing command must not receive inline material")
            assertEquals(alias, selector.alias)
            assertEquals(providerId, selector.providerId)
            IdkResult.ok(CreateRawSignatureResult(kms.createRawSignature(privateKey, input.input, false)))
        }
        val command = CreateJwsJsonGeneralCommandImpl(mockk<SessionExecution>(relaxed = true), prepare, sign)
        val result = command.execute(request)
        assertTrue(result.isOk, "${result.errorOrNull()}")
        coVerify(exactly = 1) { sign.execute(any()) }
    }

    @Test
    fun aliasOnlySignerPassesPrivateSelectorToSigningCommand() = runTest {
        initialize()
        val request = CreateJwsJsonArgs(
            issuer = ManagedOptsAlias(identifier = alias),
            payload = JsonObject(mapOf("sub" to JsonPrimitive("test-subject"))),
        )
        val prepared = jwt.prepareJws(request)
        assertTrue(prepared.isOk)
        val prepare = mockk<PrepareJwsCommand>()
        val sign = mockk<CreateRawSignatureCommand>()
        coEvery { prepare.execute(any()) } returns prepared
        coEvery { sign.execute(any()) } coAnswers {
            val input = firstArg<CreateRawSignatureArgs>()
            val selector = assertNotNull(input.keyInfo)
            assertNull(selector.key, "Alias signing must not pass inline public material")
            assertEquals(alias, selector.alias)
            assertEquals(providerId, selector.providerId)
            IdkResult.ok(CreateRawSignatureResult(kms.createRawSignature(privateKey, input.input, false)))
        }
        val command = CreateJwsJsonGeneralCommandImpl(mockk<SessionExecution>(relaxed = true), prepare, sign)

        val result = command.execute(request)

        assertTrue(result.isOk, "Alias selector was not preserved: ${result.errorOrNull()}")
        coVerify(exactly = 1) { sign.execute(any()) }
    }

    @Test
    fun signOnlyManagedKeyDoesNotNeedVerificationPermissionForConsistencyCheck() = runTest {
        assertManagedOperationPolicy(KeyOperations.SIGN, shouldSign = true)
    }

    @Test
    fun verifyOnlyResolvedKeyIsRejectedBeforeSigningCommandRuns() = runTest {
        assertManagedOperationPolicy(KeyOperations.VERIFY, shouldSign = false)
    }

    private suspend fun assertManagedOperationPolicy(operation: KeyOperations, shouldSign: Boolean) {
        initialize()
        val constrained = ManagedKeyInfo.fromKeyInfo(
            KeyInfo.fromDTO(publicKey).copy(key = (publicKey.key as Jwk).copy(key_ops = arrayOf(operation.jose))),
        )
        val request = args()
        val prepared = jwt.prepareJws(request.copy(issuer = ManagedOptsKeyInfo(identifier = constrained)))
        assertTrue(prepared.isOk)
        val prepare = mockk<PrepareJwsCommand>()
        val sign = mockk<CreateRawSignatureCommand>()
        coEvery { prepare.execute(any()) } returns prepared
        coEvery { sign.execute(any()) } coAnswers {
            IdkResult.ok(CreateRawSignatureResult(kms.createRawSignature(privateKey, firstArg<CreateRawSignatureArgs>().input, false)))
        }
        val command = CreateJwsJsonGeneralCommandImpl(mockk<SessionExecution>(relaxed = true), prepare, sign)
        val result = command.execute(request)
        assertEquals(shouldSign, result.isOk, "Operation policy result: ${result.errorOrNull()}")
        coVerify(exactly = if (shouldSign) 1 else 0) { sign.execute(any()) }
    }

    @Test
    fun wrongKeySignatureIsRejectedBeforeJwsIsReturned() = runTest {
        initialize()
        val request = args()
        val prepared = jwt.prepareJws(request)
        assertTrue(prepared.isOk)
        val other = kms.generateKeyAsync(alias = "other-signing-key", alg = SignatureAlgorithm.ECDSA_SHA256)
            .joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val prepare = mockk<PrepareJwsCommand>()
        val sign = mockk<CreateRawSignatureCommand>()
        coEvery { prepare.execute(any()) } returns prepared
        coEvery { sign.execute(any()) } coAnswers {
            IdkResult.ok(CreateRawSignatureResult(kms.createRawSignature(other, firstArg<CreateRawSignatureArgs>().input, false)))
        }
        val command = CreateJwsJsonGeneralCommandImpl(mockk<SessionExecution>(relaxed = true), prepare, sign)
        val result = command.execute(request)
        assertTrue(result.isErr, "A signature from another managed key must not be returned")
        assertTrue(result.error.message.defaultMessage.orEmpty().contains("does not match the prepared public key"))
    }

    private fun IdkResult<*, *>.errorOrNull(): Any? = if (isErr) error else null
}
