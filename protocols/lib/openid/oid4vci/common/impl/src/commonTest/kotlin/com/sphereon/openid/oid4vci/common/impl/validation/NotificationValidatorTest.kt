/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.common.impl.validation

import com.sphereon.openid.oid4vci.common.model.CredentialNotification
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlin.test.Test
import kotlin.test.assertTrue

class NotificationValidatorTest {
    @Test
    fun validNotificationPasses() {
        val notification =
            CredentialNotification(
                notificationId = "notification-123",
                event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
            )
        val result = notificationValidator(notification)
        assertTrue(result is Valid, "A valid notification should pass validation")
    }

    @Test
    fun emptyNotificationIdFails() {
        val notification =
            CredentialNotification(
                notificationId = "",
                event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
            )
        val result = notificationValidator(notification)
        assertTrue(result is Invalid, "An empty notificationId should fail validation")
    }
}
