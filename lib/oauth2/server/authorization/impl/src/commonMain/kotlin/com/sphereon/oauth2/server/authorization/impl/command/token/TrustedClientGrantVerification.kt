/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationCodeGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.command.VerifiedClientCredentialsGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedTokenExchangeGrant
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand

internal suspend fun VerifyClientCredentialsGrantCommand.executeWithTrustedClientAuthorization(
    args: VerifyClientCredentialsGrantArgs,
    clientAuthorization: VerifiedClientAuthorization?,
): IdkResult<VerifiedClientCredentialsGrant, IdkError> =
    if (clientAuthorization != null && this is VerifyClientCredentialsGrantCommandImpl) {
        verifyWithTrustedClientAuthorization(args, clientAuthorization)
    } else {
        execute(args)
    }

internal suspend fun VerifyTokenExchangeGrantCommand.executeWithTrustedClientAuthorization(
    args: VerifyTokenExchangeGrantArgs,
    clientAuthorization: VerifiedClientAuthorization?,
): IdkResult<VerifiedTokenExchangeGrant, IdkError> =
    if (clientAuthorization != null && this is VerifyTokenExchangeGrantCommandImpl) {
        verifyWithTrustedClientAuthorization(args, clientAuthorization)
    } else {
        execute(args)
    }

internal suspend fun VerifyAuthorizationCodeGrantCommand.executeWithTrustedClientAuthorization(
    args: VerifyAuthorizationCodeGrantArgs,
    clientAuthorization: VerifiedClientAuthorization?,
): IdkResult<VerifiedAuthorizationCodeGrant, IdkError> =
    if (clientAuthorization != null && this is VerifyAuthorizationCodeGrantCommandImpl) {
        verifyWithTrustedClientAuthorization(args, clientAuthorization)
    } else {
        execute(args)
    }
