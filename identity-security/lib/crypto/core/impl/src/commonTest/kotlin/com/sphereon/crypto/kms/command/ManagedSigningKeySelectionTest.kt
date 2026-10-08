/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.OperationCapability
import com.sphereon.crypto.core.kms.TestKmsMock
import com.sphereon.crypto.core.kms.TestKmsProviderMock
import com.sphereon.crypto.core.kms.command.CreateRawSignatureArgs
import com.sphereon.crypto.core.kms.command.SignDigestArgs
import com.sphereon.crypto.core.kms.command.SignatureEncoding
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ManagedSigningKeySelectionTest {
    @Test
    fun signingCommandConversionKeepsInlineKeyMaterialAuthoritative() {
        val inline =
            KeyInfo(
                alias = ALIAS_B,
                kid = KID_A,
                providerId = PROVIDER_ID,
                key =
                    Jwk(
                        kty = JwaKeyType.EC,
                        crv = JwaCurve.P_256,
                        kid = KID_A,
                        x = "eA",
                        y = "eQ",
                        d = "ZA",
                    ),
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            )

        assertSame(inline, inline.toSigningCommandKeyInfo())
    }

    @Test
    fun signingCommandConversionPreservesInlinePrivateCoseKeyWithSelectorMetadata() {
        val inline =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                kid = KID_A,
                x = "eA",
                y = "eQ",
                d = "ZA",
            ).jwkToCoseKey()
        val keyInfo =
            KeyInfo<CoseKeyType>(
                alias = ALIAS_B,
                kid = KID_A,
                providerId = PROVIDER_ID,
                key = inline,
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            )

        val converted = keyInfo.toSigningCommandKeyInfo()

        assertTrue(converted is ResolvedKeyInfo<*>)
        assertEquals(inline.toJson(), converted.key)
        assertEquals(KID_A, converted.kid)
    }

    @Test
    fun signingCommandConversionPreservesInlinePublicCoseKeyWithSelectorMetadata() {
        val inline =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                kid = KID_A,
                x = "eA",
                y = "eQ",
            ).jwkToCoseKey()
        val keyInfo =
            KeyInfo<CoseKeyType>(
                alias = ALIAS_B,
                kid = KID_A,
                providerId = PROVIDER_ID,
                key = inline,
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            )

        val converted = keyInfo.toSigningCommandKeyInfo()

        assertTrue(converted is ResolvedKeyInfo<*>)
        assertEquals(inline.toJson(), converted.key)
        assertEquals(KID_A, converted.kid)
    }

    @Test
    fun createRawSignatureRejectsAliasKidMismatchBeforeProviderSigning() =
        runTest {
            val fixture = selectionFixture()
            val command = CreateRawSignatureCommandImpl(TestSessionExecution(), fixture.registry)

            val result =
                command.execute(
                    CreateRawSignatureArgs(
                        keyInfo = selector(alias = ALIAS_B, kid = KID_A),
                        input = "managed raw signature".encodeToByteArray(),
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("CRYPTO_ERROR", result.error.code)
            assertTrue(result.error.message.defaultMessage.orEmpty().contains(expectedMismatch()))
            assertEquals(0, fixture.rawSignatureInvocations)
            assertEquals(listOf<Pair<String?, String?>>(ALIAS_B to null), fixture.lookups)
        }

    @Test
    fun serializedRemoteRawCommandRetainsKidAndRejectsMismatchThroughCommonCommand() =
        runTest {
            val fixture = selectionFixture()
            val command = CreateRawSignatureCommandImpl(TestSessionExecution(), fixture.registry)
            val json = Json { encodeDefaults = true }
            val encoded =
                json.encodeToString(
                    CreateRawSignatureArgs(
                        keyInfo = selector(alias = ALIAS_B, kid = KID_A),
                        input = byteArrayOf(1, 2, 3),
                    ),
                )
            val decoded = json.decodeFromString<CreateRawSignatureArgs>(encoded)

            assertEquals(KID_A, decoded.keyInfo?.kid)
            val result = command.execute(decoded)

            assertTrue(result.isErr)
            assertEquals("CRYPTO_ERROR", result.error.code)
            assertTrue(result.error.message.defaultMessage.orEmpty().contains(expectedMismatch()))
            assertEquals(0, fixture.rawSignatureInvocations)
        }

    @Test
    fun createRawSignatureAcceptsMatchingAndSingleSelectors() =
        runTest {
            val fixture = selectionFixture()
            val command = CreateRawSignatureCommandImpl(TestSessionExecution(), fixture.registry)

            val matching = command.execute(CreateRawSignatureArgs(selector(ALIAS_B, KID_B), byteArrayOf(1)))
            val aliasOnly = command.execute(CreateRawSignatureArgs(selector(alias = ALIAS_B), byteArrayOf(2)))
            val kidOnly = command.execute(CreateRawSignatureArgs(selector(kid = KID_A), byteArrayOf(3)))
            val legacyAliasShapedKid = command.execute(CreateRawSignatureArgs(selector(kid = ALIAS_B), byteArrayOf(4)))

            assertTrue(matching.isOk)
            assertTrue(aliasOnly.isOk)
            assertTrue(kidOnly.isOk)
            assertTrue(legacyAliasShapedKid.isOk)
            assertEquals(4, fixture.rawSignatureInvocations)
            assertEquals(listOf<Pair<String?, String?>>(ALIAS_B to null), fixture.lookups)
        }

    @Test
    fun createRawSignatureSearchesRegisteredProvidersForAliasOnlySelector() =
        runTest {
            val fixture = selectionFixture()
            val command = CreateRawSignatureCommandImpl(TestSessionExecution(), fixture.registry)

            val result =
                command.execute(
                    CreateRawSignatureArgs(
                        keyInfo = selector(alias = ALIAS_B, providerId = null),
                        input = byteArrayOf(1),
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(1, fixture.rawSignatureInvocations)
            assertEquals(listOf<Pair<String?, String?>>(ALIAS_B to null), fixture.lookups)
        }

    @Test
    fun createRawSignatureAcceptsProviderNativeAliasAsWireKid() =
        runTest {
            val fixture = selectionFixture()
            val command = CreateRawSignatureCommandImpl(TestSessionExecution(), fixture.registry)

            val result =
                command.execute(
                    CreateRawSignatureArgs(
                        keyInfo = selector(alias = ALIAS_B, kid = ALIAS_B),
                        input = byteArrayOf(1),
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(1, fixture.rawSignatureInvocations)
            assertEquals(listOf<Pair<String?, String?>>(ALIAS_B to null), fixture.lookups)
        }

    /**
     * A store that keeps no kid for the alias has nothing to disagree with. The alias is the
     * selector and is unique within the tenant and provider, so signing proceeds. Refusing here
     * would reject a correctly provisioned key because an optional metadata field is absent, which
     * is exactly what a PKCS12-backed keystore looks like.
     */
    @Test
    fun createRawSignatureAcceptsCompoundSelectorWhenAliasHasNoCanonicalKid() =
        runTest {
            val fixture = selectionFixture(canonicalKid = null)
            val command = CreateRawSignatureCommandImpl(TestSessionExecution(), fixture.registry)

            val result =
                command.execute(
                    CreateRawSignatureArgs(
                        keyInfo = selector(alias = ALIAS_B, kid = KID_A),
                        input = byteArrayOf(1),
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(1, fixture.rawSignatureInvocations)
            assertEquals(listOf<Pair<String?, String?>>(ALIAS_B to null), fixture.lookups)
        }

    @Test
    fun signDigestRejectsAliasKidMismatchBeforeProviderSigning() =
        runTest {
            val fixture = selectionFixture()
            val command = SignDigestCommandImpl(TestSessionExecution(), fixture.registry)

            val result =
                command.execute(
                    SignDigestArgs(
                        keyInfo = selector(alias = ALIAS_B, kid = KID_A),
                        digest = ByteArray(32) { it.toByte() },
                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                        signatureEncoding = SignatureEncoding.RAW,
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("CRYPTO_ERROR", result.error.code)
            assertTrue(result.error.message.defaultMessage.orEmpty().contains(expectedMismatch()))
            assertEquals(0, fixture.digestSignatureInvocations)
            assertEquals(listOf<Pair<String?, String?>>(ALIAS_B to null), fixture.lookups)
        }

    @Test
    fun signDigestAcceptsMatchingAliasAndKid() =
        runTest {
            val fixture = selectionFixture()
            val command = SignDigestCommandImpl(TestSessionExecution(), fixture.registry)

            val result =
                command.execute(
                    SignDigestArgs(
                        keyInfo = selector(alias = ALIAS_B, kid = KID_B),
                        digest = ByteArray(32) { it.toByte() },
                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                        signatureEncoding = SignatureEncoding.RAW,
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(1, fixture.digestSignatureInvocations)
            assertEquals(listOf<Pair<String?, String?>>(ALIAS_B to null), fixture.lookups)
        }

    @Test
    fun signDigestAcceptsProviderNativeAliasAsWireKid() =
        runTest {
            val fixture = selectionFixture()
            val command = SignDigestCommandImpl(TestSessionExecution(), fixture.registry)

            val result =
                command.execute(
                    SignDigestArgs(
                        keyInfo = selector(alias = ALIAS_B, kid = ALIAS_B),
                        digest = ByteArray(32) { it.toByte() },
                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                        signatureEncoding = SignatureEncoding.RAW,
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(1, fixture.digestSignatureInvocations)
            assertEquals(listOf<Pair<String?, String?>>(ALIAS_B to null), fixture.lookups)
        }

    private fun selectionFixture(canonicalKid: String? = KID_B): SelectionFixture {
        val registry = TestKmsMock()
        val fixture = SelectionFixture(registry, canonicalKid)
        registry.registerProvider(fixture.provider, makeDefaultKms = true)
        return fixture
    }

    private fun selector(
        alias: String? = null,
        kid: String? = null,
        providerId: String? = PROVIDER_ID,
    ): KeyInfo<KeyType> =
        KeyInfo(
            alias = alias,
            kid = kid,
            providerId = providerId,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            keyType = KeyTypeMapping.EC,
        )

    private fun expectedMismatch(): String =
        "Managed signing key selector mismatch: alias '$ALIAS_B' resolved to kid '$KID_B' " +
            "in provider '$PROVIDER_ID', not requested kid '$KID_A'"

    private class SelectionFixture(
        val registry: TestKmsMock,
        private val canonicalKid: String?,
    ) {
        val lookups = mutableListOf<Pair<String?, String?>>()
        var rawSignatureInvocations = 0
        var digestSignatureInvocations = 0

        val provider: KmsProvider =
            object : KmsProvider by TestKmsProviderMock() {
                override val id: String = PROVIDER_ID

                // This fixture implements digest signing, so its advertised capabilities must
                // include the operation that SignDigestCommandImpl checks before dispatch.
                override fun getCapabilities(): KmsProviderCapabilities =
                    TestKmsProviderMock()
                        .getCapabilities()
                        .copy(
                            providerId = id,
                            operations =
                                arrayOf(
                                    OperationCapability(KmsProviderOperation.SIGN, true),
                                    OperationCapability(KmsProviderOperation.SIGN_DIGEST, true),
                                    OperationCapability(KmsProviderOperation.VERIFY, true),
                                ),
                        )

                override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
                    lookups += keyInfo.alias to keyInfo.kid
                    return canonical(ALIAS_B, canonicalKid)
                }

                override suspend fun createRawSignature(
                    keyInfo: KeyInfoType<*>,
                    input: ByteArray,
                    requireX5Chain: Boolean,
                ): ByteArray {
                    rawSignatureInvocations++
                    return byteArrayOf(1, 2, 3)
                }

                override suspend fun signDigest(
                    keyInfo: KeyInfoType<*>,
                    digest: ByteArray,
                    signatureAlgorithm: SignatureAlgorithm,
                    signatureEncoding: SignatureEncoding,
                    requireX5Chain: Boolean,
                ): ByteArray {
                    digestSignatureInvocations++
                    return byteArrayOf(4, 5, 6)
                }
            }

        private fun canonical(
            alias: String,
            kid: String?,
        ): ManagedKeyInfoType<*> =
            ManagedKeyInfo(
                alias = alias,
                providerId = PROVIDER_ID,
                resolvedKeyInfo =
                    ResolvedKeyInfo(
                        key =
                            Jwk(
                                kty = JwaKeyType.EC,
                                crv = JwaCurve.P_256,
                                kid = kid,
                                x = "eA",
                                y = "eQ",
                            ),
                        kid = kid,
                        alias = alias,
                        providerId = PROVIDER_ID,
                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                        keyType = KeyTypeMapping.EC,
                    ),
            )
    }

    private class TestSessionExecution(
        override val sessionContext: SessionContext = createAnonymousSessionContext("managed-selector-test", "managed-selector-correlation"),
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for test")
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val conf: ContextConfig = NoOpContextConfig()
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext,
    ) : SessionLogService {
        override val id: String = "test-log"
        override val isEnabled: Boolean = false
        override val scope = com.sphereon.core.api.context.IdkScope.SESSION
        override val logManager: SessionLogManager
            get() = throw NotImplementedError("Not needed for test")

        override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for test")
    }

    private class NoOpContextConfig : ContextConfig {
        override val app: AppConfigService get() = throw NotImplementedError("Not needed for test")
        override val tenant: TenantConfigService get() = throw NotImplementedError("Not needed for test")
        override val principal: PrincipalConfigService get() = throw NotImplementedError("Not needed for test")

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for test")
    }

    private companion object {
        const val PROVIDER_ID = "managed-selector-provider"
        const val ALIAS_B = "key-b"
        const val KID_A = "kid-a"
        const val KID_B = "kid-b"
    }
}
