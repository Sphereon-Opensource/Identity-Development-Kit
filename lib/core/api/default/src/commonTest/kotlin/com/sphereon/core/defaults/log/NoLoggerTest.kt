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
 */

package com.sphereon.core.defaults.log

import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.api.context.IdkScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class NoLoggerTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "no-logger-test", "test-profile", "0.0.1-TEST"
    )

    // ========== AppNoLogService Tests ==========

    @Test
    fun appNoLogServiceHasCorrectId() {
        val appComponent = createAppComponent()
        try {
            val service = AppNoLogService()
            assertEquals(AbstractNoLogService.SERVICE_ID, service.id)
            assertEquals("NoLogger", service.id)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appNoLogServiceIsDisabled() {
        val appComponent = createAppComponent()
        try {
            val service = AppNoLogService()
            assertFalse(service.isEnabled)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appNoLogServiceHasAppScope() {
        val appComponent = createAppComponent()
        try {
            val service = AppNoLogService()
            assertEquals(IdkScope.APP, service.scope)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== UserContextNoLogService Tests ==========

    @Test
    fun userContextNoLogServiceHasCorrectId() {
        val appComponent = createAppComponent()
        try {
            val service = UserContextNoLogService()
            assertEquals(AbstractNoLogService.SERVICE_ID, service.id)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextNoLogServiceIsDisabled() {
        val appComponent = createAppComponent()
        try {
            val service = UserContextNoLogService()
            assertFalse(service.isEnabled)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextNoLogServiceHasUserScope() {
        val appComponent = createAppComponent()
        try {
            val service = UserContextNoLogService()
            assertEquals(IdkScope.USER, service.scope)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== SessionNoLogService Tests ==========

    @Test
    fun sessionNoLogServiceHasCorrectId() {
        val appComponent = createAppComponent()
        try {
            val service = SessionNoLogService()
            assertEquals(AbstractNoLogService.SERVICE_ID, service.id)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionNoLogServiceIsDisabled() {
        val appComponent = createAppComponent()
        try {
            val service = SessionNoLogService()
            assertFalse(service.isEnabled)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionNoLogServiceHasSessionScope() {
        val appComponent = createAppComponent()
        try {
            val service = SessionNoLogService()
            assertEquals(IdkScope.SESSION, service.scope)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== AbstractNoLogService Companion Tests ==========

    @Test
    fun abstractNoLogServiceServiceIdConstant() {
        val appComponent = createAppComponent()
        try {
            assertEquals("NoLogger", AbstractNoLogService.SERVICE_ID)
        } finally {
            appComponent.destroy()
        }
    }
}
