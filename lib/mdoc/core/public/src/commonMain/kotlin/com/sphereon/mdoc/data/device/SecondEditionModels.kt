package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.localDateToDateStringISO
import com.sphereon.cbor.toCborItem
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/** A requested element followed by the alternative element sets that can satisfy it. */
typealias ElementReference = Pair<NameSpace, DataElementIdentifier>

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("AlternativeDataElementsSet", exact = true)
data class AlternativeDataElementsSet(
    val requestedElement: ElementReference,
    val alternativeElementSets: List<List<ElementReference>>,
) {
    init {
        require(alternativeElementSets.isNotEmpty()) { "alternativeElementSets must not be empty" }
        require(alternativeElementSets.all { it.isNotEmpty() }) { "alternativeElementSets entries must not be empty" }
    }
}

/** The second-edition request information carried by an ItemsRequest. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocRequestInfo", exact = true)
data class DocRequestInfo(
    val alternativeDataElements: List<AlternativeDataElementsSet>? = null,
    val issuerIdentifiers: List<ByteArray>? = null,
    val uniqueDocSetRequired: Boolean? = null,
    val maximumResponseSize: UInt? = null,
    val zkRequest: ZkRequest? = null,
    val docResponseEncryption: EncryptionParameters? = null,
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        alternativeDataElements?.let { require(it.isNotEmpty()) { "alternativeDataElements must not be empty" } }
        issuerIdentifiers?.let { require(it.isNotEmpty()) { "issuerIdentifiers must not be empty" } }
        issuerIdentifiers?.let { identifiers ->
            require(identifiers.all { it.isNotEmpty() }) { "issuerIdentifiers entries must not be empty" }
        }
        require(
            alternativeDataElements != null ||
                issuerIdentifiers != null ||
                uniqueDocSetRequired != null ||
                maximumResponseSize != null ||
                zkRequest != null ||
                docResponseEncryption != null ||
                !unknown.isNullOrEmpty(),
        ) { "DocRequestInfo must contain at least one field" }
    }

    override fun equals(other: Any?): Boolean =
        other is DocRequestInfo &&
            alternativeDataElements == other.alternativeDataElements &&
            issuerIdentifiers.contentEqualsNullable(other.issuerIdentifiers) &&
            uniqueDocSetRequired == other.uniqueDocSetRequired &&
            maximumResponseSize == other.maximumResponseSize &&
            zkRequest == other.zkRequest &&
            docResponseEncryption == other.docResponseEncryption &&
            unknown == other.unknown

    override fun hashCode(): Int {
        var result = alternativeDataElements?.hashCode() ?: 0
        result = 31 * result + issuerIdentifiers.contentHashCodeNullable()
        result = 31 * result + (uniqueDocSetRequired?.hashCode() ?: 0)
        result = 31 * result + (maximumResponseSize?.hashCode() ?: 0)
        result = 31 * result + (zkRequest?.hashCode() ?: 0)
        result = 31 * result + (docResponseEncryption?.hashCode() ?: 0)
        result = 31 * result + (unknown?.hashCode() ?: 0)
        return result
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("UseCase", exact = true)
data class UseCase(
    val mandatory: Boolean,
    val documentSets: List<List<UInt>>,
    val purposeHints: Map<String, Int>? = null,
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        require(documentSets.isNotEmpty()) { "documentSets must not be empty" }
        require(documentSets.all { it.isNotEmpty() }) { "documentSets entries must not be empty" }
        purposeHints?.let { require(it.isNotEmpty()) { "purposeHints must not be empty" } }
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRequestInfo", exact = true)
data class DeviceRequestInfo(
    val useCases: List<UseCase>? = null,
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        useCases?.let { require(it.isNotEmpty()) { "useCases must not be empty" } }
        require(useCases != null || !unknown.isNullOrEmpty()) { "DeviceRequestInfo must contain at least one field" }
    }
}

/** ZKP negotiation metadata. Proof generation is deliberately supplied by a separate provider. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ZkRequest", exact = true)
data class ZkRequest(
    val systemSpecs: List<ZkSystemSpec>,
    val zkRequired: Boolean,
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        require(systemSpecs.isNotEmpty()) { "systemSpecs must not be empty" }
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ZkSystemSpec", exact = true)
data class ZkSystemSpec(
    val zkSystemId: String,
    val system: String,
    val params: Map<String, CborItem<*>> = emptyMap(),
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        require(zkSystemId.isNotBlank()) { "zkSystemId must not be blank" }
        require(system.isNotBlank()) { "system must not be blank" }
    }
}

/** Opaque proof transport with typed document-data metadata. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ZkDocument", exact = true)
data class ZkDocument(
    val documentData: ZkDocumentData,
    val proof: ByteArray,
    val original: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is ZkDocument && documentData == other.documentData && proof.contentEquals(other.proof)

    override fun hashCode(): Int = 31 * documentData.hashCode() + proof.contentHashCode()
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ZkDocumentData", exact = true)
data class ZkDocumentData(
    val docType: DocType,
    val zkSystemId: String,
    /** ISO 8601 full-date text, encoded as the CBOR full-date tag. */
    val timestamp: String,
    val issuerSigned: Map<NameSpace, List<ZkSignedItem>>? = null,
    val deviceSigned: Map<NameSpace, List<ZkSignedItem>>? = null,
    val msoX5chain: List<ByteArray>? = null,
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        msoX5chain?.let { chain ->
            require(chain.isNotEmpty()) { "msoX5chain must not be empty" }
            require(chain.all { it.isNotEmpty() }) { "msoX5chain entries must not be empty" }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ZkDocumentData &&
            docType == other.docType &&
            zkSystemId == other.zkSystemId &&
            timestamp == other.timestamp &&
            issuerSigned == other.issuerSigned &&
            deviceSigned == other.deviceSigned &&
            msoX5chain.contentEqualsNullable(other.msoX5chain) &&
            unknown == other.unknown

    override fun hashCode(): Int =
        (((((docType.hashCode() * 31 + zkSystemId.hashCode()) * 31 + timestamp.hashCode()) * 31 +
            (issuerSigned?.hashCode() ?: 0)) * 31 + (deviceSigned?.hashCode() ?: 0)) * 31 +
            msoX5chain.contentHashCodeNullable()) * 31 + (unknown?.hashCode() ?: 0)
}

/**
 * Projects an already selected mdoc into the data envelope supplied to a ZKP provider.
 *
 * IssuerSignedItems are deliberately reduced to their disclosed identifier/value pairs: the
 * issuer digest and random value belong to the normal IssuerSigned wire structure and must not
 * be smuggled into the opaque ZKP input. The provider receives the exact session transcript
 * separately when it creates the proof.
 */
fun Document.toZkDocumentData(
    zkSystemId: String,
    timestamp: String = LocalDateTimeKMP.now().localDateToDateStringISO(),
): ZkDocumentData {
    require(zkSystemId.isNotBlank()) { "zkSystemId must not be blank" }

    val issuerSigned =
        issuerSigned.nameSpaces
            ?.mapValues { (_, items) ->
                items.map { encoded ->
                    val item = encoded.data()
                    ZkSignedItem(
                        elementIdentifier = item.elementIdentifier,
                        elementValue = item.elementValue.toCborItem(),
                    )
                }
            }?.filterValues { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }

    val deviceSigned =
        this.deviceSigned?.nameSpaces?.value
            ?.mapValues { (_, items) ->
                items.value.map { (identifier, value) ->
                    ZkSignedItem(
                        elementIdentifier = identifier,
                        elementValue = value.toCborItem(),
                    )
                }
            }?.filterValues { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }

    val msoX5chain =
        (this.issuerSigned.issuerAuth.protectedHeader.x5chain ?: this.issuerSigned.issuerAuth.unprotectedHeader?.x5chain)
            ?.value
            ?.map { it.value.copyOf() }
            ?.takeIf { it.isNotEmpty() }

    require(issuerSigned != null || deviceSigned != null) {
        "ZkDocumentData must contain issuerSigned or deviceSigned data"
    }
    return ZkDocumentData(
        docType = docType,
        zkSystemId = zkSystemId,
        timestamp = timestamp,
        issuerSigned = issuerSigned,
        deviceSigned = deviceSigned,
        msoX5chain = msoX5chain,
    )
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ZkSignedItem", exact = true)
data class ZkSignedItem(
    val elementIdentifier: DataElementIdentifier,
    val elementValue: CborItem<*>,
    val unknown: Map<String, CborItem<*>>? = null,
)

/** Encryption parameters for an encrypted document response. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("EncryptionParameters", exact = true)
data class EncryptionParameters(
    val recipientPublicKey: CoseKey,
    val nonce: ByteArray? = null,
    val recipientCertificate: List<ByteArray>? = null,
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        recipientCertificate?.let { certificates ->
            require(certificates.isNotEmpty()) { "recipientCertificate must not be empty" }
            require(certificates.all { it.isNotEmpty() }) { "recipientCertificate entries must not be empty" }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is EncryptionParameters &&
            recipientPublicKey == other.recipientPublicKey &&
            nonce.contentEqualsNullable(other.nonce) &&
            recipientCertificate.contentEqualsNullable(other.recipientCertificate) &&
            unknown == other.unknown

    override fun hashCode(): Int =
        (((recipientPublicKey.hashCode() * 31 + nonce.contentHashCodeNullable()) * 31 +
            recipientCertificate.contentHashCodeNullable()) * 31 + (unknown?.hashCode() ?: 0))
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("EncryptedDocumentsPlaintext", exact = true)
data class EncryptedDocumentsPlaintext(
    val documents: List<Document>? = null,
    val zkDocuments: List<ZkDocument>? = null,
    val unknown: Map<String, CborItem<*>>? = null,
)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("EncryptedDocuments", exact = true)
data class EncryptedDocuments(
    val enc: ByteArray,
    val cipherText: ByteArray,
    val docRequestID: UInt,
    val original: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is EncryptedDocuments &&
            enc.contentEquals(other.enc) &&
            cipherText.contentEquals(other.cipherText) &&
            docRequestID == other.docRequestID

    override fun hashCode(): Int = ((enc.contentHashCode() * 31 + cipherText.contentHashCode()) * 31 + docRequestID.hashCode())
}

/** Detached ReaderAuthenticationAll content; the enclosing field is a CBOR array. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderAuthenticationAll", exact = true)
data class ReaderAuthenticationAll(
    val sessionTranscript: SessionTranscript,
    val itemsRequestBytesAll: List<ByteArray>,
    val deviceRequestInfoBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is ReaderAuthenticationAll &&
            sessionTranscript == other.sessionTranscript &&
            itemsRequestBytesAll.contentEqualsNullable(other.itemsRequestBytesAll) &&
            deviceRequestInfoBytes.contentEqualsNullable(other.deviceRequestInfoBytes)

    override fun hashCode(): Int =
        ((sessionTranscript.hashCode() * 31 + itemsRequestBytesAll.contentHashCodeNullable()) * 31 +
            deviceRequestInfoBytes.contentHashCodeNullable())
}

/** Detached ReaderAuthenticationAll content carried by the DeviceRequest signature. */
typealias ReaderAuthenticationAllBytes = CborEncodedItem<ReaderAuthenticationAll>

/** The COSE_Sign1 signature for one ReaderAuthenticationAll structure. */
typealias ReaderAuthAll = COSE_Sign1<ReaderAuthenticationAllBytes>

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    when {
        this == null || other == null -> this == null && other == null
        else -> contentEquals(other)
    }

private fun List<ByteArray>?.contentEqualsNullable(other: List<ByteArray>?): Boolean =
    when {
        this == null || other == null -> this == null && other == null
        size != other.size -> false
        else -> indices.all { this[it].contentEquals(other[it]) }
    }

private fun ByteArray?.contentHashCodeNullable(): Int = this?.contentHashCode() ?: 0

private fun List<ByteArray>?.contentHashCodeNullable(): Int =
    this?.fold(1) { result, bytes -> 31 * result + bytes.contentHashCode() } ?: 0
