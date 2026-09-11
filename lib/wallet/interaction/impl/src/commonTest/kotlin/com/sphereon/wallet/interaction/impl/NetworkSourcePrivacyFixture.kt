/*
 * Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.interaction.ProtocolExecutionOwner

data class ObservedRemoteExchange(
    val owner: ProtocolExecutionOwner,
    val peerAddress: String,
    val requestHeaderNames: Set<String>,
    val forwardedFor: String?,
    val userAgent: String?,
    val appRegistrationId: String?,
)

/**
 * Instrumented issuer/RP recorder used by later live gates. It records observed remote-party
 * exchanges and fails closed when a WALLET_BACKEND flow would leak app-network metadata or load
 * RP-controlled content in the app.
 */
class NetworkSourcePrivacyFixture(
    val approvedBackendEgressAddresses: Set<String> = setOf(DEFAULT_BACKEND_EGRESS),
) {
    private val recorded = mutableListOf<ObservedRemoteExchange>()

    val observations: List<ObservedRemoteExchange>
        get() = recorded.toList()

    fun recordIssuerOrRpExchange(
        owner: ProtocolExecutionOwner,
        peerAddress: String,
        headers: Map<String, String> = emptyMap(),
        appRegistrationId: String? = null,
        usesAppNetworking: Boolean = owner == ProtocolExecutionOwner.WALLET_APP,
        loadsRpControlledContentInApp: Boolean = false,
    ): IdkResult<ObservedRemoteExchange, IdkError> {
        val exchange =
            ObservedRemoteExchange(
                owner = owner,
                peerAddress = peerAddress,
                requestHeaderNames = headers.keys.map { it.lowercase() }.toSet(),
                forwardedFor = header(headers, FORWARDED_FOR_HEADERS),
                userAgent = header(headers, USER_AGENT_HEADERS),
                appRegistrationId = appRegistrationId ?: header(headers, APP_REGISTRATION_HEADERS),
            )
        return observe(exchange, usesAppNetworking, loadsRpControlledContentInApp)
    }

    fun observe(
        exchange: ObservedRemoteExchange,
        usesAppNetworking: Boolean = exchange.owner == ProtocolExecutionOwner.WALLET_APP,
        loadsRpControlledContentInApp: Boolean = false,
    ): IdkResult<ObservedRemoteExchange, IdkError> {
        if (exchange.owner == ProtocolExecutionOwner.WALLET_BACKEND && loadsRpControlledContentInApp) {
            return privacyError(STRICT_PRIVACY_REMOTE_CONTENT_FORBIDDEN)
        }
        return when (exchange.owner) {
            ProtocolExecutionOwner.WALLET_BACKEND -> observeBackend(exchange)
            ProtocolExecutionOwner.WALLET_APP -> observeApp(exchange, usesAppNetworking)
        }
    }

    private fun observeBackend(exchange: ObservedRemoteExchange): IdkResult<ObservedRemoteExchange, IdkError> {
        if (exchange.peerAddress !in approvedBackendEgressAddresses) {
            return privacyError("wallet_backend_unapproved_egress")
        }
        if (exchange.forwardedFor != null || exchange.requestHeaderNames.any { it in FORWARDED_FOR_HEADERS }) {
            return privacyError("wallet_backend_forwarded_for_forbidden")
        }
        if (exchange.userAgent.isMobileOrBrowserUserAgent()) {
            return privacyError("wallet_backend_mobile_user_agent_forbidden")
        }
        if (exchange.appRegistrationId != null || exchange.requestHeaderNames.any { it in APP_REGISTRATION_HEADERS }) {
            return privacyError("wallet_backend_app_registration_header_forbidden")
        }
        recorded += exchange
        return Ok(exchange)
    }

    private fun observeApp(
        exchange: ObservedRemoteExchange,
        usesAppNetworking: Boolean,
    ): IdkResult<ObservedRemoteExchange, IdkError> {
        if (!usesAppNetworking) {
            return privacyError("wallet_app_network_source_unrecorded")
        }
        recorded += exchange
        return Ok(exchange)
    }

    private fun privacyError(code: String): IdkResult<ObservedRemoteExchange, IdkError> =
        Err(IdkError.fromString(message = code, code = code))

    private fun header(
        headers: Map<String, String>,
        names: Set<String>,
    ): String? =
        headers.entries
            .firstOrNull { it.key.lowercase() in names }
            ?.value
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val DEFAULT_BACKEND_EGRESS: String = "203.0.113.10"
        const val STRICT_PRIVACY_REMOTE_CONTENT_FORBIDDEN: String = "wallet_backend_strict_privacy_remote_content_forbidden"

        private val FORWARDED_FOR_HEADERS = setOf("x-forwarded-for", "forwarded", "x-real-ip")
        private val USER_AGENT_HEADERS = setOf("user-agent")
        private val APP_REGISTRATION_HEADERS =
            setOf(
                "x-app-registration-id",
                "x-wallet-app-registration",
                "x-wallet-app-registration-id",
            )
    }
}

private fun String?.isMobileOrBrowserUserAgent(): Boolean {
    if (this.isNullOrBlank()) return false
    val value = lowercase()
    return listOf("mobile", "android", "iphone", "ipad", "mozilla", "chrome", "safari", "firefox", "edg/").any { it in value }
}
