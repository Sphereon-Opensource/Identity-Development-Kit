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

package com.sphereon.crypto.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.kms.command.CreateRawSignatureCommand
import com.sphereon.crypto.core.kms.command.DecryptCommand
import com.sphereon.crypto.core.kms.command.DeleteKeyCommand
import com.sphereon.crypto.core.kms.command.EncryptCommand
import com.sphereon.crypto.core.kms.command.GenerateKeyCommand
import com.sphereon.crypto.core.kms.command.GenerateMacCommand
import com.sphereon.crypto.core.kms.command.GetKeyCommand
import com.sphereon.crypto.core.kms.command.ListKeysCommand
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementCommand
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyCommand
import com.sphereon.crypto.core.kms.command.StoreKeyCommand
import com.sphereon.crypto.core.kms.command.UnwrapKeyCommand
import com.sphereon.crypto.core.kms.command.VerifyMacCommand
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureCommand
import com.sphereon.crypto.core.kms.command.WrapKeyCommand
import com.sphereon.crypto.jose.jwe.CreateJweCompactCommand
import com.sphereon.crypto.jose.jwe.CreateJweJsonFlattenedCommand
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralCommand
import com.sphereon.crypto.jose.jwe.DecryptJweCommand
import com.sphereon.crypto.jose.jwe.PrepareJweCommand
import com.sphereon.crypto.jose.jwe.command.CreateJweCompactCommandImpl
import com.sphereon.crypto.jose.jwe.command.CreateJweJsonFlattenedCommandImpl
import com.sphereon.crypto.jose.jwe.command.CreateJweJsonGeneralCommandImpl
import com.sphereon.crypto.jose.jwe.command.DecryptJweCommandImpl
import com.sphereon.crypto.jose.jwe.command.PrepareJweCommandImpl
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommandImpl
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonFlattenedCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonFlattenedCommandImpl
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonGeneralCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonGeneralCommandImpl
import com.sphereon.crypto.jose.jws.command.PrepareJwsCommand
import com.sphereon.crypto.jose.jws.command.PrepareJwsCommandImpl
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommandImpl
import com.sphereon.crypto.kms.command.CreateRawSignatureCommandImpl
import com.sphereon.crypto.kms.command.DecryptCommandImpl
import com.sphereon.crypto.kms.command.DeleteKeyCommandImpl
import com.sphereon.crypto.kms.command.EncryptCommandImpl
import com.sphereon.crypto.kms.command.GenerateKeyCommandImpl
import com.sphereon.crypto.kms.command.GenerateMacCommandImpl
import com.sphereon.crypto.kms.command.GetKeyCommandImpl
import com.sphereon.crypto.kms.command.ListKeysCommandImpl
import com.sphereon.crypto.kms.command.PerformKeyAgreementCommandImpl
import com.sphereon.crypto.kms.command.ResolvePublicKeyCommandImpl
import com.sphereon.crypto.kms.command.StoreKeyCommandImpl
import com.sphereon.crypto.kms.command.UnwrapKeyCommandImpl
import com.sphereon.crypto.kms.command.VerifyMacCommandImpl
import com.sphereon.crypto.kms.command.VerifyRawSignatureCommandImpl
import com.sphereon.crypto.kms.command.WrapKeyCommandImpl
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface CryptoCoreCommandDescriptors {
    // Signature commands
    @Provides @IntoMap
    @StringKey(CreateRawSignatureCommand.COMMAND_ID)
    fun createRawSignature(impl: CreateRawSignatureCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyRawSignatureCommand.COMMAND_ID)
    fun verifyRawSignature(impl: VerifyRawSignatureCommandImpl): ServiceCommand<*, *> = impl

    // Key resolution commands
    @Provides @IntoMap
    @StringKey(ResolvePublicKeyCommand.COMMAND_ID)
    fun resolvePublicKey(impl: ResolvePublicKeyCommandImpl): ServiceCommand<*, *> = impl

    // Key management commands
    @Provides @IntoMap
    @StringKey(GenerateKeyCommand.COMMAND_ID)
    fun generateKey(impl: GenerateKeyCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ListKeysCommand.COMMAND_ID)
    fun listKeys(impl: ListKeysCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(GetKeyCommand.COMMAND_ID)
    fun getKey(impl: GetKeyCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(StoreKeyCommand.COMMAND_ID)
    fun storeKey(impl: StoreKeyCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteKeyCommand.COMMAND_ID)
    fun deleteKey(impl: DeleteKeyCommandImpl): ServiceCommand<*, *> = impl

    // Encryption commands
    @Provides @IntoMap
    @StringKey(EncryptCommand.COMMAND_ID)
    fun encrypt(impl: EncryptCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(DecryptCommand.COMMAND_ID)
    fun decrypt(impl: DecryptCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(WrapKeyCommand.COMMAND_ID)
    fun wrapKey(impl: WrapKeyCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(UnwrapKeyCommand.COMMAND_ID)
    fun unwrapKey(impl: UnwrapKeyCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(PerformKeyAgreementCommand.COMMAND_ID)
    fun performKeyAgreement(impl: PerformKeyAgreementCommandImpl): ServiceCommand<*, *> = impl

    // JWE commands
    @Provides @IntoMap
    @StringKey(PrepareJweCommand.COMMAND_ID)
    fun prepareJwe(impl: PrepareJweCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(DecryptJweCommand.COMMAND_ID)
    fun decryptJwe(impl: DecryptJweCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateJweJsonGeneralCommand.COMMAND_ID)
    fun createJweJsonGeneral(impl: CreateJweJsonGeneralCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateJweJsonFlattenedCommand.COMMAND_ID)
    fun createJweJsonFlattened(impl: CreateJweJsonFlattenedCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateJweCompactCommand.COMMAND_ID)
    fun createJweCompact(impl: CreateJweCompactCommandImpl): ServiceCommand<*, *> = impl

    // JWS commands
    @Provides @IntoMap
    @StringKey(VerifyJwsCommand.COMMAND_ID)
    fun verifyJws(impl: VerifyJwsCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateJwsJsonGeneralCommand.COMMAND_ID)
    fun createJwsJsonGeneral(impl: CreateJwsJsonGeneralCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(PrepareJwsCommand.COMMAND_ID)
    fun prepareJws(impl: PrepareJwsCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateJwsJsonFlattenedCommand.COMMAND_ID)
    fun createJwsJsonFlattened(impl: CreateJwsJsonFlattenedCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateJwsCompactCommand.COMMAND_ID)
    fun createJwsCompact(impl: CreateJwsCompactCommandImpl): ServiceCommand<*, *> = impl

    // MAC commands
    @Provides @IntoMap
    @StringKey(GenerateMacCommand.COMMAND_ID)
    fun generateMac(impl: GenerateMacCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyMacCommand.COMMAND_ID)
    fun verifyMac(impl: VerifyMacCommandImpl): ServiceCommand<*, *> = impl
}
