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

package com.sphereon.oauth2.client.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.EmptyResult
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.common.model.PkceMethod
import kotlin.jvm.JvmOverloads

/**
 * Arguments for creating a PKCE challenge/verifier pair
 *
 * @property codeVerifier Optional code verifier; if not provided, one will be generated
 * @property allowedMethods Allowed PKCE methods (defaults to S256 and PLAIN)
 */
@JsExportCompat
data class CreatePkceArgs
    @JvmOverloads
    constructor(
        val codeVerifier: String? = null,
        val allowedMethods: List<PkceMethod> = listOf(PkceMethod.S256, PkceMethod.PLAIN),
    )

/**
 * Arguments for verifying a PKCE challenge/verifier pair
 *
 * @property codeVerifier The code verifier
 * @property codeChallenge The code challenge to verify against
 * @property method The PKCE method used
 */
@JsExportCompat
data class VerifyPkceArgs(
    val codeVerifier: String,
    val codeChallenge: String,
    val method: PkceMethod,
)

/**
 * Command for creating PKCE challenge/verifier pairs (RFC 7636)
 */
@JsExportCompat
interface CreatePkceCommand : ServiceCommand<CreatePkceArgs, PkceData, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.pkce.create"
    }
}

/**
 * Command for verifying PKCE challenge/verifier pairs (RFC 7636)
 */
@JsExportCompat
interface VerifyPkceCommand : ServiceCommand<VerifyPkceArgs, EmptyResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.pkce.verify"
    }
}
