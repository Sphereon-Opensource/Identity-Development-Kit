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

package com.sphereon.link.data.ble

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.createAndroidTestAppComponent
import com.sphereon.crypto.kms.KeyManagerService
import com.sphereon.data.link.ble.client.AndroidBlePlatformClient
import com.sphereon.data.link.ble.client.cmd.ScanDevicesArgs
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@RunWith(AndroidJUnit4::class)
class ScanTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
//            Manifest.permission.BLUETOOTH,
//            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

    @Test
    @ExperimentalTime
    fun testScan() = runTest(timeout = 45.seconds) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "mdoc.ble.test.profile.kms.providers.test-software.type" to "software",
                "mdoc.ble.test.profile.kms.providers.test-software.id" to "test-software",
                "mdoc.ble.test.profile.kms.providers.test-software2.type" to "software",
                "mdoc.ble.test.profile.kms.providers.test-software2.id" to "test2"
            )
        )
        val app = createAndroidTestAppComponent(application = context, appId = "mdoc-ble-test", profile = "profile", version = "0.1.0")
        val contextComponent = app.userContextManager.initAnonymous()
        val sessionComponent = contextComponent.getSessionContextManager().initSession("test")



        sessionComponent as KeyManagerService.KmsComponent
        val keyManager = sessionComponent.keyManagerService
        keyManager.generateKeyAsync()

//        val softwareKmsProvider = SoftwareKmsProviderImpl(SoftwareKmsProviderConfig())
//        val keyManager = KeyManagerService(kmsProviders = setOf(softwareKmsProvider), keyResolvers = setOf(CoseJoseProvidedKeyResolverServiceImpl(X509VerifyService())))
        val bleClient = AndroidBlePlatformClient(app.app, logManager = app.appLogManager)

        val (result, duration) = measureTimedValue {
            bleClient.scan(ScanDevicesArgs(maxResults = 1) {
                address = "50:10:10:5B:EB:DE"
            })

        }

        println("duration: $duration, for $result")
        val device = bleClient.connect(result.value.first())
        assertNotNull(device)
        assertTrue(device.isOk)
        assertEquals(device, bleClient.getDevice())
        val mtuResult = bleClient.setMtu(512)
        assertTrue(mtuResult.isOk)


    }


    fun logQrFileLink(qr: String) {

        // 2) URL-encode it
        val encoded = URLEncoder.encode(qr, "UTF-8")

        // 3) Point at a public QR-code API
        //    This will return a PNG of size 200×200
        val url = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=$encoded"

        // 4) Log it—Android Studio will hyperlink http(s) URLs
        Log.i("TEST_QR_HTTP", "Click to view QR in browser:\n$url")
    }

}