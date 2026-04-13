package com.sphereon.core.api.service.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableExecutionStateTest {
    @Test
    fun setAndGetTypedValue() {
        val state = MutableExecutionState()
        val plan = CommandPlan.leaf()

        state[ExecutionStateKeys.PLAN] = plan
        assertEquals(plan, state[ExecutionStateKeys.PLAN])
    }

    @Test
    fun getMissingKeyReturnsNull() {
        val state = MutableExecutionState()
        assertNull(state[ExecutionStateKeys.PLAN])
    }

    @Test
    fun hasReturnsTrueForSetKey() {
        val state = MutableExecutionState()
        assertFalse(state.has(ExecutionStateKeys.PLAN))

        state[ExecutionStateKeys.PLAN] = CommandPlan.leaf()
        assertTrue(state.has(ExecutionStateKeys.PLAN))
    }

    @Test
    fun customKeysWork() {
        val state = MutableExecutionState()
        val key = ExecutionStateKey<String>("myCustomKey")

        state[key] = "hello"
        assertEquals("hello", state[key])
    }

    @Test
    fun overwriteExistingKey() {
        val state = MutableExecutionState()
        val plan1 = CommandPlan.leaf()
        val plan2 = CommandPlan(listOf(CommandIntent("test.cmd.a")))

        state[ExecutionStateKeys.PLAN] = plan1
        state[ExecutionStateKeys.PLAN] = plan2

        assertEquals(plan2, state[ExecutionStateKeys.PLAN])
        assertEquals(1, state[ExecutionStateKeys.PLAN]!!.intents.size)
    }
}
