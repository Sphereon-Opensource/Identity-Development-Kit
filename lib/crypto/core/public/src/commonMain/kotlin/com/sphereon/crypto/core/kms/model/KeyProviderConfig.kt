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

package com.sphereon.crypto.core.kms.model

import com.sphereon.core.api.Base64UrlSerializer
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyVisibility
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyProviderConfig", exact = true)
data class
KeyProviderConfig
    @JvmOverloads
    constructor(
        /** Enable caching of keys/certificates. Requires a JSR107 Cache implementation on the classpath! */
        val cacheEnabled: Boolean? = false,
        /** How long in seconds should certificates be kept in the cache since last access. Default: 5 min */
        val cacheTTLInSeconds: Int? = 5 * 60,
        val type: KeyProviderType,
        val externalKeyVisibility: KeyVisibility = KeyVisibility.PUBLIC, // only public keys for anything with hardware
        val password: PasswordInputCallback? = null,
        val pkcs11Parameters: Pkcs11Parameters? = null,
        val pkcs12Parameters: KeystoreParameters? = null,
        val jksParameters: KeystoreParameters? = null,
        val aws: AwsKmsClientConfig? = null,
//    val restConfig: RestConfig? = null
    )
/*
@kotlinx.serialization.Serializable
@JsExportCompat
data class RestConfig @JvmOverloads constructor(
    val baseUrl: String? = "http://localhost/",

    val connectTimeoutInMS: Int? = 5000,
    val readTimeoutInMS: Int? = 10000

)*/

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("Pkcs11Parameters", exact = true)
data class
Pkcs11Parameters
    @JvmOverloads
    constructor(
        /** The path to the library  */
        val pkcs11LibraryPath: String? = null,
        /** The callback to enter a password/pincode  */
        val callback: PasswordInputCallback? = null,
        /** The slot Id to use  */
        val slotId: Int? = 0,
        /** The slot list index to use  */
        val slotListIndex: Int? = -1,
        /** Additional PKCS11 config  */
        val extraPkcs11Config: String? = null,
    )

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeystoreParameters", exact = true)
data class
KeystoreParameters
    @JvmOverloads
    constructor(
        val providerPath: String? = null,
        @Serializable(with = Base64UrlSerializer::class)
        val providerBytes: ByteArray? = null,
        val persistCallback: (suspend (bytes: ByteArray) -> Unit)? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as KeystoreParameters

            if (providerPath != other.providerPath) {
                return false
            }
            if (providerBytes != null) {
                if (other.providerBytes == null) {
                    return false
                }
                if (!providerBytes.contentEquals(other.providerBytes)) {
                    return false
                }
            } else if (other.providerBytes != null) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = providerPath?.hashCode() ?: 0
            result = 31 * result + (providerBytes?.contentHashCode() ?: 0)
            return result
        }
    }
/*

@kotlinx.serialization.Serializable
@JsExportCompat
class Pkcs12Paremeters(providerPath: String?, providerBytes: ByteArray?) : KeystoreParameters(providerPath, providerBytes)
*/
