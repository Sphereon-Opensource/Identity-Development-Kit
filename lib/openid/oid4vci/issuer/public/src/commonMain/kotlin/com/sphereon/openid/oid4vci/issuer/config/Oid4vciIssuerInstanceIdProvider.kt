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

/**
 * Root config namespace under which per-instance OID4VCI issuer configuration lives:
 * `oid4vci.issuers.<issuerInstanceId>.*`. The singular, single-issuer deployment namespace
 * (`oid4vci.issuer.*`, see `ConfigDrivenOid4vciIssuerConfigProvider.NAMESPACE`) is the fallback
 * when no instance id is resolved for the current request.
 *
 * Mirrors the OAuth2 AS `oauth2.servers.<asId>.*` instance keyspace.
 */
const val INSTANCES_NAMESPACE: String = "oid4vci.issuers"

/** Stable persistence identity for the singular, config-only issuer deployment. */
const val DEFAULT_OID4VCI_ISSUER_INSTANCE_ID: String = "default"

/**
 * Per-request seam exposing the active OID4VCI issuer instance id.
 *
 * Lives at `SessionScope` (one HTTP request = one session in the issuer). The HTTP adapter
 * (or an upstream resolver) sets the active id at the entry of every endpoint that may reach a
 * session-scoped collaborator that needs to scope config under
 * `${INSTANCES_NAMESPACE}.<issuerInstanceId>`. Collaborators read it via [currentInstanceId]
 * without taking the id as a method argument.
 *
 * `null` means no issuer instance has been resolved for the current request, e.g. the request hit
 * an entry point that does not need per-instance routing or the resolution step has not run yet.
 * Consumers fall back to the singular issuer config namespace in that case.
 *
 * Twin of the OAuth2 `OAuth2ServerInstanceIdProvider`.
 */
@JsExportCompat
interface Oid4vciIssuerInstanceIdProvider {
    fun currentInstanceId(): String?
}

/** Resolve the routed instance or the canonical identity of the singular issuer deployment. */
fun Oid4vciIssuerInstanceIdProvider.currentInstanceIdOrDefault(): String =
    currentInstanceId()?.trim()?.takeIf(String::isNotEmpty) ?: DEFAULT_OID4VCI_ISSUER_INSTANCE_ID

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
