/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.verifier.requesturi

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfoType
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.native.ObjCName

/**
 * Handler for request_uri fetches.
 *
 * Generates signed JARs on-demand from stored session data.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RequestUriHandler", exact = true)
//@JsExportCompat
interface RequestUriHandler {
    suspend fun handleGet(requestUriPath: String): IdkResult<RequestUriResponse, IdkError>

    suspend fun handlePost(
        requestUriPath: String,
        walletMetadata: String? = null,
        walletNonce: String? = null
    ): IdkResult<RequestUriResponse, IdkError>
}

/**
 * Configuration for JAR signing when serving request_uri.
 *
 * Not exported to JS as it contains non-exportable crypto key types.
 */
@JsExportIgnoreCompat
interface RequestUriSigningConfig {
    val signingKey: KeyInfoType<*>
    val audience: String
    val expirationSeconds: Long

    /**
     * Whether JAR signing is enabled. When false, the request object is returned
     * as plain JSON (application/json) instead of a signed JWT (application/oauth-authz-req+jwt).
     * Defaults to true for backwards compatibility.
     */
    val enabled: Boolean get() = true

    companion object {
        /**
         * Creates a disabled signing config that returns request objects as plain JSON.
         * Use this when JAR signing is not required (e.g., development, or wallets
         * that accept unsigned request objects).
         */
        fun disabled(expirationSeconds: Long = 300): RequestUriSigningConfig = DisabledRequestUriSigningConfig(expirationSeconds)
    }
}

/**
 * A [RequestUriSigningConfig] that disables JAR signing.
 * The signing key and audience are not used.
 */
private class DisabledRequestUriSigningConfig(
    override val expirationSeconds: Long
) : RequestUriSigningConfig {
    override val enabled: Boolean = false
    override val signingKey: KeyInfoType<*> get() = throw UnsupportedOperationException("JAR signing is disabled")
    override val audience: String get() = throw UnsupportedOperationException("JAR signing is disabled")
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RequestUriResponse", exact = true)
@JsExportCompat
@Serializable
data class RequestUriResponse(
    val signedJar: String,
    val contentType: String = CONTENT_TYPE_JAR,
    val sessionId: String,
    val expiresAt: Long
) {
    companion object {
        const val CONTENT_TYPE_JAR = "application/oauth-authz-req+jwt"
        const val CONTENT_TYPE_JSON = "application/json"
    }
}
