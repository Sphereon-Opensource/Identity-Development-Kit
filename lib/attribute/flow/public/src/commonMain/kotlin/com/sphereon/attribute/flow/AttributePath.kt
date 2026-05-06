/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.flow

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Typed handle to a single attribute within an [AttributeBag].
 *
 * The value is an opaque string; interpretation (dotted path, JSON pointer, flat key) is the
 * responsibility of the producer. Consumers should treat the value as an identity key and not
 * attempt to parse structure out of it.
 */
@JsExportCompat
@Serializable
data class AttributePath(
    val value: String,
)

/**
 * Identifier for a user-supplied input field, used by [UserInputAttribute] to bind the value
 * a user enters for a form / prompt into a downstream attribute.
 */
@JsExportCompat
@Serializable
data class InputFieldId(
    val value: String,
)

/**
 * Opaque identifier for the producer of an attribute value in an [AttributeBag.provenance]
 * map. The string payload is producer-defined (e.g. an IDV node id, an OID4VCI credential
 * request id, a tabular row id, a backend service id). Keeping provenance generic avoids
 * coupling the attribute-flow primitives to any particular consumer's identifier type.
 */
@JsExportCompat
@Serializable
data class AttributeProvenanceRef(
    val value: String,
)
