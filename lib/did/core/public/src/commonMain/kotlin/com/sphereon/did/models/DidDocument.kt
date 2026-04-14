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

package com.sphereon.did.models

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * W3C DID Document model.
 *
 * A DID Document is a set of data describing the DID subject, including mechanisms,
 * such as cryptographic public keys, that the DID subject or a DID delegate can use
 * to authenticate itself and prove its association with the DID.
 *
 * This is a plain data class for Swift/Obj-C and JS compatibility - no sealed classes.
 *
 * @property context The JSON-LD context(s) for this DID Document
 * @property id The DID that this document describes
 * @property controller The DID of the controller of this DID Document
 * @property alsoKnownAs Alternative identifiers for this DID subject
 * @property verificationMethod Verification methods (public keys) defined in this document
 * @property authentication Verification methods for authentication purposes
 * @property assertionMethod Verification methods for making assertions (signing credentials)
 * @property keyAgreement Verification methods for key agreement (encryption)
 * @property capabilityInvocation Verification methods for capability invocation
 * @property capabilityDelegation Verification methods for capability delegation
 * @property service Service endpoints for interacting with the DID subject
 *
 * @see <a href="https://www.w3.org/TR/did-core/">W3C DID Core Specification</a>
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidDocument", exact = true)
@JsExportCompat
@Serializable
data class DidDocument
    @JvmOverloads
    constructor(
        @SerialName("@context")
        val context: List<String> = listOf(DEFAULT_CONTEXT),
        val id: String,
        val controller: String? = null,
        val alsoKnownAs: List<String>? = null,
        val verificationMethod: List<VerificationMethod>? = null,
        val authentication: List<VerificationMethodOrReference>? = null,
        val assertionMethod: List<VerificationMethodOrReference>? = null,
        val keyAgreement: List<VerificationMethodOrReference>? = null,
        val capabilityInvocation: List<VerificationMethodOrReference>? = null,
        val capabilityDelegation: List<VerificationMethodOrReference>? = null,
        val service: List<DidService>? = null,
    ) {
        companion object {
            /**
             * The default W3C DID context.
             */
            const val DEFAULT_CONTEXT: String = "https://www.w3.org/ns/did/v1"

            /**
             * The JSON-LD context for JSON Web Key 2020.
             */
            const val JWK_2020_CONTEXT: String = "https://w3id.org/security/suites/jws-2020/v1"

            /**
             * The JSON-LD context for Ed25519 Verification Key 2020.
             */
            const val ED25519_2020_CONTEXT: String = "https://w3id.org/security/suites/ed25519-2020/v1"

            /**
             * The JSON-LD context for Multikey.
             */
            const val MULTIKEY_CONTEXT: String = "https://w3id.org/security/multikey/v1"
        }

        /**
         * Gets a verification method by its ID.
         *
         * @param vmId The verification method ID (can be absolute like "did:example:123#key-1"
         *            or just the fragment like "#key-1" or "key-1")
         * @return The matching VerificationMethod, or null if not found
         */
        fun getVerificationMethodById(vmId: String): VerificationMethod? {
            // Normalize the search ID
            val searchId =
                when {
                    vmId.startsWith("#") -> vmId.substring(1)
                    vmId.contains("#") -> vmId.substringAfter("#")
                    else -> vmId
                }

            return verificationMethod?.find { vm ->
                val vmFragment = vm.getKeyId()
                vmFragment == searchId || vm.id == vmId
            }
        }

        /**
         * Gets all verification methods for a specific purpose (resolves references).
         *
         * This method resolves both direct references (strings) and embedded verification
         * methods from the specified relationship array.
         *
         * @param purpose The verification purpose to get methods for
         * @return List of resolved VerificationMethod objects
         */
        fun getVerificationMethodsForPurpose(purpose: VerificationPurpose): List<VerificationMethod> {
            val relationships =
                when (purpose) {
                    VerificationPurpose.AUTHENTICATION -> authentication
                    VerificationPurpose.ASSERTION_METHOD -> assertionMethod
                    VerificationPurpose.KEY_AGREEMENT -> keyAgreement
                    VerificationPurpose.CAPABILITY_INVOCATION -> capabilityInvocation
                    VerificationPurpose.CAPABILITY_DELEGATION -> capabilityDelegation
                } ?: return emptyList()

            return relationships.mapNotNull { vmOrRef ->
                when {
                    vmOrRef.embedded != null -> vmOrRef.embedded
                    vmOrRef.reference != null -> getVerificationMethodById(vmOrRef.reference)
                    else -> null
                }
            }
        }

        /**
         * Gets a map of all verification methods indexed by their purpose.
         *
         * @return Map from VerificationPurpose to list of VerificationMethod objects
         */
        fun getVerificationMethodsByPurpose(): Map<VerificationPurpose, List<VerificationMethod>> =
            VerificationPurpose.entries
                .associateWith { purpose ->
                    getVerificationMethodsForPurpose(purpose)
                }.filterValues { it.isNotEmpty() }

        /**
         * Gets a service by its ID.
         *
         * @param serviceId The service ID (can be absolute like "did:example:123#service-1"
         *                 or just the fragment like "#service-1" or "service-1")
         * @return The matching DidService, or null if not found
         */
        fun getServiceById(serviceId: String): DidService? {
            val searchId =
                when {
                    serviceId.startsWith("#") -> serviceId.substring(1)
                    serviceId.contains("#") -> serviceId.substringAfter("#")
                    else -> serviceId
                }

            return service?.find { svc ->
                val svcFragment = svc.getServiceId()
                svcFragment == searchId || svc.id == serviceId
            }
        }

        /**
         * Gets services by their type.
         *
         * @param type The service type to search for
         * @return List of services with the specified type
         */
        fun getServicesByType(type: String): List<DidService> = service?.filter { it.type == type } ?: emptyList()

        /**
         * Checks if the document has any verification methods.
         */
        fun hasVerificationMethods(): Boolean = !verificationMethod.isNullOrEmpty()

        /**
         * Checks if the document has any services.
         */
        fun hasServices(): Boolean = !service.isNullOrEmpty()

        /**
         * Gets the primary controller of this DID Document.
         * Returns the controller if set, otherwise returns the DID itself.
         */
        fun getPrimaryController(): String = controller ?: id
    }
