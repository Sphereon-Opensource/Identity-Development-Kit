/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.model

import kotlinx.serialization.Serializable

/**
 * Selector-facing known-holder match state.
 *
 * Used by [ReconciliationSelectorRule] and [ReconciliationSelectorInput] to express
 * the result of a holder-lookup step. This enum drives selector rule matching only;
 * the resolved binding payload is carried by [ResolvedKnownHolder] in auth-bridge.
 */
@Serializable
enum class KnownHolderState {
    MATCHED_HOLDER_KEY,
    MATCHED_CLAIM_TUPLE,
    NOT_FOUND,
    EXPIRED_BINDING,
}
