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

package com.sphereon.crypto.core.x509

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1Primitive
import at.asitplus.awesn1.Asn1Sequence
import at.asitplus.awesn1.crypto.pki.X509CertificateExtension
import at.asitplus.awesn1.encoding.parse

// ASN.1 context-specific tag ranges for SAN parsing
private const val ASN1_CONTEXT_PRIMITIVE_BASE = 0x80
private const val ASN1_CONTEXT_PRIMITIVE_END = 0x88
private const val ASN1_CONTEXT_CONSTRUCTED_BASE = 0xA0
private const val ASN1_CONTEXT_CONSTRUCTED_END = 0xA8
private const val GENERAL_NAME_TYPE_MAX = 8

// IP address sizes
private const val IPV4_BYTE_LENGTH = 4
private const val IPV6_BYTE_LENGTH = 16
private const val IPV6_STEP = 2

// Byte/bit manipulation
private const val BYTE_MASK = 0xFF
private const val SHIFT_8 = 8
private const val HEX_RADIX = 16

// OID encoding
private const val OID_FIRST_ARC_DIVISOR = 40
private const val OID_HIGH_BIT_MASK = 0x80
private const val OID_VALUE_MASK = 0x7F
private const val OID_SHIFT = 7

/*
 * Internal utility functions for parsing Subject Alternative Name (SAN) extension.
 *
 * These functions use the signum library internally and should not be exposed outside
 * the crypto/core module. Other modules should use the [Certificate] abstraction
 * which includes the parsed [SubjectAlternativeName].
 */

/**
 * Extracts the Subject Alternative Name extension from a list of X.509 extensions.
 *
 * @param extensions The list of certificate extensions
 * @return The parsed SubjectAlternativeName, or null if not present
 */
internal fun getSubjectAlternativeName(extensions: List<X509CertificateExtension>?): SubjectAlternativeName? {
    if (extensions == null) {
        return null
    }

    val sanExtension =
        extensions.firstOrNull {
            it.oid.toString() == X509ExtensionOids.SUBJECT_ALTERNATIVE_NAME
        } ?: return null

    return parseSubjectAlternativeNameExtension(sanExtension.value)
}

/**
 * Parses the Subject Alternative Name extension value.
 *
 * The SAN extension is encoded as:
 * SubjectAltName ::= GeneralNames
 * GeneralNames ::= SEQUENCE SIZE (1..MAX) OF GeneralName
 *
 * In awesn1 0.3.0 [X509CertificateExtension.value] is the raw extnValue OCTET STRING content,
 * i.e. the DER encoding of the GeneralNames SEQUENCE.
 *
 * @param extensionValue The raw DER bytes of the SAN extension value
 * @return The parsed SubjectAlternativeName
 */
internal fun parseSubjectAlternativeNameExtension(extensionValue: ByteArray): SubjectAlternativeName {
    val names = mutableListOf<GeneralName>()

    // The extension value holds the DER encoding of the GeneralNames SEQUENCE
    val content =
        try {
            Asn1Element.parse(extensionValue)
        } catch (_: Exception) {
            return SubjectAlternativeName.EMPTY
        }

    // The content should be a SEQUENCE of GeneralName
    val sequence = content as? Asn1Sequence ?: return SubjectAlternativeName.EMPTY

    for (child in sequence.children) {
        val generalName = parseGeneralName(child)
        if (generalName != null) {
            names.add(generalName)
        }
    }

    return SubjectAlternativeName(names)
}

/**
 * Parses a single GeneralName from an ASN.1 element.
 *
 * GeneralName is a CHOICE type with context-specific tags [0] through [8].
 *
 * @param element The ASN.1 element representing a GeneralName
 * @return The parsed GeneralName, or null if parsing fails
 */
internal fun parseGeneralName(element: Asn1Element): GeneralName? {
    // GeneralName uses implicit tagging with context-specific tags
    val primitive = element as? Asn1Primitive ?: return null

    // The tag class should be context-specific (0x80 base for context tags)
    // For implicit tagging, the tag value indicates the GeneralName type
    val tagValue = primitive.tag.tagValue.toInt()

    // Context-specific tags are in the range 0x80-0xBF
    // We need to extract the actual type number (0-8)
    val typeTag =
        when {
            // Primitive context-specific: tag is 0x80 + type
            tagValue in ASN1_CONTEXT_PRIMITIVE_BASE..ASN1_CONTEXT_PRIMITIVE_END -> tagValue - ASN1_CONTEXT_PRIMITIVE_BASE

            // Constructed context-specific: tag is 0xA0 + type
            tagValue in ASN1_CONTEXT_CONSTRUCTED_BASE..ASN1_CONTEXT_CONSTRUCTED_END -> tagValue - ASN1_CONTEXT_CONSTRUCTED_BASE

            // Some implementations use direct tag values
            tagValue in 0..GENERAL_NAME_TYPE_MAX -> tagValue

            else -> return null
        }

    val generalNameType = GeneralNameType.fromTag(typeTag) ?: return null

    // Parse the value based on the type
    val value =
        when (generalNameType) {
            GeneralNameType.DNS_NAME,
            GeneralNameType.RFC822_NAME,
            GeneralNameType.URI,
            -> {
                // These are IA5String (ASCII), stored directly in content
                primitive.content.decodeToString()
            }

            GeneralNameType.IP_ADDRESS -> {
                // IP address is stored as OCTET STRING
                parseIpAddress(primitive.content)
            }

            GeneralNameType.REGISTERED_ID -> {
                // OID - parse as dotted string
                parseOid(primitive.content)
            }

            else -> {
                // For other types (otherName, directoryName, etc.),
                // return the hex-encoded content for now
                primitive.content.toHexString()
            }
        }

    return GeneralName(generalNameType, value)
}

/**
 * Parses an IP address from raw bytes.
 *
 * @param bytes The raw IP address bytes (4 bytes for IPv4, 16 bytes for IPv6)
 * @return The IP address as a string
 */
internal fun parseIpAddress(bytes: ByteArray): String =
    when (bytes.size) {
        IPV4_BYTE_LENGTH -> {
            // IPv4: a.b.c.d
            bytes.joinToString(".") { (it.toInt() and BYTE_MASK).toString() }
        }

        IPV6_BYTE_LENGTH -> {
            // IPv6: simplified representation
            val parts = mutableListOf<String>()
            for (i in 0 until IPV6_BYTE_LENGTH step IPV6_STEP) {
                val value = ((bytes[i].toInt() and BYTE_MASK) shl SHIFT_8) or (bytes[i + 1].toInt() and BYTE_MASK)
                parts.add(value.toString(HEX_RADIX))
            }
            parts.joinToString(":")
        }

        else -> {
            // Unknown format, return hex
            bytes.toHexString()
        }
    }

/**
 * Parses an OID from raw bytes.
 *
 * @param bytes The raw OID bytes
 * @return The OID as a dotted string
 */
internal fun parseOid(bytes: ByteArray): String {
    if (bytes.isEmpty()) {
        return ""
    }

    val arcs = mutableListOf<Int>()

    // First byte encodes first two arcs: first = byte / 40, second = byte % 40
    val first = bytes[0].toInt() and BYTE_MASK
    arcs.add(first / OID_FIRST_ARC_DIVISOR)
    arcs.add(first % OID_FIRST_ARC_DIVISOR)

    // Remaining bytes encode subsequent arcs using base-128 encoding
    var value = 0
    for (i in 1 until bytes.size) {
        val b = bytes[i].toInt() and BYTE_MASK
        value = (value shl OID_SHIFT) or (b and OID_VALUE_MASK)
        if (b and OID_HIGH_BIT_MASK == 0) {
            arcs.add(value)
            value = 0
        }
    }

    return arcs.joinToString(".")
}

/**
 * Converts a ByteArray to an uppercase hexadecimal string.
 */
private fun ByteArray.toHexString(): String =
    joinToString("") { byte ->
        val hex = (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).uppercase()
        if (hex.length == 1) {
            "0$hex"
        } else {
            hex
        }
    }
