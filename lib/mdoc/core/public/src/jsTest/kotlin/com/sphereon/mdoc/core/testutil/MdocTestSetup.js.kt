/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc.core.testutil

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.CoseCryptoServiceImpl
import com.sphereon.di.app.AppGraph
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.MdocSignServiceImpl
import com.sphereon.mdoc.SessionTranscriptCborCodecImpl
import com.sphereon.mdoc.core.component.createJsMdocCoreTestAppGraph
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl

actual fun createMdocTestAppGraph(testInstance: Any): AppGraph =
    createJsMdocCoreTestAppGraph(
        application = testInstance,
    )

actual fun createMdocSignService(execution: SessionExecution): MdocSignService =
    MdocSignServiceImpl(
        coseCryptoService = CoseCryptoServiceImpl(),
        execution = execution,
        mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
        sessionTranscriptCborCodec = SessionTranscriptCborCodecImpl(),
    )
