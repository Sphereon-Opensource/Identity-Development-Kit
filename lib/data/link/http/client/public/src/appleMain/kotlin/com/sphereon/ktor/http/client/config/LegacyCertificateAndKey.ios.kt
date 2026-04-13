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
    fun toNSURLCredential(): NSURLCredential {
        return NSURLCredential.credentialWithIdentity(
            identity = key, // SecIdentityRef contains both certificate and private key
            certificates = certificateChain.toList(), // Additional certificates in the chain
            persistence = NSURLCredentialPersistence.NSURLCredentialPersistenceNone
        )
    }

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
