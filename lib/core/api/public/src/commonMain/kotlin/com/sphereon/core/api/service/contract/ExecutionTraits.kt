package com.sphereon.core.api.service.contract

import kotlinx.serialization.Serializable

/**
 * Declares execution characteristics of a command.
 *
 * These traits inform the EDK/VDX transport layer about how a command can be
 * executed: whether it's safe to retry, defer, or schedule.
 *
 * All fields are nullable — null means "unknown/unspecified". This is conservative:
 * unknown commands are not assumed safe for deferral or retry.
 *
 * Declared values are defaults — EDK config can override per commandId pattern and per tenant.
 */
@Serializable
data class ExecutionTraits(
    /** True if executing multiple times with the same input produces the same result. Enables safe retry. */
    val isIdempotent: Boolean? = null,
    /** True if this command can be queued for later execution. */
    val supportsDeferral: Boolean? = null,
    /** True if this command can be scheduled for recurring execution. */
    val supportsScheduling: Boolean? = null,
    /** Expected execution duration category. */
    val expectedDuration: ExpectedDuration? = null,
) {
    companion object {
        val UNSPECIFIED = ExecutionTraits()
        val READ_ONLY = ExecutionTraits(isIdempotent = true, supportsDeferral = true)
        val MUTATING = ExecutionTraits(isIdempotent = false, supportsDeferral = true)
        val LONG_RUNNING =
            ExecutionTraits(
                isIdempotent = false,
                supportsDeferral = true,
                expectedDuration = ExpectedDuration.LONG,
            )
    }
}

@Serializable
enum class ExpectedDuration {
    /** < 1 second typical */
    SHORT,

    /** 1-30 seconds typical */
    MEDIUM,

    /** > 30 seconds typical (external API calls, credential issuance, IDV workflows) */
    LONG,
}

// ========== DSL ==========

fun executionTraits(block: ExecutionTraitsBuilder.() -> Unit): ExecutionTraits = ExecutionTraitsBuilder().apply(block).build()

class ExecutionTraitsBuilder {
    private var isIdempotent: Boolean? = null
    private var supportsDeferral: Boolean? = null
    private var supportsScheduling: Boolean? = null
    private var expectedDuration: ExpectedDuration? = null

    fun idempotent() {
        isIdempotent = true
    }

    fun notIdempotent() {
        isIdempotent = false
    }

    fun supportsDeferral() {
        supportsDeferral = true
    }

    fun noDeferral() {
        supportsDeferral = false
    }

    fun supportsScheduling() {
        supportsScheduling = true
    }

    fun shortDuration() {
        expectedDuration = ExpectedDuration.SHORT
    }

    fun mediumDuration() {
        expectedDuration = ExpectedDuration.MEDIUM
    }

    fun longDuration() {
        expectedDuration = ExpectedDuration.LONG
    }

    fun readOnly() {
        isIdempotent = true
        supportsDeferral = true
    }

    fun mutating() {
        isIdempotent = false
        supportsDeferral = true
    }

    fun build() = ExecutionTraits(isIdempotent, supportsDeferral, supportsScheduling, expectedDuration)
}
