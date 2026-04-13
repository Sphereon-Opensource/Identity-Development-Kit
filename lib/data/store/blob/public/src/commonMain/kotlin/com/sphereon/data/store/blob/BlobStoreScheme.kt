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
