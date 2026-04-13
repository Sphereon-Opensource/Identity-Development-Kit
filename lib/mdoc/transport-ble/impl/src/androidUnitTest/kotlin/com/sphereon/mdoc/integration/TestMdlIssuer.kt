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

package com.sphereon.mdoc.integration

import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.DefaultCallbacks
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.cose.CoseCryptoProviderToCallbackAdapter
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProvider
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedItem
import com.sphereon.mdoc.data.device.IssuerSignedItemCborCodecImpl
import com.sphereon.mdoc.data.mdl.Mdl
import com.sphereon.mdoc.data.mso.DigestID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Test mDL (mobile Driving License) issuer for integration tests.
 * Creates ISO 18013-5 compliant mDL documents with random test data.
 */
class TestMdlIssuer(
    private val kms: KeyManagerService,
    private val mdocSignService: MdocSignService,
    private val certificateService: CertificateService
) {

    private val softwareKmsProvider: SoftwareKmsProvider = kms.getProviderById("test-software") as SoftwareKmsProvider
    private val keyStore = softwareKmsProvider.keyStore as com.sphereon.crypto.core.kms.KeyStore // Can be either MemoryKeyStoreService or SoftwareKeyStoreService

    // Cache only for certificate to avoid repeated lookups (certificates are public information)
    private var cachedIssuerCertificate: Certificate? = null

    // Mutex to ensure thread-safe initialization of cached certificate
    private val initMutex = Mutex()

    init {
        DefaultCallbacks.setCoseCryptoDefault(CoseCryptoProviderToCallbackAdapter(keyManagerServiceProvider = {kms}))
    }

    private suspend fun getOrCreateIssuerKeyInfo(): ManagedKeyInfoType<CoseKeyType> = withContext(Dispatchers.IO) {
        val keyRef = keyStore.listKeys().firstOrNull { it.alias == MDL_ISSUER_KEY_ALIAS }
        if (keyRef != null) {
            val fullKeyInfo = keyStore.getKey(KeyInfo(alias = keyRef.alias, kid = keyRef.kid, providerId = keyRef.providerId))
            return@withContext ManagedKeyInfo(
                alias = MDL_ISSUER_KEY_ALIAS,
                providerId = keyStore.id,
                resolvedKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(fullKeyInfo)
            )
        }
        val managedKeyPair = kms.generateKeyAsync(
            alias = MDL_ISSUER_KEY_ALIAS,
            alg = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PRIVATE
        )
        managedKeyPair.toManagedKeyInfo<CoseKey>(KeyVisibility.PRIVATE, KeyEncoding.COSE)
    }

    private suspend fun getOrCreateIssuerCertificate(issuerKeyInfo: ManagedKeyInfoType<CoseKeyType>): Certificate = withContext(
        Dispatchers.IO
    ) {
        // Return cached certificate if available (certificates are public information, safe to cache)
        cachedIssuerCertificate?.let { return@withContext it }

        initMutex.withLock {
            // Double-check pattern for certificate only
            cachedIssuerCertificate?.let { return@withLock it }

            if (!keyStore.listCertificateAliases().contains(MDL_ISSUER_KEY_ALIAS)) {
                val issuerCn = X509DistinguishedNameElements(
                    commonName = MDL_ISSUER_KEY_ALIAS,
                    organizationName = "Test DMV",
                    organizationUnit = "Driver Licensing",
                    country = "US"
                )
                val issuerCertResult =
                    certificateService.createCertificate(
                        issuerKeyInfo = issuerKeyInfo,
                        issuer = issuerCn,
                        subjectKeyInfo = issuerKeyInfo,
                        subject = issuerCn,
                        serialNumber = CERT_SERIAL_NUMBER
                    )
                keyStore.storeCertificateChain(
                    MDL_ISSUER_KEY_ALIAS,
                    arrayOf(issuerCertResult.certificate),
                    issuerKeyInfo
                )
            }
            val certificate = keyStore.getCertificate(MDL_ISSUER_KEY_ALIAS)
            cachedIssuerCertificate = certificate
            certificate
        }
    }

    private suspend fun getIssuerKeyInfoWithCert(): ManagedKeyInfoType<CoseKeyType> = withContext(Dispatchers.IO) {
        // Always create fresh key info with certificate - no caching of private key material
        coroutineScope {
            val issuerKeyInfoDeferred = async { getOrCreateIssuerKeyInfo() }
            val issuerKeyInfo = issuerKeyInfoDeferred.await()
            val issuerCert = getOrCreateIssuerCertificate(issuerKeyInfo)

            ManagedKeyInfo(
                alias = MDL_ISSUER_KEY_ALIAS,
                providerId = "test-software",
                resolvedKeyInfo = issuerCert.amendCoseKeyInfo(issuerKeyInfo)
            )
        }
    }

    private fun createMdlDataItems(): List<IssuerSignedItem<Any>> {
        // Pre-calculate timestamp values once
        val currentTime = Clock.System.now()
        val expiryTime = Instant.fromEpochSeconds(
            currentTime.epochSeconds + (DAYS_UNTIL_EXPIRY * SECONDS_PER_DAY)
        )

        val personalData = generateRandomPersonalData()

        return buildMdlItems(personalData, currentTime, expiryTime)
    }

    private data class PersonalData(
        val givenName: String,
        val familyName: String,
        val birthDate: String
    )

    private fun generateRandomPersonalData(): PersonalData {
        val givenNames = listOf("John", "Emma", "Michael", "Sarah", "David", "Lisa", "James", "Anna", "Robert", "Maria")
        val familyNames = listOf("Doe", "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez")
        val birthDates = listOf("1990-03-15", "1985-07-22", "1992-11-08", "1988-05-30", "1995-12-03", "1987-09-14")

        return PersonalData(
            givenName = givenNames.random(),
            familyName = familyNames.random(),
            birthDate = birthDates.random()
        )
    }

    private fun buildMdlItems(
        personalData: PersonalData,
        currentTime: Instant,
        expiryTime: Instant
    ): List<IssuerSignedItem<Any>> {
        val items = mutableListOf<IssuerSignedItem<Any>>()

        // Mandatory elements as per ISO 18013-5
        items.add(
            IssuerSignedItem.createFromDefinition(
                digestID = DigestID(1u),
                elementDef = Mdl.Def.family_name,
                elementValue = personalData.familyName
            )
        )
        items.add(
            IssuerSignedItem.createFromDefinition(
                digestID = DigestID(2u),
                elementDef = Mdl.Def.given_name,
                elementValue = personalData.givenName
            )
        )
        items.add(
            IssuerSignedItem.createFromDefinition(
                digestID = DigestID(3u),
                elementDef = Mdl.Def.birth_date,
                elementValue = personalData.birthDate
            )
        )
        items.add(
            IssuerSignedItem.createFromDefinition(
                digestID = DigestID(4u),
                elementDef = Mdl.Def.issue_date,
                elementValue = currentTime.toString().substring(0, 10) // YYYY-MM-DD format
            )
        )
        items.add(
            IssuerSignedItem.createFromDefinition(
                digestID = DigestID(5u),
                elementDef = Mdl.Def.expiry_date,
                elementValue = expiryTime.toString().substring(0, 10) // YYYY-MM-DD format
            )
        )
        items.add(
            IssuerSignedItem.createFromDefinition(
                digestID = DigestID(6u),
                elementDef = Mdl.Def.issuing_country,
                elementValue = "US"
            )
        )
        items.add(
            IssuerSignedItem.createFromDefinition(
                digestID = DigestID(7u),
                elementDef = Mdl.Def.issuing_authority,
                elementValue = "US DMV"
            )
        )
        items.add(
            IssuerSignedItem.createFromDefinition(
                digestID = DigestID(8u),
                elementDef = Mdl.Def.document_number,
                elementValue = (MIN_RANDOM_DOC_NUMBER..MAX_RANDOM_DOC_NUMBER).random().toString()
            )
        )

        // Add a minimal portrait (1x1 pixel PNG)
        items.add(
            IssuerSignedItem.createFromDefinition(
                digestID = DigestID(9u),
                elementDef = Mdl.Def.portrait,
                elementValue = MINIMAL_PNG_PORTRAIT
            )
        )

        return items
    }

    suspend fun issueMdl(deviceKeyInfo: ManagedKeyInfoType<CoseKeyType>): Document = withContext(Dispatchers.IO) {
        // Use coroutineScope to parallelize operations
        coroutineScope {
            val issuerKeyInfoWithCertDeferred = async { getIssuerKeyInfoWithCert() }
            val mdlDataItemsDeferred = async { createMdlDataItems() }

            // Await all parallel operations
            val issuerKeyInfoWithCert = issuerKeyInfoWithCertDeferred.await()
            val mdlDataItems = mdlDataItemsDeferred.await()

            // Pre-calculate time values once
            val currentTime = Clock.System.now()
            val signedTime = DateTimeUtils.DEFAULTS.dateTime(
                epochSeconds = (currentTime.epochSeconds - TIME_OFFSET_PAST_SECONDS).toInt()
            )
            val validFromTime = DateTimeUtils.DEFAULTS.dateTime(
                epochSeconds = (currentTime.epochSeconds - TIME_OFFSET_PAST_SECONDS).toInt()
            )
            val validUntilTime = DateTimeUtils.DEFAULTS.dateTime(
                epochSeconds = (currentTime.epochSeconds + TIME_OFFSET_FUTURE_SECONDS).toInt()
            )

            // Build mdoc with pre-calculated data
            val msoBuilder = IssuerSigned.MsoBuilder(issuerSignedItemCborCodec = IssuerSignedItemCborCodecImpl())
                .withDocType(com.sphereon.mdoc.data.device.DocType("org.iso.18013.5.1.mDL"))
                .withDeviceKeyInfo(deviceKeyInfo)
                .withSigningKeyInfo(issuerKeyInfoWithCert)
                .withSigned(signedTime)
                .withValidFrom(validFromTime)
                .withValidUntil(validUntilTime)

            // Add namespace with all items at once
            mdlDataItems.forEach { item ->
                msoBuilder.addNameSpace(Mdl.MDL_NAMESPACE, item)
            }

            // Build and sign mdoc
            val buildMdocDeferred = async {
                msoBuilder.buildAndSignMdoc(mdocSignService = mdocSignService, requireDeviceX5Chain = false)
            }

            buildMdocDeferred.await()
        }
    }

    companion object {
        const val MDL_ISSUER_KEY_ALIAS = "test-mdl-issuer"
        private const val CERT_SERIAL_NUMBER = 1
        private const val DAYS_UNTIL_EXPIRY = 365
        private const val SECONDS_PER_DAY = 86400L
        private const val TIME_OFFSET_PAST_SECONDS = 144000L
        private const val TIME_OFFSET_FUTURE_SECONDS = 120000L
        private const val MIN_RANDOM_DOC_NUMBER = 100000000
        private const val MAX_RANDOM_DOC_NUMBER = 999999999

        // Minimal 1x1 pixel transparent PNG (67 bytes)
        private val MINIMAL_PNG_PORTRAIT = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1F.toByte(), 0x15.toByte(), 0xC4.toByte(),
            0x89.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0A.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(), 0x54.toByte(),
            0x78.toByte(), 0x9C.toByte(), 0x63.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x05.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x2D.toByte(), 0xB4.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x49.toByte(), 0x45.toByte(), 0x4E.toByte(), 0x44.toByte(), 0xAE.toByte(), 0x42.toByte(),
            0x60.toByte(), 0x82.toByte()
        )
    }
}
