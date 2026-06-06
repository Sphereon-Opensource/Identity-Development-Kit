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
 * Where an attribute value comes from when a consumer resolves an [AttributeBinding].
 *
 * Flow-agnostic primitive shared across IDV graph execution, issuance pipelines, presentation
 * harvests, and any other consumer that wires attribute inputs. Sealed so kotlinx-serialization
 * can pick the right subtype without a custom module and so consumer `when` expressions stay
 * exhaustive. New variants must be added to this file (and the `when` sites updated); the
 * neutral location makes that a cross-team change rather than a private IDV extension.
 *
 * Variants:
 * - [ContextAttribute] — value already present in the surrounding execution context.
 * - [UserInputAttribute] — value entered by a human user at a form field.
 * - [PriorStepAttribute] — value produced by a prior step (IDV graph node, pipeline stage,
 *   ingestion batch row, etc.) identified by a producer-defined [AttributeProvenanceRef].
 */
@JsExportCompat
@Serializable
sealed interface AttributeOrigin

/**
 * The value is already present in the surrounding execution context (e.g. a session-scoped
 * attribute bag produced by an earlier step or supplied by the caller).
 */
@Serializable
data class ContextAttribute(
    val attributePath: AttributePath,
) : AttributeOrigin

/**
 * The value is entered by the human user in response to a form field prompt.
 */
@Serializable
data class UserInputAttribute(
    val fieldId: InputFieldId,
    val fieldLabel: String,
) : AttributeOrigin

/**
 * The value was produced by a prior step (an IDV graph node, an issuance-pipeline stage, an
 * ingestion row producer, …) and should be looked up under [attributePath] within that
 * producer's output. [producerId] is opaque to this module — consumers that care about the
 * specific producer type resolve it against their own registry.
 */
@Serializable
data class PriorStepAttribute(
    val producerId: AttributeProvenanceRef,
    val attributePath: AttributePath,
) : AttributeOrigin

/**
 * Where the resolved attribute value is written — an [AttributePath] key inside an
 * [AttributeBag] or equivalent attribute store.
 */
@JsExportCompat
@Serializable
data class AttributeTarget(
    val attributePath: AttributePath,
)

/**
 * Binds an [AttributeOrigin] (where the value comes from) to an [AttributeTarget] (where it
 * is stored). Required bindings must resolve before the consuming step can proceed; optional
 * bindings may be absent.
 */
@JsExportCompat
@Serializable
data class AttributeBinding(
    val source: AttributeOrigin,
    val target: AttributeTarget,
    val required: Boolean = true,
)
