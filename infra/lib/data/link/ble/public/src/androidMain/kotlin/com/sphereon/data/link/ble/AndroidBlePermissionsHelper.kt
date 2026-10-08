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

package com.sphereon.data.link.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sphereon.di.app.AppGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidBlePermissionsHelper(
    appGraph: AppGraph,
) : BlePermissionsHelper {
    val context = appGraph.application as Context

    private var launcher: ((Array<String>) -> Unit)? = null
    private var resultCallback: ((Boolean) -> Unit)? = null

    override fun setLauncher(launcher: (Array<String>) -> Unit) {
        this.launcher = launcher
    }

    override fun hasPermissions(): Boolean =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun requestPermissions(onResult: (Boolean) -> Unit) {
        checkNotNull(launcher) { "You must call setLauncher() before requestBlePermissions()" }
        resultCallback = onResult
        launcher?.invoke(REQUIRED_PERMISSIONS.toTypedArray())
    }

    // This should be called from app result callback
    fun onPermissionsResult(result: Map<String, Boolean>) {
        val granted = REQUIRED_PERMISSIONS.all { result[it] == true }
        resultCallback?.invoke(granted)
    }

    companion object {
        val REQUIRED_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            } else {
                TODO("VERSION.SDK_INT < S")
            }
    }
}
