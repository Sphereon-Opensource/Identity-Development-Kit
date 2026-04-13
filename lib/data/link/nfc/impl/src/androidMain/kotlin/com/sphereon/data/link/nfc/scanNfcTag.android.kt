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

package com.sphereon.data.link.nfc

import android.content.pm.PackageManager
import com.sphereon.data.link.nfc.model.NfcIsoTag


actual val nfcSupported: Boolean = true

/**
 * Is set to true if the device supports NFC scanning.
 */
actual val nfcTagScanningSupported: Boolean = false

/**
 * Is set to true if the device supports NFC scanning and [scanNfcTag] works without showing a dialog.
 */
actual val nfcTagScanningSupportedWithoutDialog: Boolean = false

actual suspend fun <T : Any> scanNfcTag(
    message: String?,
    tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?,
): T {
    require(message != null) { "Cannot scan NFC tag without a dialog on Android in this build" }
    throw UnsupportedOperationException("NFC tag scanning is not implemented on Android in this module yet")
}
