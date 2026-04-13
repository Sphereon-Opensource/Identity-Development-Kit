@file:OptIn(ExperimentalForeignApi::class, ExperimentalForeignApi::class, ExperimentalForeignApi::class)

package com.sphereon.ktor.http.client.config

import com.sphereon.crypto.core.KeyInfo
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecIdentityRef
import platform.Security.SecKeyRef
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.x509.Certificate

/**
 * iOS SSL provider implementation for Ktor.
 */
class LegacyIosSslProvider(private val opts: LegacySslConfig) : LegacySslProvider {

    override suspend fun getCertificates(): List<LegacyCertificateAndKeyIos> = opts.certificateAliases.map { alias ->
        // Retrieve certificate DTOs
        val certChain: Array<Certificate> = opts.certificateStoreService.getCertificateChain(alias)

        // Retrieve managed key info and extract JWK
        val managed: ManagedKeyInfoType<Jwk> =
            opts.keyStoreService.getKey(keyInfo = KeyInfo<Jwk>(alias = alias)) as ManagedKeyInfoType<Jwk>
        val jwkKey: Jwk = managed.key

        // Convert first certificate DER to CFDataRef
        val leafCert = certChain.first()
        val dataBytes = leafCert.der
        val cfData: CFDataRef? = dataBytes.usePinned { pinned ->
            // reinterpret pinned Byte pointer as UByte pointer for CFDataCreate
            val ptr: CPointer<UByteVar> = pinned.addressOf(0).reinterpret()
            CFDataCreate(null, ptr, dataBytes.size.toLong())
        }
        val dataRef = cfData ?: throw IllegalStateException("Failed to create CFDataRef for certificate")

        // Create SecCertificateRef
        val secCert: SecCertificateRef = SecCertificateCreateWithData(null, dataRef)
            ?: throw IllegalStateException("Failed to create SecCertificateRef")

        // Convert JWK to SecKeyRef (TODO)
        val secKey: SecKeyRef = convertJwkToSecKey(jwkKey)

        // Create SecIdentityRef from cert + key
        val identity: SecIdentityRef = createIdentityFromCertAndKey(secCert, secKey)
            ?: throw IllegalStateException("Failed to create SecIdentityRef")

        // Build CertificateAndKey
        LegacyCertificateAndKeyIos(arrayOf(secCert), identity)
    }

    override suspend fun getTrustManager(): Any? = null

    private fun convertJwkToSecKey(jwk: Jwk): SecKeyRef {
        // TODO: implement conversion from JWK to native SecKeyRef
        throw NotImplementedError("Key conversion not implemented")
    }

    private fun createIdentityFromCertAndKey(
        cert: SecCertificateRef,
        key: SecKeyRef
    ): SecIdentityRef? {
        // TODO: use Security APIs to import or construct a SecIdentityRef
        throw NotImplementedError("SecIdentity creation not implemented")
    }
}

actual fun createLegacySslProvider(opts: LegacySslConfig): LegacySslProvider = LegacyIosSslProvider(opts)
