package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationCommand
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceArgs
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceCommand

internal class FakeBuildIssuerMetadataCommand : BuildIssuerMetadataCommand {
    var result: IdkResult<CredentialIssuerMetadata, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: BuildIssuerMetadataArgs? = null

    override val commandId: String get() = BuildIssuerMetadataCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<BuildIssuerMetadataArgs> = typeToken()
    override val outputTypeToken: TypeToken<CredentialIssuerMetadata> = typeToken()

    override suspend fun execute(args: BuildIssuerMetadataArgs): IdkResult<CredentialIssuerMetadata, IdkError> {
        lastArgs = args
        return result
    }
}

internal class FakeBuildSignedIssuerMetadataCommand : BuildSignedIssuerMetadataCommand {
    var result: IdkResult<JwtCompactResult, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: BuildSignedIssuerMetadataArgs? = null

    override val commandId: String get() = BuildSignedIssuerMetadataCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<BuildSignedIssuerMetadataArgs> = typeToken()
    override val outputTypeToken: TypeToken<JwtCompactResult> = typeToken()

    override suspend fun execute(args: BuildSignedIssuerMetadataArgs): IdkResult<JwtCompactResult, IdkError> {
        lastArgs = args
        return result
    }
}

internal class FakeIssueNonceCommand : IssueNonceCommand {
    var result: IdkResult<NonceResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))

    override val commandId: String get() = IssueNonceCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<IssueNonceArgs> = typeToken()
    override val outputTypeToken: TypeToken<NonceResponse> = typeToken()

    override suspend fun execute(args: IssueNonceArgs): IdkResult<NonceResponse, IdkError> = result
}

internal class FakeHandleCredentialRequestCommand : HandleCredentialRequestCommand {
    var result: IdkResult<CredentialResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: HandleCredentialRequestArgs? = null

    override val commandId: String get() = HandleCredentialRequestCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<HandleCredentialRequestArgs> = typeToken()
    override val outputTypeToken: TypeToken<CredentialResponse> = typeToken()

    override suspend fun execute(args: HandleCredentialRequestArgs): IdkResult<CredentialResponse, IdkError> {
        lastArgs = args
        return result
    }
}

internal class FakeHandleDeferredCredentialRequestCommand : HandleDeferredCredentialRequestCommand {
    var result: IdkResult<CredentialResponse, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: HandleDeferredCredentialRequestArgs? = null

    override val commandId: String get() = HandleDeferredCredentialRequestCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<HandleDeferredCredentialRequestArgs> = typeToken()
    override val outputTypeToken: TypeToken<CredentialResponse> = typeToken()

    override suspend fun execute(args: HandleDeferredCredentialRequestArgs): IdkResult<CredentialResponse, IdkError> {
        lastArgs = args
        return result
    }
}

internal class FakeHandleNotificationCommand : HandleNotificationCommand {
    var result: IdkResult<Unit, IdkError> = Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))
    var lastArgs: HandleNotificationArgs? = null

    override val commandId: String get() = HandleNotificationCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<HandleNotificationArgs> = typeToken()
    override val outputTypeToken: TypeToken<Unit> = typeToken()

    override suspend fun execute(args: HandleNotificationArgs): IdkResult<Unit, IdkError> {
        lastArgs = args
        return result
    }
}
