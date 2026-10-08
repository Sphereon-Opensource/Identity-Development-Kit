/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalStdlibApi::class)

package com.sphereon.crypto.certificate.persistence

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The certificate material represented by a reference. */
@JsExportCompat
@Serializable
enum class CertificateReferenceKind {
    @SerialName("trusted_certificate")
    TRUSTED_CERTIFICATE,

    @SerialName("key_certificate_chain")
    KEY_CERTIFICATE_CHAIN,

    ;

    fun toStorageValue(): String = name.lowercase()

    companion object {
        fun fromStorageValue(value: String): CertificateReferenceKind =
            entries.firstOrNull { it.toStorageValue() == value }
                ?: error("Unknown certificate reference kind: $value")
    }
}

/** Where the public certificate bytes are resolved from. */
@JsExportCompat
@Serializable
enum class CertificateReferenceSource {
    @SerialName("stored_public_material")
    STORED_PUBLIC_MATERIAL,

    @SerialName("provider_native")
    PROVIDER_NATIVE,

    ;

    fun toStorageValue(): String = name.lowercase()

    companion object {
        fun fromStorageValue(value: String): CertificateReferenceSource =
            entries.firstOrNull { it.toStorageValue() == value }
                ?: error("Unknown certificate reference source: $value")
    }
}

/**
 * Tenant-owned certificate metadata and public verification material.
 *
 * The record never has a private-key or symmetric-key field. Stored chains use the framed binary
 * format defined by [encodeCertificateChain], while provider-native records persist only the
 * provider locator and fingerprints.
 */
@Serializable
data class CertificateReferenceRecord(
    val id: String,
    val tenantId: String,
    val alias: String,
    val providerId: String,
    val providerCertificateId: String? = null,
    val kind: CertificateReferenceKind,
    val source: CertificateReferenceSource,
    val controlMode: ResourceControlMode = ResourceControlMode.PLATFORM_MANAGED,
    val linkedKeyReferenceId: String? = null,
    val certificateChainDer: ByteArray? = null,
    /** SHA-256 over the exact DER bytes of the leaf certificate. */
    val certificateFingerprint: ByteArray,
    /** SHA-256 over canonical DER SubjectPublicKeyInfo bytes for the leaf key. */
    val publicKeyFingerprint: ByteArray,
    val createdAt: Instant,
    val createdById: String? = null,
    val updatedAt: Instant,
    val updatedById: String? = null,
    val deletedAt: Instant? = null,
    val deletedById: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Certificate reference id must not be blank" }
        require(tenantId.isNotBlank()) { "Certificate reference tenantId must not be blank" }
        require(alias.isNotBlank()) { "Certificate reference alias must not be blank" }
        require(providerId.isNotBlank()) { "Certificate reference providerId must not be blank" }
        require(certificateFingerprint.size == SHA256_DIGEST_SIZE) {
            "certificateFingerprint must be exactly $SHA256_DIGEST_SIZE bytes"
        }
        require(publicKeyFingerprint.size == SHA256_DIGEST_SIZE) {
            "publicKeyFingerprint must be exactly $SHA256_DIGEST_SIZE bytes"
        }
        when (kind) {
            CertificateReferenceKind.TRUSTED_CERTIFICATE -> {
                require(linkedKeyReferenceId == null) {
                    "TRUSTED_CERTIFICATE references must not have linkedKeyReferenceId"
                }
            }
            CertificateReferenceKind.KEY_CERTIFICATE_CHAIN -> {
                require(!linkedKeyReferenceId.isNullOrBlank()) {
                    "KEY_CERTIFICATE_CHAIN references require linkedKeyReferenceId"
                }
            }
        }
        when (source) {
            CertificateReferenceSource.STORED_PUBLIC_MATERIAL -> {
                require(certificateChainDer != null) {
                    "STORED_PUBLIC_MATERIAL references require certificateChainDer"
                }
                decodeCertificateChain(certificateChainDer)
            }
            CertificateReferenceSource.PROVIDER_NATIVE -> {
                require(certificateChainDer == null) {
                    "PROVIDER_NATIVE references must not store certificateChainDer"
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CertificateReferenceRecord) return false
        return id == other.id &&
            tenantId == other.tenantId &&
            alias == other.alias &&
            providerId == other.providerId &&
            providerCertificateId == other.providerCertificateId &&
            kind == other.kind &&
            source == other.source &&
            controlMode == other.controlMode &&
            linkedKeyReferenceId == other.linkedKeyReferenceId &&
            certificateChainDer.contentEqualsNullable(other.certificateChainDer) &&
            certificateFingerprint.contentEquals(other.certificateFingerprint) &&
            publicKeyFingerprint.contentEquals(other.publicKeyFingerprint) &&
            createdAt == other.createdAt &&
            createdById == other.createdById &&
            updatedAt == other.updatedAt &&
            updatedById == other.updatedById &&
            deletedAt == other.deletedAt &&
            deletedById == other.deletedById
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + tenantId.hashCode()
        result = 31 * result + alias.hashCode()
        result = 31 * result + providerId.hashCode()
        result = 31 * result + (providerCertificateId?.hashCode() ?: 0)
        result = 31 * result + kind.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + controlMode.hashCode()
        result = 31 * result + (linkedKeyReferenceId?.hashCode() ?: 0)
        result = 31 * result + (certificateChainDer?.contentHashCode() ?: 0)
        result = 31 * result + certificateFingerprint.contentHashCode()
        result = 31 * result + publicKeyFingerprint.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (createdById?.hashCode() ?: 0)
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + (updatedById?.hashCode() ?: 0)
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + (deletedById?.hashCode() ?: 0)
        return result
    }

    companion object {
        const val CERTIFICATE_CHAIN_ENCODING_VERSION: Byte = 1
        const val SHA256_DIGEST_SIZE: Int = 32

        /** Encodes a non-empty leaf-to-root chain without concatenating ambiguous DER blobs. */
        fun encodeCertificateChain(certificatesDer: List<ByteArray>): ByteArray {
            require(certificatesDer.isNotEmpty()) { "A certificate chain must contain at least one certificate" }
            require(certificatesDer.all { it.isNotEmpty() }) { "Certificate DER entries must not be empty" }

            val totalSize = 1 + UINT32_SIZE + certificatesDer.sumOf { UINT32_SIZE + it.size }
            val encoded = ByteArray(totalSize)
            var offset = 0
            encoded[offset++] = CERTIFICATE_CHAIN_ENCODING_VERSION
            writeUInt32(encoded, offset, certificatesDer.size)
            offset += UINT32_SIZE
            certificatesDer.forEach { der ->
                writeUInt32(encoded, offset, der.size)
                offset += UINT32_SIZE
                der.copyInto(encoded, destinationOffset = offset)
                offset += der.size
            }
            return encoded
        }

        /** Decodes and validates the versioned leaf-to-root chain framing. */
        fun decodeCertificateChain(encoded: ByteArray): List<ByteArray> {
            require(encoded.size >= 1 + UINT32_SIZE) { "Certificate chain framing is truncated" }
            var offset = 0
            require(encoded[offset++] == CERTIFICATE_CHAIN_ENCODING_VERSION) {
                "Unsupported certificate chain encoding version"
            }
            val count = readUInt32(encoded, offset)
            offset += UINT32_SIZE
            require(count > 0) { "Certificate chain must contain at least one certificate" }
            require(count <= (encoded.size - offset) / (UINT32_SIZE + 1)) {
                "Certificate chain count exceeds the remaining encoded bytes"
            }

            val certificates = ArrayList<ByteArray>(count)
            repeat(count) {
                require(offset + UINT32_SIZE <= encoded.size) { "Certificate chain length is truncated" }
                val length = readUInt32(encoded, offset)
                offset += UINT32_SIZE
                require(length > 0) { "Certificate DER entries must not be empty" }
                require(length <= encoded.size - offset) { "Certificate chain entry exceeds its encoded length" }
                certificates += encoded.copyOfRange(offset, offset + length)
                offset += length
            }
            require(offset == encoded.size) { "Certificate chain contains trailing or concatenated bytes" }
            return certificates
        }

        /** SHA-256 over the exact DER certificate bytes, with no normalization. */
        fun certificateFingerprintOf(certificateDer: ByteArray): ByteArray =
            sha256(certificateDer)

        /** SHA-256 over canonical DER SubjectPublicKeyInfo bytes. */
        fun publicKeyFingerprintOfCanonicalSpki(canonicalSpkiDer: ByteArray): ByteArray =
            sha256(canonicalSpkiDer)

        private fun sha256(value: ByteArray): ByteArray = hash(value, DigestAlg.SHA256)

        private fun writeUInt32(target: ByteArray, offset: Int, value: Int) {
            require(value >= 0) { "Framed lengths and counts must be non-negative" }
            target[offset] = (value ushr 24).toByte()
            target[offset + 1] = (value ushr 16).toByte()
            target[offset + 2] = (value ushr 8).toByte()
            target[offset + 3] = value.toByte()
        }

        private fun readUInt32(source: ByteArray, offset: Int): Int {
            val value =
                ((source[offset].toInt() and 0xff) shl 24) or
                    ((source[offset + 1].toInt() and 0xff) shl 16) or
                    ((source[offset + 2].toInt() and 0xff) shl 8) or
                    (source[offset + 3].toInt() and 0xff)
            require(value >= 0) { "Framed lengths and counts must fit in a signed 32-bit integer" }
            return value
        }
    }
}

private const val UINT32_SIZE = 4

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    when {
        this == null && other == null -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }
