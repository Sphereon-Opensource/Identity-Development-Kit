/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.data.DataElementDef
import com.sphereon.mdoc.data.NameSpaceAlias
import com.sphereon.mdoc.data.mso.DeviceKeyInfo
import com.sphereon.mdoc.data.mso.DigestAlgorithm
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.Status
import com.sphereon.mdoc.data.mso.ValidityInfo
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName
import kotlin.random.Random

typealias IssuerSignedNameSpace = Pair<NameSpace, Array<CborEncodedItem<IssuerSignedItem<Any>>>>
typealias IssuerSignedNameSpaces = Map<NameSpace, Array<CborEncodedItem<IssuerSignedItem<Any>>>>

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssuerSigned", exact = true)
data class IssuerSigned(
    val nameSpaces: IssuerSignedNameSpaces? = null,
    val issuerAuth: COSE_Sign1<MobileSecurityObject>,
    val original: ByteArray?,
) {
    @JsName("fromNameSpaces")
    constructor(nameSpaces: Collection<IssuerSignedNameSpace>? = null, issuerAuth: COSE_Sign1<MobileSecurityObject>, original: ByteArray?) : this(
        nameSpaces?.toMap(),
        issuerAuth,
        original,
    )

    fun getNameSpaces() = nameSpaces?.keys

    fun getAllIssuerSignedItems() = nameSpaces?.map { it.key to it.value.map { signedItem -> signedItem.data() } }?.toMap()

    fun getNamespace(ns: String): Array<CborEncodedItem<IssuerSignedItem<Any>>>? = nameSpaces?.get(NameSpace(ns))

    fun getIssuerSignedItem(
        ns: NameSpace,
        elementIdentifier: DataElementIdentifier,
    ): IssuerSignedItem<Any>? = nameSpaces?.get(ns)?.firstOrNull { it.data().elementIdentifier == elementIdentifier }?.data()

    fun getIssuerSignedItems(ns: String): List<IssuerSignedItem<Any>>? = nameSpaces?.get(NameSpace(ns))?.map { it.data() }

    fun limitDisclosures(docRequest: DocRequest): IssuerSigned {
        val ns = this.nameSpaces
        if (ns == null) {
            return this.copy()
        }

        // ISO 18013-5 second-edition alternative data elements are evaluated in
        // declaration order. A requested element wins when it is present; otherwise
        // the first complete alternative set available in the issuer-signed document
        // is disclosed. Availability is calculated against the original document so
        // an earlier selection cannot make a later alternative appear available.
        val requestedIdentifiersByNamespace =
            docRequest
                .getNameSpaces()
                .associateWith { nameSpace -> docRequest.getIdentifiers(nameSpace).keys.toMutableSet() }
                .toMutableMap()
        docRequest.itemsRequest.docRequestInfo?.alternativeDataElements.orEmpty().forEach { alternative ->
            val requested = alternative.requestedElement
            val requestedAvailable = ns[requested.first]?.any { it.data().elementIdentifier == requested.second } == true
            val selectedReferences =
                if (requestedAvailable) {
                    listOf(requested)
                } else {
                    alternative.alternativeElementSets.firstOrNull { candidateSet ->
                        candidateSet.isNotEmpty() &&
                            candidateSet.all { reference ->
                                ns[reference.first]?.any { it.data().elementIdentifier == reference.second } == true
                            }
                    }.orEmpty()
                }
            selectedReferences.forEach { (nameSpace, identifier) ->
                requestedIdentifiersByNamespace.getOrPut(nameSpace) { mutableSetOf() }.add(identifier)
            }
        }

        val filtered =
            ns.map { (nsName, items) ->
                {
                    val requestedIdentifiers = requestedIdentifiersByNamespace[nsName].orEmpty()
                    val value = items.filter { item: CborEncodedItem<IssuerSignedItem<Any>> -> item.data().elementIdentifier in requestedIdentifiers }
                    Pair(nsName, value.toTypedArray())
                }
            }
        return IssuerSigned(nameSpaces = filtered.associate { it() }, issuerAuth = issuerAuth, original = null)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("MsoBuilder", exact = true)
    class MsoBuilder(
        var docType: DocType? = null,
        val nameSpaces: IssuerSignedNameSpaces = mutableMapOf(),
        private val issuerSignedItemCborCodec: IssuerSignedItemCborCodec? = null,
        var signed: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal(),
        var validFrom: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal(),
        var validUntil: LocalDateTimeKMP? = null,
        var expectedUpdate: LocalDateTimeKMP? = null,
        var deviceKeyInfo: ResolvedKeyInfoType<CoseKeyType>? = null,
        var issuerKeyInfo: ManagedKeyInfoType<*>? = null,
        var status: Status? = null,
    ) {
        fun addNameSpace(
            nameSpace: NameSpace,
            vararg issuerSignedItems: IssuerSignedItem<Any>,
        ) = apply {
            val values: Array<CborEncodedItem<IssuerSignedItem<Any>>> = nameSpaces.getOrElse(nameSpace) { arrayOf() }
            val codec =
                requireNotNull(issuerSignedItemCborCodec) {
                    "IssuerSignedItemCborCodec must be provided to add issuer-signed items"
                }
            val newValues = values.plus(issuerSignedItems.map { codec.encodeItem(it).getOrThrow() }.toTypedArray())
            (nameSpaces as MutableMap)[nameSpace] = newValues
        }

        fun withDocType(docType: DocType) = apply { this.docType = docType }

        fun withSigned(signed: LocalDateTimeKMP) = apply { this.signed = signed }

        fun withValidFrom(validFrom: LocalDateTimeKMP) = apply { this.validFrom = validFrom }

        fun withValidUntil(validUntil: LocalDateTimeKMP) = apply { this.validUntil = validUntil }

        fun withExpectedUpdate(expectedUpdate: LocalDateTimeKMP?) = apply { this.expectedUpdate = expectedUpdate }

        fun withValidityInfo(
            signed: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal(),
            validFrom: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal(),
            validUntil: LocalDateTimeKMP,
            expectedUpdate: LocalDateTimeKMP? = null,
        ) = apply {
            this.withSigned(signed)
            this.withValidFrom(validFrom)
            this.withValidUntil(validUntil)
            this.withExpectedUpdate(expectedUpdate)
        }

        fun withDeviceKey(deviceKey: KeyType) =
            apply {
                val pubKey = CoseJoseKeyMappingService.toCoseKey(deviceKey).toPublicKey()
                this.deviceKeyInfo = ResolvedKeyInfo.fromKey(pubKey)
            }

        fun withDeviceKeyInfo(deviceKeyInfo: ResolvedKeyInfoType<*>) =
            apply {
                this.deviceKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(deviceKeyInfo)
            }

        fun withSigningKeyInfo(issuerKeyInfo: ManagedKeyInfoType<*>) =
            apply {
                this.issuerKeyInfo = issuerKeyInfo
            }

        /** Adds the optional second-edition ISO 18013-5 MSO status reference. */
        fun withStatus(status: Status?) = apply { this.status = status }

        fun build(alg: DigestAlg? = issuerKeyInfo?.signatureAlgorithm?.digestAlgorithm ?: DigestAlg.SHA256): Pair<MobileSecurityObject, IssuerSignedNameSpaces> {
            require(deviceKeyInfo !== null) { "Please provide a device key or key info object" }
            require(validUntil !== null) { "Please provide a valid until value" }
            require(docType !== null) { "Please provide a doc type" }
            require(nameSpaces.isNotEmpty()) { "Please provide at least one name space" }

            val validityInfo =
                ValidityInfo.fromDates(
                    signed = signed,
                    validFrom = validFrom,
                    validUntil = validUntil!!,
                    expectedUpdate = expectedUpdate,
                )

            val alg = alg ?: DigestAlg.SHA256
            val mso =
                MobileSecurityObject(
                    digestAlgorithm = DigestAlgorithm(alg.httpHeaderId!!),
                    valueDigests = toValueDigests(nameSpaces = nameSpaces, alg = alg),
                    deviceKeyInfo = DeviceKeyInfo.fromKeyInfo(deviceKeyInfo!!),
                    docType = docType!!,
                    validityInfo = validityInfo,
                    original = null,
                    status = status,
                )

            return mso to nameSpaces
        }

        suspend fun buildAndSign(
            mdocSignService: MdocSignService,
            signatureAlgorithm: SignatureAlgorithm? = issuerKeyInfo?.signatureAlgorithm,
            alg: DigestAlg = signatureAlgorithm?.digestAlgorithm ?: DigestAlg.SHA256,
            unprotectedHeader: CoseHeaderCbor? = null,
            protectedHeader: CoseHeaderCbor? = null,
            requireDeviceX5Chain: Boolean = false,
        ): IssuerSigned {
            require(issuerKeyInfo !== null) { "Please provide an issuer key info object to sign" }
            val (mso, issuerSignedNameSpaces) = build(alg = alg)
            return mdocSignService.issuerSignIssuerSigned(
                mso = mso,
                issuerSignedNameSpaces = issuerSignedNameSpaces,
                issuerKeyInfo = issuerKeyInfo!!,
                signatureAlgorithm = signatureAlgorithm,
                unprotectedHeader = unprotectedHeader,
                protectedHeader = protectedHeader,
                requireDeviceX5Chain = requireDeviceX5Chain,
            )
        }

        suspend fun buildAndSignMdoc(
            mdocSignService: MdocSignService,
            signatureAlgorithm: SignatureAlgorithm? = issuerKeyInfo?.signatureAlgorithm,
            alg: DigestAlg = signatureAlgorithm?.digestAlgorithm ?: DigestAlg.SHA256,
            unprotectedHeader: CoseHeaderCbor? = null,
            protectedHeader: CoseHeaderCbor? = null,
            requireDeviceX5Chain: Boolean = false,
        ): Document {
            require(issuerKeyInfo !== null) { "Please provide an issuer key info object to sign" }
            val (mso, issuerSignedNameSpaces) = build(alg = alg)
            return mdocSignService.issuerSignDocument(
                mso = mso,
                issuerSignedNameSpaces = issuerSignedNameSpaces,
                issuerKeyInfo = issuerKeyInfo!!,
                signatureAlgorithm = signatureAlgorithm,
                unprotectedHeader = unprotectedHeader,
                protectedHeader = protectedHeader,
                requireDeviceX5Chain = requireDeviceX5Chain,
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is IssuerSigned) {
            return false
        }

        if (nameSpaces != other.nameSpaces) {
            return false
        }
        if (issuerAuth != other.issuerAuth) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = nameSpaces?.hashCode() ?: 0
        result = 31 * result + issuerAuth.hashCode()
        return result
    }

    override fun toString(): String = "IssuerSigned(nameSpaces=$nameSpaces, issuerAuth=$issuerAuth)"

    companion object Decoder {
        @JsStatic
        @JvmStatic
        val NAME_SPACES = StringLabel("nameSpaces")

        @JsStatic
        @JvmStatic
        val ISSUER_AUTH = StringLabel("issuerAuth")

        @JsStatic
        @JvmStatic
        fun toValueDigests(
            nameSpaces: IssuerSignedNameSpaces? = null,
            alg: DigestAlg = DigestAlg.SHA256,
        ): Map<NameSpace, Map<DigestID, ByteArray>> {
            val digests =
                nameSpaces?.map { (ns: NameSpace, encodedItems: Array<CborEncodedItem<IssuerSignedItem<Any>>>) ->
                    ns to
                        mapOf(
                            *encodedItems
                                .map { encodedItem: CborEncodedItem<IssuerSignedItem<Any>> ->
                                    encodedItem.data().digestID to encodedItem.digest(alg)
                                }.toTypedArray(),
                        )
                } ?: emptyList()
            return mapOf(*digests.toTypedArray())
        }
    }
}

@JvmInline
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("RandomValue", exact = true)
value class RandomValue(
    val value: ByteArray = Random.nextBytes(RANDOM_VALUE_SIZE),
) {
    fun toCborItem() = value.toCborByteString()

    override fun toString(): String = value.encodeToHex()

    companion object Decoder {
        private const val RANDOM_VALUE_SIZE = 24

        @JvmStatic
        fun fromCborItem(structure: CborByteString) = RandomValue(structure.value)
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssuerSignedItem", exact = true)
data class IssuerSignedItem<Type : Any>(
    val digestID: DigestID,
    val random: RandomValue = RandomValue(),
    val elementIdentifier: DataElementIdentifier,
    val elementValue: Type,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is IssuerSignedItem<*>) {
            return false
        }

        if (digestID != other.digestID) {
            return false
        }
        if (random != other.random) {
            return false
        }
        if (elementIdentifier != other.elementIdentifier) {
            return false
        }
        if (elementValue != other.elementValue) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = digestID.hashCode()
        result = 31 * result + random.hashCode()
        result = 31 * result + elementIdentifier.hashCode()
        result = 31 * result + elementValue.hashCode()
        return result
    }

    override fun toString(): String = "IssuerSignedItem(digestID=$digestID, random=$random, elementIdentifier=$elementIdentifier, elementValue=$elementValue)"

    companion object Decoder {
        @JsStatic
        @JvmStatic
        val DIGEST_ID = StringLabel("digestID")

        @JsStatic
        @JvmStatic
        val RANDOM = StringLabel("random")

        @JsStatic
        @JvmStatic
        val ELEMENT_IDENTIFIER = StringLabel("elementIdentifier")

        @JsStatic
        @JvmStatic
        val ELEMENT_VALUE = StringLabel("elementValue")

        @JsStatic
        fun <Type : Any> create(
            digestID: DigestID,
            elementIdentifier: DataElementIdentifier,
            elementValue: Type,
        ): IssuerSignedItem<Type> =
            IssuerSignedItem(
                digestID = digestID,
                random = RandomValue(),
                elementIdentifier = elementIdentifier,
                elementValue = elementValue,
            )

        @JsStatic
        fun <Type : Any> createFromDefinition(
            digestID: DigestID,
            elementDef: DataElementDef,
            elementValue: Type,
        ): IssuerSignedItem<Type> =
            IssuerSignedItem(
                digestID = digestID,
                random = RandomValue(),
                elementIdentifier = elementDef.identifier,
                elementValue = elementValue,
            )
    }
}

fun CborEncodedItem<IssuerSignedItem<Any>>.digest(alg: DigestAlg = DigestAlg.SHA256): ByteArray = hash(encodeCbor(), alg)
