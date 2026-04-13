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
    TO_APP
}
