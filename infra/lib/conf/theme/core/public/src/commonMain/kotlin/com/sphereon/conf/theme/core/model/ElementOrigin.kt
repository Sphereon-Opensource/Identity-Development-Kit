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
 * The resolution layer a resolved element value came from.
 */
@JsExportCompat
@Serializable
enum class ElementOrigin {
    /** An application-scoped element binding (applicationId set) */
    APPLICATION,

    /** A tenant-scoped element binding (applicationId null) */
    TENANT,

    /** The default declared by the product's built-in feature descriptor */
    PRODUCT_DEFAULT,

    /** The default declared on the design element itself */
    ELEMENT_DEFAULT,

    /** The element's fallback token key resolved through token resolution */
    TOKEN_FALLBACK,
}
