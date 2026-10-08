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

package com.sphereon.data.store.blob

/**
 * Well-known blob store backend scheme identifiers.
 *
 * Schemes are plain strings so that custom backends can introduce new values
 * without modifying this file, following the same extensibility pattern as
 * [com.sphereon.crypto.kms.PredefinedKmsProviderTypes].
 */
object BlobStoreSchemes {
    const val MEMORY: String = "memory"
    const val FILESYSTEM: String = "filesystem"
    const val DATABASE: String = "database"
    const val S3: String = "s3"
    const val AZURE_BLOB: String = "azure-blob"
    const val GCS: String = "gcs"
    const val SFTP: String = "sftp"
    const val WEBDAV: String = "webdav"
    const val SHAREPOINT: String = "sharepoint"
    const val CMIS: String = "cmis"
}
