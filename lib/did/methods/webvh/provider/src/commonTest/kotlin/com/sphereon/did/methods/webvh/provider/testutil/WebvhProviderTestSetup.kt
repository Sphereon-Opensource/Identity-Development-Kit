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

/**
 * Creates the per-target Metro app graph for `did:webvh` provider E2E tests.
 *
 * Tests live in `commonTest`; each KMP target supplies its own `actual` so
 * the graph compiles cleanly with the platform-correct Metro KSP output.
 * Mirrors the pattern in `lib/did/manager/impl/.../testutil/DidManagerTestSetup.kt`.
 */
expect fun createWebvhProviderTestAppGraph(testInstance: Any): AppGraph
