package com.sphereon.core.api.service.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExecutionTraitsTest {
    @Test
    fun unspecifiedHasAllNulls() {
        val traits = ExecutionTraits.UNSPECIFIED
        assertNull(traits.isIdempotent)
        assertNull(traits.supportsDeferral)
        assertNull(traits.supportsScheduling)
        assertNull(traits.expectedDuration)
    }

    @Test
    fun readOnlyPreset() {
        val traits = ExecutionTraits.READ_ONLY
        assertEquals(true, traits.isIdempotent)
        assertEquals(true, traits.supportsDeferral)
    }

    @Test
    fun mutatingPreset() {
        val traits = ExecutionTraits.MUTATING
        assertEquals(false, traits.isIdempotent)
        assertEquals(true, traits.supportsDeferral)
    }

    @Test
    fun dslBuildsMutating() {
        val traits =
            executionTraits {
                mutating()
                mediumDuration()
            }
        assertEquals(false, traits.isIdempotent)
        assertEquals(true, traits.supportsDeferral)
        assertEquals(ExpectedDuration.MEDIUM, traits.expectedDuration)
    }

    @Test
    fun dslBuildsReadOnly() {
        val traits =
            executionTraits {
                readOnly()
                shortDuration()
            }
        assertEquals(true, traits.isIdempotent)
        assertEquals(true, traits.supportsDeferral)
        assertEquals(ExpectedDuration.SHORT, traits.expectedDuration)
    }

    @Test
    fun dslBuildsCustom() {
        val traits =
            executionTraits {
                notIdempotent()
                noDeferral()
                supportsScheduling()
                longDuration()
            }
        assertEquals(false, traits.isIdempotent)
        assertEquals(false, traits.supportsDeferral)
        assertEquals(true, traits.supportsScheduling)
        assertEquals(ExpectedDuration.LONG, traits.expectedDuration)
    }
}
