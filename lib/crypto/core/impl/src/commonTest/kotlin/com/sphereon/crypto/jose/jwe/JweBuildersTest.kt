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

package com.sphereon.crypto.jose.jwe

import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JweBuildersTest {
    // ========== JweHeaderBuilder Tests ==========

    @Test
    fun shouldBuildBasicJweHeader(): TestResult =
        runTest {
            val header =
                JweHeaderBuilder()
                    .alg("RSA-OAEP-256")
                    .enc("A256GCM")
                    .build()

            assertEquals("RSA-OAEP-256", header.alg)
            assertEquals("A256GCM", header.enc)
        }

    @Test
    fun shouldBuildJweHeaderWithAllAlgorithmParams(): TestResult =
        runTest {
            val header =
                JweHeaderBuilder()
                    .alg("ECDH-ES+A256KW")
                    .enc("A256GCM")
                    .zip("DEF")
                    .build()

            assertEquals("ECDH-ES+A256KW", header.alg)
            assertEquals("A256GCM", header.enc)
            assertEquals("DEF", header.zip)
        }

    @Test
    fun shouldBuildJweHeaderWithKeyIdentification(): TestResult =
        runTest {
            val header =
                JweHeaderBuilder()
                    .alg("RSA-OAEP")
                    .enc("A128GCM")
                    .kid("my-key-id")
                    .jku("https://example.com/jwks")
                    .x5u("https://example.com/cert")
                    .x5t("thumbprint-sha1")
                    .x5tS256("thumbprint-sha256")
                    .build()

            assertEquals("my-key-id", header.kid)
            assertEquals("https://example.com/jwks", header.jku)
            assertEquals("https://example.com/cert", header.x5u)
            assertEquals("thumbprint-sha1", header.x5t)
            assertEquals("thumbprint-sha256", header.x5tS256)
        }

    @Test
    fun shouldBuildJweHeaderWithX5c(): TestResult =
        runTest {
            val header =
                JweHeaderBuilder()
                    .alg("RSA-OAEP")
                    .enc("A256GCM")
                    .x5c("cert1", "cert2", "cert3")
                    .build()

            assertNotNull(header.x5c)
            assertEquals(3, header.x5c!!.size)
            assertEquals("cert1", header.x5c!![0])
            assertEquals("cert2", header.x5c!![1])
            assertEquals("cert3", header.x5c!![2])
        }

    @Test
    fun shouldBuildJweHeaderWithContentTypes(): TestResult =
        runTest {
            val header =
                JweHeaderBuilder()
                    .alg("A256KW")
                    .enc("A256GCM")
                    .typ("JOSE")
                    .cty("JWT")
                    .build()

            assertEquals("JOSE", header.typ)
            assertEquals("JWT", header.cty)
        }

    @Test
    fun shouldBuildJweHeaderWithCritical(): TestResult =
        runTest {
            val header =
                JweHeaderBuilder()
                    .alg("RSA-OAEP")
                    .enc("A256GCM")
                    .crit("custom1", "custom2")
                    .build()

            assertNotNull(header.crit)
            assertEquals(2, header.crit!!.size)
            assertTrue(header.crit!!.contains("custom1"))
            assertTrue(header.crit!!.contains("custom2"))
        }

    @Test
    fun shouldBuildJweHeaderWithEcdhParams(): TestResult =
        runTest {
            val epkJwk =
                Jwk(
                    kty = JwaKeyType.EC,
                    crv = JwaCurve.P_256,
                    x = "test-x-coord",
                    y = "test-y-coord",
                )

            val header =
                JweHeaderBuilder()
                    .alg("ECDH-ES")
                    .enc("A256GCM")
                    .epk(epkJwk)
                    .apu("party-u-info")
                    .apv("party-v-info")
                    .build()

            assertNotNull(header.epk)
            assertEquals("test-x-coord", header.epk!!.x)
            assertEquals("party-u-info", header.apu)
            assertEquals("party-v-info", header.apv)
        }

    @Test
    fun shouldBuildJweHeaderWithAesGcmKwParams(): TestResult =
        runTest {
            val header =
                JweHeaderBuilder()
                    .alg("A256GCMKW")
                    .enc("A256GCM")
                    .iv("initialization-vector")
                    .tag("authentication-tag")
                    .build()

            assertEquals("initialization-vector", header.iv)
            assertEquals("authentication-tag", header.tag)
        }

    @Test
    fun shouldBuildJweHeaderWithPbes2Params(): TestResult =
        runTest {
            val header =
                JweHeaderBuilder()
                    .alg("PBES2-HS256+A128KW")
                    .enc("A128GCM")
                    .p2s("salt-value")
                    .p2c(10000)
                    .build()

            assertEquals("salt-value", header.p2s)
            assertEquals(10000, header.p2c)
        }

    @Test
    fun shouldBuildJweHeaderWithJwk(): TestResult =
        runTest {
            val jwk =
                Jwk(
                    kty = JwaKeyType.RSA,
                    n = "modulus",
                    e = "AQAB",
                )

            val header =
                JweHeaderBuilder()
                    .alg("RSA-OAEP")
                    .enc("A256GCM")
                    .jwk(jwk)
                    .build()

            assertNotNull(header.jwk)
            assertEquals(JwaKeyType.RSA, header.jwk!!.kty)
        }

    @Test
    fun shouldCreateHeaderBuilderFromExisting(): TestResult =
        runTest {
            val original =
                JweHeaderBuilder()
                    .alg("RSA-OAEP")
                    .enc("A256GCM")
                    .kid("key-123")
                    .build()

            val copy =
                JweHeaderBuilder
                    .from(original)
                    .typ("JOSE")
                    .build()

            assertEquals("RSA-OAEP", copy.alg)
            assertEquals("A256GCM", copy.enc)
            assertEquals("key-123", copy.kid)
            assertEquals("JOSE", copy.typ)
        }

    @Test
    fun shouldUseJweHeaderDslFunction(): TestResult =
        runTest {
            val header =
                jweHeader {
                    alg("ECDH-ES+A128KW")
                    enc("A128GCM")
                    kid("dsl-key")
                }

            assertEquals("ECDH-ES+A128KW", header.alg)
            assertEquals("A128GCM", header.enc)
            assertEquals("dsl-key", header.kid)
        }

    // ========== JweOptsBuilder Tests ==========

    @Test
    fun shouldBuildDefaultJweOpts(): TestResult =
        runTest {
            val opts = JweOptsBuilder().build()

            assertEquals(false, opts.compress)
            assertNull(opts.protectedHeaderOverrides)
            assertNull(opts.unprotectedHeader)
        }

    @Test
    fun shouldBuildJweOptsWithCompression(): TestResult =
        runTest {
            val opts =
                JweOptsBuilder()
                    .compress()
                    .build()

            assertTrue(opts.compress)
        }

    @Test
    fun shouldBuildJweOptsWithExplicitCompression(): TestResult =
        runTest {
            val optsEnabled =
                JweOptsBuilder()
                    .compress(true)
                    .build()
            assertTrue(optsEnabled.compress)

            val optsDisabled =
                JweOptsBuilder()
                    .compress(false)
                    .build()
            assertEquals(false, optsDisabled.compress)
        }

    @Test
    fun shouldBuildJweOptsWithProtectedHeaderObject(): TestResult =
        runTest {
            val header =
                jweHeader {
                    typ("JOSE")
                    cty("JWT")
                }

            val opts =
                JweOptsBuilder()
                    .protectedHeader(header)
                    .build()

            assertNotNull(opts.protectedHeaderOverrides)
            assertEquals("JOSE", opts.protectedHeaderOverrides!!.typ)
            assertEquals("JWT", opts.protectedHeaderOverrides!!.cty)
        }

    @Test
    fun shouldBuildJweOptsWithProtectedHeaderBuilder(): TestResult =
        runTest {
            val opts =
                JweOptsBuilder()
                    .protectedHeader {
                        typ("JOSE+JSON")
                    }.build()

            assertNotNull(opts.protectedHeaderOverrides)
            assertEquals("JOSE+JSON", opts.protectedHeaderOverrides!!.typ)
        }

    @Test
    fun shouldBuildJweOptsWithUnprotectedHeaderObject(): TestResult =
        runTest {
            val header =
                jweHeader {
                    kid("unprotected-key")
                }

            val opts =
                JweOptsBuilder()
                    .unprotectedHeader(header)
                    .build()

            assertNotNull(opts.unprotectedHeader)
            assertEquals("unprotected-key", opts.unprotectedHeader!!.kid)
        }

    @Test
    fun shouldBuildJweOptsWithUnprotectedHeaderBuilder(): TestResult =
        runTest {
            val opts =
                JweOptsBuilder()
                    .unprotectedHeader {
                        kid("builder-unprotected-key")
                    }.build()

            assertNotNull(opts.unprotectedHeader)
            assertEquals("builder-unprotected-key", opts.unprotectedHeader!!.kid)
        }

    @Test
    fun shouldUseJweOptionsDslFunction(): TestResult =
        runTest {
            val opts =
                jweOptions {
                    compress()
                    protectedHeader {
                        typ("JOSE")
                    }
                }

            assertTrue(opts.compress)
            assertNotNull(opts.protectedHeaderOverrides)
            assertEquals("JOSE", opts.protectedHeaderOverrides!!.typ)
        }

    // ========== PrepareJweArgsBuilder Tests ==========

    @Test
    fun shouldBuildPrepareJweArgsWithPlaintext(): TestResult =
        runTest {
            val plaintext = "Hello, World!".encodeToByteArray()

            val args =
                PrepareJweArgsBuilder()
                    .plaintext(plaintext)
                    .keyEncryptionAlg("RSA-OAEP")
                    .contentEncryptionAlg("A256GCM")
                    .build()

            assertNotNull(args.plaintext)
            assertEquals("Hello, World!", args.plaintext!!.decodeToString())
            assertEquals("RSA-OAEP", args.keyEncryptionAlg)
            assertEquals("A256GCM", args.contentEncryptionAlg)
        }

    @Test
    fun shouldBuildPrepareJweArgsWithPlaintextString(): TestResult =
        runTest {
            val args =
                PrepareJweArgsBuilder()
                    .plaintextString("String plaintext")
                    .keyEncryptionAlg("ECDH-ES+A256KW")
                    .contentEncryptionAlg("A128GCM")
                    .build()

            assertNotNull(args.plaintext)
            assertEquals("String plaintext", args.plaintext!!.decodeToString())
        }

    @Test
    fun shouldBuildPrepareJweArgsWithOptions(): TestResult =
        runTest {
            val args =
                PrepareJweArgsBuilder()
                    .plaintextString("test")
                    .keyEncryptionAlg("RSA-OAEP")
                    .contentEncryptionAlg("A256GCM")
                    .options {
                        compress()
                    }.build()

            assertTrue(args.opts.compress)
        }

    @Test
    fun shouldBuildPrepareJweArgsWithOptionsObject(): TestResult =
        runTest {
            val opts = CreateJweOpts(compress = true)

            val args =
                PrepareJweArgsBuilder()
                    .plaintextString("test")
                    .keyEncryptionAlg("A256KW")
                    .contentEncryptionAlg("A256GCM")
                    .options(opts)
                    .build()

            assertTrue(args.opts.compress)
        }

    @Test
    fun shouldFailBuildPrepareJweArgsWithoutKeyEncryptionAlg(): TestResult =
        runTest {
            val builder =
                PrepareJweArgsBuilder()
                    .plaintextString("test")
                    .contentEncryptionAlg("A256GCM")

            assertFailsWith<IllegalArgumentException> {
                builder.build()
            }
        }

    @Test
    fun shouldFailBuildPrepareJweArgsWithoutContentEncryptionAlg(): TestResult =
        runTest {
            val builder =
                PrepareJweArgsBuilder()
                    .plaintextString("test")
                    .keyEncryptionAlg("RSA-OAEP")

            assertFailsWith<IllegalArgumentException> {
                builder.build()
            }
        }

    @Test
    fun shouldUsePrepareJweArgsDslFunction(): TestResult =
        runTest {
            val args =
                prepareJweArgs {
                    plaintextString("DSL test")
                    keyEncryptionAlg("ECDH-ES")
                    contentEncryptionAlg("A256GCM")
                }

            assertEquals("DSL test", args.plaintext!!.decodeToString())
            assertEquals("ECDH-ES", args.keyEncryptionAlg)
            assertEquals("A256GCM", args.contentEncryptionAlg)
        }

    // ========== CreateJweCompactArgsBuilder Tests ==========

    @Test
    fun shouldBuildCreateJweCompactArgs(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("RSA-OAEP")
                            enc("A256GCM")
                        },
                    plaintext = "test".encodeToByteArray(),
                )

            val args =
                CreateJweCompactArgsBuilder()
                    .preparedJwe(preparedJwe)
                    .build()

            assertNotNull(args.preparedJwe)
            assertEquals("RSA-OAEP", args.preparedJwe!!.header.alg)
        }

    @Test
    fun shouldBuildCreateJweCompactArgsWithAad(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("A256KW")
                            enc("A128GCM")
                        },
                )
            val aad = "additional-data".encodeToByteArray()

            val args =
                CreateJweCompactArgsBuilder()
                    .preparedJwe(preparedJwe)
                    .aad(aad)
                    .build()

            assertNotNull(args.aad)
            assertEquals("additional-data", args.aad!!.decodeToString())
        }

    @Test
    fun shouldUseCreateJweCompactArgsDslFunction(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("dir")
                            enc("A256GCM")
                        },
                )

            val args =
                createJweCompactArgs {
                    preparedJwe(preparedJwe)
                }

            assertNotNull(args.preparedJwe)
        }

    // ========== CreateJweJsonArgsBuilder Tests ==========

    @Test
    fun shouldBuildCreateJweJsonArgs(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("ECDH-ES+A128KW")
                            enc("A128GCM")
                        },
                )

            val args =
                CreateJweJsonArgsBuilder()
                    .preparedJwe(preparedJwe)
                    .build()

            assertNotNull(args.preparedJwe)
        }

    @Test
    fun shouldBuildCreateJweJsonArgsWithAad(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("RSA-OAEP")
                            enc("A256GCM")
                        },
                )

            val args =
                CreateJweJsonArgsBuilder()
                    .preparedJwe(preparedJwe)
                    .aad("json-aad".encodeToByteArray())
                    .build()

            assertNotNull(args.aad)
        }

    @Test
    fun shouldUseCreateJweJsonArgsDslFunction(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("A256KW")
                            enc("A256GCM")
                        },
                )

            val args =
                createJweJsonArgs {
                    preparedJwe(preparedJwe)
                }

            assertNotNull(args.preparedJwe)
        }

    // ========== CreateJweJsonGeneralArgsBuilder Tests ==========

    @Test
    fun shouldBuildCreateJweJsonGeneralArgs(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("ECDH-ES+A256KW")
                            enc("A256GCM")
                        },
                )

            val args =
                CreateJweJsonGeneralArgsBuilder()
                    .preparedJwe(preparedJwe)
                    .build()

            assertNotNull(args.preparedJwe)
            assertNull(args.additionalRecipients)
        }

    @Test
    fun shouldBuildCreateJweJsonGeneralArgsWithRecipientInfo(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("RSA-OAEP")
                            enc("A256GCM")
                        },
                )
            val recipientInfo =
                JweRecipientInfo(
                    recipient = null,
                    perRecipientHeader = jweHeader { kid("recipient-2") },
                )

            val args =
                CreateJweJsonGeneralArgsBuilder()
                    .preparedJwe(preparedJwe)
                    .addRecipientInfo(recipientInfo)
                    .build()

            assertNotNull(args.additionalRecipients)
            assertEquals(1, args.additionalRecipients!!.size)
        }

    @Test
    fun shouldBuildCreateJweJsonGeneralArgsWithAad(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("A256KW")
                            enc("A128GCM")
                        },
                )

            val args =
                CreateJweJsonGeneralArgsBuilder()
                    .preparedJwe(preparedJwe)
                    .aad("general-aad".encodeToByteArray())
                    .build()

            assertNotNull(args.aad)
            assertEquals("general-aad", args.aad!!.decodeToString())
        }

    @Test
    fun shouldUseCreateJweJsonGeneralArgsDslFunction(): TestResult =
        runTest {
            val preparedJwe =
                PreparedJwe(
                    header =
                        jweHeader {
                            alg("ECDH-ES")
                            enc("A256GCM")
                        },
                )

            val args =
                createJweJsonGeneralArgs {
                    preparedJwe(preparedJwe)
                }

            assertNotNull(args.preparedJwe)
        }

    // ========== DecryptJweArgsBuilder Tests ==========

    @Test
    fun shouldBuildDecryptJweArgs(): TestResult =
        runTest {
            // Create a minimal valid JweCompact for testing
            val header =
                jweHeader {
                    alg("A256KW")
                    enc("A256GCM")
                }
            val jwe =
                JweCompact(
                    header = header,
                    encryptedKey = ByteArray(32),
                    iv = ByteArray(12),
                    ciphertext = ByteArray(16),
                    authTag = ByteArray(16),
                )

            val args =
                DecryptJweArgsBuilder()
                    .jwe(jwe)
                    .build()

            assertNotNull(args.jwe)
        }

    @Test
    fun shouldUseDecryptJweArgsDslFunction(): TestResult =
        runTest {
            val header =
                jweHeader {
                    alg("A128KW")
                    enc("A128GCM")
                }
            val jwe =
                JweCompact(
                    header = header,
                    encryptedKey = ByteArray(16),
                    iv = ByteArray(12),
                    ciphertext = ByteArray(8),
                    authTag = ByteArray(16),
                )

            val args =
                decryptJweArgs {
                    jwe(jwe)
                }

            assertNotNull(args.jwe)
        }

    // ========== Factory Method Tests ==========

    @Test
    fun shouldUseJweHeaderBuilderCreate(): TestResult =
        runTest {
            val builder = JweHeaderBuilder.create()
            val header = builder.alg("RSA-OAEP").enc("A256GCM").build()

            assertEquals("RSA-OAEP", header.alg)
        }

    @Test
    fun shouldUseJweOptsBuilderCreate(): TestResult =
        runTest {
            val builder = JweOptsBuilder.create()
            val opts = builder.compress().build()

            assertTrue(opts.compress)
        }

    @Test
    fun shouldUsePrepareJweArgsBuilderCreate(): TestResult =
        runTest {
            val builder = PrepareJweArgsBuilder.create()
            val args =
                builder
                    .keyEncryptionAlg("dir")
                    .contentEncryptionAlg("A256GCM")
                    .build()

            assertEquals("dir", args.keyEncryptionAlg)
        }

    @Test
    fun shouldUseCreateJweCompactArgsBuilderCreate(): TestResult =
        runTest {
            val builder = CreateJweCompactArgsBuilder.create()
            val args = builder.build()

            assertNull(args.preparedJwe)
        }

    @Test
    fun shouldUseCreateJweJsonArgsBuilderCreate(): TestResult =
        runTest {
            val builder = CreateJweJsonArgsBuilder.create()
            val args = builder.build()

            assertNull(args.preparedJwe)
        }

    @Test
    fun shouldUseCreateJweJsonGeneralArgsBuilderCreate(): TestResult =
        runTest {
            val builder = CreateJweJsonGeneralArgsBuilder.create()
            val args = builder.build()

            assertNull(args.preparedJwe)
        }

    @Test
    fun shouldUseDecryptJweArgsBuilderCreate(): TestResult =
        runTest {
            val builder = DecryptJweArgsBuilder.create()
            val args = builder.build()

            assertNull(args.jwe)
        }
}
