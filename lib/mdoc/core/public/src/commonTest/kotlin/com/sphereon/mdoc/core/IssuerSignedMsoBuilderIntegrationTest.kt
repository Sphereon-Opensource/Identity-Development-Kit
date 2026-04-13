/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.mdoc.core

import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.core.testutil.MdocTestContext
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedItem
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.data.mso.DigestID
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration tests for IssuerSigned.MsoBuilder with real DI and MdocSignService.
 *
 * These tests cover the `buildAndSign` and `buildAndSignMdoc` methods
 * that require MdocSignService with actual cryptographic operations.
 */
class IssuerSignedMsoBuilderIntegrationTest {

    private lateinit var ctx: MdocTestContext
    private lateinit var mdocSignService: MdocSignService

    private val mdlDocType = DocType("org.iso.18013.5.1.mDL")
    private val mdlNamespace = NameSpace("org.iso.18013.5.1")

    @BeforeTest
    fun setup() {
        ctx = MdocTestContext(this)
        mdocSignService = ctx.mdocSignService
    }

    private suspend fun createIssuerKeyWithCertificate(): ManagedKeyInfoType<CoseKeyType> {
        val issuerKeyPair = ctx.kms.generateKeyAsync(
            alias = "test-issuer-key-${Uuid.v4String()}",
            alg = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PRIVATE,
            providerId = null
        )
        val issuerKeyInfo = issuerKeyPair.toManagedKeyInfo<CoseKey>(KeyVisibility.PRIVATE, KeyEncoding.COSE)

        val issuerDn = X509DistinguishedNameElements(
            commonName = "Test mDL Issuer",
            organizationName = "Test DMV",
            organizationUnit = "Driver Licensing",
            country = "US"
        )

        val certResult = ctx.certificateService.createCertificate(
            issuerKeyInfo = issuerKeyInfo,
            issuer = issuerDn,
            subjectKeyInfo = issuerKeyInfo,
            subject = issuerDn,
            serialNumber = 1
        )

        return ManagedKeyInfo(
            alias = issuerKeyInfo.alias,
            providerId = issuerKeyInfo.providerId,
            resolvedKeyInfo = certResult.certificate.amendCoseKeyInfo(issuerKeyInfo)
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testMsoBuilderBuildAndSign() = runTest {
        val issuerKeyInfo = createIssuerKeyWithCertificate()

        val deviceKeyPair = ctx.kms.generateKeyAsync(
            alias = "test-device-key-${Uuid.v4String()}",
            alg = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PUBLIC,
            providerId = null
        )
        val deviceKeyInfo = deviceKeyPair.toManagedKeyInfo<CoseKey>(KeyVisibility.PUBLIC, KeyEncoding.COSE)

        val item1 = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        val item2 = IssuerSignedItem.create(
            digestID = DigestID(2u),
            elementIdentifier = DataElementIdentifier("family_name"),
            elementValue = "Doe"
        )

        val now = DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validUntil = DateTimeUtils.DEFAULTS.dateTimeLocal()

        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKeyInfo(deviceKeyInfo)
            .withSigningKeyInfo(issuerKeyInfo)
            .withSigned(now)
            .withValidFrom(now)
            .withValidUntil(validUntil)

        builder.addNameSpace(mdlNamespace, item1 as IssuerSignedItem<Any>, item2 as IssuerSignedItem<Any>)

        val issuerSigned = builder.buildAndSign(
            mdocSignService = mdocSignService,
            requireDeviceX5Chain = false
        )

        assertNotNull(issuerSigned)
        assertNotNull(issuerSigned.issuerAuth)
        assertNotNull(issuerSigned.nameSpaces)
        assertEquals(1, issuerSigned.nameSpaces!!.size)
        assertEquals(2, issuerSigned.nameSpaces!![mdlNamespace]?.size)

        val mso = issuerSigned.MSO
        assertNotNull(mso)
        assertEquals(mdlDocType, mso.docType)
        assertEquals("SHA-256", mso.digestAlgorithm.toString())
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testMsoBuilderBuildAndSignMdoc() = runTest {
        val issuerKeyInfo = createIssuerKeyWithCertificate()

        val deviceKeyPair = ctx.kms.generateKeyAsync(
            alias = "test-device-key-mdoc-${Uuid.v4String()}",
            alg = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PUBLIC,
            providerId = null
        )
        val deviceKeyInfo = deviceKeyPair.toManagedKeyInfo<CoseKey>(KeyVisibility.PUBLIC, KeyEncoding.COSE)

        val item1 = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "Jane"
        )

        val now = DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validUntil = DateTimeUtils.DEFAULTS.dateTimeLocal()

        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKeyInfo(deviceKeyInfo)
            .withSigningKeyInfo(issuerKeyInfo)
            .withSigned(now)
            .withValidFrom(now)
            .withValidUntil(validUntil)

        builder.addNameSpace(mdlNamespace, item1 as IssuerSignedItem<Any>)

        val document = builder.buildAndSignMdoc(
            mdocSignService = mdocSignService,
            requireDeviceX5Chain = false
        )

        assertNotNull(document)
        assertEquals(mdlDocType, document.docType)
        assertNotNull(document.issuerSigned)
        assertNotNull(document.issuerSigned.issuerAuth)

        val mso = document.MSO
        assertNotNull(mso)
        assertEquals(mdlDocType, mso.docType)
    }
}
