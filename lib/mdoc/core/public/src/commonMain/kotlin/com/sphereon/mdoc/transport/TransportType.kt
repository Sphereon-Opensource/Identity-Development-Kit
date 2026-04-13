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
    WIFI_AWARE,
}
