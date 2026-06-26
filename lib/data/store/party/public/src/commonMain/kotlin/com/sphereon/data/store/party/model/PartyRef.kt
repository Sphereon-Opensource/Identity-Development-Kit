/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.data.store.party.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * A typed runtime reference to a [com.sphereon.data.store.party.model.Party].
 *
 * Used by runtime persistence entities and APIs that need to carry a party's id
 * together with enough type/display context to resolve or render it without a DB
 * round-trip. NOT used by signed license claims — those carry plain String ids
 * (see the license claims model) to stay offline-safe and free of a party-module
 * dependency.
 *
 * @property partyId stable party identifier (`party(id)` foreign key value).
 * @property type the party's [PartyType].
 * @property displayName optional human-readable label.
 * @property identifiers optional opaque key/value identifiers (email, slug, etc.).
 */
@JsExportCompat
@Serializable
data class PartyRef(
    val partyId: String,
    val type: PartyType,
    val displayName: String? = null,
    val identifiers: Map<String, String> = emptyMap(),
)
