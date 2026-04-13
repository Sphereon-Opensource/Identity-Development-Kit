package com.sphereon.mdoc.transport

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Transport types supported by the mdoc framework.
 *
 * Each transport type corresponds to a different way of exchanging mdoc data
 * between holder and reader:
 *
 * - **BLE**: Bluetooth Low Energy (ISO 18013-5)
 * - **NFC**: Near Field Communication (ISO 18013-5)
 * - **REST_API**: Device Retrieval to Website (ISO 18013-7 Annex A)
 * - **OID4VP**: OpenID for Verifiable Presentations (ISO 18013-7 Annex B)
 * - **WIFI_AWARE**: WiFi Aware (future support)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TransportType", exact = true)
enum class TransportType {
    /**
     * Bluetooth Low Energy transport per ISO 18013-5.
     *
     * Used for proximity presentations where holder and reader
     * are in close physical proximity (typically < 10 meters).
     */
    BLE,

    /**
     * Near Field Communication transport per ISO 18013-5.
     *
     * Used for very close proximity presentations where holder
     * taps their device against reader (typically < 10 cm).
     */
    NFC,

    /**
     * REST API / Device Retrieval to Website per ISO 18013-7 Annex A.
     *
     * Used for remote presentations over HTTPS where holder sends
     * DeviceResponse to a reader's web endpoint.
     */
    REST_API,

    /**
     * OpenID for Verifiable Presentations per ISO 18013-7 Annex B.
     *
     * OAuth-based presentation flow using OpenID Connect with
     * verifiable credentials extensions.
     */
    OID4VP,

    /**
     * WiFi Aware transport (future support).
     *
     * Uses WiFi Aware for device discovery and data exchange
     * without requiring WiFi access point.
     */
    WIFI_AWARE
}