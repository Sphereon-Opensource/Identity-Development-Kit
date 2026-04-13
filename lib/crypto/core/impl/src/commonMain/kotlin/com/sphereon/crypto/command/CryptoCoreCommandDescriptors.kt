package com.sphereon.crypto.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.crypto.core.kms.command.CreateRawSignatureCommand
import com.sphereon.crypto.core.kms.command.DecryptCommand
import com.sphereon.crypto.core.kms.command.DeleteKeyCommand
import com.sphereon.crypto.core.kms.command.EncryptCommand
import com.sphereon.crypto.core.kms.command.GenerateKeyCommand
import com.sphereon.crypto.core.kms.command.GetKeyCommand
import com.sphereon.crypto.core.kms.command.ListKeysCommand
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementCommand
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyCommand
import com.sphereon.crypto.core.kms.command.StoreKeyCommand
import com.sphereon.crypto.core.kms.command.UnwrapKeyCommand
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureCommand
import com.sphereon.crypto.core.kms.command.WrapKeyCommand
import com.sphereon.crypto.jose.jwe.CreateJweCompactCommand
import com.sphereon.crypto.jose.jwe.command.CreateJweCompactCommandImpl
import com.sphereon.crypto.jose.jwe.CreateJweJsonFlattenedCommand
import com.sphereon.crypto.jose.jwe.command.CreateJweJsonFlattenedCommandImpl
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralCommand
import com.sphereon.crypto.jose.jwe.command.CreateJweJsonGeneralCommandImpl
import com.sphereon.crypto.jose.jwe.DecryptJweCommand
import com.sphereon.crypto.jose.jwe.command.DecryptJweCommandImpl
import com.sphereon.crypto.jose.jwe.PrepareJweCommand
import com.sphereon.crypto.jose.jwe.command.PrepareJweCommandImpl
import com.sphereon.crypto.jose.jws.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommandImpl
import com.sphereon.crypto.jose.jws.CreateJwsJsonFlattenedCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonFlattenedCommandImpl
import com.sphereon.crypto.jose.jws.CreateJwsJsonGeneralCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonGeneralCommandImpl
import com.sphereon.crypto.jose.jws.PrepareJwsCommand
import com.sphereon.crypto.jose.jws.command.PrepareJwsCommandImpl
import com.sphereon.crypto.jose.jws.VerifyJwsCommand
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommandImpl
import com.sphereon.crypto.kms.command.CreateRawSignatureCommandImpl
import com.sphereon.crypto.kms.command.DecryptCommandImpl
import com.sphereon.crypto.kms.command.DeleteKeyCommandImpl
import com.sphereon.crypto.kms.command.EncryptCommandImpl
import com.sphereon.crypto.kms.command.GenerateKeyCommandImpl
import com.sphereon.crypto.kms.command.GetKeyCommandImpl
import com.sphereon.crypto.kms.command.ListKeysCommandImpl
import com.sphereon.crypto.kms.command.PerformKeyAgreementCommandImpl
import com.sphereon.crypto.kms.command.ResolvePublicKeyCommandImpl
import com.sphereon.crypto.kms.command.StoreKeyCommandImpl
import com.sphereon.crypto.kms.command.UnwrapKeyCommandImpl
import com.sphereon.crypto.kms.command.VerifyRawSignatureCommandImpl
import com.sphereon.crypto.kms.command.WrapKeyCommandImpl
import com.sphereon.crypto.core.kms.command.GenerateMacCommand
import com.sphereon.crypto.core.kms.command.VerifyMacCommand
import com.sphereon.crypto.kms.command.GenerateMacCommandImpl
import com.sphereon.crypto.kms.command.VerifyMacCommandImpl
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface CryptoCoreCommandDescriptors {

    // Signature commands
    @Provides @IntoSet
    fun createRawSignature(impl: Lazy<CreateRawSignatureCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateRawSignatureCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyRawSignature(impl: Lazy<VerifyRawSignatureCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyRawSignatureCommand.COMMAND_ID) { impl.value }

    // Key resolution commands
    @Provides @IntoSet
    fun resolvePublicKey(impl: Lazy<ResolvePublicKeyCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ResolvePublicKeyCommand.COMMAND_ID) { impl.value }

    // Key management commands
    @Provides @IntoSet
    fun generateKey(impl: Lazy<GenerateKeyCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GenerateKeyCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun listKeys(impl: Lazy<ListKeysCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ListKeysCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun getKey(impl: Lazy<GetKeyCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GetKeyCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun storeKey(impl: Lazy<StoreKeyCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(StoreKeyCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun deleteKey(impl: Lazy<DeleteKeyCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(DeleteKeyCommand.COMMAND_ID) { impl.value }

    // Encryption commands
    @Provides @IntoSet
    fun encrypt(impl: Lazy<EncryptCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(EncryptCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun decrypt(impl: Lazy<DecryptCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(DecryptCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun wrapKey(impl: Lazy<WrapKeyCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(WrapKeyCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun unwrapKey(impl: Lazy<UnwrapKeyCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(UnwrapKeyCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun performKeyAgreement(impl: Lazy<PerformKeyAgreementCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(PerformKeyAgreementCommand.COMMAND_ID) { impl.value }

    // JWE commands
    @Provides @IntoSet
    fun prepareJwe(impl: Lazy<PrepareJweCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(PrepareJweCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun decryptJwe(impl: Lazy<DecryptJweCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(DecryptJweCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createJweJsonGeneral(impl: Lazy<CreateJweJsonGeneralCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateJweJsonGeneralCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createJweJsonFlattened(impl: Lazy<CreateJweJsonFlattenedCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateJweJsonFlattenedCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createJweCompact(impl: Lazy<CreateJweCompactCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateJweCompactCommand.COMMAND_ID) { impl.value }

    // JWS commands
    @Provides @IntoSet
    fun verifyJws(impl: Lazy<VerifyJwsCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyJwsCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createJwsJsonGeneral(impl: Lazy<CreateJwsJsonGeneralCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateJwsJsonGeneralCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun prepareJws(impl: Lazy<PrepareJwsCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(PrepareJwsCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createJwsJsonFlattened(impl: Lazy<CreateJwsJsonFlattenedCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateJwsJsonFlattenedCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createJwsCompact(impl: Lazy<CreateJwsCompactCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateJwsCompactCommand.COMMAND_ID) { impl.value }

    // MAC commands
    @Provides @IntoSet
    fun generateMac(impl: Lazy<GenerateMacCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GenerateMacCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyMac(impl: Lazy<VerifyMacCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyMacCommand.COMMAND_ID) { impl.value }
}
