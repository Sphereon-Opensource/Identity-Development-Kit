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
 * Visual variant of a theme, controlling light/dark/high-contrast appearance.
 *
 * High contrast is named per ground rather than as a single member, because the role shapes invert
 * between them: on a light ground the secondary is a deep stop carrying white text, on a dark ground
 * it is a light stop carrying dark text. One member could not describe both, and a bare
 * `HIGH_CONTRAST` beside a `HIGH_CONTRAST_DARK` would harden that asymmetry into the name.
 */
@JsExportCompat
@Serializable
enum class ThemeVariant {
    LIGHT,
    DARK,

    /** High contrast on a light ground. See `SystemDefaults.baselineHighContrastLight`. */
    HIGH_CONTRAST_LIGHT,

    /** High contrast on a dark ground. See `SystemDefaults.baselineHighContrastDark`. */
    HIGH_CONTRAST_DARK,
}
