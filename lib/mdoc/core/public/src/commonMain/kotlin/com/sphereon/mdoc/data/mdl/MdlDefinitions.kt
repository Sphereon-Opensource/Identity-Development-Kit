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

import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborString
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.data.AbstractDataElementDef
import com.sphereon.mdoc.data.DataElementDef
import com.sphereon.mdoc.data.Presence
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.NameSpace
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
object Mdl {
    object Def {
        val family_name = FamilyNameMdlDef()
        val given_name = GivenNameMdlDef()
        val birth_date = BirthDateMdlDef()
        val issue_date = IssueDateMdlDef()
        val expiry_date = ExpiryDateMdlDef()
        val issuing_country = IssuingCountryMdlDef()
        val issuing_authority = IssuingAuthorityMdlDef()
        val document_number = DocumentNumberMdlDef()
        val portrait = PortraitMdlDef()
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("asDef", exact = true)
    class asDef(
        val definition: IMdlDef,
    ) : IMdlDef by definition {
        val isMandatory: Boolean = presence.mandatory
        val nameSpaceStr: String = nameSpace.toString()
        val identifierStr: String = identifier.toString()
    }

    const val MDL_NAMESPACE_LITERAL: String = "org.iso.18013.5.1.mDL"
    val MDL_NAMESPACE: NameSpace = NameSpace(MDL_NAMESPACE_LITERAL)
    val MDL_NAMESPACE_CBOR: CborString = CborString(MDL_NAMESPACE.toString())
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IMdlDef", exact = true)
sealed interface IMdlDef : DataElementDef

@JsExportCompat
class FamilyNameMdlDef :
    AbstractDataElementDef(),
    IMdlDef {
    override val nameSpace: NameSpace = Mdl.MDL_NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("family_name")
    override val details: String = "Family name"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("GivenNameMdlDef", exact = true)
class GivenNameMdlDef :
    AbstractDataElementDef(),
    IMdlDef {
    override val nameSpace: NameSpace = Mdl.MDL_NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("given_name")
    override val details: String = "Given name"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class BirthDateMdlDef :
    AbstractDataElementDef(),
    IMdlDef {
    override val nameSpace: NameSpace = Mdl.MDL_NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("birth_date")
    override val details: String = "Date of birth"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.full_date)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssueDateMdlDef", exact = true)
class IssueDateMdlDef :
    AbstractDataElementDef(),
    IMdlDef {
    override val nameSpace: NameSpace = Mdl.MDL_NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("issue_date")
    override val details: String = "Date of issuance"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.full_date, CDDL.tdate)
}

@JsExportCompat
class ExpiryDateMdlDef :
    AbstractDataElementDef(),
    IMdlDef {
    override val nameSpace: NameSpace = Mdl.MDL_NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("expiry_date")
    override val details: String = "Date of expiration"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.full_date, CDDL.tdate)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssuingCountryMdlDef", exact = true)
class IssuingCountryMdlDef :
    AbstractDataElementDef(),
    IMdlDef {
    override val nameSpace: NameSpace = Mdl.MDL_NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("issuing_country")
    override val details: String = "Country of issuance"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class IssuingAuthorityMdlDef :
    AbstractDataElementDef(),
    IMdlDef {
    override val nameSpace: NameSpace = Mdl.MDL_NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("issuing_authority")
    override val details: String = "Authority of issuance"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentNumberMdlDef", exact = true)
class DocumentNumberMdlDef :
    AbstractDataElementDef(),
    IMdlDef {
    override val nameSpace: NameSpace = Mdl.MDL_NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("document_number")
    override val details: String = "Document number"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
}

@JsExportCompat
class PortraitMdlDef :
    AbstractDataElementDef(),
    IMdlDef {
    override val nameSpace: NameSpace = Mdl.MDL_NAMESPACE
    override val identifier: DataElementIdentifier = DataElementIdentifier("portrait")
    override val details: String = "Portrait of holder"
    override val presence: Presence = Presence.MANDATORY
    override val cddls: Array<CDDL> = arrayOf(CDDL.bstr)
}
