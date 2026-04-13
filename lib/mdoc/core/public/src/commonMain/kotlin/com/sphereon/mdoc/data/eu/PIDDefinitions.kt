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

package com.sphereon.mdoc.data.eu

import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborString
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.data.AbstractDataElementDef
import com.sphereon.mdoc.data.DataElementDef
import com.sphereon.mdoc.data.Presence
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.NameSpace
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private const val LEGAL_ADULT_AGE = 18

@JsExportCompat
object Pid {
    object Def {
        val family_name = FamilyNamePidDef()
        val given_name = GivenNamePidDef()
        val birth_date = BirthDatePidDef()
        val age_over_18 = AgeOver18()
        val age_in_years = AgeInYearsPidDef()
        val age_birth_year = AgeBirthYearPidDef()
        val family_name_birth = FamilyNameBirthPidDef()
        val given_name_birth = GivenNameBirthPidDef()
        val birth_place = BirthPlacePidDef()
        val birth_country = BirthCountryPidDef()
        val birth_state = BirthStatePidDef()
        val birth_city = BirthCityPidDef()
        val resident_address = ResidentAddressPidDef()
        val resident_country = ResidentCountryPidDef()
        val resident_state = ResidentStatePidDef()
        val resident_city = ResidentCityPidDef()
        val resident_postal_code = ResidentPostalCodePidDef()
        val resident_street = ResidentStreetPidDef()
        val resident_house_number = ResidentHouseNumberPidDef()
        val gender = GenderPidDef()
        val nationality = NationalityPidDef()
        val issuance_date = IssuanceDatePidDef()
        val expiry_date = ExpiryDatePidDef()
        val issuing_authority = IssuingAuthorityPidDef()
        val document_number = DocumentNumberPidDef()
        val administrative_number = AdministrativeNumberPidDef()
        val issuing_country = IssuingCountryPidDef()
        val issuing_jurisdiction = IssuingJurisdictionPidDef()

        fun age_over_NN(age: Int) = AgeOverDef(age)
    }

    val asDefs: List<IEuPidDef> =
        listOf<IEuPidDef>(
            Def.family_name,
            Def.given_name,
            Def.birth_date,
            Def.age_over_18,
            Def.age_in_years,
            Def.age_birth_year,
            Def.family_name_birth,
            Def.given_name_birth,
            Def.birth_place,
            Def.birth_country,
            Def.birth_state,
            Def.birth_city,
            Def.resident_address,
            Def.resident_country,
            Def.resident_state,
            Def.resident_city,
            Def.resident_postal_code,
            Def.resident_street,
            Def.resident_house_number,
            Def.gender,
            Def.nationality,
            Def.issuance_date,
            Def.expiry_date,
            Def.issuing_authority,
            Def.document_number,
            Def.administrative_number,
            Def.issuing_country,
            Def.issuing_jurisdiction,
        )

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("asDef", exact = true)
    class asDef(
        val definition: IEuPidDef,
    ) : IEuPidDef by definition {
        val isMandatory: Boolean = presence.mandatory
        val nameSpaceStr: String = nameSpace.toString()
        val identifierStr: String = identifier.toString()
    }

    const val NAMESPACE_LITERAL: String = "eu.europa.ec.eudi.pid.1"
    val NAMESPACE: NameSpace = NameSpace(NAMESPACE_LITERAL)
    val NAMESPACE_CBOR: CborString = CborString(NAMESPACE.toString())
    val DOCTYPE: DocType = DocType(NAMESPACE_LITERAL)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IEuPidDef", exact = true)
sealed interface IEuPidDef : DataElementDef

@JsExportCompat
class FamilyNamePidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("family_name")
    override val details: String = "Current last name(s) or surname(s) of the PID User."
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("GivenNamePidDef", exact = true)
class GivenNamePidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("given_name")
    override val details: String = "Current first name(s), including middle name(s), of the PID User."
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class BirthDatePidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("birth_date")
    override val details: String = "Day, month, and year on which the PID User was born."
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.full_date)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("AgeOverDef", exact = true)
open class AgeOverDef(
    val age: Int,
) : AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("age_over_$age")
    override val details: String = "Additional current age attestations, NN <> 18."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.bool)
}

@JsExportCompat
class AgeOver18 : AgeOverDef(LEGAL_ADULT_AGE) {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("age_over_18")
    override val details: String = "Attesting whether the PID User is currently an adult (true) or a minor (false). "
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.bool)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("AgeInYearsPidDef", exact = true)
class AgeInYearsPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("age_in_years")
    override val details: String = "The current age of the PID User in years."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.uint)
}

@JsExportCompat
class AgeBirthYearPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("age_birth_year")
    override val details: String = "The year when the PID User was born. 3"
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.uint)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("FamilyNameBirthPidDef", exact = true)
class FamilyNameBirthPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("family_name_birth")
    override val details: String = "Last name(s) or surname(s) of the PID User at the time of birth."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class GivenNameBirthPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("given_name_birth")
    override val details: String = "First name(s), including middle name(s), of the PID User at the time of birth."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("BirthPlacePidDef", exact = true)
class BirthPlacePidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("birth_place")
    override val details: String = "The country, state, and city where the PID User was born."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class BirthCountryPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("birth_country")
    override val details: String = "The country where the PID User was born, as an Alpha-2 country code as specified in ISO 3166-1."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("BirthStatePidDef", exact = true)
class BirthStatePidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("birth_state")
    override val details: String = "The state, province, district, or local area where the PID User was born."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class BirthCityPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("birth_city")
    override val details: String = "The municipality, city, town, or village where the PID User was born."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResidentAddressPidDef", exact = true)
class ResidentAddressPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("resident_address")
    override val details: String = "The full address of the place where the PID User currently resides and/or can be contacted (street name, house number, city etc.)."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class ResidentCountryPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("resident_country")
    override val details: String = "The country where the PID User currently resides, as an Alpha-2 country code as specified in ISO 3166-1."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResidentStatePidDef", exact = true)
class ResidentStatePidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("resident_state")
    override val details: String = "The state, province, district, or local area where the PID User currently resides."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class ResidentCityPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("resident_city")
    override val details: String = "The municipality, city, town, or village where the PID User currently resides."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResidentPostalCodePidDef", exact = true)
class ResidentPostalCodePidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("resident_postal_code")
    override val details: String = "Postal code of the place where the PID User currently resides"
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class ResidentStreetPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("resident_street")
    override val details: String = "The name of the street where the PID User currently resides."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResidentHouseNumberPidDef", exact = true)
class ResidentHouseNumberPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("resident_house_number")
    override val details: String = "The house number where the PID User currently resides, including any affix or suffix."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class GenderPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("gender")
    override val details: String = "PID User’s gender, using a value as defined in ISO/IEC 5218."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.uint)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("NationalityPidDef", exact = true)
class NationalityPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("nationality")
    override val details: String = "Alpha-2 country code as specified in ISO 3166-1, representing the nationality of the PID User."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssuanceDatePidDef", exact = true)
class IssuanceDatePidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("issuance_date")
    override val details: String = "Date (and possibly time) when the PID was issued. "
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.full_date, CDDL.tdate)
}

@JsExportCompat
class ExpiryDatePidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("expiry_date")
    override val details: String = "Date (and possibly time) when the PID will expire.  M"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.full_date, CDDL.tdate)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssuingAuthorityPidDef", exact = true)
class IssuingAuthorityPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("issuing_authority")
    override val details: String =
        "Name of the administrative authority that has issued this PID instance, or the ISO 3166 Alpha-2 country code of the " +
            "respective Member State if there is no separate authority authorized to issue PIDs. "
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class DocumentNumberPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("document_number")
    override val details: String = "A number for the PID, assigned by the PID Provider."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("AdministrativeNumberPidDef", exact = true)
class AdministrativeNumberPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("document_number")
    override val details: String = "A number assigned by the PID Provider for audit control or other purposes."
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssuingCountryPidDef", exact = true)
class IssuingCountryPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("issuing_country")
    override val details: String = "Alpha-2 country code, as defined in ISO 3166-1, of the PID Provider’s country or territory. "
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssuingJurisdictionPidDef", exact = true)
class IssuingJurisdictionPidDef :
    AbstractDataElementDef(),
    IEuPidDef {
    override val nameSpace: NameSpace = Pid.NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("document_number")
    override val details: String =
        "Country subdivision code of the jurisdiction that issued the PID, as defined in ISO 3166-2:2020, Clause 8. " +
            "The first part of the code SHALL be the same as the value for issuing_country. "
    override val presence: Presence = Presence.OPTIONAL
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}
