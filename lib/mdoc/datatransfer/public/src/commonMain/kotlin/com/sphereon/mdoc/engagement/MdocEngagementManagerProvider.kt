package com.sphereon.mdoc.engagement

/**
 * Provider interface for exposing [MdocEngagementManager] via DI components.
 *
 * This keeps transport-level services (e.g., NFC) decoupled from datatransfer impl
 * while still allowing them to obtain the engagement manager when present.
 */
interface MdocEngagementManagerProvider {
    val mdocEngagementManager: MdocEngagementManager
}
