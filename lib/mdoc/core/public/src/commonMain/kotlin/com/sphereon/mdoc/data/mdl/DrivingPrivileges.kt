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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborFullDate
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborViewListToCborItem
import com.sphereon.cbor.dsl.cborArrayBuilder
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.cbor.cddl_full_date
import com.sphereon.cbor.cddl_tstr
import com.sphereon.cbor.instantToDateStringISO
import com.sphereon.cbor.localDateTimeToDateStringISO
import com.sphereon.cbor.localDateToDateStringISO
import com.sphereon.cbor.toCborString
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.core.compat.getDateTime
import com.sphereon.core.compat.toKotlin
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic


typealias CborDrivingPrivilegesBuilder = DrivingPrivilegesCbor.Builder

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DrivingPrivilegesCbor", exact = true)
data class DrivingPrivilegesCbor(
    private val backing: List<DrivingPrivilegeCbor> = mutableListOf(),
) : List<DrivingPrivilegeCbor> by backing,
    CborStructure<DrivingPrivilegesCbor, CborArray<CborMap<StringLabel, CborItem<*>>>>(CDDL.list) {
    @JsName("fromVarArgs")
    constructor(vararg drivingPrivileges: DrivingPrivilegeCbor) : this(drivingPrivileges.toMutableList())

    @JsName("fromCborArray")
    constructor(list: CborArray<CborMap<StringLabel, CborItem<*>>>) :
            this(list.value.map {
                DrivingPrivilegeCbor(
                    DrivingPrivilegeCbor.VEHICLE_CATEGORY_CODE.required(it),
                    DrivingPrivilegeCbor.ISSUE_DATE.optional(it),
                    DrivingPrivilegeCbor.EXPIRY_DATE.optional(it),
                    DrivingPrivilegeCbor.CODES.optional(it)
                )
            })


    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    data class Builder(val privilegeBuilders: MutableList<CborDrivingPrivilegeBuilder> = mutableListOf()) {
        fun newPrivilege(): CborDrivingPrivilegeBuilder {
            val privilegeBuilder = CborDrivingPrivilegeBuilder(parent = this)
            this.privilegeBuilders.add(privilegeBuilder)
            return privilegeBuilder
        }

        fun build() = DrivingPrivilegesCbor(privilegeBuilders.map { it.build() }.toMutableList())

    }

    override fun cborBuilder(): CborBuilder<DrivingPrivilegesCbor> = cborArrayBuilder(this) {
        backing.cborViewListToCborItem()?.value?.let { addAll(it) }
    }


    override fun toString(): String {
        return "DrivingPrivileges(privileges=$backing)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrivingPrivilegesCbor) return false

        if (backing != other.backing) return false

        return true
    }

    override fun hashCode(): Int {
        return backing.hashCode()
    }

    companion object {

        @JsStatic
        @JsName("decodeCbor")
        fun decodeCbor(encodedDrivingPrivilegesCbor: ByteArray): DrivingPrivilegesCbor {
            val cborArray: CborArray<CborMap<StringLabel, CborItem<*>>> = Cbor.decode(encodedDrivingPrivilegesCbor)
            return DrivingPrivilegesCbor(cborArray)
        }
    }


}

typealias CborDrivingPrivilegeBuilder = DrivingPrivilegeCbor.Builder

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DrivingPrivilegeCbor", exact = true)
data class DrivingPrivilegeCbor(
    val vehicle_category_code: CborString,
    val issue_date: CborFullDate? = null,
    val expiry_date: CborFullDate? = null,
    val codes: List<DrivingPrivilegesCodeCbor>? = null,
) : CborStructure<DrivingPrivilegeCbor, CborMap<StringLabel, CborItem<*>>>(CDDL.map) {

    override fun cborBuilder(): CborBuilder<DrivingPrivilegeCbor> = cborMapBuilder(this) {
        VEHICLE_CATEGORY_CODE to vehicle_category_code
        optional(ISSUE_DATE, issue_date)
        optional(EXPIRY_DATE, expiry_date)
        optional(CODES, codes?.cborViewListToCborItem())
    }


    companion object {
        @JsStatic
        val VEHICLE_CATEGORY_CODE = StringLabel("vehicle_category_code")
        @JsStatic
        val ISSUE_DATE = StringLabel("issue_date")
        @JsStatic
        val EXPIRY_DATE = StringLabel("expiry_date")
        @JsStatic
        val CODES = StringLabel("codes")

        @JsStatic
        @JsName("decodeCbor")
        fun decodeCbor(encodedDrivingPrivilegeCbor: ByteArray): DrivingPrivilegeCbor {
            val m: CborMap<StringLabel, CborItem<*>> = Cbor.decode(encodedDrivingPrivilegeCbor)
            return DrivingPrivilegeCbor(
                VEHICLE_CATEGORY_CODE.required(m),
                ISSUE_DATE.optional(m),
                EXPIRY_DATE.optional(m),
                CODES.optional(m),
            )
        }
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    data class Builder(
        val parent: DrivingPrivilegesCbor.Builder? = null,
        var vehicleCategoryCode: CborString? = null,
        var issueDate: CborFullDate? = null,
        var expiryDate: CborFullDate? = null,
        var codes: MutableList<DrivingPrivilegesCodeCbor>? = mutableListOf(),
    ) {
        fun newPrivilege(): Builder {
            if (parent == null) {
                throw IllegalArgumentException("No parent present. Cannot call add")
            }
            return parent.newPrivilege()
        }

        fun buildPrivileges(): DrivingPrivilegesCbor {
            if (parent == null) {
                throw IllegalArgumentException("No parent present. Cannot call add")
            }
            return parent.build()
        }

        fun withVehicleCategoryCode(vehicleCategoryCode: cddl_tstr) =
            apply { this.vehicleCategoryCode = vehicleCategoryCode.toCborString() }

        fun withIssueDateUsingLocalDateTime(
            issueDate: LocalDateTimeKMP,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) =
            apply {
                this.issueDate = CborFullDate(issueDate.toKotlin().localDateTimeToDateStringISO(utils, timeZoneId))
            }

        fun withIssueDateUsingLocalDate(
            issueDate: LocalDate,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) =
            apply { this.issueDate = CborFullDate(issueDate.localDateToDateStringISO(utils, timeZoneId)) }


        fun withIssueDateUsingEpochSeconds(
            epochSeconds: Int,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) =
            apply { this.issueDate = CborFullDate(getDateTime(utils).dateISO(timeZoneId, epochSeconds)) }

        fun withIssueDate(issueDate: cddl_full_date) = apply { this.issueDate = CborFullDate(issueDate) }

        fun withExpiryDateUsingLocalDateTime(
            issueDate: LocalDateTimeKMP,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) =
            apply {
                this.expiryDate = CborFullDate(issueDate.toKotlin().localDateTimeToDateStringISO(utils, timeZoneId))
            }

        fun withExpiryDateUsingLocalDate(
            issueDate: LocalDate,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) =
            apply { this.expiryDate = CborFullDate(issueDate.localDateToDateStringISO(utils, timeZoneId)) }

        fun withExpiryDateUsingInstant(
            issueDate: Instant,
            utils: DateTimeUtils = DateTimeUtils.DEFAULTS,
            timeZoneId: String? = null,
        ) =
            apply { this.expiryDate = CborFullDate(issueDate.instantToDateStringISO(utils, timeZoneId)) }

        fun withExpiryDate(expiryDate: cddl_full_date) = apply { this.expiryDate = CborFullDate(expiryDate) }
        fun withDates(issueDate: cddl_full_date?, expiryDate: cddl_full_date?) = apply {
            issueDate?.let { withIssueDate(it) }
            expiryDate?.let { withExpiryDate(it) }
        }

        //        fun addCodes(vararg codes: DrivingPrivilegesCode) = apply { this.codes!!.addAll(codes) }
        fun addCodes(vararg codes: DrivingPrivilegesCodeCbor) =
            apply { this.codes!!.addAll(codes) }

        fun addCode(code: cddl_tstr, sign: cddl_tstr? = null, value: cddl_tstr? = null) = apply {
            this.addCodes(DrivingPrivilegesCodeCbor(code.toCborString(), sign?.toCborString(), value?.toCborString()))
        }

        //        fun withCodes(vararg codes: DrivingPrivilegesCode) = apply { this.codes = codes.toMutableList() }
        fun withCodes(vararg codes: DrivingPrivilegesCodeCbor) =
            apply { this.codes = codes.map { it }.toMutableList() }


        fun end(): DrivingPrivilegesCbor.Builder? {
            return parent
        }

        fun build(): DrivingPrivilegeCbor {
            val catCode = vehicleCategoryCode
            return if (catCode == null)
                throw IllegalArgumentException("Vehicle category code must not be null")
            else
                DrivingPrivilegeCbor(
                    vehicle_category_code = catCode,
                    issue_date = issueDate,
                    expiry_date = expiryDate,
                    codes = if (codes.isNullOrEmpty()) null else codes
                )
        }
    }

    override fun toString(): String {
        return "DrivingPrivilege(vehicle_category_code=$vehicle_category_code, issue_date=$issue_date, expiry_date=$expiry_date, codes=$codes)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DrivingPrivilegeCbor

        if (vehicle_category_code != other.vehicle_category_code) return false
        if (issue_date != other.issue_date) return false
        if (expiry_date != other.expiry_date) return false
        if (codes != other.codes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = vehicle_category_code.hashCode()
        result = 31 * result + (issue_date?.hashCode() ?: 0)
        result = 31 * result + (expiry_date?.hashCode() ?: 0)
        result = 31 * result + (codes?.hashCode() ?: 0)
        return result
    }


}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DrivingPrivilegesCodeCbor", exact = true)
data class DrivingPrivilegesCodeCbor(val code: CborString, val sign: CborString?, val value: CborString?) :
    CborStructure<DrivingPrivilegesCodeCbor, CborMap<StringLabel, CborItem<*>>>(CDDL.map) {
    override fun cborBuilder(): CborBuilder<DrivingPrivilegesCodeCbor> = cborMapBuilder(this) {
        CODE to code
        optional(SIGN, sign)
        optional(VALUE, value)
    }


    companion object {
        @JsStatic
        val CODE = StringLabel("code")
        @JsStatic
        val SIGN = StringLabel("sign")
        @JsStatic
        val VALUE = StringLabel("value")


        @JsStatic
        @JsName("decodeCbor")
        fun decodeCbor(encoded: ByteArray): DrivingPrivilegeCbor {
            val m: CborMap<StringLabel, CborItem<*>> = Cbor.decode(encoded)
            return DrivingPrivilegeCbor(CODE.required(m), SIGN.optional(m), VALUE.optional(m))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrivingPrivilegesCodeCbor) return false

        if (code != other.code) return false
        if (sign != other.sign) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + (sign?.hashCode() ?: 0)
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "CborDrivingPrivilegesCode(code=$code, sign=$sign, value=$value)"
    }


}
