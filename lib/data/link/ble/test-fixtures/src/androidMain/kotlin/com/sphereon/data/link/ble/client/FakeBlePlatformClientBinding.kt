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

package com.sphereon.data.link.ble.client

import com.sphereon.core.api.log.AppLogManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Android-specific DI binding for [FakeBlePlatformClient].
 *
 * This binding replaces [AndroidBlePlatformClient] in test scopes,
 * automatically providing the fake implementation when the test-fixtures
 * module is included in dependencies.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<BlePlatformClient>(), replaces = [AndroidBlePlatformClient::class])
class FakeBlePlatformClientBinding(
    logManager: AppLogManager,
) : FakeBlePlatformClient(logManager)
