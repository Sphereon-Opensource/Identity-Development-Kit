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
 */

package com.sphereon.crypto.kms.provider.software.testutil

import com.sphereon.core.api.app.CoreApiAppExtensionGraph
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.DefaultSyncConfigSnapshotCache
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.crypto.core.kms.KmsProviderManager
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactory
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance

expect fun createSoftwareKmsTestAppGraph(testInstance: Any): AppGraph

class SoftwareKmsTestContext(
    sessionId: String,
    testInstance: Any,
) {
    val app: AppGraph = createSoftwareKmsTestAppGraph(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)

    private val appExtension: CoreApiAppExtensionGraph
        get() = app as CoreApiAppExtensionGraph

    val softwareKmsProviderFactory: SoftwareKmsProviderFactory
        get() = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider

    val kmsProviderManager: KmsProviderManager
        get() = (app as KmsProviderManager.Graph).kmsProviderManager

    val appLogManager: AppLogManager
        get() = appExtension.appLogManager

    val appConfigService: AppConfigService
        get() = appExtension.appConfig

    fun clearConfigCache() {
        (app as DefaultSyncConfigSnapshotCache.Graph).syncConfigSnapshotCache.clear()
    }
}
