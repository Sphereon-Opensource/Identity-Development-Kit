package com.sphereon.oauth2.common.validation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkErrorType
import io.konform.validation.Invalid
import io.konform.validation.Valid
import io.konform.validation.ValidationResult

/**
 * Validation error detail from Konform
 */
data class ValidationErrorDetail(
    val path: String,
    val message: String,
    val userContext: Any? = null
)

/**
 * Convert Konform ValidationResult to IdkResult
 */
fun <T, E : IdkErrorType> ValidationResult<T>.toIdkResult(
    createError: (List<ValidationErrorDetail>) -> E
): IdkResult<T, E> {
    return when (this) {
        is Valid -> Ok(this.value)
        is Invalid -> {
            val details = this.errors.map { error ->
                ValidationErrorDetail(
                    path = error.path.toString(),
                    message = error.message,
                    userContext = error.userContext
                )
            }
            Err(createError(details))
        }
    }
}

/**
 * Validate and convert to IdkResult in one step
 */
fun <T, E : IdkErrorType> validate(
    validator: io.konform.validation.Validation<T>,
    value: T,
    createError: (List<ValidationErrorDetail>) -> E
): IdkResult<T, E> {
    return validator(value).toIdkResult(createError)
}
