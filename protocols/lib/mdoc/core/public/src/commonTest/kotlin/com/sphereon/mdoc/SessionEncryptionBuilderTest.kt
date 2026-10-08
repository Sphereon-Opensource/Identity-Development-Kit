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
 *
 */

package com.sphereon.mdoc

import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests for SessionEncryption.Builder class.
 */
class SessionEncryptionBuilderTest {
    private val sessionEstablishmentCborCodec = SessionEstablishmentCborCodecImpl()
    private val sessionDataCborCodec = SessionDataCborCodecImpl()
    private val sessionTranscriptCborCodec = SessionTranscriptCborCodecImpl()
    private val coseKeyCborCodec = CoseKeyCborCodecImpl()

    private fun createBuilder(): SessionEncryption.Builder =
        SessionEncryption.Builder().withCborCodecs(
            sessionEstablishmentCborCodec = sessionEstablishmentCborCodec,
            sessionDataCborCodec = sessionDataCborCodec,
            sessionTranscriptCborCodec = sessionTranscriptCborCodec,
            coseKeyCborCodec = coseKeyCborCodec,
        )

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()

    private fun createTestCoseKeyWithPrivate(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .withD("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")
            .build()
            .toCbor()

    @Test
    fun testBuilderCreation() {
        val builder = createBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithSelfRole() {
        val builder =
            createBuilder()
                .withSelfRole(MdocRole.MDOC)
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithSelfRoleMdocReader() {
        val builder =
            createBuilder()
                .withSelfRole(MdocRole.MDOC_READER)
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithProvider() {
        val builder =
            createBuilder()
                .withProvider(CryptographyProvider.Default)
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithDebugLogger() {
        val builder =
            createBuilder()
                .withDebugLogger(null)
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithSessionTranscriptBytes() {
        val transcriptBytes = byteArrayOf(0x01, 0x02, 0x03)
        val builder =
            createBuilder()
                .withSessionTranscriptBytes(transcriptBytes)
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithPreComputedSharedSecret() {
        val sharedSecret = ByteArray(32) { it.toByte() }
        val builder =
            createBuilder()
                .withPreComputedSharedSecret(sharedSecret)
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithSelfPrivateEphemeralKey() {
        val key = createTestCoseKeyWithPrivate()
        val keyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(key)

        val builder =
            createBuilder()
                .withSelfPrivateEphemeralKey(keyInfo)
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithSelfPublicEphemeralKey() {
        val key = createTestCoseKey()
        val keyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(key)

        val builder =
            createBuilder()
                .withSelfPublicEphemeralKey(keyInfo)
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithRemotePublicEphemeralKey() {
        val key = createTestCoseKey()
        val keyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(key)

        val builder =
            createBuilder()
                .withRemotePublicEphemeralKey(keyInfo)
        assertNotNull(builder)
    }

    @Test
    fun testBuilderBuildFailsWithoutSelfKey() =
        runTest {
            val remoteKey = createTestCoseKey()
            val remoteKeyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(remoteKey)
            val transcriptBytes = byteArrayOf(0x01, 0x02, 0x03)

            val builder =
                createBuilder()
                    .withSelfRole(MdocRole.MDOC)
                    .withRemotePublicEphemeralKey(remoteKeyInfo)
                    .withSessionTranscriptBytes(transcriptBytes)

            assertFailsWith<IllegalArgumentException> {
                builder.build()
            }
        }

    @Test
    fun testBuilderBuildFailsWithoutRemoteKey() =
        runTest {
            val selfKey = createTestCoseKeyWithPrivate()
            val selfKeyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(selfKey)
            val transcriptBytes = byteArrayOf(0x01, 0x02, 0x03)

            val builder =
                createBuilder()
                    .withSelfRole(MdocRole.MDOC)
                    .withSelfPrivateEphemeralKey(selfKeyInfo)
                    .withSessionTranscriptBytes(transcriptBytes)

            assertFailsWith<IllegalArgumentException> {
                builder.build()
            }
        }

    @Test
    fun testBuilderBuildFailsWithoutSessionTranscript() =
        runTest {
            val selfKey = createTestCoseKeyWithPrivate()
            val selfKeyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(selfKey)
            val remoteKey = createTestCoseKey()
            val remoteKeyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(remoteKey)

            val builder =
                createBuilder()
                    .withSelfRole(MdocRole.MDOC)
                    .withSelfPrivateEphemeralKey(selfKeyInfo)
                    .withRemotePublicEphemeralKey(remoteKeyInfo)

            assertFailsWith<IllegalArgumentException> {
                builder.build()
            }
        }

    @Test
    fun testBuilderBuildFailsWithPublicKeyOnlyAndNoSharedSecret() =
        runTest {
            val selfKey = createTestCoseKey() // Only public key
            val selfKeyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(selfKey)
            val remoteKey = createTestCoseKey()
            val remoteKeyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(remoteKey)
            val transcriptBytes = byteArrayOf(0xD8.toByte(), 0x18, 0x43, 0xA1.toByte(), 0x00, 0x00)

            val builder =
                createBuilder()
                    .withSelfRole(MdocRole.MDOC)
                    .withSelfPublicEphemeralKey(selfKeyInfo)
                    .withRemotePublicEphemeralKey(remoteKeyInfo)
                    .withSessionTranscriptBytes(transcriptBytes)

            assertFailsWith<IllegalArgumentException> {
                builder.build()
            }
        }

    @Test
    fun testBuilderMethodChaining() {
        val selfKey = createTestCoseKeyWithPrivate()
        val selfKeyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(selfKey)
        val remoteKey = createTestCoseKey()
        val remoteKeyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(remoteKey)
        val transcriptBytes = byteArrayOf(0x01, 0x02, 0x03)

        val builder =
            createBuilder()
                .withSelfRole(MdocRole.MDOC)
                .withSelfPrivateEphemeralKey(selfKeyInfo)
                .withRemotePublicEphemeralKey(remoteKeyInfo)
                .withSessionTranscriptBytes(transcriptBytes)
                .withProvider(CryptographyProvider.Default)
                .withDebugLogger(null)

        assertNotNull(builder)
    }
}
