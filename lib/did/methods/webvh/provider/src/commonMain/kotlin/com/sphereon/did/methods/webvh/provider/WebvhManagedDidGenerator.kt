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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidCreateResult
import com.sphereon.did.manager.ManagedDidGenerator
import com.sphereon.did.methods.webvh.command.CreateWebvhDidInput
import com.sphereon.did.methods.webvh.provider.facade.WebvhDidServiceFacade
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Mints did:webvh through the DID manager. Contributed into `Set<ManagedDidGenerator>`, so the
 * manager routes `create(method = "webvh")` here instead of the generic (unsupported) provider path.
 *
 * Maps the generic [DidCreateOptions] to a [CreateWebvhDidInput] and delegates to the
 * [WebvhDidServiceFacade] (which signs the genesis entry and computes the SCID). Only the KMS update
 * key references are passed — the create command derives their multikeys — so nothing here
 * hand-encodes crypto. The manager persists the returned current-state document via its normal path.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<ManagedDidGenerator>())
class WebvhManagedDidGenerator(
    private val facade: WebvhDidServiceFacade,
) : ManagedDidGenerator {
    override val method: String = "webvh"

    override suspend fun generate(options: DidCreateOptions): IdkResult<DidCreateResult, IdkError> {
        val domain =
            options.domain?.takeIf { it.isNotBlank() }
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "did:webvh creation requires a 'domain' (the web host) in DidCreateOptions"))

        // The webvh update (authorization) keys are the configured verification-method KMS keys; the
        // genesis document binds one VM per update key, aligning with the manager's key-mapping step.
        val updateKeyRefs = options.verificationMethods.map { it.kmsKeyAlias }.filter { it.isNotBlank() }
        if (updateKeyRefs.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "did:webvh creation requires at least one verification method (KMS update key) in DidCreateOptions"))
        }

        // Honor the caller's requested verification purposes. webvh's input applies a single purpose
        // list to every update-key VM (it has no per-key purpose field), so we pass the distinct union
        // across configs; for the common single-update-key DID this is exactly the caller's intent.
        // When no purposes are supplied, CreateWebvhDidInput's own default (authentication + assertion)
        // applies. The manager still records each VM's per-config purposes in its key mapping.
        val purposes = options.verificationMethods.flatMap { it.purposes }.distinct()

        val input =
            CreateWebvhDidInput(
                domain = domain,
                path = options.path ?: emptyList(),
                updateKeyRefs = updateKeyRefs,
                // updateMultikeys intentionally omitted — the create command derives them from the keys.
                services = options.services ?: emptyList(),
            ).let { if (purposes.isNotEmpty()) it.copy(verificationPurposes = purposes) else it }
        val output = facade.create(input).getOrElse { return Err(it) }
        return Ok(
            DidCreateResult(
                did = output.did,
                didDocument = output.didDocument,
                alias = options.alias,
            ),
        )
    }
}

private inline fun <V, E> IdkResult<V, E>.getOrElse(onErr: (E) -> Nothing): V = if (isOk) value else onErr(error)
