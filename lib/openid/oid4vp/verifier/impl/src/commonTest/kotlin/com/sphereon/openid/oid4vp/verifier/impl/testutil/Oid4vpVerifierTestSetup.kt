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

package com.sphereon.openid.oid4vp.verifier.impl.testutil

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionInstance

expect fun createOid4vpVerifierTestAppGraph(testInstance: Any): AppGraph

class Oid4vpVerifierTestContext(
    sessionId: String,
    testInstance: Any,
) {
    val app: AppGraph = createOid4vpVerifierTestAppGraph(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId, principalType = com.sphereon.di.context.PrincipalType.USER)

    val execution: SessionExecution = session.sessionExecution
    val sessionContext: SessionContext = execution.sessionContext
}
