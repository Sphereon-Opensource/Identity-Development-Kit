/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

@file:OptIn(ExperimentalForeignApi::class, ExperimentalForeignApi::class)

package com.sphereon.ktor.http.client.config

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.credentialWithIdentity
import platform.Security.SecCertificateRef
import platform.Security.SecIdentityRef

/**
 * On iOS, use Security framework types for certificate and private key.
 */
data class LegacyCertificateAndKeyIos(
    override val certificateChain: Array<SecCertificateRef>,
    override val key: SecIdentityRef,
) : LegacyCertificateAndKey<SecCertificateRef, SecIdentityRef> {
    /**
     * Convert this into NSURLCredential for iOS NSURLSession usage.
     *
     * On iOS, Ktor's Darwin engine uses NSURLSession which expects
     * NSURLCredential rather than the JVM CertificateAndKey type.
     * This method creates a client certificate credential that can be used
     * in NSURLSession challenge handlers.
     */
    fun toNSURLCredential(): NSURLCredential =
        NSURLCredential.credentialWithIdentity(
            identity = key, // SecIdentityRef contains both certificate and private key
            certificates = certificateChain.toList(), // Additional certificates in the chain
            persistence = NSURLCredentialPersistence.NSURLCredentialPersistenceNone,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as LegacyCertificateAndKeyIos

        if (!certificateChain.contentEquals(other.certificateChain)) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        var result = certificateChain.contentHashCode()
        result = 31 * result + key.hashCode()
        return result
    }
}
