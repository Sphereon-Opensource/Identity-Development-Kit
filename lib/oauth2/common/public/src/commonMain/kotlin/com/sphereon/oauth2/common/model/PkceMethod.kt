package com.sphereon.oauth2.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PKCE (Proof Key for Code Exchange) method (RFC 7636)
 *
 * Used by both:
 * - Authorization Servers to advertise supported methods in metadata
 * - Clients to specify which method they're using
 * - Authorization Servers to validate code challenges
 */
@Serializable
enum class PkceMethod(val value: String) {
    @SerialName("plain")
    PLAIN("plain"),

    @SerialName("S256")
    S256("S256")
}
