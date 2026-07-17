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

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.sdjwt.SdJwt
import com.sphereon.sdjwt.SdJwtPayload
import com.sphereon.sdjwt.vc.SdJwtVcVerificationResult
import com.sphereon.sdjwt.vc.VerifySdJwtVcArgs
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import kotlinx.serialization.json.JsonObject

/**
 * Test double for the SD-JWT VC verification command the OID4VCI receiver runs on receipt.
 *
 * The receiver calls this for every issued SD-JWT before storing it; the real command resolves
 * the issuer key from the JWS header `kid` (did:jwk) and checks the signature. [accept] toggles
 * between the success and rejection outcomes so tests can assert that the receiver stores a
 * credential when verification passes and refuses one when it fails.
 */
class FakeVerifySdJwtVcCommand(
    private val accept: Boolean = true,
) : VerifySdJwtVcCommand {
    var lastVerified: String? = null
        private set

    override val inputTypeToken get() = typeToken<VerifySdJwtVcArgs>()
    override val outputTypeToken get() = typeToken<SdJwtVcVerificationResult>()
    override val isEnabled: Boolean get() = true

    override suspend fun execute(args: VerifySdJwtVcArgs): IdkResult<SdJwtVcVerificationResult, IdkError> {
        lastVerified = args.sdJwt
        return if (accept) {
            val emptyPayload = SdJwtPayload(JsonObject(emptyMap()), JsonObject(emptyMap()))
            Ok(
                SdJwtVcVerificationResult(
                    sdJwt = SdJwt(jwt = JwsCompact(args.sdJwt), header = JsonObject(emptyMap()), payload = emptyPayload),
                    vct = "test-vct",
                    typeMetadata = null,
                    issuerMetadata = null,
                ),
            )
        } else {
            Err(IdkError.fromString(code = "SD_JWT_VC_VERIFICATION_FAILED", message = "fake: issuer signature invalid"))
        }
    }
}
