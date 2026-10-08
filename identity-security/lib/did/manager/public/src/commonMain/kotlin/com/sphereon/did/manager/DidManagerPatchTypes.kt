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
package com.sphereon.did.manager

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationPurpose
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * Tri-state value for typed PATCH semantics on the [DidManager] API.
 *
 *  - [Unchanged]: caller did not provide this field; the manager keeps the existing value.
 *  - [Set]:       caller provided a value (which may itself be `null` when the type parameter
 *                 allows it, signalling an explicit clear).
 *
 * Used inside patch DTOs ([VerificationMethodPatch], [ServicePatch], …) so the manager can
 * distinguish *absence* from *present-with-null* without resorting to magic field-name sets.
 *
 * This type is deliberately **not** serialized or `@JsExport`ed. The wire layer (HTTP/REST,
 * binary transport) translates its own absent/null encoding (e.g. JSON Merge Patch) into
 * `Unchanged` / `Set(v)` before it reaches the manager.
 */
sealed interface PatchValue<out T> {
    /** Field was not supplied — leave the existing value untouched. */
    data object Unchanged : PatchValue<Nothing>

    /** Field was supplied with [value]; apply it (which may itself be `null`). */
    data class Set<out T>(
        val value: T
    ) : PatchValue<T>

    companion object {
        /** Helper: [Set] when [value] is non-null, [Unchanged] when null. */
        fun <T : Any> ofNullable(value: T?): PatchValue<T> = if (value == null) Unchanged else Set(value)
    }
}

/**
 * Returns the patched value when this is [PatchValue.Set], otherwise the [existing] value.
 * Convenience for manager-impl merge code.
 */
fun <T> PatchValue<T>.applyOr(existing: T): T =
    when (this) {
        is PatchValue.Set<T> -> value
        is PatchValue.Unchanged -> existing
    }

/**
 * Per-sub-field patch for the KMS binding of a verification method. Used inside
 * [VerificationMethodPatch.keyInfo] so the manager can interpret each KMS field's
 * presence/absence/null independently — this is the tri-state JSON Merge Patch the wire
 * layer already exposes for VM updates, lifted into a typed manager API.
 *
 * The outer [VerificationMethodPatch.keyInfo] is a bare `KeyInfoPatch` (not wrapped in
 * `PatchValue`) because there is no meaningful difference between "keyInfo object absent"
 * and "keyInfo object present but all sub-fields Unchanged" — both leave the existing
 * binding intact.
 *
 * Manager-side merge contract:
 *  - [providerId]: required after merge; cannot be cleared (hence `PatchValue<String>`).
 *  - [alias]: required after merge unless [kid] is non-null; can be cleared via `Set(null)`.
 *  - [kid]: optional KMS key id; can be cleared via `Set(null)`.
 *
 * The manager validates the resulting binding after merge: providerId must exist, and at
 * least one of alias/kid must remain. Otherwise the call returns
 * `ILLEGAL_ARGUMENT_ERROR` and the persistence layer is not touched.
 */
data class KeyInfoPatch(
    val providerId: PatchValue<String> = PatchValue.Unchanged,
    val alias: PatchValue<String?> = PatchValue.Unchanged,
    val kid: PatchValue<String?> = PatchValue.Unchanged,
)

/**
 * Partial-update payload for [DidManager.patchVerificationMethod].
 *
 * Every field defaults to [PatchValue.Unchanged]; only fields the caller explicitly supplies
 * are applied. Manager-side capability checks reject the call before the merge runs when the
 * DID method's `keyManagement.replacement` capability is false.
 *
 * @property controller new controller DID. Required field on the VM — clearing is not allowed,
 *                      hence `PatchValue<String>` rather than `PatchValue<String?>`.
 * @property keyInfo per-sub-field KMS-binding patch — see [KeyInfoPatch] for the contract.
 * @property expiresAt expiry timestamp; `Set(null)` clears the expiry.
 * @property revokedAt revocation timestamp; `Set(null)` un-revokes.
 * @property extensionProperties unknown DID 1.1 extension properties; `Set(null)` clears them.
 * @property valueVerificationRelation embedded (inline) relationship for this VM; `Set(null)`
 *                                     removes any embedded relationship.
 * @property referenceVerificationRelations referenced (URL fragment) relationships for this VM;
 *                                          `Set(null)` removes all referenced relationships.
 */
data class VerificationMethodPatch(
    val controller: PatchValue<String> = PatchValue.Unchanged,
    val keyInfo: KeyInfoPatch = KeyInfoPatch(),
    val expiresAt: PatchValue<Instant?> = PatchValue.Unchanged,
    val revokedAt: PatchValue<Instant?> = PatchValue.Unchanged,
    val extensionProperties: PatchValue<JsonObject?> = PatchValue.Unchanged,
    val valueVerificationRelation: PatchValue<VerificationPurpose?> = PatchValue.Unchanged,
    val referenceVerificationRelations: PatchValue<List<VerificationPurpose>?> = PatchValue.Unchanged,
)

/**
 * Partial-update payload for [DidManager.patchService].
 *
 * @property type new service `type` array; clearing is not allowed (a service without a type is
 *                not valid in DID Core).
 * @property serviceEndpoint new endpoint expression (string / object / array per DID Core);
 *                           clearing is not allowed.
 */
data class ServicePatch(
    val type: PatchValue<List<String>> = PatchValue.Unchanged,
    val serviceEndpoint: PatchValue<JsonElement> = PatchValue.Unchanged,
)

/**
 * Declarative replace-all body for [DidManager.replaceDidAggregate]. Every listed collection is
 * fully replaced (not merged). Verification methods and key mappings are intentionally
 * excluded — those must be mutated via their dedicated manager methods to preserve KMS-lifecycle
 * invariants.
 *
 * One entry in [relationships] must specify exactly one of [DidRelationshipReplacement.verificationMethodId]
 * (embedded, points at a local VM UUID/id) or [DidRelationshipReplacement.referenceDidUrl]
 * (referenced, full DID URL); both-null or both-set is rejected.
 */
data class DidAggregateReplacement(
    val alias: String? = null,
    val controllers: List<String> = emptyList(),
    val alsoKnownAs: List<String> = emptyList(),
    val equivalentIds: List<String> = emptyList(),
    val contexts: List<String> = emptyList(),
    val canonicalId: String? = null,
    val deactivated: Boolean? = null,
    val services: List<DidService> = emptyList(),
    val relationships: List<DidRelationshipReplacement> = emptyList(),
)

/** One entry in [DidAggregateReplacement.relationships]. */
data class DidRelationshipReplacement(
    val purpose: String,
    val verificationMethodId: String? = null,
    val referenceDidUrl: String? = null,
)

/**
 * Input for [DidManager.addKeyMapping]. The manager validates that
 *  - [verificationMethodId] resolves to an existing VM on [did];
 *  - when the KMS keyref-store is available, the supplied [keyInfo] resolves to a registered
 *    keyref (no dangling pointers).
 */
data class AddKeyMappingInput(
    val verificationMethodId: String,
    val keyInfo: KeyInfo<KeyType>,
    val purposes: List<VerificationPurpose> = emptyList(),
)
