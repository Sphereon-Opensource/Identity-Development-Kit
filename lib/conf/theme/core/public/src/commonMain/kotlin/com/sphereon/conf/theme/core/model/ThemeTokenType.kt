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
import kotlinx.serialization.Serializable

/**
 * The type of a theme token value, used for validation and client-side interpretation.
 */
@JsExportCompat
@Serializable
enum class ThemeTokenType {
    /** CSS hex color (e.g. "#FF5722") or named M3 color role */
    COLOR,

    /** Dimension in dp/sp/px (e.g. "16dp", "14sp") */
    DIMENSION,

    /** Font family name (e.g. "Roboto", "Inter") */
    FONT_FAMILY,

    /** Font weight (e.g. "400", "bold") */
    FONT_WEIGHT,

    /** Opacity 0.0–1.0 */
    OPACITY,

    /** Animation duration (e.g. "300ms") */
    DURATION,

    /** Cubic bezier easing function (e.g. "cubic-bezier(0.2, 0.0, 0, 1.0)") */
    EASING,

    /** Composite box-shadow value (e.g. "0 1px 3px 0 rgba(0,0,0,0.1)") */
    SHADOW,

    /** Border width dimension (e.g. "1px", "2px") */
    BORDER_WIDTH,

    /** Spacing dimension on 4px grid (e.g. "4px", "16px") */
    SPACING,

    /** Arbitrary string value */
    STRING,
}
