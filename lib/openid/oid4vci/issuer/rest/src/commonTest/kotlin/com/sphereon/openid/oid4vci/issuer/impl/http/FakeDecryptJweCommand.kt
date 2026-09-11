package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jwe.DecryptJweArgs
import com.sphereon.crypto.jose.jwe.DecryptJweCommand
import com.sphereon.crypto.jose.jwe.JweDecryptionResult

internal class FakeDecryptJweCommand : DecryptJweCommand {
    var decryptResult: IdkResult<JweDecryptionResult, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Not configured"))

    override val commandId: String get() = DecryptJweCommand.COMMAND_ID
    override val id: String get() = commandId
    override val isEnabled: Boolean get() = true
    override val inputTypeToken: TypeToken<DecryptJweArgs> = typeToken<DecryptJweArgs>()
    override val outputTypeToken: TypeToken<JweDecryptionResult> = typeToken<JweDecryptionResult>()

    override suspend fun execute(args: DecryptJweArgs): IdkResult<JweDecryptionResult, IdkError> = decryptResult
}
