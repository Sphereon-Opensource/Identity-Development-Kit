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

package com.sphereon.crypto.jose.jwe.command

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jwe.JweHeader
import com.sphereon.crypto.jose.jwe.PrepareJweArgs
import com.sphereon.crypto.jose.jwe.PrepareJweCommand
import com.sphereon.crypto.jose.jwe.PreparedJwe
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.random.Random

/**
 * Command implementation for preparing JWE objects
 *
 * This command prepares a JWE by:
 * 1. Generating a random Content Encryption Key (CEK) appropriate for the content encryption algorithm
 * 2. Constructing the JWE Protected Header with alg, enc, and other parameters
 * 3. Optionally compressing the plaintext if compression is requested (DEF)
 * 4. Returning a PreparedJwe object ready for encryption
 *
 * Note: This implementation generates the CEK but does not perform encryption.
 * The actual encryption is performed by CreateJwe*Command implementations.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrepareJweCommandImpl", exact = true)
class PrepareJweCommandImpl(
    execution: SessionExecution,
    private val identifierService: MultiManagedIdentifierService,
) : TypedServiceCommandAdapter<PrepareJweArgs, PreparedJwe>(
        commandId = PrepareJweCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<PrepareJweArgs>(),
        outputTypeToken = typeToken<PreparedJwe>(),
    ),
    PrepareJweCommand {
    override val commandId: String get() = PrepareJweCommand.COMMAND_ID

    override suspend fun doExecute(
        args: PrepareJweArgs,
        applyDuring: (PrepareJweArgs) -> PrepareJweArgs,
    ): IdkResult<PreparedJwe, IdkError> {
        val appliedArgs = applyDuring(args)

        // Validate required fields
        val plaintext = appliedArgs.plaintext ?: return IdkResult.err(IdkError.fromString("Plaintext is required"))
        val recipient = appliedArgs.recipient ?: return IdkResult.err(IdkError.fromString("Recipient is required"))

        // Resolve the recipient identifier to get public key
        val identifierResult = identifierService.resolve(recipient)
        if (!identifierResult.isOk) {
            return IdkResult.err(IdkError.fromDTO(identifierResult.error))
        }

        val resolvedRecipient = identifierResult.value

        // Generate or extract Content Encryption Key (CEK)
        // For "dir" (direct encryption), the CEK is the shared symmetric key itself
        // For other algorithms, generate a random CEK
        val cek =
            if (appliedArgs.keyEncryptionAlg == "dir") {
                extractCekForDirMode(resolvedRecipient.asResult().keyInfo, appliedArgs.contentEncryptionAlg)
                    .getOrElse { error -> return IdkResult.err(error) }
            } else {
                generateCEK(appliedArgs.contentEncryptionAlg)
            }

        // Construct JWE header
        val header = JweHeader()
        header.alg = appliedArgs.keyEncryptionAlg
        header.enc = appliedArgs.contentEncryptionAlg

        // Add compression if requested
        if (appliedArgs.opts.compress) {
            header.zip = "DEF"
        }

        // Apply any header overrides from opts
        appliedArgs.opts.protectedHeaderOverrides?.let { overrides ->
            // Merge override headers (but don't override alg and enc which are required)
            overrides.underlying.forEach { (key, value) ->
                if (key != "alg" && key != "enc") {
                    header.put(key, value)
                }
            }
        }

        // TODO: Add key identifier (kid) from recipient if available
        // TODO: Add ephemeral public key (epk) for ECDH-ES algorithms
        // TODO: Add other algorithm-specific parameters (apu, apv, p2s, p2c, etc.)

        val preparedJwe =
            PreparedJwe(
                header = header,
                plaintext = plaintext,
                cek = cek,
                recipient = resolvedRecipient,
            )

        return preparedJwe.asOkResult()
    }

    /**
     * Generate a random Content Encryption Key (CEK) appropriate for the content encryption algorithm
     *
     * @param contentEncryptionAlg The content encryption algorithm (e.g., "A256GCM", "A128CBC-HS256")
     * @return Random CEK bytes of appropriate length
     */
    @Suppress("MagicNumber")
    private fun generateCEK(contentEncryptionAlg: String): ByteArray {
        val keySize =
            when (contentEncryptionAlg) {
                "A128GCM" -> 16

                // 128 bits
                "A192GCM" -> 24

                // 192 bits
                "A256GCM" -> 32

                // 256 bits
                "A128CBC-HS256" -> 32

                // 256 bits (128 for encryption + 128 for MAC)
                "A192CBC-HS384" -> 48

                // 384 bits (192 for encryption + 192 for MAC)
                "A256CBC-HS512" -> 64

                // 512 bits (256 for encryption + 256 for MAC)
                else -> throw IllegalArgumentException("Unsupported content encryption algorithm: $contentEncryptionAlg")
            }

        return Random.Default.nextBytes(keySize)
    }

    /**
     * Extracts the symmetric key from KeyInfo for direct encryption (alg="dir").
     *
     * For direct encryption, the shared symmetric key is used directly as the CEK.
     * The key must be a symmetric key (kty=oct) with the correct size for the
     * content encryption algorithm.
     *
     * @param keyInfo The recipient's KeyInfo containing the symmetric key
     * @param contentEncryptionAlg The content encryption algorithm (e.g., "A256GCM")
     * @return The symmetric key bytes to use as CEK
     */
    @Suppress("MagicNumber")
    private fun extractCekForDirMode(
        keyInfo: KeyInfoType<*>,
        contentEncryptionAlg: String,
    ): IdkResult<ByteArray, IdkError> {
        // Get the key from KeyInfo
        val key =
            keyInfo.key
                ?: return IdkResult.err(IdkError.fromString("Recipient key is required for direct encryption"))

        // Must be a JWK
        val jwk =
            key as? Jwk
                ?: return IdkResult.err(IdkError.fromString("Recipient key must be a JWK for direct encryption"))

        // Must be a symmetric key (oct)
        if (jwk.kty != JwaKeyType.oct) {
            return IdkResult.err(
                IdkError.fromString(
                    "Direct encryption requires a symmetric key (kty=oct), got: ${jwk.kty}",
                ),
            )
        }

        // Extract the key material
        val kParameter =
            jwk.k
                ?: return IdkResult.err(
                    IdkError.fromString(
                        "Symmetric key must have 'k' parameter for direct encryption",
                    ),
                )

        val keyBytes = kParameter.decodeFrom(Encoding.BASE64URL)

        // Validate key size matches content encryption algorithm
        val expectedKeySize =
            when (contentEncryptionAlg) {
                "A128GCM" -> 16

                // 128 bits
                "A192GCM" -> 24

                // 192 bits
                "A256GCM" -> 32

                // 256 bits
                "A128CBC-HS256" -> 32

                // 256 bits (128 for encryption + 128 for MAC)
                "A192CBC-HS384" -> 48

                // 384 bits (192 for encryption + 192 for MAC)
                "A256CBC-HS512" -> 64

                // 512 bits (256 for encryption + 256 for MAC)
                else -> return IdkResult.err(
                    IdkError.fromString(
                        "Unsupported content encryption algorithm: $contentEncryptionAlg",
                    ),
                )
            }

        if (keyBytes.size != expectedKeySize) {
            return IdkResult.err(
                IdkError.fromString(
                    "Symmetric key size ${keyBytes.size} does not match expected size $expectedKeySize for $contentEncryptionAlg",
                ),
            )
        }

        return IdkResult.ok(keyBytes)
    }
}
