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

package com.sphereon.oauth2.client.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import io.ktor.http.Parameters
import kotlin.jvm.JvmOverloads

/**
 * Arguments for merging request object parameters with query parameters
 *
 * Per RFC 9101 Section 3.2:
 * - Request Object parameters take precedence
 * - Only specific parameters (client_id, response_type) MAY be present in both
 * - Other duplications MUST be rejected
 *
 * @property requestObjectJwt The JWT request object string (signed or encrypted)
 * @property queryParameters Query parameters from the authorization request URL
 * @property issuer Expected issuer (client_id) for JWT validation
 * @property audience Expected audience (authorization server) for JWT validation
 * @property verificationKey Optional public key for signature verification
 * @property decryptionKey Optional private key for decryption (if encrypted)
 */
@JsExportCompat
data class MergeRequestObjectArgs
    @JvmOverloads
    constructor(
        val requestObjectJwt: String,
        val queryParameters: Parameters,
        val issuer: String? = null,
        val audience: String? = null,
        val verificationKey: com.sphereon.crypto.core.KeyInfoType<*>? = null,
        val decryptionKey: com.sphereon.crypto.core.KeyInfoType<*>? = null,
    )

/**
 * Result of merging request object with query parameters
 *
 * @property mergedParameters The merged parameters as Ktor Parameters
 * @property isEncrypted Whether the request object was encrypted
 * @property claims All JWT claims from the request object
 */
@JsExportCompat
data class MergedRequestObjectResult(
    val mergedParameters: Parameters,
    val isEncrypted: Boolean,
    @property:JsExportIgnoreCompat
    val claims: Map<String, Any?>,
)

/**
 * Command for merging JWT request object parameters with query parameters
 *
 * Per RFC 9101 Section 3.2, this command:
 * 1. Parses and validates the JWT request object
 * 2. Extracts authorization request parameters from JWT claims
 * 3. Merges with query parameters following RFC 9101 rules:
 *    - Request Object parameters are the base
 *    - client_id and response_type MAY be duplicated (query param value used if different)
 *    - Other parameter duplications MUST be rejected as errors
 * 4. Returns merged parameters as Ktor Parameters
 */
@JsExportCompat
interface MergeRequestObjectCommand : ServiceCommand<MergeRequestObjectArgs, MergedRequestObjectResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.jar.merge"
    }
}
