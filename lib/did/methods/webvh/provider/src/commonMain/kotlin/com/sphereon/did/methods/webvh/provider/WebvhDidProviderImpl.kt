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
 *
 */

package com.sphereon.did.methods.webvh.provider

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.AddKeyOptions
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidCreateResult
import com.sphereon.did.manager.DidDeactivateOptions
import com.sphereon.did.manager.DidDeactivateResult
import com.sphereon.did.manager.DidProvider
import com.sphereon.did.manager.DidUpdateOptions
import com.sphereon.did.manager.DidUpdateResult
import com.sphereon.did.methods.webvh.WebvhDidCapabilities
import com.sphereon.did.methods.webvh.provider.WebvhDidProvider
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * `did:webvh` `DidProvider`.
 *
 * The generic `DidProvider` interface (create / update / deactivate / addKey
 * / removeKey / addService / removeService) does not carry webvh-specific
 * inputs (multikeys, witness configuration, watchers, pre-rotation hashes).
 * For full lifecycle access, callers MUST use the webvh-specific service
 * commands (`did.webvh.create` / `did.webvh.update` /
 * `did.webvh.deactivate`) or the `WebvhDidServiceFacade`.
 *
 * This impl is registered into the generic `Set<DidProvider>` so that
 * `DidProviderRegistry.getProvider("webvh")` returns a non-null value (which
 * advertises the method's capabilities), but each generic mutating method
 * fails fast with a directive to use the webvh-specific commands.
 *
 * The `currentDocument: DidDocument?` parameter on `removeKey` / `addService` /
 * `removeService` is part of the [DidProvider] contract — did:web needs it to
 * republish the whole document, since did:web has no stateful resolver. webvh
 * has its own log-backed state machine and these generic paths are unsupported,
 * so the parameter is accepted (for interface conformance) but ignored.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidProvider>())
class WebvhDidProviderImpl : WebvhDidProvider {
    override val method: String = WebvhDidCapabilities.METHOD
    override val capabilities: DidMethodCapabilities = WebvhDidCapabilities.CAPABILITIES

    override suspend fun create(options: DidCreateOptions): IdkResult<DidCreateResult, IdkError> = unsupported("create")

    override suspend fun update(
        did: String,
        options: DidUpdateOptions
    ): IdkResult<DidUpdateResult, IdkError> = unsupported("update")

    override suspend fun deactivate(
        did: String,
        options: DidDeactivateOptions
    ): IdkResult<DidDeactivateResult, IdkError> = unsupported("deactivate")

    override suspend fun addKey(
        did: String,
        options: AddKeyOptions
    ): IdkResult<DidUpdateResult, IdkError> = unsupported("addKey")

    override suspend fun removeKey(
        did: String,
        keyId: String,
        currentDocument: DidDocument?,
    ): IdkResult<DidUpdateResult, IdkError> = unsupported("removeKey")

    override suspend fun addService(
        did: String,
        service: DidService,
        currentDocument: DidDocument?,
    ): IdkResult<DidUpdateResult, IdkError> = unsupported("addService")

    override suspend fun removeService(
        did: String,
        serviceId: String,
        currentDocument: DidDocument?,
    ): IdkResult<DidUpdateResult, IdkError> = unsupported("removeService")

    private fun <T> unsupported(operation: String): IdkResult<T, IdkError> =
        Err(
            IdkError.fromString(
                message =
                    "did:webvh '$operation' is not exposed via the generic DidProvider interface. " +
                        "Use the webvh-specific service commands (did.webvh.* / WebvhDidServiceFacade).",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
}
