package com.sphereon.core.api.service

import kotlinx.serialization.Serializable

/**
 * Wrapper for commands that return a String value.
 *
 * Used instead of raw `String` as `ServiceCommand<Args, String>` output type
 * to ensure proper serialization through binary transport (EDK remote/server modules).
 *
 * @property value The string result value
 */
@Serializable
data class StringResult(val value: String)

/**
 * Wrapper for commands that return Unit (no meaningful output).
 *
 * Used instead of raw `Unit` as `ServiceCommand<Args, Unit>` output type
 * to ensure proper serialization through binary transport (EDK remote/server modules).
 */
@Serializable
data object EmptyResult
