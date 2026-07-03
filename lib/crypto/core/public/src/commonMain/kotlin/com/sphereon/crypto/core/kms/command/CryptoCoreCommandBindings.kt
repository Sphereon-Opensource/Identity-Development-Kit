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

package com.sphereon.crypto.core.kms.command
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.jose.jwe.CreateJweCompactCommand
import com.sphereon.crypto.jose.jwe.CreateJweJsonFlattenedCommand
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralCommand
import com.sphereon.crypto.jose.jwe.DecryptJweCommand
import com.sphereon.crypto.jose.jwe.PrepareJweCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonFlattenedCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonGeneralCommand
import com.sphereon.crypto.jose.jws.command.PrepareJwsCommand
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(SessionScope::class)
@JsExportCompat
interface CryptoCoreCommandBindings {
    // JWS commands
    @Provides
    fun prepareJws(registry: SessionScopedCommandRegistry): PrepareJwsCommand =
        registry.get(PrepareJwsCommand.COMMAND_ID) as? PrepareJwsCommand
            ?: error("No binding for ${PrepareJwsCommand.COMMAND_ID}")

    @Provides
    fun createJwsCompact(registry: SessionScopedCommandRegistry): CreateJwsCompactCommand =
        registry.get(CreateJwsCompactCommand.COMMAND_ID) as? CreateJwsCompactCommand
            ?: error("No binding for ${CreateJwsCompactCommand.COMMAND_ID}")

    @Provides
    fun createJwsJsonFlattened(registry: SessionScopedCommandRegistry): CreateJwsJsonFlattenedCommand =
        registry.get(CreateJwsJsonFlattenedCommand.COMMAND_ID) as? CreateJwsJsonFlattenedCommand
            ?: error("No binding for ${CreateJwsJsonFlattenedCommand.COMMAND_ID}")

    @Provides
    fun createJwsJsonGeneral(registry: SessionScopedCommandRegistry): CreateJwsJsonGeneralCommand =
        registry.get(CreateJwsJsonGeneralCommand.COMMAND_ID) as? CreateJwsJsonGeneralCommand
            ?: error("No binding for ${CreateJwsJsonGeneralCommand.COMMAND_ID}")

    @Provides
    fun verifyJws(registry: SessionScopedCommandRegistry): VerifyJwsCommand =
        registry.get(VerifyJwsCommand.COMMAND_ID) as? VerifyJwsCommand
            ?: error("No binding for ${VerifyJwsCommand.COMMAND_ID}")

    // JWE commands
    @Provides
    fun prepareJwe(registry: SessionScopedCommandRegistry): PrepareJweCommand =
        registry.get(PrepareJweCommand.COMMAND_ID) as? PrepareJweCommand
            ?: error("No binding for ${PrepareJweCommand.COMMAND_ID}")

    @Provides
    fun createJweCompact(registry: SessionScopedCommandRegistry): CreateJweCompactCommand =
        registry.get(CreateJweCompactCommand.COMMAND_ID) as? CreateJweCompactCommand
            ?: error("No binding for ${CreateJweCompactCommand.COMMAND_ID}")

    @Provides
    fun createJweJsonFlattened(registry: SessionScopedCommandRegistry): CreateJweJsonFlattenedCommand =
        registry.get(CreateJweJsonFlattenedCommand.COMMAND_ID) as? CreateJweJsonFlattenedCommand
            ?: error("No binding for ${CreateJweJsonFlattenedCommand.COMMAND_ID}")

    @Provides
    fun createJweJsonGeneral(registry: SessionScopedCommandRegistry): CreateJweJsonGeneralCommand =
        registry.get(CreateJweJsonGeneralCommand.COMMAND_ID) as? CreateJweJsonGeneralCommand
            ?: error("No binding for ${CreateJweJsonGeneralCommand.COMMAND_ID}")

    @Provides
    fun decryptJwe(registry: SessionScopedCommandRegistry): DecryptJweCommand =
        registry.get(DecryptJweCommand.COMMAND_ID) as? DecryptJweCommand
            ?: error("No binding for ${DecryptJweCommand.COMMAND_ID}")

    // Key management commands
    @Provides
    fun generateKey(registry: SessionScopedCommandRegistry): GenerateKeyCommand =
        registry.get(GenerateKeyCommand.COMMAND_ID) as? GenerateKeyCommand
            ?: error("No binding for ${GenerateKeyCommand.COMMAND_ID}")

    @Provides
    fun listKeys(registry: SessionScopedCommandRegistry): ListKeysCommand =
        registry.get(ListKeysCommand.COMMAND_ID) as? ListKeysCommand
            ?: error("No binding for ${ListKeysCommand.COMMAND_ID}")

    @Provides
    fun getKey(registry: SessionScopedCommandRegistry): GetKeyCommand =
        registry.get(GetKeyCommand.COMMAND_ID) as? GetKeyCommand
            ?: error("No binding for ${GetKeyCommand.COMMAND_ID}")

    @Provides
    fun storeKey(registry: SessionScopedCommandRegistry): StoreKeyCommand =
        registry.get(StoreKeyCommand.COMMAND_ID) as? StoreKeyCommand
            ?: error("No binding for ${StoreKeyCommand.COMMAND_ID}")

    @Provides
    fun deleteKey(registry: SessionScopedCommandRegistry): DeleteKeyCommand =
        registry.get(DeleteKeyCommand.COMMAND_ID) as? DeleteKeyCommand
            ?: error("No binding for ${DeleteKeyCommand.COMMAND_ID}")

    // Signature commands
    @Provides
    fun createRawSignature(registry: SessionScopedCommandRegistry): CreateRawSignatureCommand =
        registry.get(CreateRawSignatureCommand.COMMAND_ID) as? CreateRawSignatureCommand
            ?: error("No binding for ${CreateRawSignatureCommand.COMMAND_ID}")

    @Provides
    fun verifyRawSignature(registry: SessionScopedCommandRegistry): VerifyRawSignatureCommand =
        registry.get(VerifyRawSignatureCommand.COMMAND_ID) as? VerifyRawSignatureCommand
            ?: error("No binding for ${VerifyRawSignatureCommand.COMMAND_ID}")

    @Provides
    fun signDigest(registry: SessionScopedCommandRegistry): SignDigestCommand =
        registry.get(SignDigestCommand.COMMAND_ID) as? SignDigestCommand
            ?: error("No binding for ${SignDigestCommand.COMMAND_ID}")

    @Provides
    fun verifyDigest(registry: SessionScopedCommandRegistry): VerifyDigestCommand =
        registry.get(VerifyDigestCommand.COMMAND_ID) as? VerifyDigestCommand
            ?: error("No binding for ${VerifyDigestCommand.COMMAND_ID}")

    // Encryption commands
    @Provides
    fun encrypt(registry: SessionScopedCommandRegistry): EncryptCommand =
        registry.get(EncryptCommand.COMMAND_ID) as? EncryptCommand
            ?: error("No binding for ${EncryptCommand.COMMAND_ID}")

    @Provides
    fun decrypt(registry: SessionScopedCommandRegistry): DecryptCommand =
        registry.get(DecryptCommand.COMMAND_ID) as? DecryptCommand
            ?: error("No binding for ${DecryptCommand.COMMAND_ID}")

    @Provides
    fun wrapKey(registry: SessionScopedCommandRegistry): WrapKeyCommand =
        registry.get(WrapKeyCommand.COMMAND_ID) as? WrapKeyCommand
            ?: error("No binding for ${WrapKeyCommand.COMMAND_ID}")

    @Provides
    fun unwrapKey(registry: SessionScopedCommandRegistry): UnwrapKeyCommand =
        registry.get(UnwrapKeyCommand.COMMAND_ID) as? UnwrapKeyCommand
            ?: error("No binding for ${UnwrapKeyCommand.COMMAND_ID}")

    @Provides
    fun performKeyAgreement(registry: SessionScopedCommandRegistry): PerformKeyAgreementCommand =
        registry.get(PerformKeyAgreementCommand.COMMAND_ID) as? PerformKeyAgreementCommand
            ?: error("No binding for ${PerformKeyAgreementCommand.COMMAND_ID}")

    @Provides
    fun ecdhDerive(registry: SessionScopedCommandRegistry): EcdhDeriveCommand =
        registry.get(EcdhDeriveCommand.COMMAND_ID) as? EcdhDeriveCommand
            ?: error("No binding for ${EcdhDeriveCommand.COMMAND_ID}")

    @Provides
    fun ecPointMultiply(registry: SessionScopedCommandRegistry): EcPointMultiplyCommand =
        registry.get(EcPointMultiplyCommand.COMMAND_ID) as? EcPointMultiplyCommand
            ?: error("No binding for ${EcPointMultiplyCommand.COMMAND_ID}")

    // Key resolution commands
    @Provides
    fun resolvePublicKey(registry: SessionScopedCommandRegistry): ResolvePublicKeyCommand =
        registry.get(ResolvePublicKeyCommand.COMMAND_ID) as? ResolvePublicKeyCommand
            ?: error("No binding for ${ResolvePublicKeyCommand.COMMAND_ID}")

    // MAC commands
    @Provides
    fun generateMac(registry: SessionScopedCommandRegistry): GenerateMacCommand =
        registry.get(GenerateMacCommand.COMMAND_ID) as? GenerateMacCommand
            ?: error("No binding for ${GenerateMacCommand.COMMAND_ID}")

    @Provides
    fun verifyMac(registry: SessionScopedCommandRegistry): VerifyMacCommand =
        registry.get(VerifyMacCommand.COMMAND_ID) as? VerifyMacCommand
            ?: error("No binding for ${VerifyMacCommand.COMMAND_ID}")
}
