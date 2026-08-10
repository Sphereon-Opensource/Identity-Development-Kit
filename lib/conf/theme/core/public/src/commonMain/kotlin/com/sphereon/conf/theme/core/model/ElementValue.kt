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
 * The value bound to, or resolved for, a design element.
 *
 * The variant always matches the declaring [DesignElement]'s own variant: a
 * [ChoiceDesignElement] is only ever satisfied by a [ChoiceElementValue], and so on. The
 * `kind` discriminator is what the wire format and the console switch on.
 *
 * The variants are top-level declarations rather than nested ones because Kotlin/JS cannot
 * export a class nested inside an exported interface.
 */
@JsExportCompat
@Serializable
sealed interface ElementValue

/** An asset reference, satisfying an [AssetDesignElement]. */
@JsExportCompat
@Serializable
@SerialName("asset")
data class AssetElementValue(val asset: ThemeAssetReference) : ElementValue

/** Free text, satisfying a [TextDesignElement]. */
@JsExportCompat
@Serializable
@SerialName("text")
data class TextElementValue(val text: String) : ElementValue

/** One of a declared set of allowed values, satisfying a [ChoiceDesignElement]. */
@JsExportCompat
@Serializable
@SerialName("choice")
data class ChoiceElementValue(val choice: String) : ElementValue

/** An on/off flag, satisfying a [ToggleDesignElement]. */
@JsExportCompat
@Serializable
@SerialName("toggle")
data class ToggleElementValue(val enabled: Boolean) : ElementValue
