/*
 * © 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.mdoc.data.mdl

import com.sphereon.cbor.CborFullDate
import com.sphereon.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DrivingPrivileges related classes.
 */
class DrivingPrivilegesTest {

    // DrivingPrivilegesCbor tests

    @Test
    fun testDrivingPrivilegesCborEmpty() {
        val privileges = DrivingPrivilegesCbor()
        assertTrue(privileges.isEmpty())
    }

    @Test
    fun testDrivingPrivilegesCborWithPrivilege() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        val privileges = DrivingPrivilegesCbor(privilege)
        assertEquals(1, privileges.size)
        assertEquals("B", privileges[0].vehicle_category_code.value)
    }

    @Test
    fun testDrivingPrivilegesCborMultiple() {
        val privilege1 = DrivingPrivilegeCbor(CborString("B"))
        val privilege2 = DrivingPrivilegeCbor(CborString("C"))
        val privileges = DrivingPrivilegesCbor(privilege1, privilege2)
        assertEquals(2, privileges.size)
    }

    @Test
    fun testDrivingPrivilegesCborEquality() {
        val privilege1 = DrivingPrivilegeCbor(CborString("B"))
        val privileges1 = DrivingPrivilegesCbor(privilege1)
        val privileges2 = DrivingPrivilegesCbor(privilege1)
        assertEquals(privileges1, privileges2)
    }

    @Test
    fun testDrivingPrivilegesCborEqualitySameInstance() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        val privileges = DrivingPrivilegesCbor(privilege)
        assertEquals(privileges, privileges)
    }

    @Test
    fun testDrivingPrivilegesCborInequalityDifferentType() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        val privileges = DrivingPrivilegesCbor(privilege)
        assertFalse(privileges.equals("not a DrivingPrivilegesCbor"))
    }

    @Test
    fun testDrivingPrivilegesCborHashCode() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        val privileges1 = DrivingPrivilegesCbor(privilege)
        val privileges2 = DrivingPrivilegesCbor(privilege)
        assertEquals(privileges1.hashCode(), privileges2.hashCode())
    }

    @Test
    fun testDrivingPrivilegesCborToString() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        val privileges = DrivingPrivilegesCbor(privilege)
        val str = privileges.toString()
        assertTrue(str.contains("DrivingPrivileges"))
    }

    @Test
    fun testDrivingPrivilegesCborCborBuilder() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        val privileges = DrivingPrivilegesCbor(privilege)
        val builder = privileges.cborBuilder()
        assertNotNull(builder)
    }

    // DrivingPrivilegesCbor.Builder tests

    @Test
    fun testDrivingPrivilegesCborBuilderEmpty() {
        val builder = DrivingPrivilegesCbor.Builder()
        val privileges = builder.build()
        assertTrue(privileges.isEmpty())
    }

    @Test
    fun testDrivingPrivilegesCborBuilderNewPrivilege() {
        val builder = DrivingPrivilegesCbor.Builder()
        val privilegeBuilder = builder.newPrivilege()
        assertNotNull(privilegeBuilder)
    }

    @Test
    fun testDrivingPrivilegesCborBuilderMultiplePrivileges() {
        val privileges = DrivingPrivilegesCbor.Builder()
            .newPrivilege().withVehicleCategoryCode("B").end()!!
            .newPrivilege().withVehicleCategoryCode("C").end()!!
            .build()
        assertEquals(2, privileges.size)
    }

    // DrivingPrivilegeCbor tests

    @Test
    fun testDrivingPrivilegeCborCreation() {
        val privilege = DrivingPrivilegeCbor(
            vehicle_category_code = CborString("B")
        )
        assertEquals("B", privilege.vehicle_category_code.value)
        assertNull(privilege.issue_date)
        assertNull(privilege.expiry_date)
        assertNull(privilege.codes)
    }

    @Test
    fun testDrivingPrivilegeCborWithDates() {
        val privilege = DrivingPrivilegeCbor(
            vehicle_category_code = CborString("B"),
            issue_date = CborFullDate("2024-01-01"),
            expiry_date = CborFullDate("2034-01-01")
        )
        assertEquals("2024-01-01", privilege.issue_date?.value)
        assertEquals("2034-01-01", privilege.expiry_date?.value)
    }

    @Test
    fun testDrivingPrivilegeCborEquality() {
        val privilege1 = DrivingPrivilegeCbor(CborString("B"))
        val privilege2 = DrivingPrivilegeCbor(CborString("B"))
        assertEquals(privilege1, privilege2)
    }

    @Test
    fun testDrivingPrivilegeCborEqualitySameInstance() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        assertEquals(privilege, privilege)
    }

    @Test
    fun testDrivingPrivilegeCborInequalityNull() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        assertFalse(privilege.equals(null))
    }

    @Test
    fun testDrivingPrivilegeCborInequalityDifferentType() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        assertFalse(privilege.equals("not a privilege"))
    }

    @Test
    fun testDrivingPrivilegeCborInequalityDifferentCode() {
        val privilege1 = DrivingPrivilegeCbor(CborString("B"))
        val privilege2 = DrivingPrivilegeCbor(CborString("C"))
        assertNotEquals(privilege1, privilege2)
    }

    @Test
    fun testDrivingPrivilegeCborInequalityDifferentIssueDate() {
        val privilege1 = DrivingPrivilegeCbor(CborString("B"), issue_date = CborFullDate("2024-01-01"))
        val privilege2 = DrivingPrivilegeCbor(CborString("B"), issue_date = CborFullDate("2025-01-01"))
        assertNotEquals(privilege1, privilege2)
    }

    @Test
    fun testDrivingPrivilegeCborInequalityDifferentExpiryDate() {
        val privilege1 = DrivingPrivilegeCbor(CborString("B"), expiry_date = CborFullDate("2034-01-01"))
        val privilege2 = DrivingPrivilegeCbor(CborString("B"), expiry_date = CborFullDate("2035-01-01"))
        assertNotEquals(privilege1, privilege2)
    }

    @Test
    fun testDrivingPrivilegeCborHashCode() {
        val privilege1 = DrivingPrivilegeCbor(CborString("B"))
        val privilege2 = DrivingPrivilegeCbor(CborString("B"))
        assertEquals(privilege1.hashCode(), privilege2.hashCode())
    }

    @Test
    fun testDrivingPrivilegeCborToString() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        val str = privilege.toString()
        assertTrue(str.contains("DrivingPrivilege"))
        assertTrue(str.contains("vehicle_category_code"))
    }

    @Test
    fun testDrivingPrivilegeCborCborBuilder() {
        val privilege = DrivingPrivilegeCbor(CborString("B"))
        val builder = privilege.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDrivingPrivilegeCborCompanionLabels() {
        assertEquals("vehicle_category_code", DrivingPrivilegeCbor.VEHICLE_CATEGORY_CODE.value)
        assertEquals("issue_date", DrivingPrivilegeCbor.ISSUE_DATE.value)
        assertEquals("expiry_date", DrivingPrivilegeCbor.EXPIRY_DATE.value)
        assertEquals("codes", DrivingPrivilegeCbor.CODES.value)
    }

    // DrivingPrivilegeCbor.Builder tests

    @Test
    fun testDrivingPrivilegeCborBuilderBuild() {
        val privilege = DrivingPrivilegeCbor.Builder()
            .withVehicleCategoryCode("B")
            .build()
        assertEquals("B", privilege.vehicle_category_code.value)
    }

    @Test
    fun testDrivingPrivilegeCborBuilderWithDates() {
        val privilege = DrivingPrivilegeCbor.Builder()
            .withVehicleCategoryCode("B")
            .withIssueDate("2024-01-01")
            .withExpiryDate("2034-01-01")
            .build()
        assertEquals("2024-01-01", privilege.issue_date?.value)
        assertEquals("2034-01-01", privilege.expiry_date?.value)
    }

    @Test
    fun testDrivingPrivilegeCborBuilderWithDatesMethod() {
        val privilege = DrivingPrivilegeCbor.Builder()
            .withVehicleCategoryCode("B")
            .withDates("2024-01-01", "2034-01-01")
            .build()
        assertEquals("2024-01-01", privilege.issue_date?.value)
        assertEquals("2034-01-01", privilege.expiry_date?.value)
    }

    @Test
    fun testDrivingPrivilegeCborBuilderWithNullDates() {
        val privilege = DrivingPrivilegeCbor.Builder()
            .withVehicleCategoryCode("B")
            .withDates(null, null)
            .build()
        assertNull(privilege.issue_date)
        assertNull(privilege.expiry_date)
    }

    @Test
    fun testDrivingPrivilegeCborBuilderWithCodes() {
        val code = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        val privilege = DrivingPrivilegeCbor.Builder()
            .withVehicleCategoryCode("B")
            .withCodes(code)
            .build()
        assertEquals(1, privilege.codes?.size)
    }

    @Test
    fun testDrivingPrivilegeCborBuilderAddCodes() {
        val code = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        val privilege = DrivingPrivilegeCbor.Builder()
            .withVehicleCategoryCode("B")
            .addCodes(code)
            .build()
        assertEquals(1, privilege.codes?.size)
    }

    @Test
    fun testDrivingPrivilegeCborBuilderAddCode() {
        val privilege = DrivingPrivilegeCbor.Builder()
            .withVehicleCategoryCode("B")
            .addCode("78", "=", "1")
            .build()
        assertEquals(1, privilege.codes?.size)
        assertEquals("78", privilege.codes?.get(0)?.code?.value)
    }

    @Test
    fun testDrivingPrivilegeCborBuilderMissingVehicleCodeThrows() {
        assertFailsWith<IllegalArgumentException> {
            DrivingPrivilegeCbor.Builder().build()
        }
    }

    @Test
    fun testDrivingPrivilegeCborBuilderNewPrivilegeWithoutParentThrows() {
        val builder = DrivingPrivilegeCbor.Builder()
        assertFailsWith<IllegalArgumentException> {
            builder.newPrivilege()
        }
    }

    @Test
    fun testDrivingPrivilegeCborBuilderBuildPrivilegesWithoutParentThrows() {
        val builder = DrivingPrivilegeCbor.Builder()
        assertFailsWith<IllegalArgumentException> {
            builder.buildPrivileges()
        }
    }

    @Test
    fun testDrivingPrivilegeCborBuilderEnd() {
        val privilegesBuilder = DrivingPrivilegesCbor.Builder()
        val privilegeBuilder = DrivingPrivilegeCbor.Builder(parent = privilegesBuilder)
        val result = privilegeBuilder.end()
        assertEquals(privilegesBuilder, result)
    }

    @Test
    fun testDrivingPrivilegeCborBuilderEndNullParent() {
        val privilegeBuilder = DrivingPrivilegeCbor.Builder()
        val result = privilegeBuilder.end()
        assertNull(result)
    }

    // DrivingPrivilegesCodeCbor tests

    @Test
    fun testDrivingPrivilegesCodeCborCreation() {
        val code = DrivingPrivilegesCodeCbor(
            code = CborString("78"),
            sign = CborString("="),
            value = CborString("1")
        )
        assertEquals("78", code.code.value)
        assertEquals("=", code.sign?.value)
        assertEquals("1", code.value?.value)
    }

    @Test
    fun testDrivingPrivilegesCodeCborMinimal() {
        val code = DrivingPrivilegesCodeCbor(
            code = CborString("78"),
            sign = null,
            value = null
        )
        assertEquals("78", code.code.value)
        assertNull(code.sign)
        assertNull(code.value)
    }

    @Test
    fun testDrivingPrivilegesCodeCborEquality() {
        val code1 = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        val code2 = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        assertEquals(code1, code2)
    }

    @Test
    fun testDrivingPrivilegesCodeCborEqualitySameInstance() {
        val code = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        assertEquals(code, code)
    }

    @Test
    fun testDrivingPrivilegesCodeCborInequalityDifferentType() {
        val code = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        assertFalse(code.equals("not a code"))
    }

    @Test
    fun testDrivingPrivilegesCodeCborInequalityDifferentCode() {
        val code1 = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        val code2 = DrivingPrivilegesCodeCbor(CborString("79"), null, null)
        assertNotEquals(code1, code2)
    }

    @Test
    fun testDrivingPrivilegesCodeCborInequalityDifferentSign() {
        val code1 = DrivingPrivilegesCodeCbor(CborString("78"), CborString("="), null)
        val code2 = DrivingPrivilegesCodeCbor(CborString("78"), CborString("<"), null)
        assertNotEquals(code1, code2)
    }

    @Test
    fun testDrivingPrivilegesCodeCborInequalityDifferentValue() {
        val code1 = DrivingPrivilegesCodeCbor(CborString("78"), null, CborString("1"))
        val code2 = DrivingPrivilegesCodeCbor(CborString("78"), null, CborString("2"))
        assertNotEquals(code1, code2)
    }

    @Test
    fun testDrivingPrivilegesCodeCborHashCode() {
        val code1 = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        val code2 = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        assertEquals(code1.hashCode(), code2.hashCode())
    }

    @Test
    fun testDrivingPrivilegesCodeCborToString() {
        val code = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        val str = code.toString()
        assertTrue(str.contains("DrivingPrivilegesCode"))
        assertTrue(str.contains("code"))
    }

    @Test
    fun testDrivingPrivilegesCodeCborCborBuilder() {
        val code = DrivingPrivilegesCodeCbor(CborString("78"), null, null)
        val builder = code.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDrivingPrivilegesCodeCborCompanionLabels() {
        assertEquals("code", DrivingPrivilegesCodeCbor.CODE.value)
        assertEquals("sign", DrivingPrivilegesCodeCbor.SIGN.value)
        assertEquals("value", DrivingPrivilegesCodeCbor.VALUE.value)
    }
}
