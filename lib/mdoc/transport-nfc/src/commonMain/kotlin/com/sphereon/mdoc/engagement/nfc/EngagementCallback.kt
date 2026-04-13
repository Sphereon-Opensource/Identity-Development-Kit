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

package com.sphereon.mdoc.engagement.nfc

/**
 * Callback interface for engagement events
 */
interface EngagementCallback {
    fun onStateChanged(oldState: NfcEngagementState, newState: NfcEngagementState) {}
    fun onMetricsUpdated(metrics: NfcServiceMetrics) {}
    fun onHealthChanged(oldHealth: ServiceHealth, newHealth: ServiceHealth) {}
    fun onError(error: Throwable, state: NfcEngagementState) {}
    fun onEngagementComplete(metrics: NfcServiceMetrics) {}
}

abstract class EngagementCallbackAdapter : EngagementCallback {
    override fun onStateChanged(oldState: NfcEngagementState, newState: NfcEngagementState) {}
    override fun onMetricsUpdated(metrics: NfcServiceMetrics) {}
    override fun onHealthChanged(oldHealth: ServiceHealth, newHealth: ServiceHealth) {}
    override fun onError(error: Throwable, state: NfcEngagementState) {}
    override fun onEngagementComplete(metrics: NfcServiceMetrics) {}
}