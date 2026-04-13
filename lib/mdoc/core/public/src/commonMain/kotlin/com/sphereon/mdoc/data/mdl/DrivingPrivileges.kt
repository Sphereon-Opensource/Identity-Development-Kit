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
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cddl_full_date
import com.sphereon.cbor.cddl_tstr
import com.sphereon.cbor.instantToDateStringISO
import com.sphereon.cbor.localDateTimeToDateStringISO
import com.sphereon.cbor.localDateToDateStringISO
import com.sphereon.cbor.toCborString
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.core.compat.getDateTime
import com.sphereon.core.compat.toKotlin
import kotlinx.datetime.LocalDate
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.native.ObjCName
import kotlin.time.Instant

typealias DrivingPrivilegesBuilder = DrivingPrivileges.Builder

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DrivingPrivileges", exact = true)
data class DrivingPrivileges(
    private val backing: List<DrivingPrivilege> = mutableListOf(),
) : List<DrivingPrivilege> by backing {
    @JsName("fromVarArgs")
    constructor(vararg drivingPrivileges: DrivingPrivilege) : this(drivingPrivileges.toMutableList())

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    data class Builder(
        val privilegeBuilders: MutableList<DrivingPrivilegeBuilder> = mutableListOf(),
    ) {
        fun newPrivilege(): DrivingPrivilegeBuilder {
            val privilegeBuilder = DrivingPrivilegeBuilder(parent = this)
            this.privilegeBuilders.add(privilegeBuilder)
            return privilegeBuilder
        }

        fun build() = DrivingPrivileges(privilegeBuilders.map { it.build() }.toMutableList())
    }

    override fun toString(): String = "DrivingPrivileges(privileges=$backing)"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is DrivingPrivileges) {
            return false
        }

        if (backing != other.backing) {
            return false
        }

        return true
    }

    override fun hashCode(): Int = backing.hashCode()
}

typealias DrivingPrivilegeBuilder = DrivingPrivilege.Builder

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DrivingPrivilege", exact = true)
data class DrivingPrivilege(
    val vehicle_category_code: CborString,
    val issue_date: CborFullDate? = null,
    val expiry_date: CborFullDate? = null,
    val codes: List<DrivingPrivilegesCode>? = null,
) {
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    data class Builder(
        val parent: DrivingPrivileges.Builder? = null,
        var vehicleCategoryCode: CborString? = null,
        var issueDate: CborFullDate? = null,
        var expiryDate: CborFullDate? = null,
        var codes: MutableList<DrivingPrivilegesCode>? = mutableListOf(),
    ) {
        fun newPrivilege(): Builder {
            require(parent != null) { "No parent present. Cannot call add" }
            return parent.newPrivilege()
        }

        fun buildPrivileges(): DrivingPrivileges {
            require(parent != null) { "No parent present. Cannot call add" }
            return parent.build()
        }

        fun withVehicleCategoryCode(vehicleCategoryCode: cddl_tstr) = apply { this.vehicleCategoryCode = vehicleCategoryCode.toCborString() }

        fun withIssueDateUsingLocalDateTime(
            issueDate: LocalDateTimeKMP,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) = apply {
            this.issueDate = CborFullDate(issueDate.toKotlin().localDateTimeToDateStringISO(utils, timeZoneId))
        }

        fun withIssueDateUsingLocalDate(
            issueDate: LocalDate,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) = apply { this.issueDate = CborFullDate(issueDate.localDateToDateStringISO(utils, timeZoneId)) }

        fun withIssueDateUsingEpochSeconds(
            epochSeconds: Int,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) = apply { this.issueDate = CborFullDate(getDateTime(utils).dateISO(timeZoneId, epochSeconds)) }

        fun withIssueDate(issueDate: cddl_full_date) = apply { this.issueDate = CborFullDate(issueDate) }

        fun withExpiryDateUsingLocalDateTime(
            issueDate: LocalDateTimeKMP,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) = apply {
            this.expiryDate = CborFullDate(issueDate.toKotlin().localDateTimeToDateStringISO(utils, timeZoneId))
        }

        fun withExpiryDateUsingLocalDate(
            issueDate: LocalDate,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) = apply { this.expiryDate = CborFullDate(issueDate.localDateToDateStringISO(utils, timeZoneId)) }

        fun withExpiryDateUsingInstant(
            issueDate: Instant,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) = apply { this.expiryDate = CborFullDate(issueDate.instantToDateStringISO(utils, timeZoneId)) }

        fun withExpiryDate(expiryDate: cddl_full_date) = apply { this.expiryDate = CborFullDate(expiryDate) }

        fun withDates(
            issueDate: cddl_full_date?,
            expiryDate: cddl_full_date?,
        ) = apply {
            issueDate?.let { withIssueDate(it) }
            expiryDate?.let { withExpiryDate(it) }
        }

        //        fun addCodes(vararg codes: DrivingPrivilegesCode) = apply { this.codes!!.addAll(codes) }
        fun addCodes(vararg codes: DrivingPrivilegesCode) = apply { this.codes!!.addAll(codes) }

        fun addCode(
            code: cddl_tstr,
            sign: cddl_tstr? = null,
            value: cddl_tstr? = null,
        ) = apply {
            this.addCodes(DrivingPrivilegesCode(code.toCborString(), sign?.toCborString(), value?.toCborString()))
        }

        //        fun withCodes(vararg codes: DrivingPrivilegesCode) = apply { this.codes = codes.toMutableList() }
        fun withCodes(vararg codes: DrivingPrivilegesCode) = apply { this.codes = codes.map { it }.toMutableList() }

        fun end(): DrivingPrivileges.Builder? = parent

        fun build(): DrivingPrivilege {
            val catCode = vehicleCategoryCode
            require(catCode != null) { "Vehicle category code must not be null" }
            return DrivingPrivilege(
                vehicle_category_code = catCode,
                issue_date = issueDate,
                expiry_date = expiryDate,
                codes =
                    if (codes.isNullOrEmpty()) {
                        null
                    } else {
                        codes
                    },
            )
        }
    }

    override fun toString(): String = "DrivingPrivilege(vehicle_category_code=$vehicle_category_code, issue_date=$issue_date, expiry_date=$expiry_date, codes=$codes)"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as DrivingPrivilege

        if (vehicle_category_code != other.vehicle_category_code) {
            return false
        }
        if (issue_date != other.issue_date) {
            return false
        }
        if (expiry_date != other.expiry_date) {
            return false
        }
        if (codes != other.codes) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = vehicle_category_code.hashCode()
        result = 31 * result + (issue_date?.hashCode() ?: 0)
        result = 31 * result + (expiry_date?.hashCode() ?: 0)
        result = 31 * result + (codes?.hashCode() ?: 0)
        return result
    }

    companion object {
        val VEHICLE_CATEGORY_CODE = StringLabel("vehicle_category_code")
        val ISSUE_DATE = StringLabel("issue_date")
        val EXPIRY_DATE = StringLabel("expiry_date")
        val CODES = StringLabel("codes")
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DrivingPrivilegesCode", exact = true)
data class DrivingPrivilegesCode(
    val code: CborString,
    val sign: CborString?,
    val value: CborString?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is DrivingPrivilegesCode) {
            return false
        }

        if (code != other.code) {
            return false
        }
        if (sign != other.sign) {
            return false
        }
        if (value != other.value) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + (sign?.hashCode() ?: 0)
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "DrivingPrivilegesCode(code=$code, sign=$sign, value=$value)"

    companion object {
        val CODE = StringLabel("code")
        val SIGN = StringLabel("sign")
        val VALUE = StringLabel("value")
    }
}
