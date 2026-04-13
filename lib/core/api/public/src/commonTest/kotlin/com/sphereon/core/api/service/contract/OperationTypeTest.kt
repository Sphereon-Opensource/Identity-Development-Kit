package com.sphereon.core.api.service.contract

import com.sphereon.core.api.service.ActionType
import kotlin.test.Test
import kotlin.test.assertEquals

class OperationTypeTest {
    @Test
    fun fromActionTypeMapsCrudCorrectly() {
        assertEquals(OperationType.CREATE, OperationType.fromActionType(ActionType.CREATE))
        assertEquals(OperationType.READ, OperationType.fromActionType(ActionType.READ))
        assertEquals(OperationType.UPDATE, OperationType.fromActionType(ActionType.UPDATE))
        assertEquals(OperationType.DELETE, OperationType.fromActionType(ActionType.DELETE))
        assertEquals(OperationType.LIST, OperationType.fromActionType(ActionType.LIST))
        assertEquals(OperationType.EXECUTE, OperationType.fromActionType(ActionType.EXECUTE))
    }

    @Test
    fun customOperationTypeIsExtensible() {
        val custom = OperationType("my_custom_op")
        assertEquals("my_custom_op", custom.value)
    }

    @Test
    fun standardConstantsHaveCorrectValues() {
        assertEquals("sign", OperationType.SIGN.value)
        assertEquals("generate", OperationType.GENERATE.value)
        assertEquals("issue", OperationType.ISSUE.value)
        assertEquals("resolve", OperationType.RESOLVE.value)
        assertEquals("introspect", OperationType.INTROSPECT.value)
    }

    @Test
    fun equalityWorksForSameValue() {
        assertEquals(OperationType("sign"), OperationType.SIGN)
    }
}
