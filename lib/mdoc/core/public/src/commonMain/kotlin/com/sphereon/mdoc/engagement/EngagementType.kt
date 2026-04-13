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

package com.sphereon.mdoc.engagement

/**
 * Type of engagement (QR, NFC, or TO_APP for reverse/app-to-app).
 *
 * ## URI Schemes by Engagement Type
 *
 * Different engagement types use different URI schemes per ISO specifications:
 *
 * ### QR Code Engagement (ISO 18013-5)
 * - **Scheme**: `mdoc:` (opaque URI, no slashes)
 * - **Format**: `mdoc:<base64url-of-DeviceEngagement>`
 * - **Use case**: Holder displays QR code for reader to scan
 * - **Data**: Contains DeviceEngagement (holder's ephemeral key and retrieval methods)
 *
 * ### App-to-App Engagement / Reverse Engagement
 * - **Scheme**:
 *   - `mdoc:` (opaque URI, no slashes) for classic reverse engagement over BLE/NFC (ISO 18013-5)
 *   - `mdoc://` (hierarchical URI, with slashes) for website retrieval over HTTPS (ISO 18013-7 Annex A)
 * - **Format**: `mdoc:<base64url-of-ReaderEngagement>` or `mdoc://<base64url-of-ReaderEngagement>`
 * - **Use case**: Reader displays QR code / deep link for holder to scan/invoke
 * - **Data**: Contains ReaderEngagement (reader's ephemeral key and retrieval methods)
 *
 * **Important**: These two schemes are NOT interchangeable. Each part of the standard
 * uses a specific one based on who initiates the engagement.
 */
enum class EngagementType {
    /** ISO 18013-5 - NFC proximity-based engagement */
    NFC,

    /**
     * ISO 18013-5 - QR code-based engagement (holder displays QR).
     * Uses `mdoc:` scheme (no slashes).
     */
    QR,

    /**
     * ISO 18013-7 - App-to-app / Reverse engagement (reader displays QR).
     * Uses `mdoc:` (classic reverse engagement) or `mdoc://` (website retrieval).
     */
    TO_APP,
}
