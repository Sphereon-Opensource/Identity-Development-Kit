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

@file:OptIn(ExperimentalForeignApi::class, ExperimentalForeignApi::class, ExperimentalForeignApi::class)

package com.sphereon.ktor.http.client.config

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.x509.Certificate
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

/**
 * iOS SSL provider implementation for Ktor.
 */
class LegacyIosSslProvider(
    private val opts: LegacySslConfig,
) : LegacySslProvider {
    override suspend fun getCertificates(): List<LegacyCertificateAndKeyIos> =
        opts.certificateAliases.map { alias ->
            // Retrieve certificate DTOs
            val certChain: Array<Certificate> = opts.certificateStoreService.getCertificateChain(alias)

            // Retrieve managed key info and extract JWK
            val managed: ManagedKeyInfoType<Jwk> =
                opts.keyStoreService.getKey(keyInfo = KeyInfo<Jwk>(alias = alias)) as ManagedKeyInfoType<Jwk>
            val jwkKey: Jwk = managed.key

            // Convert first certificate DER to CFDataRef
            val leafCert = certChain.first()
            val dataBytes = leafCert.der
            val cfData: CFDataRef? =
                dataBytes.usePinned { pinned ->
                    // reinterpret pinned Byte pointer as UByte pointer for CFDataCreate
                    val ptr: CPointer<UByteVar> = pinned.addressOf(0).reinterpret()
                    CFDataCreate(null, ptr, dataBytes.size.toLong())
                }
            val dataRef = cfData ?: throw IllegalStateException("Failed to create CFDataRef for certificate")

            // Create SecCertificateRef
            val secCert: SecCertificateRef =
                SecCertificateCreateWithData(null, dataRef)
                    ?: throw IllegalStateException("Failed to create SecCertificateRef")

            // Convert JWK to SecKeyRef (TODO)
            val secKey: SecKeyRef = convertJwkToSecKey(jwkKey)

            // Create SecIdentityRef from cert + key
            val identity: SecIdentityRef =
                createIdentityFromCertAndKey(secCert, secKey)
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
        key: SecKeyRef,
    ): SecIdentityRef? {
        // TODO: use Security APIs to import or construct a SecIdentityRef
        throw NotImplementedError("SecIdentity creation not implemented")
    }
}

actual fun createLegacySslProvider(opts: LegacySslConfig): LegacySslProvider = LegacyIosSslProvider(opts)
