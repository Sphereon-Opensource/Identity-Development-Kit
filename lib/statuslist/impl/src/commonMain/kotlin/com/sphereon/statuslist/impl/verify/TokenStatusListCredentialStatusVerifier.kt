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

package com.sphereon.statuslist.impl.verify

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.CredentialStatusReference
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.ResolvedStatus
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import com.sphereon.statuslist.spi.StatusListResolver
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * IETF Token Status List verifier: recognizes the `status.status_list = { uri, idx }` referenced-token
 * claim (the shape the SD-JWT VC issuer embeds) and resolves it via the shared [StatusListResolver].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<CredentialStatusVerifier>())
class TokenStatusListCredentialStatusVerifier(
    private val resolver: StatusListResolver,
) : CredentialStatusVerifier {
    override val mechanism: String = MECHANISM

    override fun references(credentialClaims: JsonObject): List<CredentialStatusReference> {
        val statusList = (credentialClaims["status"] as? JsonObject)?.get("status_list") as? JsonObject ?: return emptyList()
        val uri = (statusList["uri"] as? JsonPrimitive)?.contentOrNull ?: return emptyList()
        val idx = (statusList["idx"] as? JsonPrimitive)?.intOrNull ?: return emptyList()
        return listOf(CredentialStatusReference(mechanism = MECHANISM, uri = uri, index = idx))
    }

    override suspend fun resolve(reference: CredentialStatusReference): IdkResult<ResolvedStatus, IdkError> =
        resolver.resolveStatus(
            ResolveStatusArgs(uri = reference.uri, index = reference.index, expectedSpec = StatusListSpec.TOKEN_STATUS_LIST),
        )

    companion object {
        const val MECHANISM: String = "token_status_list"
    }
}
