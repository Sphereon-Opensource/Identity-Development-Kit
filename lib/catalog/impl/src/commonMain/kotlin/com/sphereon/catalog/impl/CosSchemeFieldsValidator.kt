/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.model.CosEaaTypeFields
import com.sphereon.catalog.model.CosSchemeFields
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError

/**
 * Structural checks on the CoS-only fields of a TS 11 record. A record may carry a partial set while it is a draft, so nothing is
 * required here; what is present must be well formed. The CoS conformance rules (registration identifier, at least one attribute
 * reference, English texts) are enforced when the record is published as CoS XML.
 */
object CosSchemeFieldsValidator {
    private const val MAX_TEXT = 4096

    fun validate(fields: CosSchemeFields): IdkResult<CosSchemeFields, IdkError> {
        val problem = firstProblem(fields)
        return if (problem == null) Ok(fields) else Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid CoS fields: $problem"))
    }

    private fun firstProblem(fields: CosSchemeFields): String? {
        fields.schemeName?.let { blankOrLong("schemeName", it)?.let { p -> return p } }
        fields.schemeIdentifier?.let { blankOrLong("schemeIdentifier", it)?.let { p -> return p } }
        fields.registrationIdentifier?.let { blankOrLong("registrationIdentifier", it)?.let { p -> return p } }
        fields.owner?.let { owner ->
            names("owner.name", owner.name)?.let { return it }
            blankOrLong("owner.identifier", owner.identifier)?.let { return it }
        }
        fields.versionStatus?.let { blankOrLong("versionStatus.statusUri", it.statusUri)?.let { p -> return p } }
        fields.eaaType?.let { return eaaTypeProblem(it) }
        return null
    }

    private fun eaaTypeProblem(type: CosEaaTypeFields): String? {
        type.identifier?.let { blankOrLong("eaaType.identifier", it)?.let { p -> return p } }
        type.name?.let { names("eaaType.name", it)?.let { p -> return p } }
        type.schemeDefinition?.let { names("eaaType.schemeDefinition", it)?.let { p -> return p } }
        type.dataModelReference?.let { blankOrLong("eaaType.dataModelReference", it)?.let { p -> return p } }
        type.attributeReferences.forEachIndexed { index, ref ->
            val path = "eaaType.attributeReferences[$index]"
            blankOrLong("$path.namespace", ref.namespace)?.let { return it }
            blankOrLong("$path.identifier", ref.identifier)?.let { return it }
            val hasDefinition = !ref.definitionPointer.isNullOrBlank()
            if (hasDefinition == (ref.cataloguePointer != null)) {
                return "$path needs exactly one of definitionPointer and cataloguePointer"
            }
            ref.cataloguePointer?.let {
                blankOrLong("$path.cataloguePointer.location", it.location)?.let { p -> return p }
                blankOrLong("$path.cataloguePointer.catalogueIdentifier", it.catalogueIdentifier)?.let { p -> return p }
            }
        }
        type.trustModelTypes.forEach { model ->
            if (model !in EuCatalogueConstants.TRUST_MODEL_TYPES) return "eaaType.trustModelTypes has unknown trust model type $model"
        }
        if (type.trustModelTypes.toSet().size != type.trustModelTypes.size) return "eaaType.trustModelTypes must not repeat a value"
        return null
    }

    private fun names(
        path: String,
        value: InternationalNames,
    ): String? {
        value.names.forEach { entry ->
            if (entry.lang.isBlank()) return "$path entries need a language"
            if (entry.value.isBlank()) return "$path entries must not be blank"
            if (entry.value.length > MAX_TEXT) return "$path entries must not exceed $MAX_TEXT characters"
        }
        return null
    }

    private fun blankOrLong(
        path: String,
        value: String,
    ): String? =
        when {
            value.isBlank() -> "$path must not be blank"
            value.length > MAX_TEXT -> "$path must not exceed $MAX_TEXT characters"
            else -> null
        }
}
