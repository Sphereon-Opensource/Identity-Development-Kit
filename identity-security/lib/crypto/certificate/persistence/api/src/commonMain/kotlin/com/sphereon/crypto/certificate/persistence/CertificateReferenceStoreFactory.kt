/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.certificate.persistence

/** Factory SPI used by the runtime selector to choose the configured database dialect. */
interface CertificateReferenceStoreFactory {
    val type: String

    fun createStore(): CertificateReferenceStore

    companion object {
        const val TYPE_SQLITE = "sqlite"
        const val TYPE_POSTGRESQL = "postgresql"
        const val TYPE_MYSQL = "mysql"
    }
}
