package com.sphereon.core.api.http.error

import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse

/**
 * Renders [IdkErrorType] instances into protocol-specific HTTP responses.
 *
 * Default implementation: [DefaultRestErrorRenderer] for standard REST APIs.
 * Protocol-specific renderers (OAuth, Problem Details) implement this interface
 * with different response shapes and headers.
 */
interface HttpErrorRenderer {
    fun render(
        error: IdkErrorType,
        request: GenericHttpRequest? = null,
    ): GenericHttpResponse
}
