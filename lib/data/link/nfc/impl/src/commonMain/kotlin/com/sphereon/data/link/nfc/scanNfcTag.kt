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

import com.sphereon.data.link.nfc.model.NfcIsoTag

expect val nfcSupported: Boolean

/**
 * Is set to true if the device supports NFC scanning.
 */
expect val nfcTagScanningSupported: Boolean

/**
 * Is set to true if the device supports NFC scanning and [scanNfcTag] works without showing a dialog.
 */
expect val nfcTagScanningSupportedWithoutDialog: Boolean

/**
 * Shows a dialog prompting the user to scan a NFC tag.
 *
 * This only works if [nfcTagScanningSupported] is `true`.
 *
 * When a tag is in the field, [tagInteractionFunc] is called and is passed a [com.sphereon.data.link.nfc.model.NfcIsoTag] which can be
 * used to communicate with the remote tag and also a function to update the message shown in the dialog.
 * The latter is useful if the transaction is expected to take a long time, for example if reading data
 * from a passport this can be used to convey the progress.
 *
 * If the given [tagInteractionFunc] returns `null` then polling is restarted, the session is kept alive,
 * the dialog stays visible, and the function may be called again if another tag enters the field. Otherwise
 * the session ends, a brief success indication is displayed, the dialog is removed, and the return value of
 * [tagInteractionFunc] is returned.
 *
 * If [tagInteractionFunc] throws an exception which isn't [com.sphereon.data.link.nfc.model.NfcTagLostException] the message in
 * the [Throwable] is briefly displayed in the dialog with an error indication, and the exception is
 * rethrown. If [com.sphereon.data.link.nfc.model.NfcTagLostException] is thrown, the behavior is the same as if [tagInteractionFunc]
 * returns `null`, that is, the dialog is kept visible so the user can scan another tag.
 * This behavior is to properly handle emulated tags - such as on Android - which may be showing
 * disambiguation UI if multiple applications have registered for the same AID.
 *
 * If the [message] parameter is `null` no user dialog is shown but everything else works as expected
 * and scanning will continue until the view-model holding [org.multipaz.prompt.PromptModel] is cleared
 * or programmatically dismissed by canceling the coroutine this is launched from.
 *
 * @param message the message to initially show in the dialog or `null` to not show a dialog. Not all
 *   platforms supports not showing a dialog, use [nfcTagScanningSupportedWithoutDialog] to check at runtime
 *   if the platform supports this.
 * @param tagInteractionFunc the function which is called when the tag is in the field, see above.
 * @return return value of [tagInteractionFunc]
 * @throws PromptDismissedException if the user canceled the dialog
 * @throws IllegalArgumentException if [message] is `null` and [nfcTagScanningSupportedWithoutDialog] is `false`.
 * @throws Throwable exceptions thrown in [tagInteractionFunc] are rethrown.
 */
// FIXME
expect suspend fun<T: Any> scanNfcTag(
    message: String?,
    tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?,
): T
