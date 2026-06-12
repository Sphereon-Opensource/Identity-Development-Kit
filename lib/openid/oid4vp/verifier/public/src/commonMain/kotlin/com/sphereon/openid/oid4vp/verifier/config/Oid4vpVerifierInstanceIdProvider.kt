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

package com.sphereon.openid.oid4vp.verifier.config

import com.sphereon.core.compat.JsExportCompat

/**
 * Root config namespace under which per-instance OID4VP verifier configuration lives:
 * `oid4vp.verifiers.<verifierInstanceId>.*`. The singular, single-verifier deployment namespace
 * (`oid4vp.verifier.*`, see [Oid4vpVerifierInstanceResolver] / the config-driven verifier providers)
 * is the fallback when no instance id is resolved for the current request.
 *
 * Mirrors the OID4VCI issuer `oid4vci.issuers.<id>.*` instance keyspace and the OAuth2 AS
 * `oauth2.servers.<asId>.*` instance keyspace.
 */
const val INSTANCES_NAMESPACE: String = "oid4vp.verifiers"

/**
 * Per-request seam exposing the active OID4VP verifier instance id.
 *
 * Lives at `SessionScope` (one HTTP request = one session in the verifier). The HTTP adapter
 * (or an upstream resolver) sets the active id at the entry of every endpoint that may reach a
 * session-scoped collaborator that needs to scope config under
 * `${INSTANCES_NAMESPACE}.<verifierInstanceId>`. Collaborators read it via [currentInstanceId]
 * without taking the id as a method argument.
 *
 * `null` means no verifier instance has been resolved for the current request, e.g. the request hit
 * an entry point that does not need per-instance routing or the resolution step has not run yet.
 * Consumers fall back to the singular verifier config namespace in that case.
 *
 * Twin of the OID4VCI `Oid4vciIssuerInstanceIdProvider` and the OAuth2 `OAuth2ServerInstanceIdProvider`.
 */
@JsExportCompat
interface Oid4vpVerifierInstanceIdProvider {
    fun currentInstanceId(): String?
}

/**
 * Mutable counterpart of [Oid4vpVerifierInstanceIdProvider]. The HTTP adapter (or any other request
 * entry point) calls [setCurrentInstanceId] before delegating to downstream session-scoped
 * collaborators, then clears it via [clearCurrentInstanceId] on exit so the holder does not leak
 * across unrelated dispatches that share the session.
 *
 * Twin of the OID4VCI `MutableOid4vciIssuerInstanceIdProvider` and the OAuth2
 * `MutableOAuth2ServerInstanceIdProvider`.
 */
@JsExportCompat
interface MutableOid4vpVerifierInstanceIdProvider : Oid4vpVerifierInstanceIdProvider {
    fun setCurrentInstanceId(instanceId: String)

    fun clearCurrentInstanceId()
}
