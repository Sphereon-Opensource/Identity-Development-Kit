/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh.provider.testutil

import com.sphereon.di.app.AppGraph
import com.sphereon.did.methods.webvh.provider.createJsWebvhProviderTestAppGraph

actual fun createWebvhProviderTestAppGraph(testInstance: Any): AppGraph = createJsWebvhProviderTestAppGraph(application = testInstance)
