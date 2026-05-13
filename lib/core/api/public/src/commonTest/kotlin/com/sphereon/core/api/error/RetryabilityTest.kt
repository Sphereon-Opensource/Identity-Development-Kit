package com.sphereon.core.api.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetryabilityTest {
    @Test
    fun interfaceDefaultIsNone() {
        val plain =
            object : IdkErrorType {
                override val code = "PLAIN"
                override val message = IdkError.Message(i18nKey = "k", defaultMessage = "m")
                override val severity = IdkError.Severity.ERROR
                override val exception: Throwable? = null
                override val causes: List<IdkErrorType> = emptyList()
                override val meta: Map<String, Any?> = emptyMap()
            }
        assertEquals(Retryability.NONE, plain.retryability)
        assertNull(plain.retryAfter)
    }

    @Test
    fun overrideMakesErrorTransient() {
        val transient =
            object : IdkErrorType {
                override val code = "TRANSIENT"
                override val message = IdkError.Message(i18nKey = "k", defaultMessage = "m")
                override val severity = IdkError.Severity.ERROR
                override val exception: Throwable? = null
                override val causes: List<IdkErrorType> = emptyList()
                override val meta: Map<String, Any?> = emptyMap()
                override val retryability = Retryability.TRANSIENT
            }
        assertEquals(Retryability.TRANSIENT, transient.retryability)
    }

    @Test
    fun idkErrorBuiltDirectlyHasNoneRetryability() {
        val err = IdkError.fromString(message = "boom")
        assertEquals(Retryability.NONE, err.retryability)
        assertNull(err.retryAfter)
    }

    @Test
    fun idkErrorFromDtoForwardsTransientSourceClassification() {
        val source =
            object : IdkErrorType {
                override val code = "X"
                override val message = IdkError.Message(i18nKey = "k", defaultMessage = "m")
                override val severity = IdkError.Severity.ERROR
                override val exception: Throwable? = null
                override val causes: List<IdkErrorType> = emptyList()
                override val meta: Map<String, Any?> = emptyMap()
                override val retryability = Retryability.TRANSIENT
            }
        val wrapped = IdkError.fromDTO(source)
        assertEquals(Retryability.TRANSIENT, wrapped.retryability)
    }
}
