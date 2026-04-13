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

package com.sphereon.data.store.okd.server

import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.BlobStoreSchemes
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import com.sphereon.data.store.blob.okd.OkdAuthConfig
import com.sphereon.data.store.blob.okd.OkdAuthMode
import com.sphereon.data.store.blob.okd.OkdBlobMapping
import com.sphereon.data.store.blob.okd.OkdBlobStoreConfig
import com.sphereon.data.store.okd.generated.models.DocumentMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OkdEndpointCommandsTest {
    @Test
    fun okdBlobStoreConfigDefaults() {
        val config = OkdBlobStoreConfig()
        assertEquals("okd", config.backendId)
        assertEquals("okd", config.id)
        assertTrue(config.enabled)
        assertEquals("", config.baseUrl)
        assertEquals(OkdAuthMode.PASSTHROUGH, config.auth.mode)
    }

    @Test
    fun okdBlobStoreConfigWithClientCredentials() {
        val config =
            OkdBlobStoreConfig(
                id = "school-dms",
                baseUrl = "https://dms.school.nl/api/v5",
                auth =
                    OkdAuthConfig(
                        mode = OkdAuthMode.CLIENT_CREDENTIALS,
                        tokenUri = "https://dms.school.nl/oauth2/token",
                        clientId = "portal",
                        clientSecret = "secret",
                        scopes = listOf("okd:alldocuments"),
                    ),
            )
        assertEquals("school-dms", config.id)
        assertEquals("https://dms.school.nl/api/v5", config.baseUrl)
        assertEquals(OkdAuthMode.CLIENT_CREDENTIALS, config.auth.mode)
        assertEquals("portal", config.auth.clientId)
        assertEquals(1, config.auth.scopes.size)
    }

    @Test
    fun mappingPreservesDocumentTempDownloadUrl() {
        val okdMeta =
            DocumentMetadata(
                dmsDocumentId = "doc-1",
                title = "Test",
                documentTempDownloadUrl = "https://example.com/signed/doc-1",
            )
        val descriptor = OkdBlobMapping.fromOkdMetadata(okdMeta)
        assertEquals("https://example.com/signed/doc-1", descriptor.metadata.custom["documentTempDownloadUrl"])
    }

    @Test
    fun mappingRoundtripPreservesTitle() {
        val original =
            DocumentMetadata(
                dmsDocumentId = "rt-1",
                title = "Round Trip",
                format = "application/pdf",
                documentname = "test.pdf",
                documentsize = 1234L,
            )
        val descriptor = OkdBlobMapping.fromOkdMetadata(original)
        val back = OkdBlobMapping.toOkdMetadata(descriptor)

        assertEquals("rt-1", back.dmsDocumentId)
        assertEquals("Round Trip", back.title)
        assertEquals("application/pdf", back.format)
        assertEquals("test.pdf", back.documentname)
        assertEquals(1234L, back.documentsize)
    }

    @Test
    fun mappingToOkdWithTempUrl() {
        val descriptor =
            BlobDescriptor(
                path = "doc-2",
                storeId = BlobStoreSchemes.MEMORY,
                sizeBytes = 500L,
                contentType = "text/plain",
                filename = "readme.txt",
            )
        val okdMeta = OkdBlobMapping.toOkdMetadata(descriptor, tempDownloadUrl = "https://cdn.example.com/doc-2")
        assertEquals("https://cdn.example.com/doc-2", okdMeta.documentTempDownloadUrl)
        assertEquals("readme.txt", okdMeta.title) // Falls back to filename when no title in custom
    }

    @Test
    fun mappingFromOkdWithNullOptionalFields() {
        val okdMeta = DocumentMetadata(dmsDocumentId = "minimal")
        val descriptor = OkdBlobMapping.fromOkdMetadata(okdMeta)

        assertEquals("minimal", descriptor.path)
        assertEquals(0L, descriptor.sizeBytes)
        assertNull(descriptor.contentType)
        assertNull(descriptor.filename)
        assertNull(descriptor.createdAt)
    }

    @Test
    fun authConfigDefaults() {
        val auth = OkdAuthConfig()
        assertEquals(OkdAuthMode.PASSTHROUGH, auth.mode)
        assertNull(auth.token)
        assertNull(auth.tokenUri)
        assertNull(auth.clientId)
        assertEquals("Authorization", auth.authHeader)
        assertTrue(auth.useTenantFromContext)
    }

    @Test
    fun okdAuthConfigClientCredentialsValidation() {
        val auth =
            OkdAuthConfig(
                mode = OkdAuthMode.CLIENT_CREDENTIALS,
                tokenUri = "https://auth.school.nl/token",
                clientId = "portal",
                clientSecret = "\${OKD_SECRET}",
                scopes = listOf("okd:alldocuments", "okd:studentinfo"),
            )
        assertEquals(OkdAuthMode.CLIENT_CREDENTIALS, auth.mode)
        assertEquals(2, auth.scopes.size)
        assertTrue(auth.scopes.contains("okd:alldocuments"))
    }

    @Test
    fun okdConfigScopeBindingDefault() {
        val config = OkdBlobStoreConfig(baseUrl = "https://dms.school.nl")
        assertEquals(BlobStoreScopeBinding.TENANT, config.scopeBinding)
    }

    @Test
    fun mappingFromOkdPreservesAllCustomFields() {
        val okdMeta =
            DocumentMetadata(
                dmsDocumentId = "full-1",
                title = "Full Test",
                format = "application/pdf",
                documentname = "full.pdf",
                documentsize = 99999L,
                documentTempDownloadUrl = "https://signed.url/full-1",
            )
        val desc = OkdBlobMapping.fromOkdMetadata(okdMeta)
        assertEquals("Full Test", desc.metadata.custom["title"])
        assertEquals("full.pdf", desc.metadata.custom["documentname"])
        assertEquals("https://signed.url/full-1", desc.metadata.custom["documentTempDownloadUrl"])
        assertEquals("application/pdf", desc.contentType)
        assertEquals(99999L, desc.sizeBytes)
    }

    @Test
    fun mappingToOkdFallsBackToFilenameForTitle() {
        val desc =
            BlobDescriptor(
                path = "no-title",
                storeId = BlobStoreSchemes.MEMORY,
                sizeBytes = 10L,
                filename = "fallback.txt",
                metadata = BlobMetadata(),
            )
        val okd = OkdBlobMapping.toOkdMetadata(desc)
        assertEquals("fallback.txt", okd.title)
    }

    @Test
    fun mappingToOkdWithNoFilenameOrTitle() {
        val desc =
            BlobDescriptor(
                path = "bare",
                storeId = BlobStoreSchemes.MEMORY,
                sizeBytes = 5L,
            )
        val okd = OkdBlobMapping.toOkdMetadata(desc)
        assertNull(okd.title)
        assertNull(okd.documentname)
    }
}
