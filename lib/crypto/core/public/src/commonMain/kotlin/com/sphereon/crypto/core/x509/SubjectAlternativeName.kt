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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Types of General Names as defined in RFC 5280 Section 4.2.1.6.
 *
 * GeneralName ::= CHOICE {
 *      otherName                       [0]     OtherName,
 *      rfc822Name                      [1]     IA5String,
 *      dNSName                         [2]     IA5String,
 *      x400Address                     [3]     ORAddress,
 *      directoryName                   [4]     Name,
 *      ediPartyName                    [5]     EDIPartyName,
 *      uniformResourceIdentifier       [6]     IA5String,
 *      iPAddress                       [7]     OCTET STRING,
 *      registeredID                    [8]     OBJECT IDENTIFIER }
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.6">RFC 5280 Section 4.2.1.6</a>
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GeneralNameType", exact = true)
@JsExportCompat
@Serializable
enum class GeneralNameType(
    val tag: Int,
) {
    @SerialName("otherName")
    OTHER_NAME(0),

    @SerialName("rfc822Name")
    RFC822_NAME(1),

    @SerialName("dNSName")
    DNS_NAME(2),

    @SerialName("x400Address")
    X400_ADDRESS(3),

    @SerialName("directoryName")
    DIRECTORY_NAME(4),

    @SerialName("ediPartyName")
    EDI_PARTY_NAME(5),

    @SerialName("uniformResourceIdentifier")
    URI(6),

    @SerialName("iPAddress")
    IP_ADDRESS(7),

    @SerialName("registeredID")
    REGISTERED_ID(8),
    ;

    companion object {
        @JvmStatic
        fun fromTag(tag: Int): GeneralNameType? = entries.find { it.tag == tag }
    }
}

/**
 * A single General Name entry from the Subject Alternative Name extension.
 *
 * @property type The type of the general name (DNS, URI, IP, etc.)
 * @property value The string value of the general name
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GeneralName", exact = true)
@JsExportCompat
@Serializable
data class GeneralName(
    val type: GeneralNameType,
    val value: String,
) {
    /**
     * Returns true if this is a DNS name.
     */
    val isDnsName: Boolean get() = type == GeneralNameType.DNS_NAME

    /**
     * Returns true if this is a URI.
     */
    val isUri: Boolean get() = type == GeneralNameType.URI

    /**
     * Returns true if this is an IP address.
     */
    val isIpAddress: Boolean get() = type == GeneralNameType.IP_ADDRESS

    /**
     * Returns true if this is an email address (RFC822 name).
     */
    val isEmail: Boolean get() = type == GeneralNameType.RFC822_NAME
}

/**
 * Subject Alternative Name extension content.
 *
 * This extension allows identities to be bound to the subject of the certificate.
 * Common uses include DNS names for TLS certificates, email addresses, URIs, and IP addresses.
 *
 * @property names List of general names in this SAN extension
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.6">RFC 5280 Section 4.2.1.6</a>
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SubjectAlternativeName", exact = true)
@JsExportCompat
@Serializable
data class SubjectAlternativeName(
    val names: List<GeneralName>,
) {
    /**
     * Get all DNS names from this SAN extension.
     */
    val dnsNames: List<String> get() = names.filter { it.isDnsName }.map { it.value }

    /**
     * Get all URIs from this SAN extension.
     */
    val uris: List<String> get() = names.filter { it.isUri }.map { it.value }

    /**
     * Get all IP addresses from this SAN extension.
     */
    val ipAddresses: List<String> get() = names.filter { it.isIpAddress }.map { it.value }

    /**
     * Get all email addresses (RFC822 names) from this SAN extension.
     */
    val emails: List<String> get() = names.filter { it.isEmail }.map { it.value }

    /**
     * Check if this SAN contains a specific DNS name (case-insensitive).
     */
    fun containsDnsName(dnsName: String): Boolean = dnsNames.any { it.equals(dnsName, ignoreCase = true) }

    /**
     * Check if this SAN contains a specific URI (case-sensitive for scheme, case-insensitive for host).
     */
    fun containsUri(uri: String): Boolean = uris.contains(uri)

    /**
     * Check if this SAN contains a specific IP address.
     */
    fun containsIpAddress(ipAddress: String): Boolean = ipAddresses.contains(ipAddress)

    companion object {
        /**
         * Empty SAN extension (no names).
         */
        @JvmStatic
        val EMPTY = SubjectAlternativeName(emptyList())
    }
}
