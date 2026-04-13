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

import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType

/**
 * Event subsystem for blob store operations.
 */
object BlobEventSubsystem {
    val BLOB_STORE = EventSubsystem("blob.store")
}

/**
 * Event types emitted by the blob store.
 *
 * Consumers subscribe via [EventHub] filtering:
 * ```kotlin
 * eventHub.subscribe(scope) {
 *     filter {
 *         subsystems(BlobEventSubsystem.BLOB_STORE)
 *     }
 *     onEvent { event -> ... }
 * }
 * ```
 *
 * OKD destruction notifications subscribe to [BLOB_DELETED] events.
 * Audit logging subscribes to all blob events via subsystem filter.
 * EDK metadata sync subscribes to [BLOB_CREATED] and [BLOB_DELETED].
 */
object BlobEventTypes {
    val BLOB_CREATED = EventType("blob.store.created")
    val BLOB_DELETED = EventType("blob.store.deleted")
    val BLOB_COPIED = EventType("blob.store.copied")
    val BLOB_MOVED = EventType("blob.store.moved")
}

/**
 * Event category for blob storage operations.
 */
object BlobEventCategories {
    val STORAGE = EventCategory("storage")
}
