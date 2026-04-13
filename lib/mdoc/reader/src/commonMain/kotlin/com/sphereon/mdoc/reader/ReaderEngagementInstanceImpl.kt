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

package com.sphereon.mdoc.reader

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.core.api.log.LogManager
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Simplified reader engagement instance for tracking reader-side engagement.
 *
 * This provides:
 * - Unique ID for tracking the engagement session
 * - DeviceEngagement (forward: reader scans holder QR)
 * - ReaderEngagement (reverse: reader shows QR for holder to scan)
 * - Reader ephemeral key
 *
 * This is used by MdocReaderManager to initialize data channels.
 * Unlike the full EngagementInstance, this is a lightweight structure
 * specifically for reader needs.
 */
@OptIn(ExperimentalUuidApi::class,ExperimentalObjCName::class)
@ObjCName("ReaderEngagementInstanceImpl", exact = true)
data class ReaderEngagementInstanceImpl(
    val id: Uuid = Uuid.random(),
    val logManager: LogManager,
    val deviceEngagement: DeviceEngagement?, // Forward engagement
    val readerEngagement: ReaderEngagement?, // Reverse engagement
    var readerEphemeralKey: ResolvedKeyInfoType<CoseKeyType>? = null // Set when created in sendRequest()
)
