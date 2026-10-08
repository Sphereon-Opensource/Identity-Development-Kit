/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import com.sphereon.core.api.error.IdkErrorType

/**
 * Operator-facing diagnostics for the wallet interaction engine and its protocol executors.
 *
 * Details travel as plain strings and must never carry secrets: no tokens, codes, transaction
 * codes, PINs, key material, or full URLs with query strings. Callers pass error codes, exception
 * types, messages and non-sensitive identifiers only.
 */
interface WalletInteractionDiagnostics {
    fun warn(
        event: String,
        sessionId: WalletInteractionSessionId?,
        details: Map<String, String> = emptyMap(),
    )

    companion object {
        val none: WalletInteractionDiagnostics =
            object : WalletInteractionDiagnostics {
                override fun warn(
                    event: String,
                    sessionId: WalletInteractionSessionId?,
                    details: Map<String, String>,
                ) = Unit
            }
    }
}

/** Flattens an error and its cause chain into log details keyed under [prefix]. */
fun IdkErrorType.toDiagnosticDetails(prefix: String = "error"): Map<String, String> {
    val details = linkedMapOf<String, String>()
    var current: IdkErrorType? = this
    var depth = 0
    while (current != null && depth < MAX_DIAGNOSTIC_CAUSE_DEPTH) {
        val key = if (depth == 0) prefix else "$prefix.cause$depth"
        details["$key.code"] = current.code
        details["$key.message"] = current.message.defaultMessage
        current.exception?.let { exception ->
            details["$key.exception"] = (exception::class.simpleName ?: "Exception") + ": " + exception.message.orEmpty()
        }
        current = current.causes.firstOrNull()
        depth++
    }
    return details
}

/** Flattens a thrown exception and its cause chain into log details keyed under [prefix]. */
fun Throwable.toDiagnosticDetails(prefix: String = "cause"): Map<String, String> {
    val details = linkedMapOf<String, String>()
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_DIAGNOSTIC_CAUSE_DEPTH) {
        val key = if (depth == 0) prefix else "$prefix.cause$depth"
        details["$key.type"] = current::class.simpleName ?: "Exception"
        details["$key.message"] = current.message.orEmpty()
        current = current.cause?.takeIf { it !== current }
        depth++
    }
    return details
}

private const val MAX_DIAGNOSTIC_CAUSE_DEPTH = 4
