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

package com.sphereon.oauth2.server.resource.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Represents an incoming HTTP request to a protected resource
 *
 * This model captures the essential information needed to verify
 * access tokens and DPoP proofs for resource server protection.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResourceRequest", exact = true)
@JsExportCompat
data class ResourceRequest(
    /**
     * HTTP method (GET, POST, PUT, DELETE, etc.)
     */
    val method: String,
    /**
     * Full URL of the request
     * Used for DPoP proof verification (htu claim)
     */
    val url: String,
    /**
     * HTTP headers (case-insensitive map)
     * Must include Authorization header
     * May include DPoP header for DPoP-bound tokens
     */
    @property:JsExportIgnoreCompat
    val headers: Map<String, String>,
    /**
     * Request body (optional)
     * Not used for token verification, but may be needed by the application
     */
    val body: ByteArray? = null,
    /**
     * Leaf TLS client certificate (DER bytes) presented at the resource server's TLS edge.
     * Populated by the platform HTTP adapter for mTLS deployments. `null` when no cert was
     * presented; required to be non-null and match `cnf.x5t#S256` when validating an
     * RFC 8705 §3.2 certificate-bound access token.
     */
    @property:JsExportIgnoreCompat
    val clientCertificateDer: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as ResourceRequest

        if (method != other.method) {
            return false
        }
        if (url != other.url) {
            return false
        }
        if (headers != other.headers) {
            return false
        }
        if (body != null) {
            if (other.body == null) {
                return false
            }
            if (!body.contentEquals(other.body)) {
                return false
            }
        } else if (other.body != null) {
            return false
        }
        if (clientCertificateDer != null) {
            if (other.clientCertificateDer == null) {
                return false
            }
            if (!clientCertificateDer.contentEquals(other.clientCertificateDer)) {
                return false
            }
        } else if (other.clientCertificateDer != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        result = 31 * result + (clientCertificateDer?.contentHashCode() ?: 0)
        return result
    }
}
