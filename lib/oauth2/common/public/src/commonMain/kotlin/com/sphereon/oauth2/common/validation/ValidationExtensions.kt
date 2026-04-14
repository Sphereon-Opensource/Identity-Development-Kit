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

package com.sphereon.oauth2.common.validation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import io.konform.validation.Invalid
import io.konform.validation.Valid
import io.konform.validation.ValidationResult

/**
 * Validation error detail from Konform
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class ValidationErrorDetail(
    val path: String,
    val message: String,
    val userContext: Any? = null,
)

/**
 * Convert Konform ValidationResult to IdkResult
 */
fun <T, E : IdkErrorType> ValidationResult<T>.toIdkResult(createError: (List<ValidationErrorDetail>) -> E): IdkResult<T, E> =
    when (this) {
        is Valid -> {
            Ok(this.value)
        }

        is Invalid -> {
            val details =
                this.errors.map { error ->
                    ValidationErrorDetail(
                        path = error.path.toString(),
                        message = error.message,
                        userContext = error.userContext,
                    )
                }
            Err(createError(details))
        }
    }

/**
 * Validate and convert to IdkResult in one step
 */
fun <T, E : IdkErrorType> validate(
    validator: io.konform.validation.Validation<T>,
    value: T,
    createError: (List<ValidationErrorDetail>) -> E,
): IdkResult<T, E> = validator(value).toIdkResult(createError)
