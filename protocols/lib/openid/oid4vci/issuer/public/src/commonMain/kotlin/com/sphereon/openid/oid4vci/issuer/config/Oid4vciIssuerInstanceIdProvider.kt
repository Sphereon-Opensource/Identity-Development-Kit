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

package com.sphereon.openid.oid4vci.issuer.config

import com.sphereon.core.compat.JsExportCompat
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Root config namespace under which per-instance OID4VCI issuer configuration lives:
 * `oid4vci.issuers.<issuerInstanceId>.*`. The instance id is always a canonical resource UUID.
 * An unresolved request fails closed instead of reading the retired singular issuer namespace.
 *
 * Mirrors the OAuth2 AS `oauth2.servers.<asId>.*` instance keyspace.
 */
const val INSTANCES_NAMESPACE: String = "oid4vci.issuers"

/**
 * Per-request seam exposing the active OID4VCI issuer instance id.
 *
 * Lives at `SessionScope` (one HTTP request = one session in the issuer). The HTTP adapter
 * (or an upstream resolver) sets the active id at the entry of every endpoint that may reach a
 * session-scoped collaborator that needs to scope config under
 * `${INSTANCES_NAMESPACE}.<issuerInstanceId>`. Collaborators read it via [currentInstanceId]
 * without taking the id as a method argument.
 *
 * `null` means no issuer instance has been resolved and must be rejected by every routed consumer.
 *
 * Twin of the OAuth2 `OAuth2ServerInstanceIdProvider`.
 */
@JsExportCompat
interface Oid4vciIssuerInstanceIdProvider {
    fun currentInstanceId(): String?
}

/**
 * Validates and returns a canonical OID4VCI issuer resource UUID.
 *
 * Slugs, aliases, empty selectors, and non-canonical UUID spellings are rejected. This function is
 * shared by HTTP and service-command boundaries so no non-HTTP entry point can regain the retired
 * singular issuer fallback.
 */
@OptIn(ExperimentalUuidApi::class)
fun requireCanonicalOid4vciIssuerInstanceId(selector: String?): String {
    val value = selector?.takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("A canonical UUID OID4VCI issuer instance selector is required")
    if (value != value.trim()) {
        throw IllegalArgumentException("OID4VCI issuer instance selector must be a canonical UUID")
    }
    val parsed = runCatching { Uuid.parse(value) }.getOrElse {
        throw IllegalArgumentException("OID4VCI issuer instance selector must be a canonical UUID")
    }
    if (parsed.toString() != value) {
        throw IllegalArgumentException("OID4VCI issuer instance selector must be a canonical UUID")
    }
    return value
}

/** Returns the mandatory canonical issuer UUID currently bound to this request. */
fun Oid4vciIssuerInstanceIdProvider.requireCurrentInstanceId(): String =
    requireCanonicalOid4vciIssuerInstanceId(currentInstanceId())

/**
 * Mutable counterpart of [Oid4vciIssuerInstanceIdProvider]. The HTTP adapter (or any other request
 * entry point) calls [setCurrentInstanceId] before delegating to downstream session-scoped
 * collaborators, then clears it via [clearCurrentInstanceId] on exit so the holder does not leak
 * across unrelated dispatches that share the session.
 *
 * Twin of the OAuth2 `MutableOAuth2ServerInstanceIdProvider`.
 */
@JsExportCompat
interface MutableOid4vciIssuerInstanceIdProvider : Oid4vciIssuerInstanceIdProvider {
    fun setCurrentInstanceId(instanceId: String)

    fun clearCurrentInstanceId()
}
