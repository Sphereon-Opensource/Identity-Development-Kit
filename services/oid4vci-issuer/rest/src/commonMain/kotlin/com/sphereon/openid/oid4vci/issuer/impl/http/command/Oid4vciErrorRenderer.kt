package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.error.DefaultRestErrorRenderer
import com.sphereon.core.api.http.error.HttpErrorRenderer

/**
 * OID4VCI protocol-specific error renderer.
 *
 * Produces RFC 6749-style error responses required by OID4VCI:
 * `{"error": "invalid_token", "error_description": "..."}`
 *
 * Falls back to [DefaultRestErrorRenderer] for non-IdkError types.
 */
class Oid4vciErrorRenderer : HttpErrorRenderer {
    private val fallback = DefaultRestErrorRenderer()

    override fun render(
        error: IdkErrorType,
        request: GenericHttpRequest?,
    ): GenericHttpResponse =
        if (error is IdkError) {
            mapOid4vciError(error)
        } else {
            fallback.render(error, request)
        }
}
