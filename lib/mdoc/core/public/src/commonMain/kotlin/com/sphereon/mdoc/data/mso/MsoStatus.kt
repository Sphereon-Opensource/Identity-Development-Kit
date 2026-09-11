package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.CborItem
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/** The optional second-edition MSO revocation reference. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Status", exact = true)
data class Status(
    val identifierList: IdentifierListInfo? = null,
    val statusList: StatusListInfo? = null,
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        require((identifierList == null) xor (statusList == null)) {
            "Status must contain exactly one of identifierList or statusList"
        }
        require(unknown?.keys?.none { it == "identifier_list" || it == "status_list" } != false) {
            "Status unknown fields must not replace a defined status mechanism"
        }
    }
}

/** Identifier-list reference carried in an MSO status structure. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IdentifierListInfo", exact = true)
data class IdentifierListInfo(
    val id: ByteArray,
    val uri: String,
    val certificate: ByteArray? = null,
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        require(id.isNotEmpty()) { "IdentifierListInfo.id must not be empty" }
        require(uri.isNotBlank()) { "IdentifierListInfo.uri must not be blank" }
        require(certificate == null || certificate.isNotEmpty()) { "IdentifierListInfo.certificate must not be empty" }
        require(unknown?.keys?.none { it == "id" || it == "uri" || it == "certificate" } != false) {
            "IdentifierListInfo unknown fields must not replace defined fields"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is IdentifierListInfo &&
            id.contentEquals(other.id) &&
            uri == other.uri &&
            certificate.contentEqualsNullable(other.certificate) &&
            unknown == other.unknown

    override fun hashCode(): Int {
        var result = id.contentHashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + (certificate?.contentHashCode() ?: 0)
        result = 31 * result + (unknown?.hashCode() ?: 0)
        return result
    }
}

/** Token Status List reference carried in an MSO status structure. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("StatusListInfo", exact = true)
data class StatusListInfo(
    val idx: UInt,
    val uri: String,
    val certificate: ByteArray? = null,
    val aggregationUri: String? = null,
    val unknown: Map<String, CborItem<*>>? = null,
) {
    init {
        require(uri.isNotBlank()) { "StatusListInfo.uri must not be blank" }
        require(certificate == null || certificate.isNotEmpty()) { "StatusListInfo.certificate must not be empty" }
        require(aggregationUri == null || aggregationUri.isNotBlank()) { "StatusListInfo.aggregationUri must not be blank" }
        require(unknown?.keys?.none { it == "idx" || it == "uri" || it == "certificate" || it == "aggregation_uri" } != false) {
            "StatusListInfo unknown fields must not replace defined fields"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is StatusListInfo &&
            idx == other.idx &&
            uri == other.uri &&
            certificate.contentEqualsNullable(other.certificate) &&
            aggregationUri == other.aggregationUri &&
            unknown == other.unknown

    override fun hashCode(): Int {
        var result = idx.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + (certificate?.contentHashCode() ?: 0)
        result = 31 * result + (aggregationUri?.hashCode() ?: 0)
        result = 31 * result + (unknown?.hashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    when {
        this == null || other == null -> this == null && other == null
        else -> contentEquals(other)
    }
