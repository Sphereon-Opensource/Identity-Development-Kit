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

package com.sphereon.data.store.credential.design.impl.mapper

import com.sphereon.data.store.credential.design.model.RenderVariantRecord

/**
 * Selects the render variant whose [RenderVariantRecord.localeApplicability] contains
 * [locale], falling back to the first variant when none match, or `null` for an empty list.
 *
 * Shared by the outbound design mappers (SD-JWT VCT and OID4VCI) so the locale-matching /
 * fallback policy lives in one place.
 */
internal fun selectRenderVariant(
    variants: List<RenderVariantRecord>,
    locale: String,
): RenderVariantRecord? {
    if (variants.isEmpty()) return null
    return variants.firstOrNull { locale in it.localeApplicability } ?: variants.first()
}
