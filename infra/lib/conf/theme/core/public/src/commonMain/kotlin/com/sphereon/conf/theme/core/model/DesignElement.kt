/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A named, overridable design element a feature declares: a sign-in logo, a tagline, an email
 * header style.
 *
 * Each variant carries only the constraints and the default that apply to it, and knows how to
 * turn a raw token string into its own [ElementValue]. That is what lets the resolver stay
 * uniform across kinds.
 *
 * The variants are top-level declarations rather than nested ones because Kotlin/JS cannot
 * export a class nested inside an exported interface.
 */
@JsExportCompat
@Serializable
sealed interface DesignElement {
    val elementId: String
    val required: Boolean
    val description: String?

    /** Token key resolution falls back to when no binding and no default exists, e.g. `branding.logoUrl`. */
    val fallbackTokenKey: String?

    /** The element's own declared default, or null when it declares none. */
    fun defaultValue(): ElementValue?

    /** Coerces a resolved token string into this element's value, or null when it does not apply. */
    fun valueFromToken(raw: String): ElementValue?
}

/** An overridable asset, such as a sign-in logo. Satisfied by an [AssetElementValue]. */
@JsExportCompat
@Serializable
@SerialName("asset")
data class AssetDesignElement(
    override val elementId: String,
    override val required: Boolean = false,
    override val description: String? = null,
    override val fallbackTokenKey: String? = null,
    val acceptedContentTypes: List<String>? = null,
    val maxSizeBytes: Long? = null,
    val default: ThemeAssetReference? = null,
) : DesignElement {
    override fun defaultValue(): ElementValue? = default?.let { AssetElementValue(it) }

    override fun valueFromToken(raw: String): ElementValue? =
        raw.trim().takeIf { it.isNotEmpty() }?.let { AssetElementValue(ThemeAssetReference(uri = it)) }
}

/** An overridable piece of text, such as a tagline. Satisfied by a [TextElementValue]. */
@JsExportCompat
@Serializable
@SerialName("text")
data class TextDesignElement(
    override val elementId: String,
    override val required: Boolean = false,
    override val description: String? = null,
    override val fallbackTokenKey: String? = null,
    val maxLength: Int? = null,
    val default: String? = null,
) : DesignElement {
    override fun defaultValue(): ElementValue? = default?.let { TextElementValue(it) }

    override fun valueFromToken(raw: String): ElementValue? =
        raw.takeIf { it.isNotEmpty() }?.let { TextElementValue(it) }
}

/**
 * A choice between declared [allowedValues], such as an email header style. Satisfied by a
 * [ChoiceElementValue] carrying one of those values.
 */
@JsExportCompat
@Serializable
@SerialName("choice")
data class ChoiceDesignElement(
    override val elementId: String,
    val allowedValues: List<String>,
    val default: String,
    override val required: Boolean = false,
    override val description: String? = null,
    override val fallbackTokenKey: String? = null,
) : DesignElement {
    init {
        require(allowedValues.isNotEmpty()) { "Choice element '$elementId' must declare at least one allowed value" }
        require(default in allowedValues) { "Choice element '$elementId' default '$default' is not one of $allowedValues" }
    }

    override fun defaultValue(): ElementValue = ChoiceElementValue(default)

    override fun valueFromToken(raw: String): ElementValue? =
        raw.takeIf { it in allowedValues }?.let { ChoiceElementValue(it) }
}

/** An overridable on/off flag, such as whether to show a tagline. Satisfied by a [ToggleElementValue]. */
@JsExportCompat
@Serializable
@SerialName("toggle")
data class ToggleDesignElement(
    override val elementId: String,
    val default: Boolean,
    override val required: Boolean = false,
    override val description: String? = null,
    override val fallbackTokenKey: String? = null,
) : DesignElement {
    override fun defaultValue(): ElementValue = ToggleElementValue(default)

    override fun valueFromToken(raw: String): ElementValue? =
        when (raw) {
            "true" -> ToggleElementValue(true)
            "false" -> ToggleElementValue(false)
            else -> null
        }
}
