package com.sphereon.core.api.service.contract

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Optional interface for input types that declare structural validation rules.
 *
 * When implemented, the command framework validates automatically before doExecute().
 * The interface is validation-library-agnostic — modules can implement using
 * Konform, manual checks, or any validation mechanism.
 *
 * For config-dependent or context-dependent validation, use
 * [ServiceCommand.validateArgs] instead.
 */
interface ValidatableInput {
    fun validate(): IdkResult<Unit, IdkError>
}
