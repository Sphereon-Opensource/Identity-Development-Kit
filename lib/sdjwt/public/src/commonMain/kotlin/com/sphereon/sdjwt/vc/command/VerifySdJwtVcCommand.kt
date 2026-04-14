/*
 * Copyright (c) 2026 Sphereon B.V.
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
package com.sphereon.sdjwt.vc.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.sdjwt.vc.SdJwtVcPresentationVerificationResult
import com.sphereon.sdjwt.vc.SdJwtVcVerificationResult
import com.sphereon.sdjwt.vc.VerifySdJwtVcArgs
import com.sphereon.sdjwt.vc.VerifySdJwtVcPresentationArgs

/**
 * Command for verifying SD-JWT-VC credentials
 */
@JsExportCompat
interface VerifySdJwtVcCommand : ServiceCommand<VerifySdJwtVcArgs, SdJwtVcVerificationResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "sdjwt.vc.verify"
    }
}

/**
 * Command for verifying SD-JWT-VC presentations (with KB-JWT)
 */
@JsExportCompat
interface VerifySdJwtVcPresentationCommand : ServiceCommand<VerifySdJwtVcPresentationArgs, SdJwtVcPresentationVerificationResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "sdjwt.presentation.verify"
    }
}
