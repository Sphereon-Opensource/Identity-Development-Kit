/*
 * © 2026 Sphereon International B.V.
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
    // DrivingPrivileges tests

    @Test
    fun testDrivingPrivilegesEmpty() {
        val privileges = DrivingPrivileges()
        assertTrue(privileges.isEmpty())
    }

    @Test
    fun testDrivingPrivilegesWithPrivilege() {
        val privilege = DrivingPrivilege(CborString("B"))
        val privileges = DrivingPrivileges(privilege)
        assertEquals(1, privileges.size)
        assertEquals("B", privileges[0].vehicle_category_code.value)
    }

    @Test
    fun testDrivingPrivilegesMultiple() {
        val privilege1 = DrivingPrivilege(CborString("B"))
        val privilege2 = DrivingPrivilege(CborString("C"))
        val privileges = DrivingPrivileges(privilege1, privilege2)
        assertEquals(2, privileges.size)
    }

    @Test
    fun testDrivingPrivilegesEquality() {
        val privilege1 = DrivingPrivilege(CborString("B"))
        val privileges1 = DrivingPrivileges(privilege1)
        val privileges2 = DrivingPrivileges(privilege1)
        assertEquals(privileges1, privileges2)
    }

    @Test
    fun testDrivingPrivilegesEqualitySameInstance() {
        val privilege = DrivingPrivilege(CborString("B"))
        val privileges = DrivingPrivileges(privilege)
        assertEquals(privileges, privileges)
    }

    @Test
    fun testDrivingPrivilegesInequalityDifferentType() {
        val privilege = DrivingPrivilege(CborString("B"))
        val privileges = DrivingPrivileges(privilege)
        assertFalse(privileges.equals("not a DrivingPrivileges"))
    }

    @Test
    fun testDrivingPrivilegesHashCode() {
        val privilege = DrivingPrivilege(CborString("B"))
        val privileges1 = DrivingPrivileges(privilege)
        val privileges2 = DrivingPrivileges(privilege)
        assertEquals(privileges1.hashCode(), privileges2.hashCode())
    }

    @Test
    fun testDrivingPrivilegesToString() {
        val privilege = DrivingPrivilege(CborString("B"))
        val privileges = DrivingPrivileges(privilege)
        val str = privileges.toString()
        assertTrue(str.contains("DrivingPrivileges"))
    }

    // DrivingPrivileges.Builder tests

    @Test
    fun testDrivingPrivilegesBuilderEmpty() {
        val builder = DrivingPrivileges.Builder()
        val privileges = builder.build()
        assertTrue(privileges.isEmpty())
    }

    @Test
    fun testDrivingPrivilegesBuilderNewPrivilege() {
        val builder = DrivingPrivileges.Builder()
        val privilegeBuilder = builder.newPrivilege()
        assertNotNull(privilegeBuilder)
    }

    @Test
    fun testDrivingPrivilegesBuilderMultiplePrivileges() {
        val privileges =
            DrivingPrivileges
                .Builder()
                .newPrivilege()
                .withVehicleCategoryCode("B")
                .end()!!
                .newPrivilege()
                .withVehicleCategoryCode("C")
                .end()!!
                .build()
        assertEquals(2, privileges.size)
    }

    // DrivingPrivilege tests

    @Test
    fun testDrivingPrivilegeCreation() {
        val privilege =
            DrivingPrivilege(
                vehicle_category_code = CborString("B"),
            )
        assertEquals("B", privilege.vehicle_category_code.value)
        assertNull(privilege.issue_date)
        assertNull(privilege.expiry_date)
        assertNull(privilege.codes)
    }

    @Test
    fun testDrivingPrivilegeWithDates() {
        val privilege =
            DrivingPrivilege(
                vehicle_category_code = CborString("B"),
                issue_date = CborFullDate("2024-01-01"),
                expiry_date = CborFullDate("2034-01-01"),
            )
        assertEquals("2024-01-01", privilege.issue_date?.value)
        assertEquals("2034-01-01", privilege.expiry_date?.value)
    }

    @Test
    fun testDrivingPrivilegeEquality() {
        val privilege1 = DrivingPrivilege(CborString("B"))
        val privilege2 = DrivingPrivilege(CborString("B"))
        assertEquals(privilege1, privilege2)
    }

    @Test
    fun testDrivingPrivilegeEqualitySameInstance() {
        val privilege = DrivingPrivilege(CborString("B"))
        assertEquals(privilege, privilege)
    }

    @Test
    fun testDrivingPrivilegeInequalityNull() {
        val privilege = DrivingPrivilege(CborString("B"))
        assertFalse(privilege.equals(null))
    }

    @Test
    fun testDrivingPrivilegeInequalityDifferentType() {
        val privilege = DrivingPrivilege(CborString("B"))
        assertFalse(privilege.equals("not a privilege"))
    }

    @Test
    fun testDrivingPrivilegeInequalityDifferentCode() {
        val privilege1 = DrivingPrivilege(CborString("B"))
        val privilege2 = DrivingPrivilege(CborString("C"))
        assertNotEquals(privilege1, privilege2)
    }

    @Test
    fun testDrivingPrivilegeInequalityDifferentIssueDate() {
        val privilege1 = DrivingPrivilege(CborString("B"), issue_date = CborFullDate("2024-01-01"))
        val privilege2 = DrivingPrivilege(CborString("B"), issue_date = CborFullDate("2025-01-01"))
        assertNotEquals(privilege1, privilege2)
    }

    @Test
    fun testDrivingPrivilegeInequalityDifferentExpiryDate() {
        val privilege1 = DrivingPrivilege(CborString("B"), expiry_date = CborFullDate("2034-01-01"))
        val privilege2 = DrivingPrivilege(CborString("B"), expiry_date = CborFullDate("2035-01-01"))
        assertNotEquals(privilege1, privilege2)
    }

    @Test
    fun testDrivingPrivilegeHashCode() {
        val privilege1 = DrivingPrivilege(CborString("B"))
        val privilege2 = DrivingPrivilege(CborString("B"))
        assertEquals(privilege1.hashCode(), privilege2.hashCode())
    }

    @Test
    fun testDrivingPrivilegeToString() {
        val privilege = DrivingPrivilege(CborString("B"))
        val str = privilege.toString()
        assertTrue(str.contains("DrivingPrivilege"))
        assertTrue(str.contains("vehicle_category_code"))
    }

    @Test
    fun testDrivingPrivilegeCompanionLabels() {
        assertEquals("vehicle_category_code", DrivingPrivilege.VEHICLE_CATEGORY_CODE.value)
        assertEquals("issue_date", DrivingPrivilege.ISSUE_DATE.value)
        assertEquals("expiry_date", DrivingPrivilege.EXPIRY_DATE.value)
        assertEquals("codes", DrivingPrivilege.CODES.value)
    }

    // DrivingPrivilege.Builder tests

    @Test
    fun testDrivingPrivilegeBuilderBuild() {
        val privilege =
            DrivingPrivilege
                .Builder()
                .withVehicleCategoryCode("B")
                .build()
        assertEquals("B", privilege.vehicle_category_code.value)
    }

    @Test
    fun testDrivingPrivilegeBuilderWithDates() {
        val privilege =
            DrivingPrivilege
                .Builder()
                .withVehicleCategoryCode("B")
                .withIssueDate("2024-01-01")
                .withExpiryDate("2034-01-01")
                .build()
        assertEquals("2024-01-01", privilege.issue_date?.value)
        assertEquals("2034-01-01", privilege.expiry_date?.value)
    }

    @Test
    fun testDrivingPrivilegeBuilderWithDatesMethod() {
        val privilege =
            DrivingPrivilege
                .Builder()
                .withVehicleCategoryCode("B")
                .withDates("2024-01-01", "2034-01-01")
                .build()
        assertEquals("2024-01-01", privilege.issue_date?.value)
        assertEquals("2034-01-01", privilege.expiry_date?.value)
    }

    @Test
    fun testDrivingPrivilegeBuilderWithNullDates() {
        val privilege =
            DrivingPrivilege
                .Builder()
                .withVehicleCategoryCode("B")
                .withDates(null, null)
                .build()
        assertNull(privilege.issue_date)
        assertNull(privilege.expiry_date)
    }

    @Test
    fun testDrivingPrivilegeBuilderWithCodes() {
        val code = DrivingPrivilegesCode(CborString("78"), null, null)
        val privilege =
            DrivingPrivilege
                .Builder()
                .withVehicleCategoryCode("B")
                .withCodes(code)
                .build()
        assertEquals(1, privilege.codes?.size)
    }

    @Test
    fun testDrivingPrivilegeBuilderAddCodes() {
        val code = DrivingPrivilegesCode(CborString("78"), null, null)
        val privilege =
            DrivingPrivilege
                .Builder()
                .withVehicleCategoryCode("B")
                .addCodes(code)
                .build()
        assertEquals(1, privilege.codes?.size)
    }

    @Test
    fun testDrivingPrivilegeBuilderAddCode() {
        val privilege =
            DrivingPrivilege
                .Builder()
                .withVehicleCategoryCode("B")
                .addCode("78", "=", "1")
                .build()
        assertEquals(1, privilege.codes?.size)
        assertEquals(
            "78",
            privilege.codes
                ?.get(0)
                ?.code
                ?.value,
        )
    }

    @Test
    fun testDrivingPrivilegeBuilderMissingVehicleCodeThrows() {
        assertFailsWith<IllegalArgumentException> {
            DrivingPrivilege.Builder().build()
        }
    }

    @Test
    fun testDrivingPrivilegeBuilderNewPrivilegeWithoutParentThrows() {
        val builder = DrivingPrivilege.Builder()
        assertFailsWith<IllegalArgumentException> {
            builder.newPrivilege()
        }
    }

    @Test
    fun testDrivingPrivilegeBuilderBuildPrivilegesWithoutParentThrows() {
        val builder = DrivingPrivilege.Builder()
        assertFailsWith<IllegalArgumentException> {
            builder.buildPrivileges()
        }
    }

    @Test
    fun testDrivingPrivilegeBuilderEnd() {
        val privilegesBuilder = DrivingPrivileges.Builder()
        val privilegeBuilder = DrivingPrivilege.Builder(parent = privilegesBuilder)
        val result = privilegeBuilder.end()
        assertEquals(privilegesBuilder, result)
    }

    @Test
    fun testDrivingPrivilegeBuilderEndNullParent() {
        val privilegeBuilder = DrivingPrivilege.Builder()
        val result = privilegeBuilder.end()
        assertNull(result)
    }

    // DrivingPrivilegesCode tests

    @Test
    fun testDrivingPrivilegesCodeCreation() {
        val code =
            DrivingPrivilegesCode(
                code = CborString("78"),
                sign = CborString("="),
                value = CborString("1"),
            )
        assertEquals("78", code.code.value)
        assertEquals("=", code.sign?.value)
        assertEquals("1", code.value?.value)
    }

    @Test
    fun testDrivingPrivilegesCodeMinimal() {
        val code =
            DrivingPrivilegesCode(
                code = CborString("78"),
                sign = null,
                value = null,
            )
        assertEquals("78", code.code.value)
        assertNull(code.sign)
        assertNull(code.value)
    }

    @Test
    fun testDrivingPrivilegesCodeEquality() {
        val code1 = DrivingPrivilegesCode(CborString("78"), null, null)
        val code2 = DrivingPrivilegesCode(CborString("78"), null, null)
        assertEquals(code1, code2)
    }

    @Test
    fun testDrivingPrivilegesCodeEqualitySameInstance() {
        val code = DrivingPrivilegesCode(CborString("78"), null, null)
        assertEquals(code, code)
    }

    @Test
    fun testDrivingPrivilegesCodeInequalityDifferentType() {
        val code = DrivingPrivilegesCode(CborString("78"), null, null)
        assertFalse(code.equals("not a code"))
    }

    @Test
    fun testDrivingPrivilegesCodeInequalityDifferentCode() {
        val code1 = DrivingPrivilegesCode(CborString("78"), null, null)
        val code2 = DrivingPrivilegesCode(CborString("79"), null, null)
        assertNotEquals(code1, code2)
    }

    @Test
    fun testDrivingPrivilegesCodeInequalityDifferentSign() {
        val code1 = DrivingPrivilegesCode(CborString("78"), CborString("="), null)
        val code2 = DrivingPrivilegesCode(CborString("78"), CborString("<"), null)
        assertNotEquals(code1, code2)
    }

    @Test
    fun testDrivingPrivilegesCodeInequalityDifferentValue() {
        val code1 = DrivingPrivilegesCode(CborString("78"), null, CborString("1"))
        val code2 = DrivingPrivilegesCode(CborString("78"), null, CborString("2"))
        assertNotEquals(code1, code2)
    }

    @Test
    fun testDrivingPrivilegesCodeHashCode() {
        val code1 = DrivingPrivilegesCode(CborString("78"), null, null)
        val code2 = DrivingPrivilegesCode(CborString("78"), null, null)
        assertEquals(code1.hashCode(), code2.hashCode())
    }

    @Test
    fun testDrivingPrivilegesCodeToString() {
        val code = DrivingPrivilegesCode(CborString("78"), null, null)
        val str = code.toString()
        assertTrue(str.contains("DrivingPrivilegesCode"))
        assertTrue(str.contains("code"))
    }

    @Test
    fun testDrivingPrivilegesCodeCompanionLabels() {
        assertEquals("code", DrivingPrivilegesCode.CODE.value)
        assertEquals("sign", DrivingPrivilegesCode.SIGN.value)
        assertEquals("value", DrivingPrivilegesCode.VALUE.value)
    }
}
