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
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import com.sphereon.statuslist.spi.StatusListResolver
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * W3C Bitstring Status List verifier: recognizes `credentialStatus` entries of
 * `type: "BitstringStatusListEntry"` (top-level or under a `vc` envelope, single object or array) and
 * resolves them via the shared [StatusListResolver]. W3C carries the index as a string.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<CredentialStatusVerifier>())
class BitstringStatusListCredentialStatusVerifier(
    private val resolver: StatusListResolver,
) : CredentialStatusVerifier {
    override val mechanism: String = MECHANISM

    override fun references(credentialClaims: JsonObject): List<CredentialStatusReference> {
        val container =
            credentialClaims["credentialStatus"]
                ?: (credentialClaims["vc"] as? JsonObject)?.get("credentialStatus")
                ?: return emptyList()
        val entries =
            when (container) {
                is JsonArray -> container.filterIsInstance<JsonObject>()
                is JsonObject -> listOf(container)
                else -> emptyList()
            }
        return entries.mapNotNull { entry ->
            if ((entry["type"] as? JsonPrimitive)?.contentOrNull != ENTRY_TYPE) return@mapNotNull null
            val uri = (entry["statusListCredential"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val index =
                (entry["statusListIndex"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
                    ?: return@mapNotNull null
            val purpose = (entry["statusPurpose"] as? JsonPrimitive)?.contentOrNull?.let { StatusPurpose.fromValue(it) }
            val statusSize =
                entry["statusSize"]?.let { size ->
                    (size as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
                }
            CredentialStatusReference(mechanism = MECHANISM, uri = uri, index = index, purpose = purpose, statusSize = statusSize)
        }
    }

    override suspend fun resolve(reference: CredentialStatusReference): IdkResult<ResolvedStatus, IdkError> =
        resolver.resolveStatus(
            ResolveStatusArgs(
                uri = reference.uri,
                index = reference.index,
                expectedPurpose = reference.purpose,
                expectedStatusSize = reference.statusSize,
                expectedSpec = StatusListSpec.BITSTRING_STATUS_LIST,
            ),
        )

    companion object {
        const val MECHANISM: String = "bitstring_status_list"
        private const val ENTRY_TYPE = "BitstringStatusListEntry"
    }
}
