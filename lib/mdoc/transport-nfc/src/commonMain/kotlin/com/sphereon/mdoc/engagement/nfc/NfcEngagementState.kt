/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.mdoc.engagement.nfc

/**
 * Represents the different states of the mDoc NFC engagement process
 */
enum class NfcEngagementState {
    /** No engagement is active */
    IDLE,

    /** NFC engagement is starting */
    NFC_STARTING,

    /** NFC engagement is active and processing APDUs */
    NFC_ACTIVE,

    /** NFC handover completed, transitioning to BLE */
    HANDOVER_COMPLETE,

    /** Transfer manager is active over BLE */
    BLE_TRANSFER_ACTIVE,

    /** Engagement/transfer is complete */
    COMPLETE,

    /** An error occurred during NFC phase */
    NFC_ERROR,

    /** An error occurred during BLE phase */
    BLE_ERROR,

    /** Engagement was cancelled by user or system */
    CANCELED,

    ;

    fun isErrorState(): Boolean = this in listOf(NFC_ERROR, BLE_ERROR, CANCELED)

    fun isActiveState(): Boolean = this in listOf(NFC_STARTING, NFC_ACTIVE, HANDOVER_COMPLETE, BLE_TRANSFER_ACTIVE)
}
